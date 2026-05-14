package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YalpReaderTest {

    @Test
    fun `parse produces correct terminals and ignored tokens`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        val terminalNames = grammar.terminals.map { it.name }.toSet()
        assertTrue("KEYWORD" in terminalNames)
        assertTrue("ID" in terminalNames)
        assertTrue("INT" in terminalNames)
        assertTrue("FLOAT" in terminalNames)
        assertTrue("OPERATOR" in terminalNames)
        assertTrue("PUNCTUATION" in terminalNames)
        assertTrue("WHITESPACE" in terminalNames)
        assertTrue("COMMENT" in terminalNames)

        val ignoredNames = grammar.ignoredTokens.map { it.name }.toSet()
        assertEquals(setOf("WHITESPACE", "COMMENT"), ignoredNames)
    }

    @Test
    fun `parse produces correct start symbol`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        assertEquals("program", grammar.startSymbol.name)
    }

    @Test
    fun `parse produces non-empty productions with correct ids`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        assertTrue(grammar.productions.isNotEmpty())
        grammar.productions.forEachIndexed { index, production ->
            assertEquals(index + 1, production.id)
        }
    }

    @Test
    fun `parse classifies heads as NonTerminal`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        grammar.productions.forEach { production ->
            assertNotNull(production.head)
        }
    }

    @Test
    fun `productionsByHead groups correctly`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        val byHead = grammar.productionsByHead
        assertTrue(byHead.containsKey(Symbol.NonTerminal("program")))
        assertTrue(byHead.containsKey(Symbol.NonTerminal("expr")))

        for ((head, prods) in byHead) {
            assertTrue(prods.isNotEmpty())
            for (prod in prods) assertEquals(head, prod.head)
        }
    }
}
