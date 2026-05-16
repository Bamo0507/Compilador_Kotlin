package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SLR1AutomataBuilderTest {

    // Dragon Book figure 4.41 grammar:
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

    @Test
    fun `closure expands NonTerminal productions and computes lookaheads`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val initial = SLR1Item(productionSCC, 0, setOf(Symbol.EndMarker))
        val result = SLR1AutomataBuilder.closure(setOf(initial), ccGrammar, firstSets)

        assertEquals(3, result.size)
        assertTrue(SLR1Item(productionSCC, 0, setOf(Symbol.EndMarker)) in result)
        assertTrue(SLR1Item(productionCcC, 0, setOf(terminalC, terminalD)) in result)
        assertTrue(SLR1Item(productionCd, 0, setOf(terminalC, terminalD)) in result)
    }

    @Test
    fun `closure with terminal after dot does not expand anything`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val initial = SLR1Item(productionCd, 0, setOf(Symbol.EndMarker))
        val result = SLR1AutomataBuilder.closure(setOf(initial), ccGrammar, firstSets)

        assertEquals(1, result.size)
        assertTrue(initial in result)
    }

    @Test
    fun `closure propagates caller lookaheads through nullable continuation`() {
        // Grammar with nullable C so that beta = [C] falls through to caller lookahead.
        //   A -> B C
        //   B -> b
        //   C -> c | epsilon
        // Initial: [A -> . B C, $]
        // Expected: B gets lookaheads {c, $} (c from FIRST(C), $ via Rama 3 after C nullable)
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val nonTerminalCNullable = Symbol.NonTerminal("C")
        val terminalB = Symbol.Terminal("b")
        val terminalCSym = Symbol.Terminal("c")
        val productionABC = Production(1, nonTerminalA, listOf(nonTerminalB, nonTerminalCNullable))
        val productionBb = Production(2, nonTerminalB, listOf(terminalB))
        val productionCc = Production(3, nonTerminalCNullable, listOf(terminalCSym))
        val productionCepsilon = Production(4, nonTerminalCNullable, listOf(Symbol.Epsilon))
        val grammar = Grammar(
            terminals = setOf(terminalB, terminalCSym),
            nonTerminals = setOf(nonTerminalA, nonTerminalB, nonTerminalCNullable),
            productions = listOf(productionABC, productionBb, productionCc, productionCepsilon),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val initial = SLR1Item(productionABC, 0, setOf(Symbol.EndMarker))
        val result = SLR1AutomataBuilder.closure(setOf(initial), grammar, firstSets)

        val bItem = result.first { it.production == productionBb }
        assertEquals(setOf<Symbol>(terminalCSym, Symbol.EndMarker), bItem.lookaheads)
    }

    @Test
    fun `goto advances matching items and applies closure`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val initial = SLR1Item(productionSCC, 0, setOf(Symbol.EndMarker))
        val i0 = SLR1AutomataBuilder.closure(setOf(initial), ccGrammar, firstSets)

        // goto(I0, c) advances [C -> . cC, {c,d}] to [C -> c . C, {c,d}] then closures C's productions.
        val gotoOnC = SLR1AutomataBuilder.goto(i0, terminalC, ccGrammar, firstSets)
        assertEquals(3, gotoOnC.size)
        assertTrue(SLR1Item(productionCcC, 1, setOf(terminalC, terminalD)) in gotoOnC)
        assertTrue(SLR1Item(productionCcC, 0, setOf(terminalC, terminalD)) in gotoOnC)
        assertTrue(SLR1Item(productionCd, 0, setOf(terminalC, terminalD)) in gotoOnC)
    }

    @Test
    fun `goto returns empty when no items match the symbol`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val initial = SLR1Item(productionSCC, 0, setOf(Symbol.EndMarker))
        val i0 = SLR1AutomataBuilder.closure(setOf(initial), ccGrammar, firstSets)

        val gotoOnUnknown = SLR1AutomataBuilder.goto(i0, Symbol.Terminal("UNKNOWN"), ccGrammar, firstSets)
        assertEquals(emptySet<SLR1Item>(), gotoOnUnknown)
    }

    @Test
    fun `build augments the grammar with a production having id 0`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)

        val augmenting = automata.augmentedGrammar.productions.first { it.id == 0 }
        assertEquals(listOf<Symbol>(nonTerminalS), augmenting.body)
        assertEquals(augmenting.head, automata.augmentedGrammar.startSymbol)
    }

    @Test
    fun `build produces 10 states for the Dragon Book CC grammar`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        assertEquals(10, automata.states.size)
    }

    @Test
    fun `build initial state contains 4 items including augmenting kernel`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)

        val initialState = automata.initialState
        assertEquals(0, initialState.id)
        assertEquals(4, initialState.items.size)
        val augmentingKernel = initialState.items.first { it.production.id == 0 }
        assertEquals(0, augmentingKernel.dotPosition)
        assertEquals(setOf<Symbol>(Symbol.EndMarker), augmentingKernel.lookaheads)
    }

    @Test
    fun `build transition on start symbol leads to state with completed augmenting item`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)

        val targetId = automata.transitions[automata.initialState.id to nonTerminalS]!!
        val targetState = automata.states[targetId]
        val completed = targetState.items.first { it.production.id == 0 }
        assertTrue(completed.isComplete)
        assertEquals(setOf<Symbol>(Symbol.EndMarker), completed.lookaheads)
    }

    @Test
    fun `build dedup recognizes that goto from I3 on c returns to I3`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)

        // I3 contains the kernel [C -> c . C, {c, d}] reached via goto(I0, c).
        val expectedKernel = SLR1Item(productionCcC, 1, setOf(terminalC, terminalD))
        val state3 = automata.states.first { expectedKernel in it.items }
        val targetOnC = automata.transitions[state3.id to terminalC]
        assertEquals(state3.id, targetOnC)
    }
}
