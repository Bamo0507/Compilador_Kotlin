package org.compiler.diagnostics

import org.compiler.models.LexemeLocation

sealed interface CompilerError {
    val location: LexemeLocation
    val message: String

    data class LexerError(
        override val location: LexemeLocation,
        override val message: String,
        val invalidLexeme: String
    ) : CompilerError

    data class ParserError(
        override val location: LexemeLocation,
        override val message: String,
        val foundCategory: String,
        val foundLexeme: String,
        val expectedCategories: List<String>
    ) : CompilerError
}
