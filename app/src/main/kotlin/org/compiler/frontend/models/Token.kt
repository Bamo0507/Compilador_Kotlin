package org.compiler.frontend.models

data class Token(
    val category: String,
    val lexeme: String,
    val symbolIndex: Int?   // null for KEYWORD; symbol table index for all other categories
)
