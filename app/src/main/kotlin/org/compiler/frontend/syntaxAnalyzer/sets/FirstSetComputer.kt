package org.compiler.frontend.syntaxAnalyzer.sets

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets

object FirstSetComputer {

    fun compute(grammar: Grammar): FirstSets {
        val nullables = computeNullables(grammar)
        val firstSets: Map<Symbol.NonTerminal, MutableSet<Symbol>> =
            grammar.nonTerminals.associateWith { mutableSetOf() }

        var changed = true
        while (changed) {
            changed = false
            for ((nonTerminal, productions) in grammar.productionsByHead) {
                for (production in productions) {
                    val nonTerminalResults = firstSets[nonTerminal] ?: continue

                    for (symbol in production.body) {
                        if (symbol is Symbol.Epsilon) {
                            if (nonTerminalResults.add(Symbol.Epsilon)) changed = true
                            break
                        }
                        if (symbol is Symbol.Terminal) {
                            if (nonTerminalResults.add(symbol)) changed = true
                            break
                        }
                        if (symbol is Symbol.NonTerminal) {
                            val firstOfSymbol = firstSets[symbol] ?: emptySet()
                            if (nonTerminalResults.addAll(firstOfSymbol.filter { it !is Symbol.Epsilon })) changed = true
                            if (symbol !in nullables) break
                        }
                    }

                    val allNullable = production.body.all { symbol ->
                        symbol is Symbol.Epsilon ||
                        (symbol is Symbol.NonTerminal && symbol in nullables)
                    }
                    if (allNullable && nonTerminalResults.add(Symbol.Epsilon)) changed = true
                }
            }
        }

        return FirstSets(firstSets)
    }

    fun firstOfSequence(body: List<Symbol>, firstSets: FirstSets): Set<Symbol> {
        val result = mutableSetOf<Symbol>()

        for (symbol in body) {
            if (symbol is Symbol.Epsilon) {
                result.add(Symbol.Epsilon)
                break
            }
            if (symbol is Symbol.Terminal) {
                result.add(symbol)
                break
            }
            if (symbol is Symbol.NonTerminal) {
                val firstOfSymbol = firstSets.firstOf(symbol)
                result.addAll(firstOfSymbol.filter { it !is Symbol.Epsilon })
                if (Symbol.Epsilon !in firstOfSymbol) break
            }
        }

        val allNullable = body.all { symbol ->
            symbol is Symbol.Epsilon ||
            (symbol is Symbol.NonTerminal && Symbol.Epsilon in firstSets.firstOf(symbol))
        }
        if (allNullable) result.add(Symbol.Epsilon)

        return result
    }

    private fun computeNullables(grammar: Grammar): Set<Symbol.NonTerminal> {
        val nullables = mutableSetOf<Symbol.NonTerminal>()

        var changed = true
        while (changed) {
            changed = false
            for ((nonTerminal, productions) in grammar.productionsByHead) {
                if (nonTerminal in nullables) continue
                for (production in productions) {
                    val isNullable = production.body.all { symbol ->
                        symbol is Symbol.Epsilon ||
                        (symbol is Symbol.NonTerminal && symbol in nullables)
                    }
                    if (isNullable) {
                        nullables.add(nonTerminal)
                        changed = true
                        break
                    }
                }
            }
        }

        return nullables
    }
}
