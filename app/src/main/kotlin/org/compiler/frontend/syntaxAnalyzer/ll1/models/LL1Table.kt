package org.compiler.frontend.syntaxAnalyzer.ll1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production

data class LL1Table(
    val startSymbol: Symbol.NonTerminal,
    val cells: Map<Pair<Symbol.NonTerminal, Symbol>, Production>,
    val conflicts: List<LL1Conflict> = emptyList(),
    val followSets: Map<Symbol.NonTerminal, Set<Symbol>> = emptyMap()
) {
    val isLL1: Boolean
        get() = conflicts.isEmpty()

    fun lookup(nonTerminal: Symbol.NonTerminal, lookahead: Symbol): Production? =
        cells[nonTerminal to lookahead]

    fun followOf(nonTerminal: Symbol.NonTerminal): Set<Symbol> =
        followSets[nonTerminal] ?: emptySet()
}