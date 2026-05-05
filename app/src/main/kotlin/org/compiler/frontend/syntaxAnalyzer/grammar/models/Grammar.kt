package org.compiler.frontend.syntaxAnalyzer.grammar.models

data class Grammar(
    val terminals: Set<Symbol.Terminal>,
    val nonTerminals: Set<Symbol.NonTerminal>,
    val productions: List<Production>,
    val startSymbol: Symbol.NonTerminal,
    val ignoredTokens: Set<Symbol.Terminal>
)

val Grammar.productionsByHead: Map<Symbol.NonTerminal, List<Production>>
    get() = productions.groupBy { it.head }
