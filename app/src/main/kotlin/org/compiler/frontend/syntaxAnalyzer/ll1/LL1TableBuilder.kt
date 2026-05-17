package org.compiler.frontend.syntaxAnalyzer.ll1

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Conflict
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Table
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.sets.models.FollowSets

object LL1TableBuilder {

    fun build(grammar: Grammar, firstSets: FirstSets, followSets: FollowSets): LL1Table {
        // Phase 1 -- collect candidate productions per cell (Dragon Book §4.4.3, Algorithm 4.31).
        val candidates = mutableMapOf<Pair<Symbol.NonTerminal, Symbol>, MutableList<Production>>()

        for (production in grammar.productions) {
            val head = production.head
            val firstOfBody = FirstSetComputer.firstOfSequence(production.body, firstSets)

            for (symbol in firstOfBody) {
                if (symbol != Symbol.Epsilon) {
                    candidates.getOrPut(head to symbol) { mutableListOf() }.add(production)
                }
            }

            if (Symbol.Epsilon in firstOfBody) {
                for (followTerminal in followSets.followOf(head)) {
                    candidates.getOrPut(head to followTerminal) { mutableListOf() }.add(production)
                }
            }
        }

        // Phase 2 -- collapse each cell's candidate list to a single production.
        val cells = mutableMapOf<Pair<Symbol.NonTerminal, Symbol>, Production>()
        val conflicts = mutableListOf<LL1Conflict>()

        for ((cell, cellCandidates) in candidates) {
            val distinct = cellCandidates.distinct()
            if (distinct.size == 1) {
                cells[cell] = distinct.first()
            } else {
                cells[cell] = resolveConflict(distinct)
                conflicts.add(LL1Conflict(
                    nonTerminal = cell.first,
                    terminal = cell.second,
                    productions = distinct
                ))
            }
        }

        return LL1Table(
            startSymbol = grammar.startSymbol,
            cells = cells,
            conflicts = conflicts,
            followSets = followSets.results
        )
    }

    // Conflict resolution policy: the production with the lowest id wins.
    // Same convention as SLR1TableBuilder for reduce-reduce: gives the user
    // control via the order of declarations in the grammar file.
    private fun resolveConflict(productions: List<Production>): Production =
        productions.minBy { it.id }
}
