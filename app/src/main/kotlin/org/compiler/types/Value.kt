package org.compiler.types

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

// Sirve a CHAR, VARCHAR y TEXT. El tipo dice que regla se aplica al escribir.
data class StringValue(val value: String) : Value {
    override fun display() = value
}

data class BoolValue(val value: Boolean) : Value {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : Value {
    override fun display() = "NULL"
}
