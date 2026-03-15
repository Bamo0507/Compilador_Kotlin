package org.compiler.lexicalAnalyzer.scanner.models

data class ErrorEntry(
    val consumed: String,
    val line: Int
)
