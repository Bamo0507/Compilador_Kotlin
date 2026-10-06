package org.compiler.frontend.intermediate

import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
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
import org.compiler.frontend.ast.models.OperatorGroup
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While
import org.compiler.frontend.intermediate.DagNode.Subexpression
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
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
import org.compiler.frontend.intermediate.models.TacUnaryOperator
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
 * Lo que el generador todavia no traduce es un TODO que nombra la construccion. El
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
            is Break, is Continue -> TODO("control de flujo")
            is TryCatch -> TODO("try/catch")
            is FunctionDeclaration, is ReturnStatement -> TODO("funciones y llamadas")
            is ClassDeclaration -> TODO("clases y objetos")
        }

        // El invariante del pool: entre sentencias no queda ningun temporal vivo. Si
        // queda uno, el generador perdio una lectura y el TAC estaria mal: es un bug
        // del compilador, y es mejor que salte aqui que en el assembler.
        check(!temporaries.hasLiveTemporaries) {
            "Quedaron temporales vivos al terminar la sentencia de la linea ${stmt.location.line}"
        }
    }

    // `let x = e;` -> el codigo de e, y despues `x = <direccion de e>`. La copia final
    // se conserva aunque cueste una instruccion: es la forma de la
    // diapositiva 24, y escribir directo en x es una optimizacion que la teoria no
    // muestra.
    //
    // `let x: integer;` -> `x = 0`: el cero de su tipo, el mismo que usa el interprete.
    private fun generateVariableDeclaration(decl: VariableDeclaration) {
        val symbol = requireNotNull(decl.symbol) {
            "'${decl.name}' sin Symbol: ¿corrio el TypeChecker?"
        }
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
            ?: TODO("asignacion a campos y elementos de lista")

        copyInto(Name(symbolOf(target)), generateExpression(stmt.value))
    }

    private fun generatePrint(stmt: PrintStatement) {
        val value = generateExpression(stmt.expr)
        instructions += Print(value, ExpressionDag.kindOf(stmt.expr.type))
        temporaries.consume(value)
    }

    private fun copyInto(target: Address, value: Address) {
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

                is Subexpression -> generateSubexpression(dagNode)
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
     * para entonces la asignacion ya corrio. Por eso, si el operando derecho puede
     * modificar la variable de la izquierda (una asignacion o una subexpresion, como
     * una llamada), la izquierda se copia antes a un temporal.
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
            if (address is Name && later.any { mayModify(dag, it, address.symbol) }) {
                val snapshot = temporaries.newTemp(uses = 1)
                instructions += Copy(snapshot, address)
                addresses += snapshot
            } else {
                addresses += address
            }
        }
        return addresses
    }

    // Si este nodo puede cambiar la variable. Una subexpresion se considera que si: una
    // llamada puede modificar una global, y un ternario puede tener una asignacion en
    // una rama. Revisar su contenido seria analisis de efectos.
    private fun mayModify(dag: ExpressionDag, node: Int, symbol: Symbol): Boolean =
        when (val dagNode = dag.nodes[node]) {
            is DagNode.Assign ->
                dagNode.target.symbol === symbol || mayModify(dag, dagNode.value, symbol)
            is DagNode.Operation -> dagNode.operands.any { mayModify(dag, it, symbol) }
            is Subexpression -> true
            is DagNode.Leaf -> false
        }

    private fun quadrupleOf(
        operation: DagOperation,
        result: Temporary,
        operands: List<Address>
    ): Quadruple =
        when (operation) {
            is DagOperation.Arithmetic ->
                Arithmetic(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Relational ->
                Relational(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Unary -> Unary(result, operation.operator, operands[0], operation.kind)
            DagOperation.Concat -> Concat(result, operands[0], operands[1])
        }

    // ══════════════════════════════════════════════════════════════════════
    //  Subexpresiones: lo que el GDA no descompone
    // ══════════════════════════════════════════════════════════════════════

    // Cada una se traduce con su propia funcion, y devuelve la direccion donde quedo su
    // valor. Como nunca se comparte, su resultado tiene un solo lector.
    private fun generateSubexpression(subexpression: Subexpression): Address =
        when (subexpression) {
            is Subexpression.Ternary -> generateTernary(subexpression.expression)
            is Subexpression.LogicalValue -> generateLogicalValue(subexpression.expression)
            is Subexpression.Call -> TODO("llamadas a funciones")
            is Subexpression.NewObject -> TODO("creacion de objetos")
            is Subexpression.NewList -> TODO("creacion de listas")
            is Subexpression.FieldAccess -> TODO("acceso a campos")
            is Subexpression.ElementAccess -> TODO("acceso a elementos de lista")
        }

    // c ? x : y. El temporal del resultado se pide ANTES de los saltos, para que los
    // temporales de la condicion y de las ramas queden por encima, y cada rama copia su
    // valor en el. Si el ternario es float, una rama entera se convierte.
    private fun generateTernary(expression: TernaryOperation): Address {
        val result = temporaries.newTemp(uses = 1)
        val elseLabel = newLabel()
        val endLabel = newLabel()
        val kind = ExpressionDag.kindOf(expression.type)

        generateCondition(expression.condition, null, elseLabel)
        copyInto(result, convertedTo(kind, expression.ifTrue))
        instructions += Goto(endLabel)

        instructions += LabelDefinition(elseLabel)
        copyInto(result, convertedTo(kind, expression.ifFalse))
        instructions += LabelDefinition(endLabel)

        return result
    }

    // `let b = x < y && z`: && y || solo existen como saltos, asi que se generan los
    // saltos y despues se escribe el valor.
    private fun generateLogicalValue(expression: BinaryOperation): Address {
        val result = temporaries.newTemp(uses = 1)
        val falseLabel = newLabel()
        val endLabel = newLabel()

        generateCondition(expression, null, falseLabel)
        instructions += Copy(result, Constant(true))
        instructions += Goto(endLabel)

        instructions += LabelDefinition(falseLabel)
        instructions += Copy(result, Constant(false))
        instructions += LabelDefinition(endLabel)

        return result
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Condiciones: codigo de saltos
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Genera saltos a trueLabel si la condicion es verdadera y a falseLabel si es
     * falsa. Una etiqueta null significa caer: seguir con la instruccion siguiente.
     *
     * Con caida, cada condicion solo salta hacia el lado que no sigue, invirtiendo la
     * relacion si hace falta: `if a >= b goto Lfin` en vez de `if a < b goto L1` mas un
     * `goto Lfin`.
     */
    private fun generateCondition(condition: Expression, trueLabel: Label?, falseLabel: Label?) {
        // Plegada por el TypeChecker: `while (true)` no compara nada.
        val constant = condition.constantValue
        if (constant is Boolean) {
            jumpTo(if (constant) trueLabel else falseLabel)
            return
        }

        when {
            // !B no genera nada propio: es B con las etiquetas intercambiadas.
            condition is UnaryOperation && condition.operator == UnaryOperator.NOT ->
                generateCondition(condition.operand, falseLabel, trueLabel)

            condition is BinaryOperation && condition.operator == BinaryOperator.AND ->
                generateAnd(condition, trueLabel, falseLabel)

            condition is BinaryOperation && condition.operator == BinaryOperator.OR ->
                generateOr(condition, trueLabel, falseLabel)

            condition is BinaryOperation && isComparison(condition.operator) ->
                generateComparison(condition, trueLabel, falseLabel)

            else -> generateBooleanTest(condition, trueLabel, falseLabel)
        }
    }

    // Si B1 es falsa, B2 ni se evalua: salta directo al lado falso. Si el lado falso
    // cae, hace falta una etiqueta propia para saltarse B2.
    private fun generateAnd(condition: BinaryOperation, trueLabel: Label?, falseLabel: Label?) {
        val falseTarget = falseLabel ?: newLabel()
        generateCondition(condition.left, null, falseTarget)
        generateCondition(condition.right, trueLabel, falseLabel)
        if (falseLabel == null) instructions += LabelDefinition(falseTarget)
    }

    // Si B1 es verdadera, B2 ni se evalua: salta directo al lado verdadero.
    private fun generateOr(condition: BinaryOperation, trueLabel: Label?, falseLabel: Label?) {
        val trueTarget = trueLabel ?: newLabel()
        generateCondition(condition.left, trueTarget, null)
        generateCondition(condition.right, trueLabel, falseLabel)
        if (trueLabel == null) instructions += LabelDefinition(trueTarget)
    }

    private fun generateComparison(
        condition: BinaryOperation,
        trueLabel: Label?,
        falseLabel: Label?
    ) {
        val kind = ExpressionDag.comparisonKind(condition.left, condition.right)
        val left = readBefore(convertedTo(kind, condition.left), condition.right)
        val right = convertedTo(kind, condition.right)
        val operator = ExpressionDag.relationalOf(condition.operator)

        when {
            trueLabel != null -> {
                instructions += IfRelationalGoto(left, operator, right, kind, trueLabel)
                jumpTo(falseLabel)
            }
            falseLabel != null ->
                instructions += IfRelationalGoto(left, operator.inverted, right, kind, falseLabel)
        }

        temporaries.consume(left)
        temporaries.consume(right)
    }

    // Una variable, una llamada o cualquier otro valor booleano: `if (bandera)`.
    private fun generateBooleanTest(condition: Expression, trueLabel: Label?, falseLabel: Label?) {
        val value = generateExpression(condition)

        when {
            trueLabel != null -> {
                instructions += IfGoto(value, trueLabel)
                jumpTo(falseLabel)
            }
            falseLabel != null -> instructions += IfFalseGoto(value, falseLabel)
        }

        temporaries.consume(value)
    }

    private fun jumpTo(label: Label?) {
        if (label != null) instructions += Goto(label)
    }

    private fun isComparison(operator: BinaryOperator): Boolean =
        operator.group == OperatorGroup.RELATIONAL || operator.group == OperatorGroup.EQUALITY

    // Un operando de una comparacion, convertido a flotante si el otro lo es. Una
    // constante entera se convierte aqui mismo, sin instruccion.
    private fun convertedTo(kind: OperandKind, operand: Expression): Address {
        val address = generateExpression(operand)
        val needsConversion =
            kind == OperandKind.FLOAT && ExpressionDag.kindOf(operand.type) == OperandKind.INTEGER
        if (!needsConversion) return address

        val value = (address as? Constant)?.value
        if (value is Long) return Constant(value.toDouble())

        temporaries.consume(address)
        val converted = temporaries.newTemp(uses = 1)
        instructions += Unary(converted, TacUnaryOperator.INT_TO_FLOAT, address, OperandKind.FLOAT)
        return converted
    }

    // En `x < f()`, la x se lee ANTES de la llamada, porque se evalua primero. Una
    // variable no genera instruccion y se leeria despues, asi que se copia a un
    // temporal si lo que sigue puede modificarla.
    private fun readBefore(address: Address, later: Expression): Address {
        if (address !is Name || ExpressionDag.isPure(later)) return address

        val snapshot = temporaries.newTemp(uses = 1)
        instructions += Copy(snapshot, address)
        return snapshot
    }

    // ══════════════════════════════════════════════════════════════════════
    //  El chequeo de division entre cero
    // ══════════════════════════════════════════════════════════════════════

    // Solo la division ENTERA: en flotantes IEEE 754, dividir entre 0.0 da Infinity,
    // que es un valor legitimo. Es la misma regla del interprete.
    //
    // Un divisor constante no lo necesita: el cero constante ya lo rechazo el
    // TypeChecker, asi que cualquier otra constante es segura.
    private fun needsZeroCheck(operation: DagOperation, operands: List<Address>): Boolean {
        if (operation !is DagOperation.Arithmetic) return false
        if (operation.kind != OperandKind.INTEGER) return false

        val isDivision = operation.operator == ArithmeticOperator.DIVIDE ||
            operation.operator == ArithmeticOperator.MODULO
        if (!isDivision) return false

        return operands[1] !is Constant
    }

    //     if b != 0 goto L1
    //     throw "División entre cero (línea 7)"
    // L1:
    //
    // En linea, junto a la division, para que el mensaje lleve la linea del fuente.
    private fun emitZeroCheck(divisor: Address, line: Int) {
        val safe = newLabel()
        instructions += IfRelationalGoto(
            divisor, RelationalOperator.NOT_EQUAL, Constant(0L), OperandKind.INTEGER, safe
        )
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

}
