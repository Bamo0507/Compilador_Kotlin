package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1AutomatonMerger
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LALR1TableBuilderTest {

    // Dragon Book figure 4.41 grammar (CC grammar):
    //   S -> C C
    //   C -> c C | d
    private val nonTerminalS = Symbol.NonTerminal("S")
    private val nonTerminalC = Symbol.NonTerminal("C")
    private val terminalC = Symbol.Terminal("c")
    private val terminalD = Symbol.Terminal("d")
    private val productionSCC = Production(1, nonTerminalS, listOf(nonTerminalC, nonTerminalC))
    private val productionCcC = Production(2, nonTerminalC, listOf(terminalC, nonTerminalC))
    private val productionCd = Production(3, nonTerminalC, listOf(terminalD))

    private val ccGrammar = Grammar(
        terminals = setOf(terminalC, terminalD),
        nonTerminals = setOf(nonTerminalS, nonTerminalC),
        productions = listOf(productionSCC, productionCcC, productionCd),
        startSymbol = nonTerminalS,
        ignoredTokens = emptySet()
    )

    private fun buildLALR1Table(): org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val slr1Automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val mergedAutomata = LALR1AutomatonMerger.mergeFromSLR1(slr1Automata)
        return LALR1TableBuilder.build(mergedAutomata)
    }

    @Test
    fun `LALR1 table for CC grammar has no conflicts`() {
        val table = buildLALR1Table()
        assertTrue(table.isLALR1)
        assertTrue(table.conflicts.isEmpty())
    }

    @Test
    fun `LALR1 table for CC grammar has numStates equal to merged states count`() {
        val table = buildLALR1Table()
        assertEquals(7, table.numStates)
    }

    @Test
    fun `LALR1 ACTION sets Accept on EndMarker after seeing the start symbol`() {
        val table = buildLALR1Table()
        // The state reached from I0 on S should accept on EndMarker.
        // We identify that state by looking at the action map directly.
        val acceptEntry = table.action.entries.first { it.value is Action.Accept }
        assertEquals(Symbol.EndMarker, acceptEntry.key.second)
    }

    @Test
    fun `LALR1 GOTO contains entries for the non-terminals of the grammar`() {
        val table = buildLALR1Table()
        val gotoNonTerminals = table.goto.keys.map { it.second }.toSet()
        assertTrue(nonTerminalS in gotoNonTerminals)
        assertTrue(nonTerminalC in gotoNonTerminals)
    }
}
