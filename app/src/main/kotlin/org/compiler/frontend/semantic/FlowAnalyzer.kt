package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.Block
import org.compiler.frontend.ast.models.Break
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.Continue
import org.compiler.frontend.ast.models.DoWhile
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.For
import org.compiler.frontend.ast.models.ForEach
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.If
import org.compiler.frontend.ast.models.Node
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.Return
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.While

/**
 * Valida las reglas que dependen de DONDE esta una sentencia, no de que tipos tiene.
 *
 * Lleva dos contadores propios en vez de consultar el arbol de ambitos: son dos
 * enteros y hacen el codigo trivial de leer.
 */
class FlowAnalyzer(private val diagnostics: Diagnostics) {

    private var loopDepth = 0
    private var functionDepth = 0

    fun analyze(program: Program) {
        analyzeStatements(program.statements)
    }

    // Recorre una lista y ademas busca codigo muerto EN ESA LISTA.
    //
    // Todo lugar del AST con una lista de sentencias pasa por aqui: el programa, los
    // bloques, los cuerpos de funcion, los cuerpos de case.
    private fun analyzeStatements(statements: List<Statement>) {
        statements.forEach { analyzeStatement(it) }
        checkUnreachable(statements)
    }

    private fun analyzeBlock(block: Block) = analyzeStatements(block.statements)

    private fun analyzeStatement(stmt: Statement) {
        when (stmt) {
            is Break -> if (loopDepth == 0) {
                report(stmt, "'break' solo se puede usar dentro de un bucle")
            }

            is Continue -> if (loopDepth == 0) {
                report(stmt, "'continue' solo se puede usar dentro de un bucle")
            }

            is Return -> if (functionDepth == 0) {
                report(stmt, "'return' solo se puede usar dentro de una función")
            }

            is While -> withinLoop { analyzeBlock(stmt.body) }
            is DoWhile -> withinLoop { analyzeBlock(stmt.body) }
            is For -> withinLoop { analyzeBlock(stmt.body) }
            is ForEach -> withinLoop { analyzeBlock(stmt.body) }

            is FunctionDeclaration -> analyzeFunction(stmt)

            is If -> {
                analyzeBlock(stmt.thenBranch)
                stmt.elseBranch?.let { analyzeBlock(it) }
            }

            is Block -> analyzeBlock(stmt)
            is Switch -> analyzeSwitch(stmt)

            is TryCatch -> {
                analyzeBlock(stmt.tryBlock)
                analyzeBlock(stmt.catchBlock)
            }

            is ClassDeclaration -> stmt.members.forEach { analyzeStatement(it) }

            // Las demas no afectan el flujo.
            else -> Unit
        }
    }

    private inline fun withinLoop(body: () -> Unit) {
        loopDepth += 1
        body()
        loopDepth -= 1
    }

    // Un switch NO sube loopDepth: no es un bucle. Por eso un `break` dentro de un
    // switch que no esta en un bucle sigue dando error.
    //
    // Y dentro de un bucle, ese break rompe el BUCLE: Compiscript no tiene break de
    // switch, porque no hay fall-through.
    private fun analyzeSwitch(stmt: Switch) {
        stmt.cases.forEach { analyzeStatements(it.body) }
        stmt.defaultBody?.let { analyzeStatements(it) }
    }

    private fun analyzeFunction(decl: FunctionDeclaration) {
        val previousLoopDepth = loopDepth

        functionDepth += 1

        // Una funcion anidada dentro de un bucle NO hereda ese bucle:
        //   while (x) { function f() { break; } }   <- ERROR, y hay que atraparlo
        loopDepth = 0

        analyzeBlock(decl.body)
        checkAllPathsReturn(decl)

        loopDepth = previousLoopDepth
        functionDepth -= 1
    }

    // Una sentencia despues de return / break / continue nunca se ejecuta.
    //
    // Se reporta solo la PRIMERA: diez errores despues de un return no informan mas
    // que uno, y el mensaje nombra donde esta el corte.
    private fun checkUnreachable(statements: List<Statement>) {
        val terminatorIndex = statements.indexOfFirst { isTerminator(it) }
        if (terminatorIndex < 0 || terminatorIndex == statements.size - 1) return

        val terminator = statements[terminatorIndex]
        report(
            statements[terminatorIndex + 1],
            "Código inalcanzable: nunca se ejecuta porque " +
                "'${describeTerminator(terminator)}' de la línea " +
                "${terminator.location.line} corta el flujo"
        )
    }

    private fun isTerminator(stmt: Statement): Boolean =
        stmt is Return || stmt is Break || stmt is Continue

    private fun describeTerminator(stmt: Statement): String = when (stmt) {
        is Return -> "return"
        is Break -> "break"
        is Continue -> "continue"
        else -> "el salto"
    }

    // Si una funcion declara tipo de retorno, TODOS sus caminos deben devolver algo.
    private fun checkAllPathsReturn(decl: FunctionDeclaration) {
        // Sin tipo declarado la funcion es void: no tiene que retornar (decision 15).
        val returnType = decl.returnType ?: return

        if (!alwaysReturns(decl.body.statements)) {
            report(
                decl,
                "La función '${decl.name}' declara devolver '${returnType.name}' " +
                    "pero hay caminos que no retornan"
            )
        }
    }

    // Basta que UNA garantice: se ejecutan en secuencia, asi que llegar a esa
    // sentencia significa retornar.
    private fun alwaysReturns(statements: List<Statement>): Boolean =
        statements.any { alwaysReturns(it) }

    // No busca un `return`: le pregunta a cada construccion si ELLA garantiza uno, y
    // cada una contesta preguntandole lo mismo a sus hijos.
    private fun alwaysReturns(stmt: Statement): Boolean = when (stmt) {
        is Return -> true

        is Block -> alwaysReturns(stmt.statements)

        // Solo si hay else Y las dos ramas retornan. Sin else, la condicion falsa no
        // pasa por ninguna.
        is If -> stmt.elseBranch != null &&
            alwaysReturns(stmt.thenBranch.statements) &&
            alwaysReturns(stmt.elseBranch.statements)

        // El cuerpo corre al menos una vez.
        is DoWhile -> alwaysReturns(stmt.body.statements)

        // while y for podrian no ejecutarse nunca, salvo que la condicion sea
        // constantemente verdadera y nadie se escape con break.
        is While -> isAlwaysTrue(stmt.condition) && !escapesWithBreak(stmt.body)
        is For -> (stmt.condition == null || isAlwaysTrue(stmt.condition)) &&
            !escapesWithBreak(stmt.body)

        // Sin default, un valor que no coincida con ningun case pasa de largo.
        is Switch -> stmt.defaultBody != null &&
            stmt.cases.all { alwaysReturns(it.body) } &&
            alwaysReturns(stmt.defaultBody)

        else -> false
    }

    // Funciona con `true`, con `1 == 1` y con una `const`, porque la Fase 4 ya plego
    // el valor. Con una variable mutable no: ver la limitacion en el ticket.
    private fun isAlwaysTrue(condition: Expression): Boolean =
        condition.constantValue == true

    // ¿Este cuerpo tiene un break que salga de ESTE bucle?
    //
    //   while (true) { return 1; }            <- garantiza retorno
    //   while (true) { if (x) { break; } }    <- NO
    private fun escapesWithBreak(block: Block): Boolean =
        block.statements.any { containsOwnBreak(it) }

    private fun containsOwnBreak(stmt: Statement): Boolean = when (stmt) {
        is Break -> true

        is Block -> escapesWithBreak(stmt)
        is If -> escapesWithBreak(stmt.thenBranch) ||
            stmt.elseBranch?.let { escapesWithBreak(it) } == true
        is TryCatch -> escapesWithBreak(stmt.tryBlock) || escapesWithBreak(stmt.catchBlock)
        is Switch -> stmt.cases.any { case -> case.body.any { containsOwnBreak(it) } } ||
            stmt.defaultBody?.any { containsOwnBreak(it) } == true

        // Los bucles y funciones ANIDADOS no cuentan: un break ahi dentro sale del
        // interno, no de este.
        //
        //   while (true) {
        //     while (y) { break; }   <- sale del while interno
        //     return 1;              <- asi que el externo SI garantiza retorno
        //   }
        is While, is DoWhile, is For, is ForEach, is FunctionDeclaration -> false

        else -> false
    }

    private fun report(node: Node, message: String) {
        diagnostics.report(CompilerError.SemanticError(node.location, message))
    }
}
