package org.compiler.engine

import org.compiler.frontend.ast.models.AggregateFunction
import org.compiler.types.DecimalType
import org.compiler.types.DecimalValue
import org.compiler.types.FloatType
import org.compiler.types.FloatValue
import org.compiler.types.IntType
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.Type
import org.compiler.types.Value
import java.math.BigDecimal
import java.math.MathContext

/**
 * La cuenta de una funcion de agregacion sobre un grupo.
 *
 * Recibe los valores ya evaluados, uno por fila del grupo, porque evaluar la
 * expresion de adentro necesita el contexto y eso es del evaluador.
 *
 * El trato de los nulos es lo que mas se equivoca, asi que esta todo en un lugar:
 * COUNT(*) cuenta filas, el resto cuenta valores presentes.
 */
object Aggregator {

    fun compute(
        function: AggregateFunction,
        values: List<Value>,
        rowCount: Int,
        distinct: Boolean,
        type: Type
    ): Value {
        // COUNT(*) cuenta filas, aunque esten todas en nulo.
        if (function == AggregateFunction.COUNT_ALL) return IntValue(rowCount.toLong())

        val present = values.filterNot { it == NullValue }
        val considered = if (distinct) present.distinct() else present

        return when (function) {
            AggregateFunction.COUNT_ALL -> IntValue(rowCount.toLong())
            AggregateFunction.COUNT -> IntValue(considered.size.toLong())

            // Sobre cero filas da NULL y no 0: no hay nada que sumar, y decir 0
            // seria inventar un dato.
            AggregateFunction.SUM -> if (considered.isEmpty()) NullValue else sum(considered, type)

            AggregateFunction.AVG -> if (considered.isEmpty()) {
                NullValue
            } else {
                FloatValue(considered.sumOf { ValueOrder.toDouble(it) ?: 0.0 } / considered.size)
            }

            AggregateFunction.MIN -> considered.minWithOrNull(::order) ?: NullValue
            AggregateFunction.MAX -> considered.maxWithOrNull(::order) ?: NullValue
        }
    }

    private fun sum(values: List<Value>, type: Type): Value = when (type) {
        IntType -> IntValue(values.sumOf { ValueOrder.toLong(it) ?: 0L })
        FloatType -> FloatValue(values.sumOf { ValueOrder.toDouble(it) ?: 0.0 })

        is DecimalType -> DecimalValue(
            values.fold(BigDecimal.ZERO) { total, value ->
                total.add(ValueOrder.toDecimal(value) ?: BigDecimal.ZERO, MathContext.DECIMAL128)
            }
        )

        else -> NullValue
    }

    private fun order(left: Value, right: Value): Int = ValueOrder.compare(left, right) ?: 0
}
