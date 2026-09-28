package org.compiler

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Query
import org.compiler.models.LexemeLocation
import org.compiler.runtime.DbmsPipeline
import org.compiler.runtime.models.CompilationResult
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // Con un script VALIDO: con uno que no parsea, el pipeline cortaria en la
    // etapa A y el test no probaria las etapas de despues.
    @Test
    fun `correr no escribe nada en el directorio`(@TempDir temp: File) {
        DbmsPipeline.run("SELECT * FROM users;", DataDirectory(temp))

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

    // ── Etapas A y B conectadas (ticket 3.6) ───────────────────────────────

    @Test
    fun `un script valido devuelve el AST y el arbol sin errores`(@TempDir temp: File) {
        val result = DbmsPipeline.run(
            "CREATE TABLE users (id INT PRIMARY KEY);\nSELECT * FROM users;",
            DataDirectory(temp)
        )

        assertTrue(result.errors.isEmpty(), "errores: ${result.errors}")
        assertNotNull(result.parseTreeView)

        val ast = assertNotNull(result.ast)
        assertIs<CreateTable>(ast.statements[0])
        assertIs<Query>(ast.statements[1])
    }

    // La etapa A es la UNICA que corta: sin arbol no hay AST, pero los errores
    // si llegan a la GUI, y nada lanza excepcion.
    @Test
    fun `un error de sintaxis devuelve errores y ningun arbol`(@TempDir temp: File) {
        val result = DbmsPipeline.run("SELECT *\nFORM users;", DataDirectory(temp))

        assertTrue(result.hasErrors)
        assertEquals(1, result.syntaxErrors.size)
        assertNull(result.ast)
        assertNull(result.parseTreeView)
    }

    // La etapa B puede reportar sin cortar: DATE '2026-02-30' parsea, el builder
    // descubre que no es fecha, y el AST llega completo junto con el error.
    @Test
    fun `un literal imposible reporta pero conserva el AST`(@TempDir temp: File) {
        val result = DbmsPipeline.run("SELECT * FROM t WHERE d = DATE '2026-02-30';", DataDirectory(temp))

        assertEquals(1, result.semanticErrors.size)
        assertNotNull(result.ast)
    }
}
