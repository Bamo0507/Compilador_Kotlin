package org.compiler.frontend.lexicalAnalyzer.scanner.models

data class ErrorEntry(
    val consumed: String,
    val line: Int
)
