package org.compiler.frontend.syntaxAnalyzer.grammar.models

sealed interface Symbol {
    val name: String

    data class Terminal(override val name: String) : Symbol
    data class NonTerminal(override val name: String) : Symbol
    data object Epsilon : Symbol { override val name = "ε" }
    data object EndMarker : Symbol { override val name = "$" }
}
