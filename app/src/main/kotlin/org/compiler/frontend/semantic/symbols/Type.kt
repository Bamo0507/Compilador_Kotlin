package org.compiler.frontend.semantic.symbols

/**
 * Un tipo de dato de Compiscript. Sellado para que un `when` sin `else` no compile si
 * se olvida alguno.
 */
sealed interface Type {
    // Como se escribe el tipo en un mensaje de error.
    val name: String
}

// `data object` y no `data class`: una sola instancia de cada primitivo, asi que
// compararlos es comparar referencias.
data object IntegerType : Type {
    override val name = "integer"
}

data object FloatType : Type {
    override val name = "float"
}

data object StringType : Type {
    override val name = "string"
}

data object BooleanType : Type {
    override val name = "boolean"
}

// Los tres siguientes no se pueden escribir en el codigo fuente: existen solo dentro
// del analisis.

// Una funcion que no devuelve nada.
data object VoidType : Type {
    override val name = "void"
}

// El literal `null`. Compatible con clases y arreglos, no con los primitivos.
data object NullType : Type {
    override val name = "null"
}

// Se devuelve cuando ya se reporto un error. Corta cascadas: `(1 + "a") * 2` reporta
// un solo error porque cualquier operacion con ErrorType se acepta en silencio.
data object ErrorType : Type {
    override val name = "<error>"
}

// integer[] es ArrayType(IntegerType); integer[][] es ArrayType(ArrayType(IntegerType)).
data class ArrayType(val element: Type) : Type {
    override val name = "${element.name}[]"
}

// Guarda SOLO el nombre, no el Scope de la clase: eso crearia un ciclo
// Type -> Scope -> Symbol -> Type.
data class ClassType(val className: String) : Type {
    override val name = className
}

data class FunctionType(
    val parameters: List<Type>,
    val returns: Type
) : Type {
    override val name =
        "(${parameters.joinToString(", ") { it.name }}) -> ${returns.name}"
}
