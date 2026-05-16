package org.compiler.frontend.syntaxAnalyzer.runtime.models

sealed interface ParseResult {
    data class Accepted(
        val trace: List<ParseStep>,
        val parseTree: ParseTree,
        val errors: List<ParseError> = emptyList()
    ) : ParseResult

    data class Rejected(
        val trace: List<ParseStep>,
        val errors: List<ParseError>,
        val partialTree: ParseTree?
    ) : ParseResult
}
