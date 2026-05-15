package org.compiler.frontend.syntaxAnalyzer.grammar.models

data class ValidationError(
    val message: String,
    val severity: Severity = Severity.ERROR
) {
    enum class Severity { ERROR, WARNING }
}
