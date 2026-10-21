// Ticket 9.2: el catalogo como arbol de dos niveles.
package org.compiler.gui

import org.compiler.catalog.CatalogLoader
import org.compiler.diagnostics.Diagnostics
import org.compiler.engine.database
import org.compiler.engine.pipeline
import org.compiler.engine.usersTable
import org.compiler.engine.write
import org.compiler.gui.components.catalogEntriesOf
import org.compiler.gui.components.rowCountOf
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTreeTest {

    private fun entriesOf(root: File) = with(Diagnostics()) {
        val loader = CatalogLoader(DataDirectory(root), this)
        catalogEntriesOf(loader.load(), DataDirectory(root), loader.problems)
    }

    // ── El arbol sano ──────────────────────────────────────────────────────

    @Test
    fun `un directorio con dos tablas se ve completo`(@TempDir root: File) {
        database(root)
        val entries = entriesOf(root)

        assertEquals(listOf("posts", "users"), entries.map { it.table })
        assertEquals(
            listOf("id", "name", "city", "age"),
            assertNotNull(entries.firstOrNull { it.table == "users" }).columns.map { it.name }
        )
    }

    // El conteo se lee contando lineas, sin decodificar: esta vista se refresca en
    // cada corrida y decodificar seria cargar la tabla solo para poner un numero.
    @Test
    fun `cada tabla dice cuantas columnas y cuantas filas tiene`(@TempDir root: File) {
        database(root)
        val users = assertNotNull(entriesOf(root).firstOrNull { it.table == "users" })

        assertEquals("4 columns · 4 rows", users.summary)
    }

    @Test
    fun `una tabla vacia dice cero filas`(@TempDir root: File) {
        write(root, usersTable, emptyList())
        assertEquals("4 columns · 0 rows", entriesOf(root).single().summary)
    }

    @Test
    fun `el conteo no decodifica nada`(@TempDir root: File) {
        database(root)
        assertEquals(4, rowCountOf(File(root, "users.csv")))
        assertEquals(0, rowCountOf(File(root, "no_existe.csv")))
    }

    // ── Las restricciones ──────────────────────────────────────────────────

    /**
     * La pantalla demuestra que las restricciones se guardan DE VERDAD: salen del
     * .json, no de volver a leer el CREATE TABLE.
     */
    @Test
    fun `las restricciones de cada columna se ven`(@TempDir root: File) {
        database(root)
        val users = assertNotNull(entriesOf(root).firstOrNull { it.table == "users" })

        assertEquals("PRIMARY KEY", users.columns.first().constraints)
        assertEquals("NOT NULL", users.columns[1].constraints)
        assertEquals("", users.columns[2].constraints, "una columna sin restricciones no inventa texto")
    }

    // La flecha es clicable y salta a la columna referenciada, asi que hace falta
    // saber a donde apunta y no solo como se escribe.
    @Test
    fun `una clave foranea sabe a donde apunta`(@TempDir root: File) {
        database(root)
        val posts = assertNotNull(entriesOf(root).firstOrNull { it.table == "posts" })

        assertEquals("users" to "id", posts.columns.first().referenceTo)
        assertEquals("-> users.id", posts.columns.first().constraints)
        assertNull(posts.columns[1].referenceTo)
    }

    // Lo que la fase 8 agrego al esquema tambien se ve aqui.
    @Test
    fun `una columna indexada dice que indice la cubre`(@TempDir root: File) {
        database(root)
        pipeline(root, "CREATE INDEX idx_city ON users(city);")
        val users = assertNotNull(entriesOf(root).firstOrNull { it.table == "users" })

        assertEquals("pk_users", users.columns.first().indexedBy)
        assertEquals("idx_city", users.columns[2].indexedBy)
        assertNull(users.columns[1].indexedBy)
    }

    // ── Lo que no cargo ────────────────────────────────────────────────────

    /**
     * Una tabla rota NO esta en el catalogo, asi que si la vista mostrara solo lo
     * que cargo, un directorio roto se veria igual que uno sano.
     */
    @Test
    fun `una tabla sin json sale marcada con su error`(@TempDir root: File) {
        write(root, usersTable, emptyList())
        File(root, "huerfana.csv").writeText("a,b\r\n")

        val entries = entriesOf(root)
        val rota = assertNotNull(entries.firstOrNull { it.table == "huerfana" })

        assertTrue(rota.isBroken)
        assertEquals("huerfana.csv no tiene esquema", rota.problem)
        assertEquals(emptyList(), rota.columns)
        assertFalse(assertNotNull(entries.firstOrNull { it.table == "users" }).isBroken)
    }

    @Test
    fun `un esquema con un tipo desconocido marca su tabla`(@TempDir root: File) {
        File(root, "rara.csv").writeText("a\r\n")
        File(root, "rara.json").writeText("""{"columns":{"a":{"type":"CUALQUIERA"}}}""")

        // Carga a medias: la tabla entra al catalogo sin esa columna, y aun asi va
        // marcada, porque lo que se ve no es lo que el .json dice.
        val rara = assertNotNull(entriesOf(root).firstOrNull { it.table == "rara" })
        assertTrue(rara.isBroken)
        assertTrue(assertNotNull(rara.problem).contains("no reconocido"), rara.problem)
        assertEquals(emptyList(), rara.columns)
    }

    // Un directorio vacio no es un error: no hay nada que marcar.
    @Test
    fun `un directorio vacio no produce entradas`(@TempDir root: File) {
        assertEquals(emptyList(), entriesOf(root))
    }

    // ── Se refresca ────────────────────────────────────────────────────────

    @Test
    fun `una tabla creada en el editor aparece al correr`(@TempDir root: File) {
        database(root)
        assertEquals(2, entriesOf(root).size)

        pipeline(root, "CREATE TABLE eventos (id INT PRIMARY KEY, titulo TEXT);")

        val entries = entriesOf(root)
        assertEquals(listOf("eventos", "posts", "users"), entries.map { it.table })
        assertEquals("2 columns · 0 rows", assertNotNull(entries.firstOrNull { it.table == "eventos" }).summary)
    }

    @Test
    fun `una tabla borrada desaparece`(@TempDir root: File) {
        write(root, usersTable, emptyList())
        pipeline(root, "DROP TABLE users;")

        assertEquals(emptyList(), entriesOf(root))
    }
}
