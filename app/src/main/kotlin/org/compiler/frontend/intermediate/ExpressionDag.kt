package org.compiler.frontend.intermediate

import org.compiler.frontend.ast.models.ArrayLiteral
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.ObjectCreation
import org.compiler.frontend.ast.models.OperatorGroup
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.ThisReference
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.intermediate.DagNode.Subexpression
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Type

// El numero de valor de un nodo es su indice en `ExpressionDag.nodes`.
sealed interface DagNode {

    // Una variable o una constante: no genera instruccion.
    data class Leaf(val address: Address) : DagNode

    // Una operacion. `operands` son numeros de valor de otros nodos.
    data class Operation(val operation: DagOperation, val operands: List<Int>) : DagNode

    // `x = (y = 5)`: la asignacion anidada a una variable. Emite la copia y su valor
    // es lo que quedo en el destino, asi que el padre lee Name(y) y no un temporal.
    // Nunca se comparte: dos asignaciones iguales son dos escrituras.
    data class Assign(val target: Name, val value: Int) : DagNode

    // Un operando que no es una operacion aritmetica simple y necesita su propia
    // traduccion: con saltos, con una llamada o con acceso a memoria. El GDA lo trata
    // como una hoja que nunca se comparte, y la operacion de arriba solo recibe el
    // temporal donde quedo su resultado. Un caso por construccion, para que el
    // generador los nombre y el compilador obligue a traducir cada uno.
    sealed interface Subexpression : DagNode {
        data class Ternary(val expression: TernaryOperation) : Subexpression
        data class LogicalValue(val expression: BinaryOperation) : Subexpression
        data class Call(val expression: FunctionCall) : Subexpression
        data class NewObject(val expression: ObjectCreation) : Subexpression
        data class NewList(val expression: ArrayLiteral) : Subexpression
        data class FieldAccess(val expression: PropertyAccess) : Subexpression
        data class ElementAccess(val expression: IndexAccess) : Subexpression
    }
}

// Lo que distingue a dos operaciones. Es la parte "op" de la llave <op, izq, der>.
sealed interface DagOperation {
    data class Arithmetic(val operator: ArithmeticOperator, val kind: OperandKind) : DagOperation
    data class Relational(val operator: RelationalOperator, val kind: OperandKind) : DagOperation
    data class Unary(val operator: TacUnaryOperator, val kind: OperandKind) : DagOperation
    data object Concat : DagOperation
}

/**
 * El GDA de UNA expresion, construido con el metodo del numero de valor.
 *
 * Los nodos viven en una lista y su indice es su numero de valor; una tabla hash con
 * la llave del nodo contesta "¿ya existe?". Asi `(b - c)` escrito dos veces es un solo
 * nodo con dos padres, y se calcula una sola vez.
 *
 * Se construye desde el AST ya decorado por el TypeChecker, y no sobre el TAC: es la
 * misma definicion dirigida por la sintaxis que construye el arbol, con un `new` que
 * reutiliza (diapositivas 12 y 13).
 */
class ExpressionDag private constructor(
    val nodes: List<DagNode>,
    val root: Int,

    // Cuantas aristas llegan a cada nodo: los `uses` del TemporaryAllocator. Una
    // arista repetida cuenta dos veces: en (x + y) * (x + y) el nodo x + y tiene dos.
    val parentCount: List<Int>,

    // La linea del fuente de la primera aparicion de cada nodo. Va aparte de la
    // llave a proposito: si fuera parte de ella, dos `a / b` en lineas distintas
    // dejarian de ser el mismo valor. La usa el mensaje del chequeo de division.
    private val lines: List<Int>
) {
    fun lineOf(node: Int): Int = lines[node]

    companion object {

        fun build(expression: Expression): ExpressionDag {
            val builder = Builder(shareOperations = isPure(expression))
            val root = builder.build(expression)
            return ExpressionDag(builder.nodes, root, builder.countParents(), builder.lines)
        }

        /**
         * Solo una expresion pura comparte calculos: variables, constantes y
         * operaciones aritmeticas, relacionales o de texto. Cualquier otra cosa puede
         * cambiar un valor o no ejecutarse, y en ese caso dos subexpresiones iguales no
         * son el mismo valor:
         *
         *   a * b + f() + a * b         f puede modificar a o b
         *   (x = 5) + x                 la x de la derecha no es la de antes
         *   (a * b) + (c ? a * b : 0)   la rama puede no ejecutarse
         *
         * Saber exactamente que nodos se pueden compartir es analisis de efectos.
         * Apagarlo para toda la expresion es una regla de una linea.
         */
        fun isPure(expression: Expression): Boolean {
            if (expression.constantValue != null) return true
            return when (expression) {
                is Literal, is Identifier -> true
                is UnaryOperation -> isPure(expression.operand)
                is BinaryOperation -> expression.operator.group != OperatorGroup.LOGICAL &&
                    isPure(expression.left) && isPure(expression.right)
                else -> false
            }
        }

        // El tipo de los operandos de una operacion, que decide la instruccion de
        // maquina: sumar enteros y sumar flotantes son instrucciones distintas.
        fun kindOf(type: Type?): OperandKind = when (type) {
            IntegerType -> OperandKind.INTEGER
            FloatType -> OperandKind.FLOAT
            StringType -> OperandKind.STRING
            BooleanType -> OperandKind.BOOLEAN

            // Clases, listas y null: se comparan por direccion.
            else -> OperandKind.REFERENCE
        }

        // El tipo con el que se comparan dos operandos: entero con flotante compara
        // como flotante. Cualquier otra mezcla ya la rechazo el TypeChecker, asi que
        // basta con el de la izquierda. Lo usan el GDA y las condiciones con saltos.
        fun comparisonKind(left: Expression, right: Expression): OperandKind {
            val leftKind = kindOf(left.type)
            val rightKind = kindOf(right.type)
            val involvesFloat = leftKind == OperandKind.FLOAT || rightKind == OperandKind.FLOAT
            return if (involvesFloat) OperandKind.FLOAT else leftKind
        }

        fun relationalOf(operator: BinaryOperator): RelationalOperator = when (operator) {
            BinaryOperator.LESS -> RelationalOperator.LESS
            BinaryOperator.LESS_EQUAL -> RelationalOperator.LESS_EQUAL
            BinaryOperator.GREATER -> RelationalOperator.GREATER
            BinaryOperator.GREATER_EQUAL -> RelationalOperator.GREATER_EQUAL
            BinaryOperator.EQUAL -> RelationalOperator.EQUAL
            BinaryOperator.NOT_EQUAL -> RelationalOperator.NOT_EQUAL
            else -> error("'${operator.symbol}' no es relacional")
        }
    }

    private class Builder(private val shareOperations: Boolean) {

        val nodes = mutableListOf<DagNode>()
        val lines = mutableListOf<Int>()

        // La tabla del numero de valor: de la llave del nodo a su indice.
        private val existing = HashMap<DagNode, Int>()

        fun build(expression: Expression): Int {
            val line = expression.location.line

            // 1. El TypeChecker ya plego el valor: una hoja, sin importar cuantos nodos
            //    tenia debajo. `3 + 5` es la constante 8.
            expression.constantValue?.let { return leaf(Constant(it), line) }

            return when (expression) {
                // 2. Las hojas. El literal `null` no tiene constantValue (es null), asi
                //    que llega aqui.
                is Literal -> leaf(Constant(expression.value), line)
                is Identifier -> {
                    val symbol = requireNotNull(expression.resolvedSymbol) {
                        "'${expression.name}' sin resolver"
                    }
                    leaf(Name(symbol), line)
                }

                // 3. Las operaciones: primero los hijos, de abajo hacia arriba.
                is BinaryOperation -> binary(expression, line)
                is UnaryOperation -> unary(expression, line)

                is AssignmentExpression -> {
                    val target = expression.target as? Identifier
                        ?: TODO("asignacion a campos y elementos de lista")

                    val value = build(expression.value)
                    add(DagNode.Assign(Name(requireNotNull(target.resolvedSymbol)), value), line)
                }

                // 4. Lo que necesita su propia traduccion entra entero.
                is TernaryOperation -> subexpression(Subexpression.Ternary(expression), line)
                is FunctionCall -> subexpression(Subexpression.Call(expression), line)
                is ObjectCreation -> subexpression(Subexpression.NewObject(expression), line)
                is ArrayLiteral -> subexpression(Subexpression.NewList(expression), line)
                is PropertyAccess -> subexpression(Subexpression.FieldAccess(expression), line)
                is IndexAccess -> subexpression(Subexpression.ElementAccess(expression), line)

                is ThisReference -> TODO("this")
            }
        }

        private fun binary(expression: BinaryOperation, line: Int): Int {
            val operator = expression.operator

            // && y || como valor necesitan saltos: no son una operacion.
            if (operator.group == OperatorGroup.LOGICAL) {
                return subexpression(Subexpression.LogicalValue(expression), line)
            }

            // Una suma de strings es concatenacion: en la maquina no es una suma, sino
            // una rutina que reserva memoria.
            if (operator == BinaryOperator.ADD && expression.type == StringType) {
                val left = build(expression.left)
                val right = build(expression.right)
                return operation(DagOperation.Concat, listOf(left, right), line)
            }

            // El tipo de la operacion: para la aritmetica es el del resultado; para una
            // comparacion, el de los operandos YA ensanchados (x < 2.5 compara floats).
            val kind = when (operator.group) {
                OperatorGroup.ARITHMETIC -> kindOf(expression.type)
                else -> comparisonKind(expression.left, expression.right)
            }

            val left = convertedTo(kind, expression.left)
            val right = convertedTo(kind, expression.right)

            val dagOperation = when (operator.group) {
                OperatorGroup.ARITHMETIC -> DagOperation.Arithmetic(arithmeticOf(operator), kind)
                else -> DagOperation.Relational(relationalOf(operator), kind)
            }
            return operation(dagOperation, listOf(left, right), line)
        }

        private fun unary(expression: UnaryOperation, line: Int): Int {
            val operand = build(expression.operand)
            val operation = when (expression.operator) {
                UnaryOperator.NEGATE ->
                    DagOperation.Unary(TacUnaryOperator.NEGATE, kindOf(expression.type))
                UnaryOperator.NOT -> DagOperation.Unary(TacUnaryOperator.NOT, OperandKind.BOOLEAN)
            }
            return operation(operation, listOf(operand), line)
        }

        // 4. Las conversiones implicitas SON nodos, asi que tambien se comparten:
        //    `x + 2.5` dos veces convierte x una sola vez.
        //
        //    Una constante entera no necesita instruccion: se convierte aqui mismo, con
        //    el mismo criterio del plegado.
        private fun convertedTo(kind: OperandKind, operand: Expression): Int {
            val node = build(operand)
            val needsConversion =
                kind == OperandKind.FLOAT && kindOf(operand.type) == OperandKind.INTEGER
            if (!needsConversion) return node

            val child = nodes[node]
            if (child is DagNode.Leaf && child.address is Constant && child.address.value is Long) {
                return leaf(Constant(child.address.value.toDouble()), operand.location.line)
            }

            val conversion = DagOperation.Unary(TacUnaryOperator.INT_TO_FLOAT, OperandKind.FLOAT)
            return operation(conversion, listOf(node), operand.location.line)
        }

        // Las hojas se comparten en los dos modos: no generan instruccion, y leer `a`
        // dos veces es leer la misma direccion.
        private fun leaf(address: Address, line: Int): Int =
            lookupOrAdd(DagNode.Leaf(address), line)

        private fun operation(operation: DagOperation, operands: List<Int>, line: Int): Int {
            val node = DagNode.Operation(operation, operands)
            return if (shareOperations) lookupOrAdd(node, line) else add(node, line)
        }

        // Nunca se comparte: cada una es una traduccion aparte.
        private fun subexpression(node: Subexpression, line: Int): Int = add(node, line)

        // El `new` que reutiliza: si la llave ya existe, devuelve ese numero de valor.
        private fun lookupOrAdd(node: DagNode, line: Int): Int =
            existing[node] ?: add(node, line).also { existing[node] = it }

        private fun add(node: DagNode, line: Int): Int {
            nodes += node
            lines += line
            return nodes.size - 1
        }

        fun countParents(): List<Int> {
            val counts = MutableList(nodes.size) { 0 }
            nodes.forEach { node ->
                when (node) {
                    is DagNode.Operation -> node.operands.forEach { counts[it]++ }
                    is DagNode.Assign -> counts[node.value]++
                    is DagNode.Leaf, is Subexpression -> Unit
                }
            }
            return counts
        }

        // Entero con flotante compara como flotante; cualquier otra mezcla ya la
        // rechazo el TypeChecker, asi que basta con el de la izquierda.
        private fun arithmeticOf(operator: BinaryOperator): ArithmeticOperator = when (operator) {
            BinaryOperator.ADD -> ArithmeticOperator.ADD
            BinaryOperator.SUBTRACT -> ArithmeticOperator.SUBTRACT
            BinaryOperator.MULTIPLY -> ArithmeticOperator.MULTIPLY
            BinaryOperator.DIVIDE -> ArithmeticOperator.DIVIDE
            BinaryOperator.MODULO -> ArithmeticOperator.MODULO
            else -> error("'${operator.symbol}' no es aritmetico")
        }
    }
}
