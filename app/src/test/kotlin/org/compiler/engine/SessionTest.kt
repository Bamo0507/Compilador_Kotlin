package org.compiler.engine

import org.compiler.catalog.CatalogLoader
import org.compiler.diagnostics.Diagnostics
import org.compiler.storage.DataDirectory
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * El estado de una corrida: lo que ya se leyo y lo que todavia no llego al disco.
 *
 * Es el mismo objeto para las dos cosas a proposito, y eso es justo lo que hay que
 * probar: un INSERT y un SELECT en el mismo script tienen que verse.
 */
class SessionTest {

    private fun session(root: File, diagnostics: Diagnostics = Diagnostics()): Session {
        val directory = DataDirectory(root)
        return Session(directory, CatalogLoader(directory, diagnostics).load(), diagnostics)
    }

    // ── Lectura y cache ────────────────────────────────────────────────────

    @Test
    fun `lee las filas de una tabla decodificadas`(@TempDir root: File) {
        database(root)
        val rows = session(root).rowsOf("users")

        assertEquals(4, rows.size)
        assertEquals(IntValue(1), rows.first().values[0])
        assertEquals(StringValue("Ana"), rows.first().values[1])
    }

    // Un campo vacio del CSV es un NULL, no una cadena vacia.
    @Test
    fun `un campo vacio se decodifica como nulo`(@TempDir root: File) {
        database(root)
        val rows = session(root).rowsOf("users")

        assertEquals(NullValue, rows[2].values[3])
        assertEquals(NullValue, rows[3].values[2])
    }

    @Test
    fun `la misma tabla leida dos veces abre el archivo una sola vez`(@TempDir root: File) {
        database(root)
        val session = session(root)

        session.rowsOf("users")
        session.rowsOf("users")

        assertEquals(1, session.reads)
    }

    @Test
    fun `devuelve siempre la misma lista viva`(@TempDir root: File) {
        database(root)
        val session = session(root)

        assertSame(session.rowsOf("users"), session.rowsOf("users"))
    }

    @Test
    fun `una tabla que no existe da una lista vacia sin reventar`(@TempDir root: File) {
        database(root)

        assertTrue(session(root).rowsOf("fantasma").isEmpty())
    }

    // ── El caso que justifica todo ─────────────────────────────────────────

    /**
     * Si el cache de lectura y el almacen de escritura fueran dos objetos, el
     * SELECT leeria de uno y la fila agregada estaria en el otro.
     */
    @Test
    fun `una fila agregada en memoria la ve el SELECT de la linea siguiente`(@TempDir root: File) {
        database(root)
        val session = session(root)

        session.rowsOf("users").add(Row(listOf(IntValue(9), StringValue("Nueva"), NullValue, NullValue)))
        session.markModified("users")

        val visible = Operators.scan(session, "users").rows
        assertEquals(5, visible.size)
        assertEquals(StringValue("Nueva"), visible.last().values[1])
    }

    // ── Aislamiento ────────────────────────────────────────────────────────

    // Si fuera un singleton, dos corridas seguidas compartirian filas y habria que
    // acordarse de limpiarlo entre una y otra.
    @Test
    fun `dos sesiones sobre el mismo directorio no comparten nada`(@TempDir root: File) {
        database(root)
        val first = session(root)
        val second = session(root)

        first.rowsOf("users").add(Row(listOf(IntValue(9), StringValue("Nueva"), NullValue, NullValue)))

        assertEquals(4, second.rowsOf("users").size)
        assertNotSame(first.rowsOf("users"), second.rowsOf("users"))
    }

    // ── Lo que queda por escribir ──────────────────────────────────────────

    @Test
    fun `leer una tabla no la marca como modificada`(@TempDir root: File) {
        database(root)
        val session = session(root)
        session.rowsOf("users")

        assertTrue(session.changes().rows.isEmpty())
    }

    @Test
    fun `los cambios listan exactamente las tablas tocadas`(@TempDir root: File) {
        database(root)
        val session = session(root)
        session.rowsOf("users")
        session.rowsOf("posts")
        session.markModified("posts")

        val changes = session.changes()
        assertEquals(setOf("posts"), changes.rows.keys)
        assertEquals(4, changes.rows.getValue("posts").size)
    }

    @Test
    fun `una tabla nueva nace vacia y marcada`(@TempDir root: File) {
        database(root)
        val session = session(root)
        session.createTable(org.compiler.catalog.Table("nueva", usersTable.columns))

        assertTrue(session.rowsOf("nueva").isEmpty())
        assertEquals(setOf("nueva"), session.changes().rows.keys)
    }

    // ── Datos corruptos ────────────────────────────────────────────────────

    // El archivo pudo editarse a mano. El motor reporta y sigue con un nulo, en vez
    // de reventar en la mitad de una corrida.
    @Test
    fun `un valor que no cuadra con su tipo reporta y queda en nulo`(@TempDir root: File) {
        database(root)
        File(root, "users.csv").writeText("id,name,city,age\r\n1,Ana,GT,treinta\r\n")

        val diagnostics = Diagnostics()
        val rows = session(root, diagnostics).rowsOf("users")

        assertEquals(NullValue, rows.single().values[3])
        assertEquals(1, diagnostics.execution().size)
        assertTrue(diagnostics.all().single().message.contains("no es un valor de INT"))
    }

    @Test
    fun `el catalogo arranca igual al del directorio`(@TempDir root: File) {
        database(root)
        val session = session(root)

        assertEquals(setOf("posts", "users"), session.catalog.tables.keys)
        assertFalse(session.diagnostics.hasErrors)
    }
}
