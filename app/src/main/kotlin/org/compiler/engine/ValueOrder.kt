package org.compiler.engine

import org.compiler.types.BoolValue
import org.compiler.types.DateValue
import org.compiler.types.DecimalValue
import org.compiler.types.FloatValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.TimeValue
import org.compiler.types.Value
import java.math.BigDecimal

/**
 * Comparar dos valores concretos.
 *
 * Los tipos ya los verifico la fase 5, asi que aqui no se pregunta si tiene
 * sentido compararlos: solo como. Lo unico que queda abierto es el nulo, que no
 * es menor ni mayor que nada.
 */
object ValueOrder {

    // null significa "no se sabe": alguno de los dos es NULL.
    fun compare(left: Value, right: Value): Int? {
        if (left == NullValue || right == NullValue) return null

        return when {
            left is StringValue && right is StringValue -> left.value.compareTo(right.value)
            left is DateValue && right is DateValue -> left.value.compareTo(right.value)
            left is TimeValue && right is TimeValue -> left.value.compareTo(right.value)
            left is BoolValue && right is BoolValue -> left.value.compareTo(right.value)
            else -> {
                val a = toDecimal(left) ?: return null
                val b = toDecimal(right) ?: return null
                a.compareTo(b)
            }
        }
    }

    /**
     * El orden del ORDER BY, que si tiene que ser total.
     *
     * Los nulos van al final en ASC y al principio en DESC, que es la convencion
     * de SQLite. Hay que decidirlo porque comparar un nulo con cualquier cosa da
     * nulo, asi que el comparador necesita una regla explicita.
     */
    fun forSort(left: Value, right: Value, descending: Boolean): Int {
        if (left == NullValue && right == NullValue) return 0
        if (left == NullValue) return if (descending) -1 else 1
        if (right == NullValue) return if (descending) 1 else -1

        val order = compare(left, right) ?: 0
        return if (descending) -order else order
    }

    fun toDecimal(value: Value): BigDecimal? = when (value) {
        is IntValue -> BigDecimal.valueOf(value.value)
        is DecimalValue -> value.value
        is FloatValue -> BigDecimal.valueOf(value.value)
        else -> null
    }

    fun toDouble(value: Value): Double? = when (value) {
        is IntValue -> value.value.toDouble()
        is DecimalValue -> value.value.toDouble()
        is FloatValue -> value.value
        else -> null
    }

    fun toLong(value: Value): Long? = when (value) {
        is IntValue -> value.value
        is DecimalValue -> value.value.toLong()
        is FloatValue -> value.value.toLong()
        else -> null
    }

    fun text(value: Value): String? = (value as? StringValue)?.value
}
