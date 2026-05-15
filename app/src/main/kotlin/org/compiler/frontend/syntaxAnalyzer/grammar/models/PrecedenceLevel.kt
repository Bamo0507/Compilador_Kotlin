package org.compiler.frontend.syntaxAnalyzer.grammar.models

data class PrecedenceLevel(
    val level: Int,
    val operators: Set<Symbol.Terminal>,
    val associativity: Associativity
)
