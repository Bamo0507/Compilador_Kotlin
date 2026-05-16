package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.slr1.models.ConflictType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SLR1TableBuilderTest {

    // Dragon Book figure 4.41 grammar (no conflicts):
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
    fun `build returns no conflicts for the well-formed CC grammar`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        assertTrue(table.isSLR1)
        assertTrue(table.conflicts.isEmpty())
    }

    @Test
    fun `build sets Accept on EndMarker for the state reached after the start symbol`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        val targetAfterStart = automata.transitions[0 to nonTerminalS]!!
        assertEquals(Action.Accept, table.action[targetAfterStart to Symbol.EndMarker])
    }

    @Test
    fun `build sets Shift entries with correct target state ids`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        val cTarget = automata.transitions[0 to terminalC]!!
        val dTarget = automata.transitions[0 to terminalD]!!
        assertEquals(Action.Shift(cTarget), table.action[0 to terminalC])
        assertEquals(Action.Shift(dTarget), table.action[0 to terminalD])
    }

    @Test
    fun `build sets Reduce entries on each lookahead of a complete item`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        // The state holding [C -> d ., {c, d}] should reduce by productionCd on c and d.
        val reduceState = automata.states.first { state ->
            state.items.any { it.production == productionCd && it.isComplete && terminalC in it.lookaheads }
        }
        assertEquals(Action.Reduce(productionCd), table.action[reduceState.id to terminalC])
        assertEquals(Action.Reduce(productionCd), table.action[reduceState.id to terminalD])
    }

    @Test
    fun `build populates GOTO for NonTerminal transitions`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        val sTarget = automata.transitions[0 to nonTerminalS]!!
        val cTarget = automata.transitions[0 to nonTerminalC]!!
        assertEquals(sTarget, table.goto[0 to nonTerminalS])
        assertEquals(cTarget, table.goto[0 to nonTerminalC])
    }

    @Test
    fun `build numStates matches the automata states count`() {
        val firstSets = FirstSetComputer.compute(ccGrammar)
        val automata = SLR1AutomataBuilder.build(ccGrammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        assertEquals(automata.states.size, table.numStates)
    }

    @Test
    fun `build detects reduce-reduce conflict and resolves with the lower production id`() {
        // A -> B | C; B -> a; C -> a
        // After shifting 'a', the state has [B -> a., $] and [C -> a., $] -- both reduce on $.
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val nonTerminalCsharp = Symbol.NonTerminal("C")
        val terminalA = Symbol.Terminal("a")
        val productionAB = Production(1, nonTerminalA, listOf(nonTerminalB))
        val productionAC = Production(2, nonTerminalA, listOf(nonTerminalCsharp))
        val productionBa = Production(3, nonTerminalB, listOf(terminalA))
        val productionCa = Production(4, nonTerminalCsharp, listOf(terminalA))
        val grammar = Grammar(
            terminals = setOf(terminalA),
            nonTerminals = setOf(nonTerminalA, nonTerminalB, nonTerminalCsharp),
            productions = listOf(productionAB, productionAC, productionBa, productionCa),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val automata = SLR1AutomataBuilder.build(grammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        assertFalse(table.isSLR1)
        val reduceReduce = table.conflicts.first { it.type == ConflictType.REDUCE_REDUCE }
        assertEquals(Symbol.EndMarker, reduceReduce.terminal)
        // Policy: lower production id wins. productionBa has id 3 < productionCa id 4.
        assertEquals(Action.Reduce(productionBa), table.action[reduceReduce.state to Symbol.EndMarker])
    }

    @Test
    fun `build detects shift-reduce conflict and resolves with Shift`() {
        // S -> A a; A -> a | a A
        // After shifting first 'a', the state can both reduce by A -> a or shift another 'a'.
        val startS = Symbol.NonTerminal("S")
        val nonTerminalA = Symbol.NonTerminal("A")
        val terminalA = Symbol.Terminal("a")
        val productionSAa = Production(1, startS, listOf(nonTerminalA, terminalA))
        val productionAa = Production(2, nonTerminalA, listOf(terminalA))
        val productionAaA = Production(3, nonTerminalA, listOf(terminalA, nonTerminalA))
        val grammar = Grammar(
            terminals = setOf(terminalA),
            nonTerminals = setOf(startS, nonTerminalA),
            productions = listOf(productionSAa, productionAa, productionAaA),
            startSymbol = startS,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val automata = SLR1AutomataBuilder.build(grammar, firstSets)
        val table = SLR1TableBuilder.build(automata)

        assertFalse(table.isSLR1)
        val shiftReduce = table.conflicts.first { it.type == ConflictType.SHIFT_REDUCE }
        assertEquals(terminalA, shiftReduce.terminal)
        val winner = table.action[shiftReduce.state to terminalA]
        assertTrue(winner is Action.Shift, "Expected Shift to win shift-reduce, got $winner")
    }
}
