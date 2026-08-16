package org.compiler.diagnostics

/**
 * Colector de errores de UNA compilacion.
 *
 * Es una clase y no un `object`: cada corrida crea la suya, asi que no hay estado
 * global que limpiar ni que se pise entre corridas.
 */
class Diagnostics {

    private val errors = mutableListOf<CompilerError>()

    fun report(error: CompilerError) {
        errors.add(error)
    }

    // Ordenados por posicion en el fuente, para que la lista de la GUI se lea de
    // arriba hacia abajo igual que el codigo.
    fun all(): List<CompilerError> =
        errors.sortedWith(compareBy({ it.location.line }, { it.location.position }))

    fun lexical(): List<CompilerError.LexerError> = errors.filterIsInstance<CompilerError.LexerError>()

    fun syntactic(): List<CompilerError.ParserError> = errors.filterIsInstance<CompilerError.ParserError>()

    fun semantic(): List<CompilerError.SemanticError> = errors.filterIsInstance<CompilerError.SemanticError>()

    val hasErrors: Boolean get() = errors.isNotEmpty()

    val count: Int get() = errors.size
}
