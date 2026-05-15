package org.compiler.frontend.syntaxAnalyzer.sets

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.sets.models.FollowSets

object FollowSetComputer {

    fun compute(grammar: Grammar, firstSets: FirstSets): FollowSets {
        val followSets: Map<Symbol.NonTerminal, MutableSet<Symbol>> =
            grammar.nonTerminals.associateWith { mutableSetOf() }

        followSets[grammar.startSymbol]!!.add(Symbol.EndMarker)

        var changed = true
        while (changed) {
            changed = false
            for ((nonTerminal, productions) in grammar.productionsByHead) {
                for (production in productions) {
                    val body = production.body

                    for (i in body.indices) {
                        val symbol = body[i]
                        if (symbol !is Symbol.NonTerminal) continue

                        val symbolFollowSet = followSets[symbol] ?: continue
                        val symbolsAfter = body.subList(i + 1, body.size)

                        var symbolsAfterAllNullable = true
                        for (nextSymbol in symbolsAfter) {
                            if (nextSymbol is Symbol.Terminal) {
                                if (symbolFollowSet.add(nextSymbol)) changed = true
                                symbolsAfterAllNullable = false
                                break
                            }
                            if (nextSymbol is Symbol.NonTerminal) {
                                val firstOfNext = firstSets.firstOf(nextSymbol)
                                if (symbolFollowSet.addAll(firstOfNext.filter { it !is Symbol.Epsilon })) changed = true
                                if (Symbol.Epsilon !in firstOfNext) {
                                    symbolsAfterAllNullable = false
                                    break
                                }
                            }
                        }

                        if (symbolsAfterAllNullable) {
                            val followOfHead = followSets[nonTerminal] ?: emptySet()
                            if (symbolFollowSet.addAll(followOfHead)) changed = true
                        }
                    }
                }
            }
        }

        return FollowSets(followSets)
    }
}
