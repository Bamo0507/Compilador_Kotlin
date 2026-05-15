package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.FollowSetComputer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowSetComputerTest {

    // E -> E + T | T
    // T -> T * F | F
    // F -> ( E ) | id
    private val nonTerminalE = Symbol.NonTerminal("E")
    private val nonTerminalT = Symbol.NonTerminal("T")
    private val nonTerminalF = Symbol.NonTerminal("F")
    private val terminalPlus = Symbol.Terminal("+")
    private val terminalStar = Symbol.Terminal("*")
    private val terminalLParen = Symbol.Terminal("(")
    private val terminalRParen = Symbol.Terminal(")")
    private val terminalId = Symbol.Terminal("id")

    private val dragonBookGrammar = Grammar(
        terminals = setOf(terminalPlus, terminalStar, terminalLParen, terminalRParen, terminalId),
        nonTerminals = setOf(nonTerminalE, nonTerminalT, nonTerminalF),
        productions = listOf(
            Production(1, nonTerminalE, listOf(nonTerminalE, terminalPlus, nonTerminalT)),
            Production(2, nonTerminalE, listOf(nonTerminalT)),
            Production(3, nonTerminalT, listOf(nonTerminalT, terminalStar, nonTerminalF)),
            Production(4, nonTerminalT, listOf(nonTerminalF)),
            Production(5, nonTerminalF, listOf(terminalLParen, nonTerminalE, terminalRParen)),
            Production(6, nonTerminalF, listOf(terminalId))
        ),
        startSymbol = nonTerminalE,
        ignoredTokens = emptySet()
    )

    @Test
    fun `Dragon Book expression grammar - FOLLOW(E) equals plus close-paren and end-marker`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        assertEquals(
            setOf(terminalPlus, terminalRParen, Symbol.EndMarker),
            followSets.followOf(nonTerminalE)
        )
    }

    @Test
    fun `Dragon Book expression grammar - FOLLOW(T) equals plus star close-paren and end-marker`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        assertEquals(
            setOf(terminalPlus, terminalStar, terminalRParen, Symbol.EndMarker),
            followSets.followOf(nonTerminalT)
        )
    }

    @Test
    fun `Dragon Book expression grammar - FOLLOW(F) equals plus star close-paren and end-marker`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        assertEquals(
            setOf(terminalPlus, terminalStar, terminalRParen, Symbol.EndMarker),
            followSets.followOf(nonTerminalF)
        )
    }

    @Test
    fun `start symbol always has end-marker in FOLLOW`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        assertTrue(Symbol.EndMarker in followSets.followOf(nonTerminalE))
    }

    @Test
    fun `FOLLOW propagates from head to last position non-terminal`() {
        // S -> A b; A -> c
        // FOLLOW(A) = {b}; FOLLOW(S) = {$}
        val nonTerminalS = Symbol.NonTerminal("S")
        val nonTerminalA = Symbol.NonTerminal("A")
        val terminalB = Symbol.Terminal("b")
        val terminalC = Symbol.Terminal("c")
        val grammar = Grammar(
            terminals = setOf(terminalB, terminalC),
            nonTerminals = setOf(nonTerminalS, nonTerminalA),
            productions = listOf(
                Production(1, nonTerminalS, listOf(nonTerminalA, terminalB)),
                Production(2, nonTerminalA, listOf(terminalC))
            ),
            startSymbol = nonTerminalS,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        assertEquals(setOf(terminalB), followSets.followOf(nonTerminalA))
        assertEquals(setOf(Symbol.EndMarker), followSets.followOf(nonTerminalS))
    }

    @Test
    fun `nullable symbol after B causes FOLLOW of head to propagate to B`() {
        // S -> A B; A -> a | epsilon; B -> b
        // FOLLOW(A) = {b}, FOLLOW(B) = FOLLOW(S) = {$}
        val nonTerminalS = Symbol.NonTerminal("S")
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val terminalA = Symbol.Terminal("a")
        val terminalB = Symbol.Terminal("b")
        val grammar = Grammar(
            terminals = setOf(terminalA, terminalB),
            nonTerminals = setOf(nonTerminalS, nonTerminalA, nonTerminalB),
            productions = listOf(
                Production(1, nonTerminalS, listOf(nonTerminalA, nonTerminalB)),
                Production(2, nonTerminalA, listOf(terminalA)),
                Production(3, nonTerminalA, listOf(Symbol.Epsilon)),
                Production(4, nonTerminalB, listOf(terminalB))
            ),
            startSymbol = nonTerminalS,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        assertEquals(setOf(terminalB), followSets.followOf(nonTerminalA))
        assertEquals(setOf(Symbol.EndMarker), followSets.followOf(nonTerminalB))
    }

    @Test
    fun `FOLLOW includes first of nullable suffix and head follow when suffix is all nullable`() {
        // S -> A B c; A -> a | epsilon; B -> b | epsilon
        // FOLLOW(A) = {b, c}  (B nullable so c is reachable after A)
        // FOLLOW(B) = {c}
        val nonTerminalS = Symbol.NonTerminal("S")
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val terminalA = Symbol.Terminal("a")
        val terminalB = Symbol.Terminal("b")
        val terminalC = Symbol.Terminal("c")
        val grammar = Grammar(
            terminals = setOf(terminalA, terminalB, terminalC),
            nonTerminals = setOf(nonTerminalS, nonTerminalA, nonTerminalB),
            productions = listOf(
                Production(1, nonTerminalS, listOf(nonTerminalA, nonTerminalB, terminalC)),
                Production(2, nonTerminalA, listOf(terminalA)),
                Production(3, nonTerminalA, listOf(Symbol.Epsilon)),
                Production(4, nonTerminalB, listOf(terminalB)),
                Production(5, nonTerminalB, listOf(Symbol.Epsilon))
            ),
            startSymbol = nonTerminalS,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        assertEquals(setOf(terminalB, terminalC), followSets.followOf(nonTerminalA))
        assertEquals(setOf(terminalC), followSets.followOf(nonTerminalB))
    }

    @Test
    fun `FOLLOW does not contain epsilon`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        for (nonTerminal in dragonBookGrammar.nonTerminals) {
            assertTrue(Symbol.Epsilon !in followSets.followOf(nonTerminal),
                "FOLLOW(${nonTerminal.name}) must not contain epsilon")
        }
    }

    @Test
    fun `FOLLOW of unknown non-terminal returns empty set`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val followSets = FollowSetComputer.compute(dragonBookGrammar, firstSets)
        assertEquals(emptySet(), followSets.followOf(Symbol.NonTerminal("unknown")))
    }

    @Test
    fun `single production grammar gives only end-marker in start FOLLOW`() {
        // A -> a
        val nonTerminalA = Symbol.NonTerminal("A")
        val terminalA = Symbol.Terminal("a")
        val grammar = Grammar(
            terminals = setOf(terminalA),
            nonTerminals = setOf(nonTerminalA),
            productions = listOf(Production(1, nonTerminalA, listOf(terminalA))),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        assertEquals(setOf(Symbol.EndMarker), followSets.followOf(nonTerminalA))
    }
}
