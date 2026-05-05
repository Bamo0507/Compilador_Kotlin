package org.compiler.frontend.syntaxAnalyzer.grammar.models

// id identifies this production by number (e.g. "reduce by production 5") so that
// LR items and parse traces can reference it without comparing full symbol lists.
data class Production(
    val id: Int,
    val head: Symbol.NonTerminal,
    val body: List<Symbol>
)
