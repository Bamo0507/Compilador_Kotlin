package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Conflict
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Table
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.slr1.models.ConflictType
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Conflict
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table
import org.compiler.frontend.syntaxAnalyzer.visualization.TableFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableFormatterTest {

    // ── Dragon Book LL(1) grammar ──────────────────────────────────────────────
    //   E  -> T E'      (p1)
    //   E' -> + T E'    (p2)
    //   E' -> ε         (p3)
    //   T  -> F T'      (p4)
    //   T' -> * F T'    (p5)
    //   T' -> ε         (p6)
    //   F  -> ( E )     (p7)
    //   F  -> id        (p8)

    private val E      = Symbol.NonTerminal("E")
    private val Eprime = Symbol.NonTerminal("E'")
    private val T      = Symbol.NonTerminal("T")
    private val Tprime = Symbol.NonTerminal("T'")
    private val F      = Symbol.NonTerminal("F")

    private val id     = Symbol.Terminal("id")
    private val plus   = Symbol.Terminal("+")
    private val times  = Symbol.Terminal("*")
    private val lparen = Symbol.Terminal("(")
    private val rparen = Symbol.Terminal(")")
    private val dollar = Symbol.EndMarker

    private val p1 = Production(1, E,      listOf(T, Eprime))
    private val p2 = Production(2, Eprime, listOf(plus, T, Eprime))
    private val p3 = Production(3, Eprime, listOf(Symbol.Epsilon))
    private val p4 = Production(4, T,      listOf(F, Tprime))
    private val p5 = Production(5, Tprime, listOf(times, F, Tprime))
    private val p6 = Production(6, Tprime, listOf(Symbol.Epsilon))
    private val p7 = Production(7, F,      listOf(lparen, E, rparen))
    private val p8 = Production(8, F,      listOf(id))

    // Insert cells in an order that yields NTs: E, E', T, T', F
    // and terminals: id, +, *, (, ), $ — matching the spec example.
    private val ll1Table = LL1Table(
        startSymbol = E,
        cells = linkedMapOf(
            (E      to id)     to p1,
            (Eprime to plus)   to p2,
            (T      to id)     to p4,
            (Tprime to times)  to p5,
            (F      to id)     to p8,
            (E      to lparen) to p1,
            (T      to lparen) to p4,
            (Eprime to rparen) to p3,
            (Eprime to dollar) to p3,
            (Tprime to plus)   to p6,
            (Tprime to rparen) to p6,
            (Tprime to dollar) to p6,
            (F      to lparen) to p7
        )
    )

    // ── SLR(1) / LALR(1) minimal table ─────────────────────────────────────────
    //   3 states, grammar: S -> a
    //   Action: (0,a)→Shift(1), (1,$)→Reduce(p1), (2,$)→Accept
    //   Goto:   (0,S)→2
    //   Conflict at (0,a): [Shift(1), Reduce(p1)]

    private val termA = Symbol.Terminal("a")
    private val ntS   = Symbol.NonTerminal("S")
    private val pSlr  = Production(1, ntS, listOf(termA))

    private val slrTable = SLR1Table(
        action    = linkedMapOf(
            (0 to termA)  to Action.Shift(1),
            (1 to dollar) to Action.Reduce(pSlr),
            (2 to dollar) to Action.Accept
        ),
        goto      = linkedMapOf(
            (0 to ntS) to 2
        ),
        numStates = 3,
        conflicts = listOf(
            SLR1Conflict(
                state    = 0,
                terminal = termA,
                type     = ConflictType.SHIFT_REDUCE,
                actions  = listOf(Action.Shift(1), Action.Reduce(pSlr))
            )
        )
    )

    private val lalrTable = LALR1Table(
        action    = linkedMapOf(
            (0 to termA)  to Action.Shift(1),
            (1 to dollar) to Action.Reduce(pSlr),
            (2 to dollar) to Action.Accept
        ),
        goto      = linkedMapOf(
            (0 to ntS) to 2
        ),
        numStates = 3,
        conflicts = listOf(
            LALR1Conflict(
                state    = 0,
                terminal = termA,
                type     = ConflictType.SHIFT_REDUCE,
                actions  = listOf(Action.Shift(1), Action.Reduce(pSlr))
            )
        )
    )

    // ── LL(1) tests ────────────────────────────────────────────────────────────

    @Test
    fun `formatLL1Table for Dragon Book grammar matches expected snapshot`() {
        // Each line is exactly 60 chars: 7 (left) + 5×9 (non-last cols) + 8 (last col).
        // Last column cell = "| " + content.padEnd(6)  — no trailing space after the cell.
        val expected =
            "       | id     | +      | *      | (      | )      | \$     \n" +
            "-------+--------+--------+--------+--------+--------+-------\n" +
            "E      | E->TE' |        |        | E->TE' |        |       \n" +
            "E'     |        | +TE'   |        |        | ε      | ε     \n" +
            "T      | T->FT' |        |        | T->FT' |        |       \n" +
            "T'     |        | ε      | *FT'   |        | ε      | ε     \n" +
            "F      | F->id  |        |        | F->(E) |        |       "
        assertEquals(expected, TableFormatter.formatLL1Table(ll1Table))
    }

    @Test
    fun `formatLL1Table all lines have equal length`() {
        val result = TableFormatter.formatLL1Table(ll1Table)
        val lengths = result.lines().map { it.length }.toSet()
        assertEquals(1, lengths.size, "Expected all lines same length, got lengths: $lengths")
    }

    // ── SLR(1) Action tests ────────────────────────────────────────────────────
    // Layout: stateWidth=5, col "a" w=5 (conflict "s1/r1"), col "$" w=4 ("acc").
    // Each line = 5+1 + (5+3) + (4+2) = 20 chars.

    @Test
    fun `formatSLR1Action matches expected snapshot`() {
        val expected =
            "State | a     | \$   \n" +
            "------+-------+-----\n" +
            "    0 | s1/r1 |     \n" +
            "    1 |       | r1  \n" +
            "    2 |       | acc "
        assertEquals(expected, TableFormatter.formatSLR1Action(slrTable))
    }

    @Test
    fun `formatSLR1Action renders shift reduce and accept correctly`() {
        val result = TableFormatter.formatSLR1Action(slrTable)
        assertTrue(result.contains("r1"),  "Expected reduce action 'r1' in:\n$result")
        assertTrue(result.contains("acc"), "Expected accept action 'acc' in:\n$result")
        assertTrue(result.contains("s1"),  "Expected shift action 's1' in:\n$result")
    }

    @Test
    fun `formatSLR1Action renders conflict cells with slash separator`() {
        val result = TableFormatter.formatSLR1Action(slrTable)
        assertTrue(result.contains("s1/r1"),
            "Expected conflict cell 's1/r1' in action table:\n$result")
    }

    @Test
    fun `formatSLR1Action all lines have equal length`() {
        val result = TableFormatter.formatSLR1Action(slrTable)
        val lengths = result.lines().map { it.length }.toSet()
        assertEquals(1, lengths.size, "Expected all lines same length, got: $lengths\n$result")
    }

    // ── SLR(1) Goto tests ──────────────────────────────────────────────────────
    // Layout: stateWidth=5, col "S" w=4 (min), goto values right-justified.
    // Each line = 5+1 + (4+2) = 12 chars.

    @Test
    fun `formatSLR1Goto matches expected snapshot`() {
        val expected =
            "State | S   \n" +
            "------+-----\n" +
            "    0 |    2\n" +
            "    1 |     \n" +
            "    2 |     "
        assertEquals(expected, TableFormatter.formatSLR1Goto(slrTable))
    }

    @Test
    fun `formatSLR1Goto all lines have equal length`() {
        val result = TableFormatter.formatSLR1Goto(slrTable)
        val lengths = result.lines().map { it.length }.toSet()
        assertEquals(1, lengths.size, "Expected all lines same length, got: $lengths\n$result")
    }

    @Test
    fun `formatSLR1Goto goto values are right-justified`() {
        val result = TableFormatter.formatSLR1Goto(slrTable)
        assertTrue(result.contains("|    2"),
            "Expected right-justified goto value '|    2' in:\n$result")
    }

    // ── LALR(1) Action tests ───────────────────────────────────────────────────

    @Test
    fun `formatLALR1Action matches same snapshot as SLR1 for identical data`() {
        val expected =
            "State | a     | \$   \n" +
            "------+-------+-----\n" +
            "    0 | s1/r1 |     \n" +
            "    1 |       | r1  \n" +
            "    2 |       | acc "
        assertEquals(expected, TableFormatter.formatLALR1Action(lalrTable))
    }

    @Test
    fun `formatLALR1Action all lines have equal length`() {
        val result = TableFormatter.formatLALR1Action(lalrTable)
        val lengths = result.lines().map { it.length }.toSet()
        assertEquals(1, lengths.size, "Expected all lines same length, got: $lengths\n$result")
    }

    // ── LALR(1) Goto tests ─────────────────────────────────────────────────────

    @Test
    fun `formatLALR1Goto matches same snapshot as SLR1 for identical data`() {
        val expected =
            "State | S   \n" +
            "------+-----\n" +
            "    0 |    2\n" +
            "    1 |     \n" +
            "    2 |     "
        assertEquals(expected, TableFormatter.formatLALR1Goto(lalrTable))
    }

    @Test
    fun `formatLALR1Goto all lines have equal length`() {
        val result = TableFormatter.formatLALR1Goto(lalrTable)
        val lengths = result.lines().map { it.length }.toSet()
        assertEquals(1, lengths.size, "Expected all lines same length, got: $lengths\n$result")
    }

    // ── FIRST sets test ────────────────────────────────────────────────────────

    @Test
    fun `formatFirstSets is deterministic across calls`() {
        val firstSets = FirstSets(
            mapOf(
                E      to setOf(lparen, id),
                Eprime to setOf(plus, Symbol.Epsilon),
                T      to setOf(lparen, id),
                Tprime to setOf(times, Symbol.Epsilon),
                F      to setOf(lparen, id)
            )
        )
        val result1 = TableFormatter.formatFirstSets(firstSets)
        val result2 = TableFormatter.formatFirstSets(firstSets)
        assertEquals(result1, result2, "formatFirstSets must be deterministic")

        val lines = result1.lines()
        val ntNames = lines.map { it.substringBefore(" ").trim() }
        assertEquals(ntNames.sorted(), ntNames, "NTs must appear in alphabetical order")
    }
}