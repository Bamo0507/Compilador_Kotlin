package org.compiler.types

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// Un valor concreto dentro de una celda.
sealed interface Value {
    // Como se muestra en la rejilla de resultados.
    fun display(): String
}

data class IntValue(val value: Long) : Value {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : Value {
    override fun display() = value.toString()
}

/**
 * Un DECIMAL. Respaldado por BigDecimal, que es exacto.
 *
 * NO es `data class` porque el equals generado usaria el de BigDecimal, que
 * distingue 1.0 de 1.00 por su escala. En SQL son el mismo numero, y esa
 * diferencia romperia DISTINCT, GROUP BY y la unicidad de una clave primaria
 * DECIMAL: dos filas con 1.0 y 1.00 pasarian como distintas.
 */
class DecimalValue(val value: BigDecimal) : Value {
    override fun display(): String = value.toPlainString()

    override fun equals(other: Any?): Boolean =
        other is DecimalValue && value.compareTo(other.value) == 0

    // stripTrailingZeros es la forma canonica de los que compareTo considera
    // iguales, asi que dos valores iguales siempre dan el mismo hash.
    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = "DecimalValue(${value.toPlainString()})"
}

// Sirve a CHAR, VARCHAR y TEXT. El tipo dice que regla se aplica al escribir.
data class StringValue(val value: String) : Value {
    override fun display() = value
}

// al mostrarse y al guardarse: se lee a ojo, y ordena alfabeticamente
// igual que cronologicamente, asi que un ORDER BY no necesita nada especial.
data class DateValue(val value: LocalDate) : Value {
    override fun display() = value.toString()
}

data class TimeValue(val value: LocalTime) : Value {
    override fun display() = value.toString()
}

data class BoolValue(val value: Boolean) : Value {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : Value {
    override fun display() = "NULL"
}
