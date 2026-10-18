package org.compiler.engine

import org.compiler.catalog.Catalog
import org.compiler.storage.DataDirectory
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El volcado, que es lo unico que toca el disco, y la vuelta atras.
 *
 * Si una sentencia falla, las anteriores quedaron aplicadas en memoria, pero como
 * nada se escribio, el directorio queda identico. Es una vuelta atras sin haber
 * escrito una sola linea de logica de transacciones.
 */
class FlushTest {

    // Una foto del directorio, byte por byte.
    private fun snapshot(root: File): Map<String, String> =
        root.listFiles().orEmpty().associate { it.name to it.readText() }

    private fun stamp(root: File, millis: Long) =
        root.listFiles().orEmpty().forEach { it.setLastModified(millis) }

    // ── El volcado solo ────────────────────────────────────────────────────

    // No conoce la sesion, solo su reporte, asi que se prueba sin montar una
    // corrida entera.
    @Test
    fun `escribe las filas que le pasan`(@TempDir root: File) {
        database(root)
        val catalog = Catalog(mapOf("users" to usersTable))
        val rows = listOf(Row(listOf(IntValue(7), StringValue("Sola"), NullValue, IntValue(1))))

        Flush.flush(Changes(mapOf("users" to rows), catalog, emptyList(), emptyList()), DataDirectory(root))

        assertEquals(listOf("id,name,city,age", "7,Sola,,1"), lines(root, "users"))
    }

    @Test
    fun `borra los archivos de una tabla eliminada`(@TempDir root: File) {
        database(root)

        Flush.flush(
            Changes(emptyMap(), Catalog(mapOf("users" to usersTable)), emptyList(), listOf("posts")),
            DataDirectory(root)
        )

        assertFalse(File(root, "posts.csv").exists())
        assertFalse(File(root, "posts.json").exists())
    }

    // ── A traves del pipeline ──────────────────────────────────────────────

    @Test
    fun `un script que solo consulta no escribe nada`(@TempDir root: File) {
        database(root)
        stamp(root, 1_000_000_000L)

        pipeline(root, "SELECT * FROM users;")

        assertTrue(root.listFiles().orEmpty().all { it.lastModified() == 1_000_000_000L })
    }

    @Test
    fun `un INSERT llega al archivo`(@TempDir root: File) {
        database(root)
        pipeline(root, "INSERT INTO users (id, name) VALUES (9, 'Nueva');")

        assertTrue(lines(root, "users").last().startsWith("9,Nueva"))
    }

    @Test
    fun `con write en falso el directorio no cambia`(@TempDir root: File) {
        database(root)
        val before = snapshot(root)

        pipeline(root, "INSERT INTO users (id, name) VALUES (9, 'Nueva');", write = false)

        assertEquals(before, snapshot(root))
    }

    // ── La vuelta atras ────────────────────────────────────────────────────

    /**
     * Las tres primeras sentencias se aplicaron en memoria y la cuarta reviento.
     * Como el volcado es uno solo y va al final, el directorio ni se entero.
     */
    @Test
    fun `un script cuya cuarta sentencia falla deja el directorio identico`(@TempDir root: File) {
        database(root)
        val before = snapshot(root)

        val result = pipeline(
            root,
            """
            INSERT INTO users (id, name) VALUES (8, 'Una');
            INSERT INTO users (id, name) VALUES (9, 'Otra');
            UPDATE users SET city = 'CR' WHERE id = 1;
            INSERT INTO users (id, name) VALUES (8, 'Repetida');
            """.trimIndent()
        )

        assertTrue(result.hasErrors)
        assertEquals(before, snapshot(root))
    }

    @Test
    fun `un CREATE seguido de un error no deja la tabla nueva`(@TempDir root: File) {
        database(root)

        pipeline(
            root,
            """
            CREATE TABLE tags (id INT PRIMARY KEY);
            INSERT INTO tags VALUES (1);
            INSERT INTO tags VALUES (1);
            """.trimIndent()
        )

        assertFalse(File(root, "tags.csv").exists())
        assertFalse(File(root, "tags.json").exists())
    }

    @Test
    fun `un error de analisis tampoco escribe`(@TempDir root: File) {
        database(root)
        val before = snapshot(root)

        pipeline(root, "INSERT INTO users (id, name) VALUES (9, 'Nueva'); SELECT * FROM fantasma;")

        assertEquals(before, snapshot(root))
    }

    // ── Solo lo que cambio ─────────────────────────────────────────────────

    @Test
    fun `tocar una tabla de dos no reescribe la otra`(@TempDir root: File) {
        database(root)
        stamp(root, 1_000_000_000L)

        pipeline(root, "INSERT INTO users (id, name) VALUES (9, 'Nueva');")

        assertEquals(1_000_000_000L, File(root, "posts.csv").lastModified())
        assertEquals(1_000_000_000L, File(root, "users.json").lastModified(), "el esquema no cambio")
        assertTrue(File(root, "users.csv").lastModified() > 1_000_000_000L)
    }

    @Test
    fun `un ALTER reescribe el esquema y las filas`(@TempDir root: File) {
        database(root)
        pipeline(root, "ALTER TABLE users ADD score INT DEFAULT 0;")

        assertEquals("id,name,city,age,score", lines(root, "users").first())
        assertTrue(File(root, "users.json").readText().contains("score"))
    }

    @Test
    fun `un DROP borra los dos archivos`(@TempDir root: File) {
        database(root)
        pipeline(root, "DROP TABLE posts;")

        assertFalse(File(root, "posts.csv").exists())
        assertFalse(File(root, "posts.json").exists())
        assertTrue(File(root, "users.csv").exists())
    }

    // ── La vuelta completa ─────────────────────────────────────────────────

    // Lo que se escribio se vuelve a leer igual: es la prueba de que el formato
    // cierra consigo mismo.
    @Test
    fun `lo escrito se vuelve a leer igual`(@TempDir root: File) {
        write(root, samplesTable, listOf(
            listOf("7", "2.5", "12.34", "ab", "hola", "un texto", "2026-03-01", "10:15:00", "true")
        ))

        pipeline(root, "UPDATE samples SET whole = 8;")
        val outcome = query(root, "SELECT * FROM samples;")

        assertEquals(
            listOf("8", "2.5", "12.34", "ab ", "hola", "un texto", "2026-03-01", "10:15", "true"),
            outcome.grid.single()
        )
    }
}
