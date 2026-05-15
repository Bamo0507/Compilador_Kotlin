package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstSetComputerTest {

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
    fun `Dragon Book expression grammar - FIRST(E) equals open paren and id`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        assertEquals(setOf(terminalLParen, terminalId), firstSets.firstOf(nonTerminalE))
    }

    @Test
    fun `Dragon Book expression grammar - FIRST(T) equals open paren and id`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        assertEquals(setOf(terminalLParen, terminalId), firstSets.firstOf(nonTerminalT))
    }

    @Test
    fun `Dragon Book expression grammar - FIRST(F) equals open paren and id`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        assertEquals(setOf(terminalLParen, terminalId), firstSets.firstOf(nonTerminalF))
    }

    @Test
    fun `direct epsilon production adds epsilon to FIRST`() {
        // A -> a | epsilon
        val nonTerminalA = Symbol.NonTerminal("A")
        val terminalA = Symbol.Terminal("a")
        val grammar = Grammar(
            terminals = setOf(terminalA),
            nonTerminals = setOf(nonTerminalA),
            productions = listOf(
                Production(1, nonTerminalA, listOf(terminalA)),
                Production(2, nonTerminalA, listOf(Symbol.Epsilon))
            ),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        assertEquals(setOf(terminalA, Symbol.Epsilon), firstSets.firstOf(nonTerminalA))
    }

    @Test
    fun `nullable leading non-terminal passes through to next symbol`() {
        // S -> A B; A -> a | epsilon; B -> b
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
        assertEquals(setOf(terminalA, terminalB), firstSets.firstOf(nonTerminalS))
        assertEquals(setOf(terminalA, Symbol.Epsilon), firstSets.firstOf(nonTerminalA))
        assertEquals(setOf(terminalB), firstSets.firstOf(nonTerminalB))
    }

    @Test
    fun `all symbols nullable makes production body contribute epsilon`() {
        // S -> A B; A -> a | epsilon; B -> b | epsilon
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
                Production(4, nonTerminalB, listOf(terminalB)),
                Production(5, nonTerminalB, listOf(Symbol.Epsilon))
            ),
            startSymbol = nonTerminalS,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        assertEquals(setOf(terminalA, terminalB, Symbol.Epsilon), firstSets.firstOf(nonTerminalS))
    }

    @Test
    fun `non-nullable leading symbol blocks further FIRST propagation`() {
        // S -> A B c; A -> a (not nullable)
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
                Production(3, nonTerminalB, listOf(terminalB))
            ),
            startSymbol = nonTerminalS,
            ignoredTokens = emptySet()
        )

        val firstSets = FirstSetComputer.compute(grammar)
        assertEquals(setOf(terminalA), firstSets.firstOf(nonTerminalS))
    }

    @Test
    fun `FIRST of unknown non-terminal returns empty set`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        assertEquals(emptySet(), firstSets.firstOf(Symbol.NonTerminal("unknown")))
    }

    // firstOfSequence tests

    @Test
    fun `firstOfSequence starting with terminal returns that terminal only`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val result = FirstSetComputer.firstOfSequence(
            listOf(terminalPlus, nonTerminalT),
            firstSets
        )
        assertEquals(setOf(terminalPlus), result)
    }

    @Test
    fun `firstOfSequence with non-nullable leading non-terminal stops at it`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val result = FirstSetComputer.firstOfSequence(
            listOf(nonTerminalT, terminalPlus, nonTerminalT),
            firstSets
        )
        assertEquals(setOf(terminalLParen, terminalId), result)
    }

    @Test
    fun `firstOfSequence with nullable leading non-terminal includes next symbol`() {
        // A -> a | epsilon; B -> b
        // firstOfSequence([A, B]) = {a, b}
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val terminalA = Symbol.Terminal("a")
        val terminalB = Symbol.Terminal("b")
        val grammar = Grammar(
            terminals = setOf(terminalA, terminalB),
            nonTerminals = setOf(nonTerminalA, nonTerminalB),
            productions = listOf(
                Production(1, nonTerminalA, listOf(terminalA)),
                Production(2, nonTerminalA, listOf(Symbol.Epsilon)),
                Production(3, nonTerminalB, listOf(terminalB))
            ),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val result = FirstSetComputer.firstOfSequence(listOf(nonTerminalA, nonTerminalB), firstSets)
        assertEquals(setOf(terminalA, terminalB), result)
    }

    @Test
    fun `firstOfSequence with all nullable symbols includes epsilon`() {
        // A -> a | epsilon; B -> b | epsilon
        // firstOfSequence([A, B]) = {a, b, epsilon}
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val terminalA = Symbol.Terminal("a")
        val terminalB = Symbol.Terminal("b")
        val grammar = Grammar(
            terminals = setOf(terminalA, terminalB),
            nonTerminals = setOf(nonTerminalA, nonTerminalB),
            productions = listOf(
                Production(1, nonTerminalA, listOf(terminalA)),
                Production(2, nonTerminalA, listOf(Symbol.Epsilon)),
                Production(3, nonTerminalB, listOf(terminalB)),
                Production(4, nonTerminalB, listOf(Symbol.Epsilon))
            ),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )
        val firstSets = FirstSetComputer.compute(grammar)
        val result = FirstSetComputer.firstOfSequence(listOf(nonTerminalA, nonTerminalB), firstSets)
        assertEquals(setOf(terminalA, terminalB, Symbol.Epsilon), result)
    }

    @Test
    fun `firstOfSequence with epsilon symbol returns epsilon`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val result = FirstSetComputer.firstOfSequence(listOf(Symbol.Epsilon), firstSets)
        assertEquals(setOf(Symbol.Epsilon), result)
    }

    @Test
    fun `firstOfSequence with empty body returns epsilon`() {
        val firstSets = FirstSetComputer.compute(dragonBookGrammar)
        val result = FirstSetComputer.firstOfSequence(emptyList(), firstSets)
        assertTrue(Symbol.Epsilon in result)
    }
}
