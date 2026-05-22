package org.compiler.frontend.syntaxAnalyzer.grammar

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead

object LeftRecursionRewriter {

    fun eliminateLeftRecursion(grammar: Grammar): Grammar {
        val orderedNonTerminals = grammar.nonTerminals.toList()
        val currentProductions = mutableMapOf<Symbol.NonTerminal, List<Production>>()
        val auxiliaryNonTerminals = mutableSetOf<Symbol.NonTerminal>()
        val processedInOrder = mutableListOf<Symbol.NonTerminal>()

        var idCounter = grammar.productions.maxOf { it.id }
        val nextId = { ++idCounter }

        for (nonTerminal in orderedNonTerminals) {
            var productions = grammar.productionsByHead[nonTerminal] ?: emptyList()

            for (processedNT in processedInOrder) {
                productions = substituteLeadingNonTerminal(
                    target = nonTerminal,
                    productions = productions,
                    substituteThis = processedNT,
                    withThese = currentProductions[processedNT] ?: emptyList(),
                    nextId = nextId
                )
            }

            val result = eliminateImmediateLeftRecursion(
                nonTerminal = nonTerminal,
                productions = productions,
                nextId = nextId
            )

            currentProductions[nonTerminal] = result.baseProductions
            processedInOrder.add(nonTerminal)

            if (result.auxiliaryNonTerminal != null) {
                auxiliaryNonTerminals.add(result.auxiliaryNonTerminal)
                currentProductions[result.auxiliaryNonTerminal] = result.auxiliaryProductions
            }
        }

        val allProductions = currentProductions.values.flatten()
        val allNonTerminals = grammar.nonTerminals + auxiliaryNonTerminals

        return Grammar(
            terminals = grammar.terminals,
            nonTerminals = allNonTerminals,
            productions = allProductions,
            startSymbol = grammar.startSymbol,
            ignoredTokens = grammar.ignoredTokens
        )
    }

    // Holds the result of rewriting a single non-terminal's immediate left recursion.
    private data class ImmediateRewriteResult(
        val baseProductions: List<Production>,
        val auxiliaryNonTerminal: Symbol.NonTerminal?, // null if no rewrite was needed
        val auxiliaryProductions: List<Production>     // empty if no rewrite was needed
    )

    // Transforms productions of the form:
    //   A -> A alpha | beta
    // into:
    //   A       -> beta A_prime
    //   A_prime -> alpha A_prime | ε
    // If no recursive production exists, returns the original productions unchanged.
    private fun eliminateImmediateLeftRecursion(
        nonTerminal: Symbol.NonTerminal,
        productions: List<Production>,
        nextId: () -> Int
    ): ImmediateRewriteResult {

        val recursive = productions.filter { production -> production.body.firstOrNull() == nonTerminal }
        val nonRecursive = productions.filter { production -> production.body.firstOrNull() != nonTerminal }

        if (recursive.isEmpty()) {
            return ImmediateRewriteResult(
                baseProductions = productions,
                auxiliaryNonTerminal = null,
                auxiliaryProductions = emptyList()
            )
        }

        val auxiliary = Symbol.NonTerminal("${nonTerminal.name}_prime")

        val rewrittenBase = nonRecursive.map { production ->
            Production(
                id = nextId(),
                head = nonTerminal,
                body = production.body + auxiliary
            )
        }

        val rewrittenRecursive = recursive.map { production ->
            Production(
                id = nextId(),
                head = auxiliary,
                body = production.body.drop(1) + auxiliary
            )
        }

        val epsilonProduction = Production(
            id = nextId(),
            head = auxiliary,
            body = listOf(Symbol.Epsilon)
        )

        return ImmediateRewriteResult(
            baseProductions = rewrittenBase,
            auxiliaryNonTerminal = auxiliary,
            auxiliaryProductions = rewrittenRecursive + epsilonProduction
        )
    }

    // Replaces every production of `target` whose body starts with `substituteThis`
    // with one new production per body in `withThese`.
    // Called once per previously-processed non-terminal (the Dragon Book's inner j-loop),
    // so that each substitution sees the results of the previous one.
    private fun substituteLeadingNonTerminal(
        target: Symbol.NonTerminal,
        productions: List<Production>,
        substituteThis: Symbol.NonTerminal,
        withThese: List<Production>,
        nextId: () -> Int
    ): List<Production> {
        return productions.flatMap { production ->
            if (production.body.firstOrNull() == substituteThis) {
                withThese.map { candidate ->
                    Production(
                        id = nextId(),
                        head = target,
                        body = candidate.body + production.body.drop(1)
                    )
                }
            } else {
                listOf(production)
            }
        }
    }
}
