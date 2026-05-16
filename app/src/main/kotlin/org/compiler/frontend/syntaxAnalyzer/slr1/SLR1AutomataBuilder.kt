package org.compiler.frontend.syntaxAnalyzer.slr1

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Item
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1State

object SLR1AutomataBuilder {

    fun build(grammar: Grammar, firstSets: FirstSets): SLR1Automata {
        val augmentedGrammar = augmentGrammar(grammar)
        val augmentingProduction = augmentedGrammar.productions.first { it.id == 0 }

        val initialKernelItem = SLR1Item(
            production = augmentingProduction,
            dotPosition = 0,
            lookaheads = setOf(Symbol.EndMarker)
        )
        val initialItems = closure(setOf(initialKernelItem), augmentedGrammar, firstSets)
        val initialState = SLR1State(id = 0, items = initialItems)

        val statesByItems = mutableMapOf(initialItems to initialState)
        val states = mutableListOf(initialState)
        val transitions = mutableMapOf<Pair<Int, Symbol>, Int>()
        val worklist = ArrayDeque<SLR1State>()
        worklist.addLast(initialState)

        while (worklist.isNotEmpty()) {
            val sourceState = worklist.removeFirst()
            for (symbol in sourceState.symbolsAfterDot) {
                val gotoItems = goto(sourceState.items, symbol, augmentedGrammar, firstSets)
                if (gotoItems.isEmpty()) continue

                val targetState = statesByItems[gotoItems] ?: run {
                    val newState = SLR1State(id = states.size, items = gotoItems)
                    statesByItems[gotoItems] = newState
                    states.add(newState)
                    worklist.addLast(newState)
                    newState
                }

                transitions[sourceState.id to symbol] = targetState.id
            }
        }

        return SLR1Automata(
            states = states,
            transitions = transitions,
            initialState = initialState,
            augmentedGrammar = augmentedGrammar
        )
    }

    private fun augmentGrammar(grammar: Grammar): Grammar {
        val takenNames = grammar.nonTerminals.map { it.name }.toMutableSet()
        val augmentedStartName = pickUniqueName("${grammar.startSymbol.name}_augmented", takenNames)
        val augmentedStart = Symbol.NonTerminal(augmentedStartName)
        val augmentingProduction = Production(
            id = 0,
            head = augmentedStart,
            body = listOf(grammar.startSymbol)
        )
        return grammar.copy(
            nonTerminals = grammar.nonTerminals + augmentedStart,
            productions = listOf(augmentingProduction) + grammar.productions,
            startSymbol = augmentedStart
        )
    }

    private fun pickUniqueName(baseName: String, takenNames: Set<String>): String {
        if (baseName !in takenNames) return baseName
        var counter = 2
        while ("${baseName}_$counter" in takenNames) counter++
        return "${baseName}_$counter"
    }

    internal fun closure(
        items: Set<SLR1Item>,
        grammar: Grammar,
        firstSets: FirstSets
    ): Set<SLR1Item> {
        val cores = enumerateCores(items, grammar)
        val lookaheadsByCore = computeLookaheads(cores, items, grammar, firstSets)
        return cores.mapNotNull { core ->
            val lookaheads = lookaheadsByCore[core] ?: emptySet()
            if (lookaheads.isEmpty()) null
            else SLR1Item(core.first, core.second, lookaheads)
        }.toSet()
    }

    internal fun goto(
        items: Set<SLR1Item>,
        symbol: Symbol,
        grammar: Grammar,
        firstSets: FirstSets
    ): Set<SLR1Item> {
        val advancedItems = items
            .filter { it.symbolAfterDot == symbol }
            .map { it.advance() }
            .toSet()
        if (advancedItems.isEmpty()) return emptySet()
        return closure(advancedItems, grammar, firstSets)
    }

    private fun enumerateCores(
        initialItems: Set<SLR1Item>,
        grammar: Grammar
    ): Set<Pair<Production, Int>> {
        val cores = initialItems.map { it.core }.toMutableSet()
        val expandedNonTerminals = mutableSetOf<Symbol.NonTerminal>()
        val worklist = ArrayDeque(cores)

        while (worklist.isNotEmpty()) {
            val core = worklist.removeFirst()
            val (production, dotPosition) = core
            val symbolAfterDot = production.body.getOrNull(dotPosition)
            if (symbolAfterDot !is Symbol.NonTerminal) continue
            if (!expandedNonTerminals.add(symbolAfterDot)) continue

            val productionsWithThisHead = grammar.productionsByHead[symbolAfterDot] ?: continue
            for (newProduction in productionsWithThisHead) {
                val newCore = newProduction to 0
                if (cores.add(newCore)) {
                    worklist.addLast(newCore)
                }
            }
        }

        return cores
    }

    private fun computeLookaheads(
        cores: Set<Pair<Production, Int>>,
        initialItems: Set<SLR1Item>,
        grammar: Grammar,
        firstSets: FirstSets
    ): Map<Pair<Production, Int>, Set<Symbol>> {
        val lookaheads = mutableMapOf<Pair<Production, Int>, MutableSet<Symbol>>()
        for (core in cores) {
            lookaheads[core] = mutableSetOf()
        }
        for (initialItem in initialItems) {
            lookaheads[initialItem.core]?.addAll(initialItem.lookaheads)
        }

        var changed = true
        while (changed) {
            changed = false
            for (callerCore in cores) {
                val callerLookaheads = lookaheads[callerCore] ?: continue
                if (callerLookaheads.isEmpty()) continue

                val (callerProduction, callerDot) = callerCore
                val symbolAfterDot = callerProduction.body.getOrNull(callerDot) ?: continue
                if (symbolAfterDot !is Symbol.NonTerminal) continue

                val beta = callerProduction.body.subList(callerDot + 1, callerProduction.body.size)
                val newLookaheads = lookaheadsFor(beta, callerLookaheads, firstSets)

                val calleeProductions = grammar.productionsByHead[symbolAfterDot] ?: continue
                for (calleeProduction in calleeProductions) {
                    val calleeCore = calleeProduction to 0
                    val existingLookaheads = lookaheads[calleeCore] ?: continue
                    if (existingLookaheads.addAll(newLookaheads)) changed = true
                }
            }
        }

        return lookaheads.mapValues { it.value.toSet() }
    }

    private fun lookaheadsFor(
        beta: List<Symbol>,
        callerLookaheads: Set<Symbol>,
        firstSets: FirstSets
    ): Set<Symbol> {
        val result = mutableSetOf<Symbol>()
        for (symbol in beta) {
            when (symbol) {
                is Symbol.Terminal -> {
                    result.add(symbol)
                    return result
                }
                is Symbol.NonTerminal -> {
                    val firstSet = firstSets.firstOf(symbol)
                    result.addAll(firstSet.filter { it !is Symbol.Epsilon })
                    if (Symbol.Epsilon !in firstSet) return result
                }
                else -> {}
            }
        }
        result.addAll(callerLookaheads)
        return result
    }
}
