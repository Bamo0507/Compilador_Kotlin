package org.compiler.types

/**
 * Un valor concreto dentro de una celda.
 *
 * La fase 1 agrega DecimalValue, DateValue y TimeValue.
 */
sealed interface Valor {
    // Como se muestra en la rejilla de resultados.
    fun display(): String
}

data class IntValue(val value: Long) : Valor {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : Valor {
    override fun display() = value.toString()
}

// Sirve a CHAR, VARCHAR y TEXT. El tipo dice que regla se aplica al escribir.
data class StringValue(val value: String) : Valor {
    override fun display() = value
}

data class BoolValue(val value: Boolean) : Valor {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : Valor {
    override fun display() = "NULL"
}
