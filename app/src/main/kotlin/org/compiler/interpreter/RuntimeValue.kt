package org.compiler.interpreter

import org.compiler.frontend.ast.models.FunctionDeclaration
import java.util.Locale

// Un valor concreto durante la ejecución.
sealed interface RuntimeValue {
    // Cómo se imprime con print(). Es la única forma en que el usuario los ve.
    fun display(): String
}

data class IntValue(val value: Long) : RuntimeValue {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : RuntimeValue {
    // Seis decimales sin los ceros sobrantes, y al menos uno: 3.5, 1.0, 0.333333. Es el
    // formato del runtime de ARM, para que las dos salidas coincidan.
    override fun display(): String {
        val fixed = String.format(Locale.ROOT, "%.6f", value).trimEnd('0')
        return if (fixed.endsWith('.')) "${fixed}0" else fixed
    }
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
