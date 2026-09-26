package org.compiler

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity
import org.compiler.models.LexemeLocation
import org.compiler.runtime.DbmsPipeline
import org.compiler.runtime.models.CompilationResult
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El pipeline no tiene logica propia: lo que se prueba es la forma del resultado
 * que la GUI espera, y que ninguna entrada lo haga lanzar.
 *
 * Las etapas se van conectando despues, y cada una trae sus propios tests.
 */
class DbmsPipelineTest {

    private fun error(line: Int, message: String) =
        CompilerError.SemanticError(LexemeLocation(line, 1), message)

    private fun warning(line: Int, message: String) =
        CompilerError.SemanticError(LexemeLocation(line, 1), message, Severity.WARNING)

    @Test
    fun `el resultado conserva el fuente que se le paso`(@TempDir temp: File) {
        val source = "SELECT * FROM users;"

        assertEquals(source, DbmsPipeline.run(source, DataDirectory(temp)).source)
    }

    @Test
    fun `un script vacio no reporta errores`(@TempDir temp: File) {
        val result = DbmsPipeline.run("", DataDirectory(temp))

        assertTrue(result.errors.isEmpty())
        assertFalse(result.hasErrors)
    }

    @Test
    fun `correr no escribe nada en el directorio`(@TempDir temp: File) {
        DbmsPipeline.run("SELECT 1;", DataDirectory(temp))

        assertEquals(emptyList(), temp.listFiles().orEmpty().map { it.name })
    }

    // La decision 11: un script que solo tiene avisos SI se ejecuta, asi que
    // hasErrors no puede contarlos.
    @Test
    fun `una advertencia no cuenta como error`() {
        val result = CompilationResult(
            source = "DELETE FROM users;",
            errors = listOf(warning(1, "esto afecta todas las filas de 'users'"))
        )

        assertFalse(result.hasErrors)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `un error si cuenta aunque venga acompaniado de avisos`() {
        val result = CompilationResult(
            source = "x",
            errors = listOf(warning(1, "aviso"), error(2, "la tabla 'x' no existe"))
        )

        assertTrue(result.hasErrors)
        assertEquals(1, result.warnings.size)
    }

    // Cuando la sintaxis falla no hay arbol ni catalogo, pero si hay que mostrar
    // los errores que se alcanzaron a juntar.
    @Test
    fun `failed conserva el fuente y los errores`() {
        val diagnostics = Diagnostics()
        diagnostics.report(error(1, "se esperaba FROM"))

        val result = CompilationResult.failed(diagnostics, "SELECT *;")

        assertEquals("SELECT *;", result.source)
        assertEquals(1, result.errors.size)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `los errores se reparten por categoria`() {
        val location = LexemeLocation(1, 1)
        val result = CompilationResult(
            source = "x",
            errors = listOf(
                CompilerError.LexerError(location, "lexico"),
                CompilerError.ParserError(location, "sintactico"),
                CompilerError.SemanticError(location, "semantico"),
                CompilerError.ExecutionError(location, "ejecucion")
            )
        )

        assertEquals(1, result.lexicalErrors.size)
        assertEquals(1, result.syntaxErrors.size)
        assertEquals(1, result.semanticErrors.size)
        assertEquals(1, result.executionErrors.size)
    }
}
