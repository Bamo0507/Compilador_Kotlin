package org.compiler.frontend.syntaxAnalyzer.runtime.models

sealed interface ParseResult {
    data class Accepted(
        val trace: List<ParseStep>,
        val parseTree: ParseTree
    ) : ParseResult

    data class Rejected(
        val trace: List<ParseStep>,
        val error: ParseError,
        val partialTree: ParseTree?
    ) : ParseResult
}
