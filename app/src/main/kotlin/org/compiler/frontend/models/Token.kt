package org.compiler.frontend.models

data class Token(
    val category: String,
    val lexeme: String,
    val location: LexemeLocation
)
