package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1AutomatonMerger
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LALR1AutomatonMergerTest {

    // Dragon Book figure 4.41 grammar:
    //   S -> C C
    //   C -> c C | d
    // SLR(1) canonico produce 10 estados (I0..I9); LALR(1) tras merge produce 7.
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

    private fun buildSLR1Automata(): org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        return SLR1AutomataBuilder.build(ccGrammar, firstSets)
    }

    @Test
    fun `merge of CC grammar collapses 10 states into 7`() {
        val slr1Automata = buildSLR1Automata()
        assertEquals(10, slr1Automata.states.size)

        val merged = LALR1AutomatonMerger.mergeFromSLR1(slr1Automata)
        assertEquals(7, merged.states.size)
    }

    @Test
    fun `merged state ids are sequential starting at 0`() {
        val merged = LALR1AutomatonMerger.mergeFromSLR1(buildSLR1Automata())
        val ids = merged.states.map { it.id }
        assertEquals((0 until merged.states.size).toList(), ids)
    }

    @Test
    fun `initial state remains id 0 after merge`() {
        val merged = LALR1AutomatonMerger.mergeFromSLR1(buildSLR1Automata())
        assertEquals(0, merged.initialState.id)
    }

    @Test
    fun `merged state combining I3 and I6 has unioned lookaheads on the C-c-dot-C kernel`() {
        // In SLR(1) canonico, I3 has [C -> c . C, {c, d}] and I6 has [C -> c . C, {$}].
        // After merge, the kernel item should carry lookaheads = {c, d, $}.
        val merged = LALR1AutomatonMerger.mergeFromSLR1(buildSLR1Automata())
        val expectedKernelCore = productionCcC to 1
        val stateWithKernel = merged.states.first { state ->
            state.items.any { it.core == expectedKernelCore }
        }
        val kernelItem = stateWithKernel.items.first { it.core == expectedKernelCore }
        assertEquals(
            setOf<Symbol>(terminalC, terminalD, Symbol.EndMarker),
            kernelItem.lookaheads
        )
    }

    @Test
    fun `merged transitions only reference state ids that exist in the merged automata`() {
        val merged = LALR1AutomatonMerger.mergeFromSLR1(buildSLR1Automata())
        val validIds = merged.states.map { it.id }.toSet()
        for ((key, target) in merged.transitions) {
            assertTrue(key.first in validIds, "Transition source ${key.first} not in merged states")
            assertTrue(target in validIds, "Transition target $target not in merged states")
        }
    }

    @Test
    fun `merged automata preserves the augmented grammar`() {
        val slr1Automata = buildSLR1Automata()
        val merged = LALR1AutomatonMerger.mergeFromSLR1(slr1Automata)
        assertEquals(slr1Automata.augmentedGrammar, merged.augmentedGrammar)
    }

    @Test
    fun `grammar without mergeable states produces an automata of the same size`() {
        // The simple expression grammar E -> id has no merge opportunities.
        val nonTerminalE = Symbol.NonTerminal("E")
        val terminalId = Symbol.Terminal("id")
        val grammar = Grammar(
            terminals = setOf(terminalId),
            nonTerminals = setOf(nonTerminalE),
            productions = listOf(Production(1, nonTerminalE, listOf(terminalId))),
            startSymbol = nonTerminalE,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val slr1Automata = SLR1AutomataBuilder.build(grammar, firstSets)
        val merged = LALR1AutomatonMerger.mergeFromSLR1(slr1Automata)
        assertEquals(slr1Automata.states.size, merged.states.size)
    }
}
