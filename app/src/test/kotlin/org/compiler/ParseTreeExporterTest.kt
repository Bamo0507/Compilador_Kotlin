package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.models.TokenEntry
import org.compiler.models.LexemeLocation
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.visualization.ParseTreeExporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseTreeExporterTest {

    // Dragon Book LL(1) expression grammar symbols
    private val E      = Symbol.NonTerminal("E")
    private val Eprime = Symbol.NonTerminal("E'")
    private val T      = Symbol.NonTerminal("T")
    private val Tprime = Symbol.NonTerminal("T'")
    private val F      = Symbol.NonTerminal("F")
    private val id     = Symbol.Terminal("id")

    private val p1 = Production(1, E,      listOf(T, Eprime))          // E  -> T E'
    private val p3 = Production(3, Eprime, listOf(Symbol.Epsilon))     // E' -> ε
    private val p4 = Production(4, T,      listOf(F, Tprime))          // T  -> F T'
    private val p6 = Production(6, Tprime, listOf(Symbol.Epsilon))     // T' -> ε
    private val p8 = Production(8, F,      listOf(id))                 // F  -> id

    private fun entry(category: String, lexeme: String) = TokenEntry(
        token    = Token(category = category, lexeme = lexeme, symbolIndex = null),
        location = LexemeLocation(line = 1, position = 1)
    )

    // Tree for a single id "x":
    //   E -> T E'
    //     T -> F T'
    //       F -> id
    //         id "x"
    //       T' -> ε
    //         ε
    //     E' -> ε
    //       ε
    private val singleIdTree: ParseTree = ParseTree.InternalNode(E, p1, listOf(
        ParseTree.InternalNode(T, p4, listOf(
            ParseTree.InternalNode(F, p8, listOf(
                ParseTree.LeafNode(id, entry("id", "x"))
            )),
            ParseTree.InternalNode(Tprime, p6, listOf(ParseTree.EpsilonNode))
        )),
        ParseTree.InternalNode(Eprime, p3, listOf(ParseTree.EpsilonNode))
    ))

    @Test
    fun `toIndentedText for single id produces expected string`() {
        val expected = """
            E [E -> T E']
            +-- T [T -> F T']
            |   +-- F [F -> id]
            |   |   +-- id "x"
            |   +-- T' [T' -> ε]
            |       +-- ε
            +-- E' [E' -> ε]
                +-- ε
        """.trimIndent()
        assertEquals(expected, ParseTreeExporter.toIndentedText(singleIdTree))
    }

    @Test
    fun `toDot for single id has N nodes and N-1 edges`() {
        val dot = ParseTreeExporter.toDot(singleIdTree)
        // 8 nodes: E, T, F, LeafNode(id "x"), T', ε, E', ε
        val nodeCount = Regex("""^\s+n\d+ \[label=""", RegexOption.MULTILINE).findAll(dot).count()
        val edgeCount = Regex("""^\s+n\d+ -> n\d+;""", RegexOption.MULTILINE).findAll(dot).count()
        assertEquals(8, nodeCount, "Expected 8 nodes, got $nodeCount in:\n$dot")
        assertEquals(7, edgeCount, "Expected 7 edges, got $edgeCount in:\n$dot")
    }

    @Test
    fun `epsilon nodes render as epsilon in both formats`() {
        val dot  = ParseTreeExporter.toDot(singleIdTree)
        val text = ParseTreeExporter.toIndentedText(singleIdTree)
        assertTrue(dot.contains("label=\"ε\""),  "DOT should contain epsilon label")
        assertTrue(dot.contains("shape=plaintext"), "DOT epsilon node should use shape=plaintext")
        assertTrue(text.contains("+-- ε"), "Text should render epsilon as ε")
    }

    @Test
    fun `children are listed in declaration order`() {
        val text  = ParseTreeExporter.toIndentedText(singleIdTree)
        val lines = text.lines()
        val tIdx      = lines.indexOfFirst { it.contains("T [T ->") }
        val eprimeIdx = lines.indexOfFirst { it.contains("E' [E' ->") }
        val fIdx      = lines.indexOfFirst { it.contains("F [F ->") }
        val tprimeIdx = lines.indexOfFirst { it.contains("T' [T' ->") }
        assertTrue(tIdx < eprimeIdx, "T must appear before E'")
        assertTrue(fIdx < tprimeIdx, "F must appear before T'")
    }
}