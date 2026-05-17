package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.FollowSetComputer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LL1TableBuilderTest {

    @Test
    fun `ambiguous grammar with two productions sharing a FIRST terminal yields a conflict`() {
        // S -> a       (production 1)
        // S -> a b     (production 2)
        // Both bodies start with 'a', so M[S, a] has two productions: first-first conflict.
        val startSymbol = Symbol.NonTerminal("S")
        val terminalA   = Symbol.Terminal("a")
        val terminalB   = Symbol.Terminal("b")

        val productionOne = Production(1, startSymbol, listOf(terminalA))
        val productionTwo = Production(2, startSymbol, listOf(terminalA, terminalB))

        val grammar = Grammar(
            terminals     = setOf(terminalA, terminalB),
            nonTerminals  = setOf(startSymbol),
            productions   = listOf(productionOne, productionTwo),
            startSymbol   = startSymbol,
            ignoredTokens = emptySet()
        )

        val firstSets  = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        val table      = LL1TableBuilder.build(grammar, firstSets, followSets)

        assertFalse(table.isLL1, "Grammar should not be LL(1)")
        assertEquals(1, table.conflicts.size, "Expected exactly one conflict cell")

        val conflict = table.conflicts.first()
        assertEquals(startSymbol, conflict.nonTerminal)
        assertEquals(terminalA, conflict.terminal)
        assertEquals(setOf(productionOne, productionTwo), conflict.productions.toSet())

        // Conflict resolution policy picks the lowest-id production.
        assertEquals(productionOne, table.lookup(startSymbol, terminalA))
    }
}
