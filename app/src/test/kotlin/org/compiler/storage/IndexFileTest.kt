// Ticket 8.4: el archivo .idx.
package org.compiler.storage

import org.compiler.types.DateType
import org.compiler.types.DateValue
import org.compiler.types.IntType
import org.compiler.types.IntValue
import org.compiler.types.StringValue
import org.compiler.types.Value
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndexFileTest {

    private fun text(value: String) = StringValue(value) as Value

    // ── Ida y vuelta ───────────────────────────────────────────────────────

    // Una columna no unica repite el valor en varias lineas, que es el caso normal.
    @Test
    fun `un indice con valores repetidos sobrevive la ida y vuelta`(@TempDir directory: File) {
        val file = File(directory, "idx_marca.idx")
        val entries = IndexFile.build(
            listOf(text("Toyota"), text("Mazda"), text("Toyota"), text("Mazda"), text("Toyota")),
            listOf(17L, 31L, 44L, 58L, 71L)
        )

        IndexFile.write(file, VarcharType(20), entries)

        val reread = IndexFile.read(file, VarcharType(20))
        assertEquals(entries.mapValues { it.value.toList() }, reread.mapValues { it.value.toList() })
        assertEquals(listOf(17L, 44L, 71L), reread[text("Toyota")]?.toList())
    }

    // El archivo es un CSV de dos columnas, ordenado por valor: se lee a ojo.
    @Test
    fun `el archivo se escribe ordenado por valor`(@TempDir directory: File) {
        val file = File(directory, "idx_marca.idx")
        IndexFile.write(
            file,
            VarcharType(20),
            IndexFile.build(listOf(text("Toyota"), text("Mazda"), text("Toyota")), listOf(17L, 31L, 44L))
        )

        assertEquals(
            listOf("value,offset", "Mazda,31", "Toyota,17", "Toyota,44"),
            file.readLines()
        )
    }

    /**
     * El valor se decodifica con el tipo de la columna indexada, y eso es lo que
     * hace que un anio ordene como numero.
     *
     * Como texto, 100 iria antes que 2. Es la razon de que `read` reciba el tipo.
     */
    @Test
    fun `un indice sobre INT ordena 2, 10, 100`(@TempDir directory: File) {
        val file = File(directory, "idx_n.idx")
        IndexFile.write(
            file,
            IntType,
            IndexFile.build(listOf(IntValue(100), IntValue(2), IntValue(10)), listOf(10L, 20L, 30L))
        )

        assertEquals(listOf("value,offset", "2,20", "10,30", "100,10"), file.readLines())
        assertEquals(listOf(IntValue(2), IntValue(10), IntValue(100)), IndexFile.read(file, IntType).keys.toList())
    }

    @Test
    fun `un indice sobre DATE ordena cronologicamente`(@TempDir directory: File) {
        val file = File(directory, "idx_dia.idx")
        val days = listOf("2026-03-01", "2025-12-31", "2026-01-15").map { DateValue(LocalDate.parse(it)) as Value }
        IndexFile.write(file, DateType, IndexFile.build(days, listOf(10L, 20L, 30L)))

        assertEquals(
            listOf("2025-12-31", "2026-01-15", "2026-03-01"),
            IndexFile.read(file, DateType).keys.map { (it as DateValue).value.toString() }
        )
    }

    @Test
    fun `un indice que no existe se lee como vacio`(@TempDir directory: File) {
        assertTrue(IndexFile.read(File(directory, "no_esta.idx"), IntType).isEmpty())
    }

    @Test
    fun `un desplazamiento que no es numero falla con un mensaje`(@TempDir directory: File) {
        val file = File(directory, "idx_roto.idx")
        file.writeText("value,offset\r\nToyota,aqui\r\n")

        assertFailsWith<IOException> { IndexFile.read(file, VarcharType(20)) }
    }

    // ── El directorio ──────────────────────────────────────────────────────

    /**
     * La extension es `.idx` y no `.csv` a proposito: `tables()` lista los `.csv`
     * como tablas, y un indice no es una tabla.
     */
    @Test
    fun `los idx no se listan como tablas`(@TempDir root: File) {
        val directory = DataDirectory(root)
        CsvWriter.write(directory.csv("carros"), listOf("id"), emptyList())
        IndexFile.write(directory.index("idx_marca"), IntType, IndexFile.empty())

        assertEquals(listOf("carros"), directory.tables())
        assertEquals(listOf("idx_marca"), directory.indexFiles())
    }

    // El archivo se llama como el INDICE: dos tablas pueden tener un indice sobre
    // una columna con el mismo nombre.
    @Test
    fun `el archivo lleva el nombre del indice`(@TempDir root: File) {
        assertEquals("idx_marca.idx", DataDirectory(root).index("idx_marca").name)
    }
}
