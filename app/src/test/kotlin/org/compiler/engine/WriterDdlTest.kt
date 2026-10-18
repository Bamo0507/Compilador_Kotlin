package org.compiler.engine

import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CREATE, ALTER y DROP.
 *
 * Cada una toca el esquema y las filas en memoria. Que un ALTER seguido de un
 * INSERT funcione es lo que demuestra que ninguna parte del motor se quedo con el
 * catalogo viejo.
 */
class WriterDdlTest {

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    fun `una tabla creada queda en el catalogo de la sesion`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "CREATE TABLE tags (id INT PRIMARY KEY, label VARCHAR(10));")

        val table = assertNotNull(outcome.session.catalog.table("tags"))
        assertEquals(listOf("id", "label"), table.columns.map { it.name })
    }

    @Test
    fun `una tabla creada nace vacia`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "CREATE TABLE tags (id INT);")

        assertTrue(outcome.rowsOf("tags").isEmpty())
    }

    @Test
    fun `crear y luego insertar en el mismo script funciona`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            CREATE TABLE tags (id INT PRIMARY KEY, label VARCHAR(10));
            INSERT INTO tags VALUES (1, 'viral');
            SELECT label FROM tags;
            """.trimIndent()
        )

        assertEquals(listOf(listOf("viral")), outcome.grid)
    }

    // ── ALTER ADD ──────────────────────────────────────────────────────────

    @Test
    fun `agregar una columna la pone al final de cada fila`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "ALTER TABLE users ADD score INT;")

        assertEquals(5, outcome.rowsOf("users").first().values.size)
        assertEquals(listOf("id", "name", "city", "age", "score"),
            assertNotNull(outcome.session.catalog.table("users")).columns.map { it.name })
    }

    @Test
    fun `agregar sin valor por omision deja nulos`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "ALTER TABLE users ADD score INT;")

        assertTrue(outcome.rowsOf("users").all { it.values[4] == NullValue })
    }

    // Es lo que permite que la fase 5 acepte agregar una columna NOT NULL.
    @Test
    fun `agregar con valor por omision rellena las filas que ya estaban`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "ALTER TABLE users ADD score INT NOT NULL DEFAULT 0;")

        assertTrue(outcome.rowsOf("users").all { it.values[4] == IntValue(0) })
    }

    // Es donde se nota si alguna parte del motor se quedo con el catalogo viejo.
    @Test
    fun `agregar una columna y usarla en el mismo script funciona`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            ALTER TABLE users ADD score INT DEFAULT 0;
            UPDATE users SET score = 5 WHERE id = 1;
            SELECT score FROM users WHERE id = 1;
            """.trimIndent()
        )

        assertEquals(listOf(listOf("5")), outcome.grid)
    }

    // ── ALTER DROP ─────────────────────────────────────────────────────────

    @Test
    fun `quitar una columna la saca de todas las filas`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "ALTER TABLE users DROP COLUMN city;")

        assertEquals(3, outcome.rowsOf("users").first().values.size)
        assertEquals(listOf("id", "name", "age"),
            assertNotNull(outcome.session.catalog.table("users")).columns.map { it.name })
    }

    @Test
    fun `lo que queda despues de quitar sigue alineado`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "ALTER TABLE users DROP COLUMN city; SELECT name, age FROM users WHERE id = 1;")

        assertEquals(listOf(listOf("Ana", "30")), outcome.grid)
    }

    // ── DROP TABLE ─────────────────────────────────────────────────────────

    @Test
    fun `borrar una tabla la saca del catalogo`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "DROP TABLE posts;")

        assertNull(outcome.session.catalog.table("posts"))
        assertEquals(listOf("posts"), outcome.session.changes().dropped)
    }

    @Test
    fun `borrar y volver a crear deja la tabla nueva`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            DROP TABLE posts;
            CREATE TABLE posts (id INT PRIMARY KEY);
            INSERT INTO posts VALUES (1);
            SELECT * FROM posts;
            """.trimIndent()
        )

        assertEquals(listOf("id"), outcome.names)
        assertEquals(listOf(listOf("1")), outcome.grid)
        assertTrue(outcome.session.changes().dropped.isEmpty(), "volvio a existir, asi que no esta borrada")
    }

    // ── Nada llega al disco ────────────────────────────────────────────────

    @Test
    fun `el DDL tampoco toca el disco hasta el volcado`(@TempDir root: File) {
        database(root)
        val before = File(root, "users.json").readText()

        runScript(root, "ALTER TABLE users ADD score INT;")

        assertEquals(before, File(root, "users.json").readText())
        assertEquals(StringValue("Ana"), Row(listOf(StringValue("Ana"))).values.single())
    }
}
