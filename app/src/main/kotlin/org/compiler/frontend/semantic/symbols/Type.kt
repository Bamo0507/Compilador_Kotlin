package org.compiler.frontend.semantic.symbols

/**
 * Un tipo de dato. Sellado para que un `when` sin `else` no compile si se olvida
 * alguno.
 */
sealed interface Type {
    // Como se escribe el tipo en un mensaje de error.
    val name: String
}

// Se devuelve cuando ya se reporto un error. Corta cascadas: cualquier operacion
// con ErrorType se acepta en silencio, asi un fuente malo reporta una vez.
data object ErrorType : Type {
    override val name = "<error>"
}
