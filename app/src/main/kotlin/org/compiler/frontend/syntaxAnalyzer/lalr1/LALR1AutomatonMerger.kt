package org.compiler.frontend.syntaxAnalyzer.lalr1

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Item
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1State

object LALR1AutomatonMerger {

    fun mergeFromSLR1(automata: SLR1Automata): SLR1Automata {
        val originalStates: Map<Int, SLR1State> = automata.states.associateBy { it.id }
        val remainingIds: MutableList<Int> = automata.states.map { it.id }.toMutableList()
        val originalIdToNewId: MutableMap<Int, Int> = mutableMapOf()
        val mergedStates: MutableList<SLR1State> = mutableListOf()
        var nextNewId = 0

        while (remainingIds.isNotEmpty()) {
            val currentId = remainingIds.removeAt(0)
            val currentState = originalStates[currentId]!!
            val currentCore = currentState.core

            // Find every other remaining state that shares the same core.
            val matchedIds = remainingIds.filter { otherId ->
                originalStates[otherId]!!.core == currentCore
            }
            remainingIds.removeAll(matchedIds.toSet())

            // Build the merged state by unioning lookaheads per core across all group members.
            val statesInGroup = listOf(currentState) + matchedIds.map { originalStates[it]!! }
            val mergedItems = mergeItemsAcrossStates(statesInGroup)
            mergedStates.add(SLR1State(id = nextNewId, items = mergedItems))

            // Record id mapping for every original id in this group so transitions can be rewritten.
            originalIdToNewId[currentId] = nextNewId
            for (matchedId in matchedIds) {
                originalIdToNewId[matchedId] = nextNewId
            }
            nextNewId++
        }

        // Rewrite transitions: (oldSrc, sym) -> oldTgt becomes (newSrc, sym) -> newTgt.
        val mergedTransitions: Map<Pair<Int, Symbol>, Int> =
            automata.transitions.entries.associate { (key, oldTarget) ->
                val (oldSourceId, symbol) = key
                val newSourceId = originalIdToNewId[oldSourceId]!!
                val newTargetId = originalIdToNewId[oldTarget]!!
                (newSourceId to symbol) to newTargetId
            }

        val newInitialId = originalIdToNewId[automata.initialState.id]!!
        val newInitialState = mergedStates.first { it.id == newInitialId }

        return SLR1Automata(
            states = mergedStates,
            transitions = mergedTransitions,
            initialState = newInitialState,
            augmentedGrammar = automata.augmentedGrammar
        )
    }

    private fun mergeItemsAcrossStates(states: List<SLR1State>): Set<SLR1Item> {
        val lookaheadsByCore = mutableMapOf<Pair<Production, Int>, MutableSet<Symbol>>()
        for (state in states) {
            for (item in state.items) {
                lookaheadsByCore.getOrPut(item.core) { mutableSetOf() }.addAll(item.lookaheads)
            }
        }
        return lookaheadsByCore.map { (core, lookaheads) ->
            SLR1Item(core.first, core.second, lookaheads.toSet())
        }.toSet()
    }
}
