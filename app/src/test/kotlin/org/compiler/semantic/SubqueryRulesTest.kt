package org.compiler.semantic

import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.semantic.findColumn
import org.compiler.types.IntType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Lo que se le puede pedir a una subconsulta segun donde este.
 *
 * La FORMA se valida aqui, sin datos. La CANTIDAD de filas que devuelve una
 * escalar depende de lo que haya guardado, asi que eso lo verifica el motor.
 */
class SubqueryRulesTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).errors
    }

    // ── La escalar ─────────────────────────────────────────────────────────

    @Test
    fun `una escalar de varias columnas reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la subconsulta debe devolver una sola columna, devuelve 3"),
            errorsOf(root, "SELECT id FROM users WHERE id = (SELECT * FROM posts);")
        )
    }

    @Test
    fun `una escalar de una sola columna pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id FROM users WHERE id = (SELECT uid FROM posts);"))
    }

    @Test
    fun `una escalar toma el tipo de su columna`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar = entre INT y VARCHAR(120)"),
            errorsOf(root, "SELECT id FROM users WHERE id = (SELECT title FROM posts);")
        )
    }

    // ── IN ─────────────────────────────────────────────────────────────────

    @Test
    fun `un IN con una subconsulta de varias columnas reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la subconsulta debe devolver una sola columna, devuelve 3"),
            errorsOf(root, "SELECT id FROM users WHERE id IN (SELECT * FROM posts);")
        )
    }

    @Test
    fun `un IN con tipos que no se comparan reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede comparar INT con VARCHAR(120)"),
            errorsOf(root, "SELECT id FROM users WHERE id IN (SELECT title FROM posts);")
        )
    }

    @Test
    fun `un IN con tipos compatibles pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id FROM users WHERE id IN (SELECT uid FROM posts);"))
    }

    // ── EXISTS ─────────────────────────────────────────────────────────────

    // A EXISTS no le importa la forma: solo si hay o no filas.
    @Test
    fun `un EXISTS acepta cualquier cantidad de columnas`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id FROM users WHERE EXISTS (SELECT * FROM posts);"))
    }

    // ── La tabla derivada ──────────────────────────────────────────────────

    @Test
    fun `una columna calculada sin AS en una tabla derivada reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 2 de la subconsulta no tiene nombre, use AS"),
            errorsOf(root, "SELECT 1 FROM (SELECT id, id + 1 FROM users) x;")
        )
    }

    // Una referencia simple hereda su nombre, asi que no necesita AS.
    @Test
    fun `una referencia simple en una tabla derivada no necesita AS`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT 1 FROM (SELECT id, name FROM users) x;"))
    }

    /**
     * La fase 4 declara las columnas de una tabla derivada antes de que exista el
     * tipado, asi que una calculada queda en ErrorType. Al terminar de tipar la
     * subconsulta se le vuelve a pedir el esquema y se reemplaza.
     */
    @Test
    fun `una columna calculada de una tabla derivada queda con tipo`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "SELECT 1 FROM (SELECT id + 1 AS doble FROM users) x;")

        val scope = assertNotNull((resolved.script.statements.single() as Query).scope)
        val column = assertNotNull(scope.findColumn("x", "doble").singleOrNull())
        assertEquals(IntType, column.column.type)
    }

    @Test
    fun `una referencia a una columna calculada de una tabla derivada se puede usar`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(root, "SELECT x.doble FROM (SELECT id + 1 AS doble FROM users) x WHERE x.doble > 2;")
        )
    }

    @Test
    fun `una tabla derivada mal tipada reporta una sola vez`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar + entre VARCHAR(80) y INT"),
            errorsOf(root, "SELECT 1 FROM (SELECT name + 1 AS raro FROM users) x;")
        )
    }

    // ── Anidamiento ────────────────────────────────────────────────────────

    @Test
    fun `tres niveles validos pasan sin errores`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(
                root,
                """
                SELECT u.name FROM users u
                 WHERE EXISTS (SELECT 1 FROM posts p
                                WHERE p.uid = u.id
                                  AND EXISTS (SELECT 1 FROM posts q WHERE q.uid = p.uid));
                """.trimIndent()
            )
        )
    }
}
