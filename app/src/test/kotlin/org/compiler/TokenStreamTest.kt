package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.syntaxAnalyzer.runtime.TokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStreamTest {

    private fun token(category: String, lexeme: String): Token =
        Token(category = category, lexeme = lexeme, symbolIndex = null)

    @Test
    fun `peek and consume skip ignored categories transparently`() {
        val tokens = listOf(
            token("KEYWORD", "if"),
            token("WS", " "),
            token("ID", "x"),
            token("WS", " "),
            token("OPERATOR", "+")
        )
        val stream = TokenStream(tokens, ignored = setOf("WS"))

        assertEquals("if", stream.peek()?.lexeme)
        assertEquals("if", stream.consume()?.lexeme)
        assertEquals("x", stream.peek()?.lexeme)
        assertEquals("x", stream.consume()?.lexeme)
        assertEquals("+", stream.consume()?.lexeme)
        assertNull(stream.peek())
        assertFalse(stream.hasNext())
    }

    @Test
    fun `peek does not advance the cursor but consume does`() {
        val tokens = listOf(
            token("ID", "a"),
            token("ID", "b")
        )
        val stream = TokenStream(tokens, ignored = emptySet())

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
    fun `stream where all tokens are ignored becomes empty`() {
        val tokens = listOf(
            token("WS", " "),
            token("WS", "\t"),
            token("WS", "\n")
        )
        val stream = TokenStream(tokens, ignored = setOf("WS"))
        assertFalse(stream.hasNext())
        assertNull(stream.peek())
    }

    @Test
    fun `hasNext is true while tokens remain and false at end`() {
        val tokens = listOf(token("ID", "x"))
        val stream = TokenStream(tokens, ignored = emptySet())
        assertTrue(stream.hasNext())
        stream.consume()
        assertFalse(stream.hasNext())
    }
}
