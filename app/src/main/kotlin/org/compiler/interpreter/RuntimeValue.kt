package org.compiler.interpreter

import org.compiler.frontend.ast.models.FunctionDeclaration

// Un valor concreto durante la ejecución.
sealed interface RuntimeValue {
    // Cómo se imprime con print(). Es la única forma en que el usuario los ve.
    fun display(): String
}

data class IntValue(val value: Long) : RuntimeValue {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : RuntimeValue {
    override fun display() = value.toString()
}

data class StringValue(val value: String) : RuntimeValue {
    override fun display() = value
}

data class BoolValue(val value: Boolean) : RuntimeValue {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : RuntimeValue {
    override fun display() = "null"
}

// Una lista. Es MUTABLE porque lista[0] = 5 debe modificarla en su lugar.
class ArrayValue(val elements: MutableList<RuntimeValue>) : RuntimeValue {
    override fun display() = elements.joinToString(", ", "[", "]") { it.display() }
}

// Una instancia de clase: sus campos, por nombre.
class ObjectValue(
    val className: String,
    val fields: MutableMap<String, RuntimeValue>
) : RuntimeValue {
    override fun display() = "$className@${hashCode()}"
}

// Una función como valor.
class FunctionValue(
    val declaration: FunctionDeclaration,
    val closure: Environment,
    val boundThis: ObjectValue? = null // no-null si es un método
) : RuntimeValue {
    override fun display() = "<function ${declaration.name}>"
}
