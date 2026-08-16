package org.compiler

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsTest {

    private fun lexico(line: Int, position: Int) =
        CompilerError.LexerError(LexemeLocation(line, position), "caracter no reconocido")

    private fun sintactico(line: Int, position: Int) =
        CompilerError.ParserError(LexemeLocation(line, position), "se esperaba ';'")

    private fun semantico(line: Int, position: Int) =
        CompilerError.SemanticError(LexemeLocation(line, position), "tipo incompatible")

    @Test
    fun `arranca vacio`() {
        val diagnostics = Diagnostics()

        assertFalse(diagnostics.hasErrors)
        assertEquals(0, diagnostics.count)
        assertTrue(diagnostics.all().isEmpty())
    }

    // Esta es la razon de ser del ticket: con un `object` habia que acordarse de
    // limpiar el estado global antes de cada corrida.
    @Test
    fun `dos instancias no comparten errores`() {
        val primera = Diagnostics()
        val segunda = Diagnostics()

        primera.report(lexico(1, 1))

        assertEquals(1, primera.count)
        assertEquals(0, segunda.count)
        assertFalse(segunda.hasErrors)
    }

    @Test
    fun `all ordena por linea y luego por columna`() {
        val diagnostics = Diagnostics()
        diagnostics.report(semantico(9, 5))
        diagnostics.report(lexico(3, 12))
        diagnostics.report(sintactico(3, 4))

        val ubicaciones = diagnostics.all().map { it.location.line to it.location.position }

        assertEquals(listOf(3 to 4, 3 to 12, 9 to 5), ubicaciones)
    }

    @Test
    fun `cada nivel se filtra por separado`() {
        val diagnostics = Diagnostics()
        diagnostics.report(lexico(1, 1))
        diagnostics.report(sintactico(2, 1))
        diagnostics.report(sintactico(3, 1))
        diagnostics.report(semantico(4, 1))

        assertEquals(1, diagnostics.lexical().size)
        assertEquals(2, diagnostics.syntactic().size)
        assertEquals(1, diagnostics.semantic().size)
        assertEquals(4, diagnostics.count)
    }
}
