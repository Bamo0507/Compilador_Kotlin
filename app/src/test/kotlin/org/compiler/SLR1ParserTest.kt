package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1Parser
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1TableBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SLR1ParserTest {

    // Dragon Book §4.1 expression grammar (unambiguous, no left-recursion elimination needed for SLR(1)):
    //   E -> E + T | T
    //   T -> T * F | F
    //   F -> ( E ) | id
    private val nonTerminalE = Symbol.NonTerminal("E")
    private val nonTerminalT = Symbol.NonTerminal("T")
    private val nonTerminalF = Symbol.NonTerminal("F")
    private val terminalPlus = Symbol.Terminal("+")
    private val terminalTimes = Symbol.Terminal("*")
    private val terminalLParen = Symbol.Terminal("(")
    private val terminalRParen = Symbol.Terminal(")")
    private val terminalId = Symbol.Terminal("id")

    private val productionEpT = Production(1, nonTerminalE, listOf(nonTerminalE, terminalPlus, nonTerminalT))
    private val productionET = Production(2, nonTerminalE, listOf(nonTerminalT))
    private val productionTtF = Production(3, nonTerminalT, listOf(nonTerminalT, terminalTimes, nonTerminalF))
    private val productionTF = Production(4, nonTerminalT, listOf(nonTerminalF))
    private val productionFParen = Production(5, nonTerminalF, listOf(terminalLParen, nonTerminalE, terminalRParen))
    private val productionFid = Production(6, nonTerminalF, listOf(terminalId))

    private val expressionGrammar = Grammar(
        terminals = setOf(terminalPlus, terminalTimes, terminalLParen, terminalRParen, terminalId),
        nonTerminals = setOf(nonTerminalE, nonTerminalT, nonTerminalF),
        productions = listOf(productionEpT, productionET, productionTtF, productionTF, productionFParen, productionFid),
        startSymbol = nonTerminalE,
        ignoredTokens = emptySet()
    )

    private fun buildTable(): org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table {
        val firstSets = FirstSetComputer.compute(expressionGrammar)
        val automata = SLR1AutomataBuilder.build(expressionGrammar, firstSets)
        return SLR1TableBuilder.build(automata)
    }

    private fun token(category: String, lexeme: String): Token =
        Token(category = category, lexeme = lexeme, symbolIndex = null)

    @Test
    fun `parsing a single id is accepted with E as the root non-terminal`() {
        val table = buildTable()
        val tokens = listOf(token("id", "x"))
        val result = SLR1Parser.parse(tokens, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        val root = result.parseTree
        assertTrue(root is ParseTree.InternalNode)
        assertEquals(nonTerminalE, root.symbol)
    }

    @Test
    fun `parsing id + id is accepted and produces a left-associative tree`() {
        val table = buildTable()
        val tokens = listOf(
            token("id", "a"),
            token("+", "+"),
            token("id", "b")
        )
        val result = SLR1Parser.parse(tokens, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(nonTerminalE, root.symbol)
        assertEquals(productionEpT, root.production)
        assertEquals(3, root.children.size)
    }

    @Test
    fun `parsing id plus id times id is accepted with precedence respected in the tree`() {
        val table = buildTable()
        val tokens = listOf(
            token("id", "a"),
            token("+", "+"),
            token("id", "b"),
            token("*", "*"),
            token("id", "c")
        )
        val result = SLR1Parser.parse(tokens, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        // Root is E -> E + T, where the right T is the T * F branch capturing the multiplication.
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(productionEpT, root.production)
        val rightT = root.children[2] as ParseTree.InternalNode
        assertEquals(productionTtF, rightT.production)
    }

    @Test
    fun `parsing id + + is rejected with an error pointing at the second plus`() {
        val table = buildTable()
        val tokens = listOf(
            token("id", "a"),
            token("+", "+"),
            token("+", "+")
        )
        val result = SLR1Parser.parse(tokens, emptySet(), table)

        assertTrue(result is ParseResult.Rejected, "Expected Rejected, got $result")
        assertEquals("+", result.error.foundToken?.lexeme)
        assertTrue(result.error.message.contains("Unexpected token '+'"))
    }

    @Test
    fun `accepted parse trace contains one step per parser action`() {
        val table = buildTable()
        val tokens = listOf(token("id", "x"))
        val result = SLR1Parser.parse(tokens, emptySet(), table) as ParseResult.Accepted

        // Parsing a single id involves: Shift(id), Reduce(F->id), Reduce(T->F), Reduce(E->T), Accept.
        // Each step appears in the trace before its action is applied.
        assertEquals(5, result.trace.size)
    }

    @Test
    fun `accepted parse leaves a single tree on the stack and matches the start symbol`() {
        val table = buildTable()
        val tokens = listOf(
            token("id", "a"),
            token("+", "+"),
            token("id", "b")
        )
        val result = SLR1Parser.parse(tokens, emptySet(), table) as ParseResult.Accepted
        val root = result.parseTree as ParseTree.InternalNode
        // The root of an accepted parse is the original start symbol, not the augmenting one.
        assertEquals(nonTerminalE, root.symbol)
    }
}
