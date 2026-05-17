package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.models.TokenEntry
import org.compiler.models.LexemeLocation
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1Parser
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.FollowSetComputer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LL1ParserTest {

    // Dragon Book §4.4 LL(1) expression grammar:
    //   E  -> T E'
    //   E' -> + T E' | ε
    //   T  -> F T'
    //   T' -> * F T' | ε
    //   F  -> ( E ) | id
    private val E      = Symbol.NonTerminal("E")
    private val Eprime = Symbol.NonTerminal("E'")
    private val T      = Symbol.NonTerminal("T")
    private val Tprime = Symbol.NonTerminal("T'")
    private val F      = Symbol.NonTerminal("F")

    private val plus   = Symbol.Terminal("+")
    private val times  = Symbol.Terminal("*")
    private val lparen = Symbol.Terminal("(")
    private val rparen = Symbol.Terminal(")")
    private val id     = Symbol.Terminal("id")

    private val p1 = Production(1, E,      listOf(T, Eprime))
    private val p2 = Production(2, Eprime, listOf(plus, T, Eprime))
    private val p3 = Production(3, Eprime, listOf(Symbol.Epsilon))
    private val p4 = Production(4, T,      listOf(F, Tprime))
    private val p5 = Production(5, Tprime, listOf(times, F, Tprime))
    private val p6 = Production(6, Tprime, listOf(Symbol.Epsilon))
    private val p7 = Production(7, F,      listOf(lparen, E, rparen))
    private val p8 = Production(8, F,      listOf(id))

    private val grammar = Grammar(
        terminals    = setOf(plus, times, lparen, rparen, id),
        nonTerminals = setOf(E, Eprime, T, Tprime, F),
        productions  = listOf(p1, p2, p3, p4, p5, p6, p7, p8),
        startSymbol  = E,
        ignoredTokens = emptySet()
    )

    private fun buildTable() = run {
        val firstSets  = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        LL1TableBuilder.build(grammar, firstSets, followSets)
    }

    private fun entry(category: String, lexeme: String, line: Int = 1, position: Int = 1): TokenEntry =
        TokenEntry(
            token    = Token(category = category, lexeme = lexeme, symbolIndex = null),
            location = LexemeLocation(line = line, position = position)
        )

    @Test
    fun `parsing a single id is accepted with E as the root non-terminal`() {
        val table  = buildTable()
        val result = LL1Parser.parse(listOf(entry("id", "x")), emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(E, root.symbol)
        assertEquals(p1, root.production) // E -> T E'
    }

    @Test
    fun `parsing id plus id times id is accepted with correct root production`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a"),
            entry("+",  "+"),
            entry("id", "b"),
            entry("*",  "*"),
            entry("id", "c")
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        // Root: E -> T E' (p1)
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(p1, root.production)
        // Right child of root is E' expanded to + T E' (p2)
        val eprime = root.children[1] as ParseTree.InternalNode
        assertEquals(p2, eprime.production)
    }

    @Test
    fun `parsing id plus id is accepted and produces correct tree structure`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a"),
            entry("+", "+"),
            entry("id", "b")
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(E, root.symbol)
        assertEquals(p1, root.production)
    }

    @Test
    fun `parsing id plus plus id recovers from the bad second plus and ends Accepted with one error`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a", line = 1, position = 1),
            entry("+",  "+", line = 1, position = 3),
            entry("+",  "+", line = 1, position = 5),
            entry("id", "b", line = 1, position = 7)
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted with errors, got $result")
        assertEquals(1, result.errors.size)
        val error = result.errors.first()
        assertEquals("+", error.foundToken?.lexeme)
        assertNotNull(error.location)
        assertTrue(error.message.contains("line 1"), "Got: ${error.message}")
        assertTrue(error.message.contains("unexpected '+'"), "Got: ${error.message}")
        assertTrue(error.message.contains("expected one of:"), "Got: ${error.message}")
    }

    @Test
    fun `parsing empty input fails recovery and returns Rejected`() {
        val table  = buildTable()
        val result = LL1Parser.parse(emptyList(), emptySet(), table)

        assertTrue(result is ParseResult.Rejected, "Expected Rejected, got $result")
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `accepted parse trace contains at least one step`() {
        val table  = buildTable()
        val result = LL1Parser.parse(listOf(entry("id", "x")), emptySet(), table) as ParseResult.Accepted
        assertTrue(result.trace.isNotEmpty())
    }

    @Test
    fun `accepted parse tree root is InternalNode with start symbol E`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a"),
            entry("+", "+"),
            entry("id", "b")
        )
        val result = LL1Parser.parse(entries, emptySet(), table) as ParseResult.Accepted
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(E, root.symbol)
    }

    // Exercises F -> ( E ) and the real recursion of the expression grammar.
    @Test
    fun `parsing parenthesized expression is accepted and uses F -- ( E )`() {
        val table = buildTable()
        val entries = listOf(
            entry("(",  "("),
            entry("id", "a"),
            entry("+",  "+"),
            entry("id", "b"),
            entry(")",  ")"),
            entry("*",  "*"),
            entry("id", "c")
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted, "Expected Accepted, got $result")
        // Root: E -> T E' (p1); the leftmost F under T must expand via p7 (F -> ( E )).
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(p1, root.production)
        val tNode = root.children[0] as ParseTree.InternalNode    // T
        val fNode = tNode.children[0] as ParseTree.InternalNode   // F
        assertEquals(p7, fNode.production)
    }

    // Recovery should report MULTIPLE errors and still terminate. The exact
    // number is whatever panic-mode produces for this input; we assert "at
    // least two" so the test surfaces regressions without being brittle.
    @Test
    fun `parsing input with multiple consecutive bad plus operators reports several errors`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a", line = 1, position = 1),
            entry("+",  "+", line = 1, position = 3),
            entry("+",  "+", line = 1, position = 5),
            entry("+",  "+", line = 1, position = 7),
            entry("id", "b", line = 1, position = 9)
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        // Parser must report more than one error and not loop forever; either
        // Accepted-with-errors or Rejected-with-errors is acceptable.
        val errors = when (result) {
            is ParseResult.Accepted -> result.errors
            is ParseResult.Rejected -> result.errors
        }
        assertTrue(errors.size >= 2, "Expected at least 2 errors, got ${errors.size}: $errors")
    }

    // Exercises the branch where the stack is exhausted (top == EndMarker) but
    // input still has tokens. After matching "id", T' and E' epsilon-out under
    // a ')' lookahead, leaving only EndMarker on the stack with ')' still in input.
    @Test
    fun `parsing id followed by unmatched close paren is rejected with an error`() {
        val table = buildTable()
        val entries = listOf(
            entry("id", "a"),
            entry(")",  ")")
        )
        val result = LL1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Rejected, "Expected Rejected, got $result")
        assertEquals(1, result.errors.size)
        assertEquals(")", result.errors.first().foundToken?.category)
    }
}
