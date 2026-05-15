package org.compiler.frontend.syntaxAnalyzer.sets.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class FollowSets(
    val results: Map<Symbol.NonTerminal, Set<Symbol>>
) {
    fun followOf(nonTerminal: Symbol.NonTerminal): Set<Symbol> =
        results[nonTerminal] ?: emptySet()
}
