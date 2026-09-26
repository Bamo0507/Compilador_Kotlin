package org.compiler

import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El directorio ES el catalogo: si existe users.csv, existe la tabla users.
 *
 * Cada test recibe su propio @TempDir, que es la razon de que DataDirectory sea
 * una clase con la raiz inyectada y no un `object` con la ruta escrita adentro.
 * Ninguna prueba toca DataDirectory.DEFAULT.
 */
class DataDirectoryTest {

    @Test
    fun `un directorio vacio no tiene tablas`(@TempDir temp: File) {
        assertEquals(emptyList(), DataDirectory(temp).tables())
    }

    @Test
    fun `si el directorio no existe se crea al pedirlo`(@TempDir temp: File) {
        val nested = File(temp, "sin/crear")
        assertTrue(!nested.exists())

        val tables = DataDirectory(nested).tables()

        assertTrue(nested.isDirectory)
        assertEquals(emptyList(), tables)
    }

    // La existencia la da el CSV. Un .json huerfano NO declara una tabla: seria
    // una segunda fuente de verdad, que es justo lo que la decision 2 evita.
    @Test
    fun `solo los csv cuentan como tabla`(@TempDir temp: File) {
        File(temp, "users.csv").writeText("id,name\n")
        File(temp, "users.json").writeText("{}")
        File(temp, "huerfano.json").writeText("{}")
        File(temp, "notas.txt").writeText("nada")

        assertEquals(listOf("users"), DataDirectory(temp).tables())
    }

    @Test
    fun `las tablas salen ordenadas`(@TempDir temp: File) {
        listOf("posts", "users", "comments").forEach {
            File(temp, "$it.csv").writeText("")
        }

        assertEquals(listOf("comments", "posts", "users"), DataDirectory(temp).tables())
    }

    @Test
    fun `csv y json apuntan al par de la misma tabla`(@TempDir temp: File) {
        val data = DataDirectory(temp)

        assertEquals(File(temp, "users.csv"), data.csv("users"))
        assertEquals(File(temp, "users.json"), data.json("users"))
    }
}
