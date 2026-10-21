// Ticket 8.3: los desplazamientos del escritor y la lectura por salto.
package org.compiler.storage

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RowReaderTest {

    private val header = listOf("id", "nombre", "nota")

    private fun write(directory: File, rows: List<List<String?>>): Pair<File, List<Long>> {
        val file = File(directory, "datos.csv")
        return file to CsvWriter.write(file, header, rows)
    }

    // ── Los desplazamientos ────────────────────────────────────────────────

    // Salen de contar bytes mientras se escribe, asi que no cuestan un recorrido
    // aparte: el primero queda justo despues del encabezado.
    @Test
    fun `el desplazamiento de cada fila apunta a su primer byte`(@TempDir directory: File) {
        val rows = listOf(listOf("1", "Ana", "x"), listOf("2", "Beto", "y"))
        val (file, offsets) = write(directory, rows)

        val bytes = file.readBytes()
        assertEquals(2, offsets.size)
        assertEquals("1,Ana,x", String(bytes, offsets[0].toInt(), 7, Charsets.UTF_8))
        assertEquals("2,Beto,y", String(bytes, offsets[1].toInt(), 8, Charsets.UTF_8))
    }

    @Test
    fun `una tabla vacia no devuelve desplazamientos`(@TempDir directory: File) {
        assertEquals(emptyList(), write(directory, emptyList()).second)
    }

    // ── La lectura por salto ───────────────────────────────────────────────

    @Test
    fun `cada fila se lee por su desplazamiento`(@TempDir directory: File) {
        val rows = listOf(listOf("1", "Ana", "x"), listOf("2", "Beto", "y"), listOf("3", "Cora", "z"))
        val (file, offsets) = write(directory, rows)

        assertEquals(rows, offsets.map { RowReader.readAt(file, it) })
    }

    /**
     * Las tildes son el detalle que muerde.
     *
     * `RandomAccessFile.readLine()` lee cada byte como un caracter, que es latin-1:
     * una ñ saldria partida en dos. Por eso se juntan los bytes y se decodifican
     * como UTF-8 de una sola vez.
     */
    @Test
    fun `una fila con tildes y ñ se lee correcta`(@TempDir directory: File) {
        val rows = listOf(listOf("1", "José", "año"), listOf("2", "Ñandú", "mañana"))
        val (file, offsets) = write(directory, rows)

        assertEquals(listOf("2", "Ñandú", "mañana"), RowReader.readAt(file, offsets[1]))
    }

    // Y por eso el desplazamiento se cuenta en BYTES: contado en caracteres, la
    // segunda fila empezaria antes de donde esta.
    @Test
    fun `los desplazamientos cuentan bytes y no caracteres`(@TempDir directory: File) {
        val rows = listOf(listOf("1", "ñññ", "x"), listOf("2", "Ana", "y"))
        val (file, offsets) = write(directory, rows)

        assertEquals(listOf("2", "Ana", "y"), RowReader.readAt(file, offsets[1]))
    }

    // El escapado del CSV se respeta: se reusa el parser del CsvReader, no uno
    // propio que se podria desincronizar.
    @Test
    fun `una fila con comas y comillas respeta el escapado`(@TempDir directory: File) {
        val rows = listOf(listOf("1", "Perez, Luis", "dijo \"hola\""), listOf("2", "Ana", null))
        val (file, offsets) = write(directory, rows)

        assertEquals(rows[0], RowReader.readAt(file, offsets[0]))
        assertEquals(rows[1], RowReader.readAt(file, offsets[1]))
    }

    // La prueba de fondo: leer por salto tiene que dar lo mismo que leer todo.
    @Test
    fun `leer por salto da lo mismo que leer el archivo entero`(@TempDir directory: File) {
        val rows = (1..20).map { listOf("$it", "nombre $it", if (it % 3 == 0) null else "nota, $it") }
        val (file, offsets) = write(directory, rows)

        assertEquals(CsvReader.read(file).rows, offsets.map { RowReader.readAt(file, it) })
    }

    @Test
    fun `un desplazamiento fuera del archivo falla con un mensaje`(@TempDir directory: File) {
        val (file, _) = write(directory, listOf(listOf("1", "Ana", "x")))

        val error = assertFailsWith<IOException> { RowReader.readAt(file, 10_000) }
        assertEquals(true, error.message?.contains("fuera del archivo"), error.message)
    }
}
