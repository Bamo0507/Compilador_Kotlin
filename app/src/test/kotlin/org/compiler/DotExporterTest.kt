package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Item
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1State
import org.compiler.frontend.syntaxAnalyzer.visualization.DotExporter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DotExporterTest {

    // Minimal grammar: S' -> S, S -> a  (3 states)
    private val sPrime = Symbol.NonTerminal("S'")
    private val S      = Symbol.NonTerminal("S")
    private val a      = Symbol.Terminal("a")

    private val pAug = Production(0, sPrime, listOf(S))   // S' -> S
    private val p1   = Production(1, S,      listOf(a))   // S -> a

    private val grammar = Grammar(
        terminals     = setOf(a),
        nonTerminals  = setOf(sPrime, S),
        productions   = listOf(pAug, p1),
        startSymbol   = sPrime,
        ignoredTokens = emptySet()
    )

    // State 0: S' -> . S, S -> . a
    private val state0 = SLR1State(0, setOf(
        SLR1Item(pAug, 0, emptySet()),
        SLR1Item(p1,   0, emptySet())
    ))
    // State 1: S -> a .
    private val state1 = SLR1State(1, setOf(
        SLR1Item(p1, 1, emptySet())
    ))
    // State 2: S' -> S .  (accepting)
    private val state2 = SLR1State(2, setOf(
        SLR1Item(pAug, 1, emptySet())
    ))

    private val automaton = SLR1Automata(
        states           = listOf(state0, state1, state2),
        transitions      = mapOf(
            (0 to a) to 1,
            (0 to S) to 2
        ),
        initialState     = state0,
        augmentedGrammar = grammar
    )

    @Test
    fun `slr1ToDot includes one node per state and one edge per transition`() {
        val dot = DotExporter.slr1ToDot(automaton)
        val nodeCount = Regex("""^\s+S\d+ \[label=""", RegexOption.MULTILINE).findAll(dot).count()
        val edgeCount = Regex("""^\s+S\d+ -> S\d+ \[label=""", RegexOption.MULTILINE).findAll(dot).count()
        assertTrue(nodeCount == 3, "Expected 3 nodes, got $nodeCount")
        assertTrue(edgeCount == 2, "Expected 2 edges, got $edgeCount")
    }

    @Test
    fun `slr1ToDot marks the accepting state with peripheries=2`() {
        val dot = DotExporter.slr1ToDot(automaton)
        val lines = dot.lines()
        val s2Line = lines.first { it.trimStart().startsWith("S2 [label=") }
        assertTrue(s2Line.contains("peripheries=2"), "Accepting state must have peripheries=2, got: $s2Line")

        val s0Line = lines.first { it.trimStart().startsWith("S0 [label=") }
        val s1Line = lines.first { it.trimStart().startsWith("S1 [label=") }
        assertFalse(s0Line.contains("peripheries=2"), "Non-accepting state S0 must not have peripheries=2")
        assertFalse(s1Line.contains("peripheries=2"), "Non-accepting state S1 must not have peripheries=2")
    }

    @Test
    fun `slr1ToDot labels transitions with the symbol name`() {
        val dot = DotExporter.slr1ToDot(automaton)
        assertTrue(dot.contains("[label=\"a\"]"), "Expected edge labelled 'a' in:\n$dot")
        assertTrue(dot.contains("[label=\"S\"]"), "Expected edge labelled 'S' in:\n$dot")
    }

    @Test
    fun `lalr1ToDot uses LALR1 as the graph title`() {
        val dot = DotExporter.lalr1ToDot(automaton)
        assertTrue(dot.trimStart().startsWith("digraph LALR1 {"),
            "Expected 'digraph LALR1 {' but got: ${dot.take(40)}")
    }

    @Test
    fun `renderToImage does not throw when dot binary is not available`() {
        // Uses valid DOT so that when dot is installed it produces an actual PNG file.
        // When dot is not installed, IOException is caught and false is returned without throwing.
        val outputPath = "/Users/brandon/Documents/UVG/Github/Compilador_Kotlin/extras/dot_exporter_test.png"
        val threwException = try {
            DotExporter.renderToImage(DotExporter.slr1ToDot(automaton), outputPath)
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threwException, "renderToImage must never throw an exception")
    }
}