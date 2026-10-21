// Ticket 9.5: el panel de cambios, que es donde la decision 5 se vuelve visible.
package org.compiler.gui

import org.compiler.engine.database
import org.compiler.engine.pipeline
import org.compiler.gui.components.changeSummaryOf
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeSummaryTest {

    private fun summaryOf(root: File, sql: String) =
        assertNotNull(changeSummaryOf(pipeline(root, sql)))

    @Test
    fun `sin haber corrido no hay panel`() {
        assertNull(changeSummaryOf(null))
    }

    // ── Cuando sale bien ───────────────────────────────────────────────────

    @Test
    fun `un script bueno lista los archivos escritos`(@TempDir root: File) {
        database(root)
        val summary = summaryOf(root, "INSERT INTO users VALUES (9, 'Nuevo', 'GT', 20);")

        assertTrue(summary.applied)
        assertEquals("Se escribieron 2 archivos", summary.headline)
        assertEquals(listOf("users.csv", "pk_users.idx"), summary.lines)
    }

    @Test
    fun `una tabla nueva escribe su esquema y su csv`(@TempDir root: File) {
        database(root)
        val summary = summaryOf(root, "CREATE TABLE eventos (id INT PRIMARY KEY);")

        assertTrue(summary.applied)
        assertTrue(summary.lines.containsAll(listOf("eventos.json", "eventos.csv")), summary.lines.toString())
    }

    // Decirlo es mejor que no decir nada: confirma que el script corrio.
    @Test
    fun `un script de puras consultas lo dice`(@TempDir root: File) {
        database(root)
        val summary = summaryOf(root, "SELECT id FROM users;")

        assertTrue(summary.applied)
        assertEquals("No habia nada que escribir", summary.headline)
        assertEquals(listOf("el script solo consulta"), summary.lines)
    }

    // ── Cuando falla ───────────────────────────────────────────────────────

    /**
     * El mensaje que importa.
     *
     * Sin el, el usuario ve un error y no sabe si las sentencias anteriores se
     * aplicaron, que es exactamente la duda que la decision 5 resuelve.
     */
    @Test
    fun `un script que falla a la mitad dice que no se escribio nada`(@TempDir root: File) {
        database(root)
        val summary = summaryOf(
            root,
            "INSERT INTO users VALUES (9, 'Uno', 'GT', 20);\n" +
                "INSERT INTO users VALUES (8, 'Dos', 'GT', 21);\n" +
                "INSERT INTO users VALUES (1, 'Repetido', 'GT', 22);"
        )

        assertFalse(summary.applied)
        assertEquals("No se escribio nada", summary.headline)
        assertEquals(3, summary.lines.size)
        assertTrue(summary.lines[0].startsWith("la sentencia de la linea 3 fallo:"), summary.lines[0])
        assertEquals("las 2 sentencias anteriores se descartaron", summary.lines[1])
        assertEquals("el directorio de datos quedo sin cambios", summary.lines[2])
    }

    // Y el disco de verdad queda igual: no solo lo dice.
    @Test
    fun `lo que el panel dice es lo que paso en el disco`(@TempDir root: File) {
        database(root)
        val antes = File(root, "users.csv").readText()

        summaryOf(root, "INSERT INTO users VALUES (9, 'Uno', 'GT', 20);\nINSERT INTO users VALUES (1, 'Mal', 'GT', 1);")

        assertEquals(antes, File(root, "users.csv").readText())
    }

    @Test
    fun `si falla la primera sentencia no se habla de anteriores`(@TempDir root: File) {
        database(root)
        val summary = summaryOf(root, "INSERT INTO users VALUES (1, 'Repetido', 'GT', 20);")

        assertFalse(summary.applied)
        assertEquals(2, summary.lines.size)
        assertTrue(summary.lines.none { it.contains("anteriores") })
    }

    // ── Las advertencias no son errores ────────────────────────────────────

    /**
     * Un script que solo avisa se ejecuta y se escribe.
     *
     * Si el panel tratara una advertencia como un error, la distincion entre las
     * dos severidades no serviria de nada.
     */
    @Test
    fun `una advertencia no impide escribir`(@TempDir root: File) {
        database(root)
        // posts y no users: borrar un usuario al que posts apunta es un error de
        // clave foranea, y eso taparia lo que este test quiere ver.
        val result = pipeline(root, "DELETE FROM posts;")
        val summary = assertNotNull(changeSummaryOf(result))

        assertTrue(result.warnings.isNotEmpty(), "un DELETE sin WHERE avisa")
        assertTrue(summary.applied)
        assertTrue(summary.lines.contains("posts.csv"), summary.lines.toString())
    }
}
