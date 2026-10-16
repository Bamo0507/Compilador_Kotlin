package org.compiler.semantic

import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cada columna que se nombra contra la tabla de la que viene, dejando anotados el
 * nivel de anidamiento y la posicion dentro de la fila.
 *
 * Los dos numeros hacen falta juntos: en `p.uid = u.id` el motor esta parado en
 * una fila de posts, pero `u.id` es de la fila de users del nivel de arriba, y un
 * indice solo no distingue las dos.
 */
class ColumnResolutionTest {

    // ── Lo que queda pegado ────────────────────────────────────────────────

    @Test
    fun `una referencia calificada se resuelve a su columna`(@TempDir root: File) {
        healthy(root, users)
        val reference = clean(root, "SELECT u.name FROM users u;").query.reference("name")

        val symbol = assertNotNull(reference.symbol)
        assertEquals(DeclarationKind.COLUMN, symbol.kind)
        assertEquals("users", symbol.sourceTable)
        assertEquals(VarcharType(80), symbol.type)
        assertEquals(0, reference.level)
        assertEquals(1, reference.index)
    }

    @Test
    fun `una referencia sin calificar se resuelve igual`(@TempDir root: File) {
        healthy(root, users)
        val reference = clean(root, "SELECT 1 FROM users WHERE id = 3;").query.reference("id")

        assertEquals(IntType, assertNotNull(reference.symbol).type)
        assertEquals(0, reference.index)
    }

    // El indice es absoluto sobre la fila concatenada, no relativo a su tabla.
    @Test
    fun `una columna del segundo origen de un JOIN lleva el desplazamiento`(@TempDir root: File) {
        healthy(root, users, posts)
        val query = clean(root, "SELECT 1 FROM users u JOIN posts p ON u.id = p.uid;").query

        assertEquals(0, query.reference("id").index)
        assertEquals(users.columns.size, query.reference("uid").index)
    }

    @Test
    fun `un nombre que no se resuelve queda sin simbolo`(@TempDir root: File) {
        healthy(root, users)
        val resolved = resolve(root, "SELECT 1 FROM users WHERE fantasma = 1;")

        assertNull(resolved.query.reference("fantasma").symbol)
    }

    // ── El nivel ───────────────────────────────────────────────────────────

    @Test
    fun `un EXISTS correlacionado deja la de adentro en cero y la de afuera en uno`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(
            root,
            "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id);"
        )

        val inner = resolved.query.subqueries().single()
        assertEquals(0, inner.reference("uid").level)
        assertEquals(1, inner.reference("id").level)
    }

    @Test
    fun `tres niveles de anidamiento dejan la mas externa en dos`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(
            root,
            """
            SELECT 1 FROM users u
             WHERE EXISTS (SELECT 1 FROM posts p
                            WHERE EXISTS (SELECT 1 FROM posts q WHERE q.uid = u.id));
            """.trimIndent()
        )

        val deepest = resolved.query.subqueries().single().subqueries().single()
        assertEquals(0, deepest.reference("uid").level)
        assertEquals(2, deepest.reference("id").level)
    }

    // Si la subconsulta redeclara el alias, el calificador es el suyo y la
    // busqueda NO se va a buscar el de afuera.
    @Test
    fun `un calificador redeclarado adentro no se escapa al de afuera`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = resolve(
            root,
            "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts u WHERE u.id = 1);"
        )

        assertEquals(listOf("'posts' no tiene columna 'id'"), resolved.errors)
    }

    // ── Los cuatro errores ─────────────────────────────────────────────────

    @Test
    fun `una columna que la tabla no tiene reporta con el nombre real de la tabla`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(
            listOf("'users' no tiene columna 'email'"),
            resolve(root, "SELECT u.email FROM users u;").errors
        )
    }

    @Test
    fun `un calificador que nadie declaro reporta`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(
            listOf("no hay ninguna tabla llamada 'x'"),
            resolve(root, "SELECT x.name FROM users u;").errors
        )
    }

    @Test
    fun `un nombre sin calificar que esta en dos tablas reporta ambiguedad`(@TempDir root: File) {
        healthy(root, users, posts)

        assertEquals(
            listOf("'city' existe en 'users' y en 'posts'"),
            resolve(root, "SELECT city FROM users u JOIN posts p ON 1 = 1;").errors
        )
    }

    @Test
    fun `un nombre sin calificar que no esta en ninguna reporta`(@TempDir root: File) {
        healthy(root, posts)

        assertEquals(
            listOf("no existe la columna 'name'"),
            resolve(root, "SELECT 1 FROM posts WHERE name = 'x';").errors
        )
    }

    // ── Los alias del SELECT ───────────────────────────────────────────────

    /**
     * El ORDER BY corre despues del SELECT, asi que puede nombrar sus alias. El
     * WHERE corre antes, y por eso el mismo nombre ahi no existe.
     */
    @Test
    fun `un alias del SELECT se puede usar en el ORDER BY`(@TempDir root: File) {
        healthy(root, users)
        val reference = clean(root, "SELECT id + 1 AS doble FROM users ORDER BY doble;")
            .query.reference("doble")

        val symbol = assertNotNull(reference.symbol)
        assertEquals(DeclarationKind.ALIAS, symbol.kind)
        assertEquals(0, reference.index)
        assertEquals(0, reference.level)
    }

    @Test
    fun `el mismo alias en el WHERE reporta`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(
            listOf("no existe la columna 'doble'"),
            resolve(root, "SELECT id + 1 AS doble FROM users WHERE doble > 40;").errors
        )
    }

    @Test
    fun `una columna real en el ORDER BY sigue siendo una columna`(@TempDir root: File) {
        healthy(root, users)
        val reference = clean(root, "SELECT u.id FROM users u ORDER BY u.city;").query.reference("city")

        assertEquals(DeclarationKind.COLUMN, assertNotNull(reference.symbol).kind)
        assertEquals(2, reference.index)
    }

    // El alias apunta a la fila YA proyectada, que es otra numeracion.
    @Test
    fun `el indice de un alias es su lugar en el esquema de salida`(@TempDir root: File) {
        healthy(root, users)
        val reference = clean(root, "SELECT u.name, u.id + 1 AS doble FROM users u ORDER BY doble;")
            .query.reference("doble")

        assertEquals(1, reference.index)
    }
}
