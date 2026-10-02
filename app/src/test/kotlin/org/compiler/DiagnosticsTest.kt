package org.compiler

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsTest {

    private fun lexical(line: Int, position: Int) =
        CompilerError.LexerError(LexemeLocation(line, position), "caracter no reconocido")

    private fun syntactic(line: Int, position: Int) =
        CompilerError.ParserError(LexemeLocation(line, position), "se esperaba ';'")

    private fun semantic(line: Int, position: Int) =
        CompilerError.SemanticError(LexemeLocation(line, position), "tipo incompatible")

    @Test
    fun `arranca vacio`() {
        val diagnostics = Diagnostics()

        assertFalse(diagnostics.hasErrors)
        assertEquals(0, diagnostics.count)
        assertTrue(diagnostics.all().isEmpty())
    }

    // Esta es la razon de que Diagnostics sea una clase: con un `object` habia que acordarse de
    // limpiar el estado global antes de cada corrida.
    @Test
    fun `dos instancias no comparten errores`() {
        val first = Diagnostics()
        val second = Diagnostics()

        first.report(lexical(1, 1))

        assertEquals(1, first.count)
        assertEquals(0, second.count)
        assertFalse(second.hasErrors)
    }

    @Test
    fun `all ordena por linea y luego por columna`() {
        val diagnostics = Diagnostics()
        diagnostics.report(semantic(9, 5))
        diagnostics.report(lexical(3, 12))
        diagnostics.report(syntactic(3, 4))

        val locations = diagnostics.all().map { it.location.line to it.location.position }

        assertEquals(listOf(3 to 4, 3 to 12, 9 to 5), locations)
    }

    @Test
    fun `cada nivel se filtra por separado`() {
        val diagnostics = Diagnostics()
        diagnostics.report(lexical(1, 1))
        diagnostics.report(syntactic(2, 1))
        diagnostics.report(syntactic(3, 1))
        diagnostics.report(semantic(4, 1))

        assertEquals(1, diagnostics.lexical().size)
        assertEquals(2, diagnostics.syntactic().size)
        assertEquals(1, diagnostics.semantic().size)
        assertEquals(4, diagnostics.count)
    }
}
