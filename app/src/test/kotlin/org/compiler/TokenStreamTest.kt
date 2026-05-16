package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.models.TokenEntry
import org.compiler.frontend.syntaxAnalyzer.runtime.TokenStream
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStreamTest {

    private fun entry(category: String, lexeme: String, line: Int = 1, position: Int = 1): TokenEntry =
        TokenEntry(
            token = Token(category = category, lexeme = lexeme, symbolIndex = null),
            location = LexemeLocation(line = line, position = position)
        )

    @Test
    fun `peek and consume skip ignored categories transparently`() {
        val entries = listOf(
            entry("KEYWORD", "if"),
            entry("WS", " "),
            entry("ID", "x"),
            entry("WS", " "),
            entry("OPERATOR", "+")
        )
        val stream = TokenStream(entries, ignored = setOf("WS"))

        assertEquals("if", stream.peek()?.token?.lexeme)
        assertEquals("if", stream.consume()?.token?.lexeme)
        assertEquals("x", stream.peek()?.token?.lexeme)
        assertEquals("x", stream.consume()?.token?.lexeme)
        assertEquals("+", stream.consume()?.token?.lexeme)
        assertNull(stream.peek())
        assertFalse(stream.hasNext())
    }

    @Test
    fun `peek does not advance the cursor but consume does`() {
        val entries = listOf(
            entry("ID", "a"),
            entry("ID", "b")
        )
        val stream = TokenStream(entries, ignored = emptySet())

        assertEquals(0, stream.position())
        stream.peek()
        stream.peek()
        assertEquals(0, stream.position())
        stream.consume()
        assertEquals(1, stream.position())
    }

    @Test
    fun `empty token list produces empty stream`() {
        val stream = TokenStream(emptyList(), ignored = setOf("WS"))
        assertFalse(stream.hasNext())
        assertNull(stream.peek())
        assertNull(stream.consume())
    }

    @Test
    fun `stream where all entries are ignored becomes empty`() {
        val entries = listOf(
            entry("WS", " "),
            entry("WS", "\t"),
            entry("WS", "\n")
        )
        val stream = TokenStream(entries, ignored = setOf("WS"))
        assertFalse(stream.hasNext())
        assertNull(stream.peek())
    }

    @Test
    fun `hasNext is true while entries remain and false at end`() {
        val entries = listOf(entry("ID", "x"))
        val stream = TokenStream(entries, ignored = emptySet())
        assertTrue(stream.hasNext())
        stream.consume()
        assertFalse(stream.hasNext())
    }
}
