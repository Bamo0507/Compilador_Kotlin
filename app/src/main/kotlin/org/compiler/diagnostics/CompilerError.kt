package org.compiler.diagnostics

import org.compiler.models.LexemeLocation

/**
 * Un error de compilador puede ser lexico, de parseo o semantico. El objetivo es
 * detectar cualquier error y desplegarlo por tipo cuando se incorpore a GUI.
 */
sealed interface CompilerError {
    val location: LexemeLocation
    val message: String

    data class LexerError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    data class ParserError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    data class SemanticError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError
}
