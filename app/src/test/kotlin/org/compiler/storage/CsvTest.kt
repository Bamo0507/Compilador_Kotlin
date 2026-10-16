package org.compiler.storage

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvTest {
    @Test
    fun `comas comillas y saltos de linea sobreviven ida y vuelta`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val header = listOf("id", "name", "notes")
        val rows = listOf(
            listOf("1", "Perez, Luis", "dijo \"hola\""),
            listOf("2", "Ana", "primera\nsegunda"),
            listOf("3", "Zoé", "primera\r\nsegunda"),
            listOf("4", "", null)
        )

        CsvWriter.write(file, header, rows)

        assertEquals(CsvReader.CsvContent(header, rows), CsvReader.read(file))
        val text = file.readText()
        assertTrue(text.contains("\"Perez, Luis\""))
        assertTrue(text.contains("\"dijo \"\"hola\"\"\""))
        assertTrue(text.contains("\"primera\nsegunda\""))
        assertTrue(text.contains("4,\"\","))
        assertTrue(text.startsWith("id,name,notes\r\n"))
    }

    @Test
    fun `NULL y cadena vacia son campos distintos`(@TempDir directory: File) {
        val file = File(directory, "values.csv")
        CsvWriter.write(file, listOf("id", "value"), listOf(
            listOf("1", null),
            listOf("2", ""),
            listOf("3", " ")
        ))

        assertEquals("id,value\r\n1,\r\n2,\"\"\r\n3, \r\n", file.readText())
        assertEquals(listOf(listOf("1", null), listOf("2", ""), listOf("3", " ")),
            CsvReader.read(file).rows)
    }

    @Test
    fun `el lector acepta filas con CRLF LF y CR`(@TempDir directory: File) {
        val file = File(directory, "mixed.csv")
        file.writeText("id,note\r\n1,first\n2,second\r3,third")

        assertEquals(listOf(
            listOf("1", "first"), listOf("2", "second"), listOf("3", "third")
        ), CsvReader.read(file).rows)
    }

    @Test
    fun `un csv con solo encabezado no tiene filas`(@TempDir directory: File) {
        val file = File(directory, "empty.csv")
        CsvWriter.write(file, listOf("id", "name"), emptyList())

        assertEquals(listOf("id", "name"), CsvReader.read(file).header)
        assertTrue(CsvReader.read(file).rows.isEmpty())
    }

    @Test
    fun `una linea vacia en tabla de una columna es una fila NULL`(@TempDir directory: File) {
        val file = File(directory, "single.csv")
        CsvWriter.write(file, listOf("value"), listOf(listOf(null), listOf("")))

        assertEquals(listOf(listOf(null), listOf("")), CsvReader.read(file).rows)
    }

    @Test
    fun `el lector admite BOM UTF8 y campos entre comillas`(@TempDir directory: File) {
        val file = File(directory, "quoted.csv")
        file.writeText("\uFEFF\"id\",\"name\"\r\n1,\"a,b\"\r\n2,\"\"\r\n")

        assertEquals(listOf("id", "name"), CsvReader.read(file).header)
        assertEquals(listOf(listOf("1", "a,b"), listOf("2", "")), CsvReader.read(file).rows)
    }

    @Test
    fun `comillas mal formadas se rechazan`(@TempDir directory: File) {
        val file = File(directory, "broken.csv")
        listOf("id\n\"open", "id\n\"ok\"oops", "id\na\"b").forEach { source ->
            file.writeText(source)
            assertFailsWith<IOException> { CsvReader.read(file) }
        }
    }

    @Test
    fun `filas con distinto numero de campos se rechazan`(@TempDir directory: File) {
        val file = File(directory, "broken.csv")
        file.writeText("id,name\n1\n")
        assertFailsWith<IOException> { CsvReader.read(file) }
        file.writeText("id,name\n1,Ana,extra\n")
        assertFailsWith<IOException> { CsvReader.read(file) }
    }

    @Test
    fun `el lector rechaza archivos sin encabezado`(@TempDir directory: File) {
        val file = File(directory, "broken.csv")
        file.writeText("")
        assertFailsWith<IOException> { CsvReader.read(file) }
        file.writeText("id,\n")
        assertFailsWith<IOException> { CsvReader.read(file) }
    }

    @Test
    fun `una fila invalida no reemplaza el archivo anterior`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        file.writeText("id,name\r\n1,Ana\r\n")
        val before = file.readBytes()

        assertFailsWith<IllegalArgumentException> {
            CsvWriter.write(file, listOf("id", "name"), listOf(listOf("2")))
        }

        assertTrue(before.contentEquals(file.readBytes()))
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `escribir una tabla nueva crea su directorio`(@TempDir directory: File) {
        val file = File(directory, "nested/users.csv")

        CsvWriter.write(file, listOf("id"), listOf(listOf("1")))

        assertEquals(listOf(listOf("1")), CsvReader.read(file).rows)
    }

    @Test
    fun `reescribir la tabla reemplaza todas las filas`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        CsvWriter.write(file, listOf("id"), listOf(listOf("1")))

        CsvWriter.write(file, listOf("id"), listOf(listOf("2")))

        assertEquals(listOf(listOf("2")), CsvReader.read(file).rows)
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }
}

/**
 * Los desplazamientos que devuelve el escritor.
 *
 * Son la base del indice: un desplazamiento mal contado hace que un WHERE
 * indexado lea la fila equivocada, y eso no se nota hasta muy tarde.
 */
class CsvOffsetTest {

    private fun lineAt(file: File, offset: Long): String =
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val bytes = ByteArrayOutputStream()
            var b = raf.read()
            while (b != -1 && b != '\n'.code) {
                if (b != '\r'.code) bytes.write(b)
                b = raf.read()
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        }

    @Test
    fun `hay un desplazamiento por fila y ninguno para el encabezado`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val offsets = CsvWriter.write(
            file,
            listOf("id", "name"),
            listOf(listOf("1", "Ana"), listOf("2", "Luis"), listOf("3", "Eva"))
        )

        assertEquals(3, offsets.size)
        assertTrue(offsets[0] > 0, "la primera fila va despues del encabezado")
    }

    @Test
    fun `cada desplazamiento apunta al primer byte de su fila`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val rows = listOf(listOf("1", "Ana"), listOf("2", "Luis"), listOf("3", "Eva"))
        val offsets = CsvWriter.write(file, listOf("id", "name"), rows)

        assertEquals("1,Ana", lineAt(file, offsets[0]))
        assertEquals("2,Luis", lineAt(file, offsets[1]))
        assertEquals("3,Eva", lineAt(file, offsets[2]))
    }

    // El caso que rompe contar caracteres en vez de bytes: en UTF-8 una tilde
    // ocupa dos, asi que la fila siguiente quedaria corrida.
    @Test
    fun `las tildes no corren los desplazamientos`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val rows = listOf(listOf("1", "Martínez"), listOf("2", "Núñez"), listOf("3", "Ruiz"))
        val offsets = CsvWriter.write(file, listOf("id", "name"), rows)

        assertEquals("1,Martínez", lineAt(file, offsets[0]))
        assertEquals("2,Núñez", lineAt(file, offsets[1]))
        assertEquals("3,Ruiz", lineAt(file, offsets[2]))
    }

    @Test
    fun `un campo entre comillas cuenta sus comillas`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val rows = listOf(listOf("1", "Perez, Luis"), listOf("2", "Eva"))
        val offsets = CsvWriter.write(file, listOf("id", "name"), rows)

        assertEquals("\"Perez, Luis\"", lineAt(file, offsets[0]).substringAfter(","))
        assertEquals("2,Eva", lineAt(file, offsets[1]))
    }

    @Test
    fun `una tabla sin filas devuelve la lista vacia`(@TempDir directory: File) {
        val offsets = CsvWriter.write(File(directory, "users.csv"), listOf("id"), emptyList())

        assertEquals(emptyList(), offsets)
    }

    // Lo que de verdad va a pasar: reescribir despues de un DELETE mueve todo lo
    // que venia despues, y por eso los indices se reconstruyen en el volcado.
    @Test
    fun `reescribir sin una fila mueve las siguientes`(@TempDir directory: File) {
        val file = File(directory, "users.csv")
        val antes = CsvWriter.write(
            file,
            listOf("id", "name"),
            listOf(listOf("1", "Ana"), listOf("2", "Luis"), listOf("3", "Eva"))
        )
        val despues = CsvWriter.write(
            file,
            listOf("id", "name"),
            listOf(listOf("1", "Ana"), listOf("3", "Eva"))
        )

        assertEquals(antes[0], despues[0])
        assertTrue(despues[1] < antes[2], "Eva deberia haber subido")
        assertEquals("3,Eva", lineAt(file, despues[1]))
    }
}
