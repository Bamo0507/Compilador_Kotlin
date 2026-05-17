package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Associativity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YalpReaderTest {

    @Test
    fun `parse produces correct terminals and ignored tokens`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        val terminalNames = grammar.terminals.map { it.name }.toSet()
        assertTrue("KW_CLASS" in terminalNames)
        assertTrue("KW_FUNCTION" in terminalNames)
        assertTrue("KW_LET" in terminalNames)
        assertTrue("ID" in terminalNames)
        assertTrue("INT" in terminalNames)
        assertTrue("FLOAT" in terminalNames)
        assertTrue("OP_PLUS" in terminalNames)
        assertTrue("OP_ASSIGN" in terminalNames)
        assertTrue("LPAREN" in terminalNames)
        assertTrue("SEMI" in terminalNames)
        assertTrue("WHITESPACE" in terminalNames)
        assertTrue("COMMENT" in terminalNames)

        val ignoredNames = grammar.ignoredTokens.map { it.name }.toSet()
        assertEquals(setOf("WHITESPACE", "COMMENT"), ignoredNames)
    }

    @Test
    fun `parser yalp declares 8 precedence levels in the expected order`() {
        val content = File("src/main/resources/parser.yalp").readText()
        val grammar = YalpReader.parse(content)

        assertEquals(8, grammar.precedenceTable.size)
        // First level (lowest precedence) is OP_OR; last level (highest) is OP_ASSIGN.
        assertEquals(setOf(org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol.Terminal("OP_OR")),
            grammar.precedenceTable[0].operators)
        assertEquals(setOf(org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol.Terminal("OP_ASSIGN")),
            grammar.precedenceTable[7].operators)
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

    @Test
    fun `no precedence declarations produces empty precedence table`() {
        val content = """
            %token ID
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        assertTrue(grammar.precedenceTable.isEmpty())
    }

    @Test
    fun `left declaration creates a LEFT precedence level`() {
        val content = """
            %token ID OP_PLUS
            %left OP_PLUS
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        assertEquals(1, grammar.precedenceTable.size)
        val level = grammar.precedenceTable[0]
        assertEquals(0, level.level)
        assertEquals(Associativity.LEFT, level.associativity)
        assertEquals(setOf(Symbol.Terminal("OP_PLUS")), level.operators)
    }

    @Test
    fun `right declaration creates a RIGHT precedence level`() {
        val content = """
            %token ID OP_NOT
            %right OP_NOT
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        assertEquals(1, grammar.precedenceTable.size)
        assertEquals(Associativity.RIGHT, grammar.precedenceTable[0].associativity)
        assertEquals(setOf(Symbol.Terminal("OP_NOT")), grammar.precedenceTable[0].operators)
    }

    @Test
    fun `multiple operators in one declaration share the same level`() {
        val content = """
            %token ID OP_PLUS OP_MINUS
            %left OP_PLUS OP_MINUS
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        assertEquals(1, grammar.precedenceTable.size)
        assertEquals(
            setOf(Symbol.Terminal("OP_PLUS"), Symbol.Terminal("OP_MINUS")),
            grammar.precedenceTable[0].operators
        )
    }

    @Test
    fun `multiple precedence declarations produce levels in declaration order`() {
        val content = """
            %token ID OP_OR OP_AND OP_TIMES OP_NOT
            %left  OP_OR
            %left  OP_AND
            %left  OP_TIMES
            %right OP_NOT
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        assertEquals(4, grammar.precedenceTable.size)
        assertEquals(0, grammar.precedenceTable[0].level)
        assertEquals(setOf(Symbol.Terminal("OP_OR")), grammar.precedenceTable[0].operators)
        assertEquals(Associativity.LEFT, grammar.precedenceTable[0].associativity)
        assertEquals(1, grammar.precedenceTable[1].level)
        assertEquals(setOf(Symbol.Terminal("OP_AND")), grammar.precedenceTable[1].operators)
        assertEquals(2, grammar.precedenceTable[2].level)
        assertEquals(setOf(Symbol.Terminal("OP_TIMES")), grammar.precedenceTable[2].operators)
        assertEquals(3, grammar.precedenceTable[3].level)
        assertEquals(setOf(Symbol.Terminal("OP_NOT")), grammar.precedenceTable[3].operators)
        assertEquals(Associativity.RIGHT, grammar.precedenceTable[3].associativity)
    }

    @Test
    fun `precedence declaration with no operators throws`() {
        val content = """
            %token ID
            %left
            %%
            program : ID ;
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { YalpReader.parse(content) }
    }
}
