package org.compiler

import org.compiler.frontend.lexicalAnalyzer.lexer.Lexer
import org.compiler.frontend.lexicalAnalyzer.lexer.models.SymbolTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {

    @Test
    fun `tokenize reproduces tokens txt format`() {
        val yalexContent = File("src/main/resources/java_lang.yal").readText()
        val sourceContent = File("src/main/resources/input.java").readText()

        SymbolTable.clear()
        val result = Lexer.tokenize(yalexContent, sourceContent)

        val rebuilt = result.tokens.joinToString("\n") { token ->
            val printedValue = if (token.category == "KEYWORD") {
                token.lexeme
            } else {
                SymbolTable.addOrGet(token.lexeme).toString()
            }
            "<${token.category}, $printedValue>"
        }

        val expected = File("src/main/resources/output/tokens.txt").readText().trimEnd()

        assertEquals(expected, rebuilt)
        assertTrue(result.automata.isNotEmpty(), "automata map should be populated")
    }
}
