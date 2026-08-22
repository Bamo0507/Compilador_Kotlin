package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.syntax.SyntaxAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyntaxAnalyzerTest {

    @Test
    fun `un programa valido devuelve arbol y no reporta errores`() {
        val diagnostics = Diagnostics()

        val tree = SyntaxAnalyzer.parse("let x: integer = 1;", diagnostics)

        assertNotNull(tree)
        assertFalse(diagnostics.hasErrors)
    }

    @Test
    fun `un error sintactico devuelve null y reporta ParserError`() {
        val diagnostics = Diagnostics()

        val tree = SyntaxAnalyzer.parse("let x: integer = ;", diagnostics)

        assertNull(tree)
        assertTrue(diagnostics.lexical().isEmpty())   // los caracteres son validos

        val error = diagnostics.syntactic().single()
        assertEquals(1, error.location.line)
        assertEquals(18, error.location.position)     // el ';' es el caracter 18
    }

    @Test
    fun `un caracter fuera del lenguaje reporta LexerError`() {
        val diagnostics = Diagnostics()

        val tree = SyntaxAnalyzer.parse("let x = @@@;", diagnostics)

        assertNull(tree)

        // Un LexerError por cada '@': el lexer no agrupa caracteres desconocidos.
        val lexicos = diagnostics.lexical()
        assertEquals(3, lexicos.size)
        assertEquals(listOf(9, 10, 11), lexicos.map { it.location.position })

        // Y ademas falla el parser, porque el stream le llega con hoyos.
        assertTrue(diagnostics.syntactic().isNotEmpty())
    }

    // El unico calculo del ticket: ANTLR cuenta columnas desde 0 y LexemeLocation
    // desde 1. Sin este test, un `+1` faltante o de mas pasa desapercibido.
    @Test
    fun `las columnas se reportan 1-based`() {
        val diagnostics = Diagnostics()

        SyntaxAnalyzer.parse("@\nlet x: integer = 1;", diagnostics)

        val error = diagnostics.lexical().single()
        assertEquals(1, error.location.line)
        assertEquals(1, error.location.position)
    }
}