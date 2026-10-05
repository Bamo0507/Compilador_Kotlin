package org.compiler.frontend.intermediate

import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.Block
import org.compiler.frontend.ast.models.Break
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.Continue
import org.compiler.frontend.ast.models.DoWhile
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.ExpressionStatement
import org.compiler.frontend.ast.models.For
import org.compiler.frontend.ast.models.ForEach
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.If
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.frontend.ast.models.Print as PrintStatement
import org.compiler.frontend.ast.models.Return as ReturnStatement

/**
 * Etapa G: el AST ya validado a codigo de tres direcciones.
 *
 * Una funcion por construccion, igual que el TypeChecker y el Interpreter. Las
 * sentencias no devuelven nada; las expresiones devuelven la Address donde quedo su
 * valor. Toda expresion pasa por su GDA, y todo temporal por el pool.
 *
 * Lo que esta fase todavia no traduce es un TODO con el punto que lo cubre. El
 * pipeline atrapa el NotImplementedError y deja el TAC en null, asi un programa con
 * un `while` no tumba el IDE.
 */
class TacGenerator {

    private val instructions = mutableListOf<Quadruple>()
    private val temporaries = TemporaryAllocator()
    private var nextLabel = 1

    fun generate(program: Program): TacProgram {
        program.statements.forEach { generateStatement(it) }
        return TacProgram(instructions.toList(), temporaries.temporaryCount)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sentencias
    // ══════════════════════════════════════════════════════════════════════

    private fun generateStatement(stmt: Statement) {
        when (stmt) {
            is VariableDeclaration -> generateVariableDeclaration(stmt)
            is Assignment -> generateAssignment(stmt)
            is PrintStatement -> generatePrint(stmt)

            // `f();` o `x + 1;`: el valor se calcula y se descarta.
            is ExpressionStatement -> temporaries.consume(generateExpression(stmt.expr))

            // El bloque no genera nada propio: las variables ya son Name con su
            // Symbol, asi que dos `x` de bloques distintos ya son direcciones distintas.
            is Block -> stmt.statements.forEach { generateStatement(it) }

            is If, is While, is DoWhile, is For, is ForEach, is Switch,
            is Break, is Continue -> TODO("control de flujo (punto 6)")
            is TryCatch -> TODO("try/catch (punto 6)")
            is FunctionDeclaration, is ReturnStatement -> TODO("funciones y llamadas (punto 9)")
            is ClassDeclaration -> TODO("clases y objetos (punto 10)")
        }

        // El invariante del pool: entre sentencias no queda ningun temporal vivo. Si
        // queda uno, el generador perdio una lectura y el TAC estaria mal: es un bug
        // del compilador, y es mejor que salte aqui que en el assembler.
        check(!temporaries.hasLiveTemporaries) {
            "Quedaron temporales vivos al terminar la sentencia de la linea ${stmt.location.line}"
        }
    }

    // `let x = e;` -> el codigo de e, y despues `x = <direccion de e>`. La copia final
    // se conserva aunque cueste una instruccion (decision 33): es la forma de la
    // diapositiva 24, y escribir directo en x es una optimizacion que la teoria no
    // muestra.
    //
    // `let x: integer;` -> `x = 0`: el cero de su tipo, el mismo que usa el interprete.
    private fun generateVariableDeclaration(decl: VariableDeclaration) {
        val symbol = requireNotNull(decl.symbol) { "'${decl.name}' sin Symbol: ¿corrio el TypeChecker?" }
        val target = Name(symbol)

        val initializer = decl.initializer
        if (initializer == null) {
            instructions += Copy(target, Constant(defaultValueOf(symbol.type)))
            return
        }

        copyInto(target, generateExpression(initializer))
    }

    private fun generateAssignment(stmt: Assignment) {
        val target = stmt.target as? Identifier
            ?: TODO("asignacion a campos y elementos de lista (punto 10)")

        copyInto(Name(symbolOf(target)), generateExpression(stmt.value))
    }

    private fun generatePrint(stmt: PrintStatement) {
        val value = generateExpression(stmt.expr)
        instructions += Print(value, ExpressionDag.kindOf(stmt.expr.type))
        temporaries.consume(value)
    }

    private fun copyInto(target: Name, value: Address) {
        instructions += Copy(target, value)
        temporaries.consume(value)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Expresiones: a traves del GDA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Construye el GDA de la expresion y lo emite en postorden desde la raiz.
     *
     * Cada nodo Operation se emite UNA vez y se recuerda su direccion: la segunda vez
     * que un padre lo pide, recibe el mismo temporal sin recalcular. Cada lectura de un
     * hijo consume el temporal, y el de cada nodo se pide con tantos usos como padres
     * tiene. La raiz tiene un lector mas: la sentencia que la contiene.
     */
    private fun generateExpression(expr: Expression): Address {
        val dag = ExpressionDag.build(expr)
        val emitted = HashMap<Int, Address>()

        fun usesOf(node: Int): Int = dag.parentCount[node] + if (node == dag.root) 1 else 0

        fun emit(node: Int): Address {
            emitted[node]?.let { return it }

            val address = when (val dagNode = dag.nodes[node]) {
                is DagNode.Leaf -> dagNode.address

                is DagNode.Operation -> {
                    val operands = readOperands(dagNode.operands, dag, ::emit)

                    val operation = dagNode.operation
                    if (needsZeroCheck(operation, operands)) {
                        emitZeroCheck(operands[1], dag.lineOf(node))
                    }

                    // Primero se consumen los operandos y DESPUES se pide el resultado:
                    // asi `t1 = t1 + d` puede reutilizar el nombre que acaba de liberar.
                    operands.forEach { temporaries.consume(it) }
                    val result = temporaries.newTemp(usesOf(node))
                    instructions += quadrupleOf(operation, result, operands)
                    result
                }

                // `x = (y = 5)`: emite `y = 5`, y el valor de la asignacion es lo que
                // quedo en y. El padre lee Name(y), no un temporal.
                is DagNode.Assign -> {
                    copyInto(dagNode.target, emit(dagNode.value))
                    dagNode.target
                }

                is DagNode.Untranslated -> TODO(untranslatedReason(dagNode.expression))
            }

            emitted[node] = address
            return address
        }

        return emit(dag.root)
    }

    /**
     * Los operandos de una operacion, de izquierda a derecha.
     *
     * Con una asignacion anidada hay un caso fino: en `x + (x = 5)` la x de la
     * izquierda vale lo que valia ANTES de la asignacion, porque se evalua primero.
     * Pero una hoja no genera instruccion: se lee recien cuando se emite la suma, y
     * para entonces la asignacion ya corrio. Por eso, si el operando derecho escribe
     * en la variable de la izquierda, la izquierda se copia antes a un temporal.
     */
    private fun readOperands(
        operands: List<Int>,
        dag: ExpressionDag,
        emit: (Int) -> Address
    ): List<Address> {
        val addresses = mutableListOf<Address>()
        operands.forEachIndexed { position, operand ->
            val address = emit(operand)

            val later = operands.drop(position + 1)
            if (address is Name && later.any { assignsTo(dag, it, address.symbol) }) {
                val snapshot = temporaries.newTemp(uses = 1)
                instructions += Copy(snapshot, address)
                addresses += snapshot
            } else {
                addresses += address
            }
        }
        return addresses
    }

    private fun assignsTo(dag: ExpressionDag, node: Int, symbol: Symbol): Boolean =
        when (val dagNode = dag.nodes[node]) {
            is DagNode.Assign -> dagNode.target.symbol === symbol || assignsTo(dag, dagNode.value, symbol)
            is DagNode.Operation -> dagNode.operands.any { assignsTo(dag, it, symbol) }
            is DagNode.Leaf, is DagNode.Untranslated -> false
        }

    private fun quadrupleOf(operation: DagOperation, result: Temporary, operands: List<Address>): Quadruple =
        when (operation) {
            is DagOperation.Arithmetic ->
                Arithmetic(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Relational ->
                Relational(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Unary -> Unary(result, operation.operator, operands[0], operation.kind)
            DagOperation.Concat -> Concat(result, operands[0], operands[1])
        }

    // ══════════════════════════════════════════════════════════════════════
    //  El chequeo de division entre cero (decision 29)
    // ══════════════════════════════════════════════════════════════════════

    // Solo la division ENTERA: en flotantes IEEE 754, dividir entre 0.0 da Infinity,
    // que es un valor legitimo. Es la misma regla del interprete.
    //
    // Un divisor constante no lo necesita: el cero constante ya lo rechazo el
    // TypeChecker, asi que cualquier otra constante es segura.
    private fun needsZeroCheck(operation: DagOperation, operands: List<Address>): Boolean {
        if (operation !is DagOperation.Arithmetic || operation.kind != OperandKind.INTEGER) return false
        if (operation.operator != ArithmeticOperator.DIVIDE && operation.operator != ArithmeticOperator.MODULO) {
            return false
        }
        return operands[1] !is Constant
    }

    //     if b != 0 goto L1
    //     throw "División entre cero (línea 7)"
    // L1:
    //
    // En linea, junto a la division, para que el mensaje lleve la linea del fuente.
    private fun emitZeroCheck(divisor: Address, line: Int) {
        val safe = newLabel()
        instructions += IfRelationalGoto(divisor, RelationalOperator.NOT_EQUAL, Constant(0L), OperandKind.INTEGER, safe)
        instructions += Throw(Constant("División entre cero (línea $line)"))
        instructions += LabelDefinition(safe)
    }

    private fun newLabel(): Label = Label(nextLabel++)

    // ══════════════════════════════════════════════════════════════════════
    //  Ayudantes
    // ══════════════════════════════════════════════════════════════════════

    private fun symbolOf(identifier: Identifier): Symbol =
        requireNotNull(identifier.resolvedSymbol) { "'${identifier.name}' sin resolver" }

    // El cero de cada tipo, el mismo del interprete: una variable sin inicializar no
    // puede tener en el TAC un valor distinto del que tiene al ejecutarse.
    private fun defaultValueOf(type: Type): Any? = when (type) {
        IntegerType -> 0L
        FloatType -> 0.0
        StringType -> ""
        BooleanType -> false

        // Clases y listas arrancan en null: son referencias.
        else -> null
    }

    private fun untranslatedReason(expression: Expression): String = when (expression) {
        is org.compiler.frontend.ast.models.FunctionCall,
        is org.compiler.frontend.ast.models.ObjectCreation -> "llamadas a funciones y constructores (punto 9)"
        is org.compiler.frontend.ast.models.BinaryOperation,
        is org.compiler.frontend.ast.models.TernaryOperation -> "&&, || y el operador ternario (punto 6)"
        else -> "objetos, listas y this (punto 10)"
    }
}
