package org.compiler.engine

import org.compiler.frontend.ast.models.Aggregate
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.Exists
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.InSubquery
import org.compiler.frontend.ast.models.IsNull
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Unary
import org.compiler.types.BinaryOperator
import org.compiler.types.BoolValue
import org.compiler.types.DecimalType
import org.compiler.types.DecimalValue
import org.compiler.types.FloatType
import org.compiler.types.FloatValue
import org.compiler.types.IntType
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.OperatorCategory
import org.compiler.types.StringValue
import org.compiler.types.Type
import org.compiler.types.UnaryOperator
import org.compiler.types.Value
import java.math.MathContext
import java.math.RoundingMode

/**
 * El valor de una expresion sobre una fila concreta.
 *
 * No decide nada de tipos: la fase 5 ya dejo pegado el tipo de cada nodo, asi que
 * aqui solo se convierte a ese tipo y se opera. Si el evaluador tuviera que
 * preguntar si `1 + 2.5` es suma entera o decimal, seria que una fase anterior no
 * dejo su trabajo hecho.
 *
 * Los nulos se propagan: cualquier cuenta o comparacion con NULL da NULL. AND y
 * OR son la excepcion, porque `false AND NULL` ya no puede ser verdadero.
 */
class Evaluator(private val engine: Engine) {

    fun evaluate(expression: Expression, context: RowContext, group: List<Row>? = null): Value =
        when (expression) {
            is Literal -> expression.value

            // Dos indexaciones de arreglo. El salto de nivel no se recorre: la
            // fase 4 ya dejo el numero.
            is ColumnReference -> context.value(expression.level, expression.index)

            is Binary -> binary(expression, context, group)
            is Unary -> unary(expression, context, group)
            is IsNull -> BoolValue((evaluate(expression.operand, context, group) == NullValue) != expression.negated)
            is InList -> inList(expression, context, group)
            is InSubquery -> inSubquery(expression, context, group)
            is Exists -> BoolValue(engine.exists(expression.query, context))
            is Query -> engine.scalar(expression, context)
            is Aggregate -> aggregate(expression, context, group)
        }

    // ── Operadores ─────────────────────────────────────────────────────────

    private fun binary(expression: Binary, context: RowContext, group: List<Row>?): Value {
        val operator = expression.operator

        // AND y OR miran un lado antes de evaluar el otro, que es lo que hace que
        // `false AND algo` no reviente aunque el otro lado no tenga sentido.
        if (operator.category == OperatorCategory.LOGICAL) {
            return logical(expression, context, group)
        }

        val left = evaluate(expression.left, context, group)
        val right = evaluate(expression.right, context, group)
        if (left == NullValue || right == NullValue) return NullValue

        return when (operator.category) {
            OperatorCategory.ARITHMETIC -> arithmetic(operator, left, right, expression.type)
            OperatorCategory.CONCATENATION -> StringValue(text(left) + text(right))
            OperatorCategory.EQUALITY -> equality(operator, left, right)
            OperatorCategory.ORDER -> order(operator, left, right)
            OperatorCategory.LOGICAL -> NullValue
        }
    }

    private fun logical(expression: Binary, context: RowContext, group: List<Row>?): Value {
        val left = evaluate(expression.left, context, group)
        val decisive = if (expression.operator == BinaryOperator.AND) BoolValue(false) else BoolValue(true)
        if (left == decisive) return decisive

        val right = evaluate(expression.right, context, group)
        if (right == decisive) return decisive
        if (left == NullValue || right == NullValue) return NullValue

        return if (expression.operator == BinaryOperator.AND) BoolValue(true) else BoolValue(false)
    }

    private fun arithmetic(operator: BinaryOperator, left: Value, right: Value, type: Type): Value = when (type) {
        IntType -> integer(operator, left, right)
        FloatType -> decimalToFloat(operator, left, right)
        is DecimalType -> decimal(operator, left, right, type)
        else -> NullValue
    }

    private fun integer(operator: BinaryOperator, left: Value, right: Value): Value {
        val a = ValueOrder.toLong(left) ?: return NullValue
        val b = ValueOrder.toLong(right) ?: return NullValue

        return when (operator) {
            BinaryOperator.ADD -> IntValue(a + b)
            BinaryOperator.SUBTRACT -> IntValue(a - b)
            BinaryOperator.MULTIPLY -> IntValue(a * b)
            // INT / INT trunca, como en SQL estandar: 10 / 3 da 3.
            BinaryOperator.DIVIDE -> if (b == 0L) dividedByZero() else IntValue(a / b)
            else -> NullValue
        }
    }

    private fun decimalToFloat(operator: BinaryOperator, left: Value, right: Value): Value {
        val a = ValueOrder.toDouble(left) ?: return NullValue
        val b = ValueOrder.toDouble(right) ?: return NullValue

        return when (operator) {
            BinaryOperator.ADD -> FloatValue(a + b)
            BinaryOperator.SUBTRACT -> FloatValue(a - b)
            BinaryOperator.MULTIPLY -> FloatValue(a * b)
            BinaryOperator.DIVIDE -> if (b == 0.0) dividedByZero() else FloatValue(a / b)
            else -> NullValue
        }
    }

    private fun decimal(operator: BinaryOperator, left: Value, right: Value, type: DecimalType): Value {
        val a = ValueOrder.toDecimal(left) ?: return NullValue
        val b = ValueOrder.toDecimal(right) ?: return NullValue

        return when (operator) {
            BinaryOperator.ADD -> DecimalValue(a.add(b, MathContext.DECIMAL128))
            BinaryOperator.SUBTRACT -> DecimalValue(a.subtract(b, MathContext.DECIMAL128))
            BinaryOperator.MULTIPLY -> DecimalValue(a.multiply(b, MathContext.DECIMAL128))

            // La escala la fijo la fase 5 al calcular el tipo del resultado, asi
            // que 1/3 tiene donde terminar.
            BinaryOperator.DIVIDE -> if (b.signum() == 0) {
                dividedByZero()
            } else {
                DecimalValue(a.divide(b, type.scale, RoundingMode.HALF_UP))
            }

            else -> NullValue
        }
    }

    private fun equality(operator: BinaryOperator, left: Value, right: Value): Value {
        val same = ValueOrder.compare(left, right)?.let { it == 0 } ?: return NullValue
        return BoolValue(if (operator == BinaryOperator.EQUAL) same else !same)
    }

    private fun order(operator: BinaryOperator, left: Value, right: Value): Value {
        val result = ValueOrder.compare(left, right) ?: return NullValue

        return BoolValue(
            when (operator) {
                BinaryOperator.LESS -> result < 0
                BinaryOperator.LESS_EQUAL -> result <= 0
                BinaryOperator.GREATER -> result > 0
                else -> result >= 0
            }
        )
    }

    private fun unary(expression: Unary, context: RowContext, group: List<Row>?): Value {
        val operand = evaluate(expression.operand, context, group)
        if (operand == NullValue) return NullValue

        return when (expression.operator) {
            UnaryOperator.NOT -> BoolValue((operand as? BoolValue)?.value?.not() ?: return NullValue)
            UnaryOperator.NEGATE -> when (operand) {
                is IntValue -> IntValue(-operand.value)
                is FloatValue -> FloatValue(-operand.value)
                is DecimalValue -> DecimalValue(operand.value.negate())
                else -> NullValue
            }
        }
    }

    // ── Pertenencia ────────────────────────────────────────────────────────

    private fun inList(expression: InList, context: RowContext, group: List<Row>?): Value {
        val operand = evaluate(expression.operand, context, group)
        val candidates = expression.values.map { evaluate(it, context, group) }

        return membership(operand, candidates, expression.negated)
    }

    private fun inSubquery(expression: InSubquery, context: RowContext, group: List<Row>?): Value {
        val operand = evaluate(expression.operand, context, group)
        val candidates = engine.subquery(expression.query, context).rows.mapNotNull { it.values.firstOrNull() }

        return membership(operand, candidates, expression.negated)
    }

    /**
     * Si no esta pero habia un nulo entre los candidatos, la respuesta es NULL y
     * no false: el valor podria ser ese nulo y no hay como saberlo.
     */
    private fun membership(operand: Value, candidates: List<Value>, negated: Boolean): Value {
        if (operand == NullValue) return NullValue
        if (candidates.any { ValueOrder.compare(operand, it) == 0 }) return BoolValue(!negated)
        if (candidates.any { it == NullValue }) return NullValue

        return BoolValue(negated)
    }

    // ── Agregacion ─────────────────────────────────────────────────────────

    /**
     * El argumento se evalua una vez por fila del grupo, cambiando solo la fila de
     * este nivel: las de afuera siguen siendo las mismas.
     */
    private fun aggregate(expression: Aggregate, context: RowContext, group: List<Row>?): Value {
        val rows = group ?: return NullValue
        val argument = expression.argument

        val values = if (argument == null) emptyList() else rows.map { evaluate(argument, context.withTop(it)) }
        return Aggregator.compute(expression.function, values, rows.size, expression.distinct, expression.type)
    }

    private fun text(value: Value): String = ValueOrder.text(value) ?: value.display()

    private fun dividedByZero(): Value {
        engine.reportDivisionByZero()
        return NullValue
    }
}
