package org.compiler.engine

import org.compiler.catalog.CatalogLoader
import org.compiler.diagnostics.Diagnostics
import org.compiler.storage.DataDirectory
import org.compiler.types.BoolValue
import org.compiler.types.DateValue
import org.compiler.types.DecimalValue
import org.compiler.types.FloatValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.TimeValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Las piezas de las que se arma una consulta: leer, filtrar, proyectar, ordenar,
 * quitar repetidos y cortar.
 */
class OperatorsTest {

    // ── Leer ───────────────────────────────────────────────────────────────

    // El esquema manda: cada columna se decodifica con el tipo que dice el JSON.
    @Test
    fun `scan decodifica cada tipo con el suyo`(@TempDir root: File) {
        write(root, samplesTable, listOf(
            listOf("7", "2.5", "12.34", "ab", "hola", "un texto", "2026-03-01", "10:15:00", "true")
        ))

        val values = query(root, "SELECT * FROM samples;").result.rows.single().values

        assertEquals(IntValue(7), values[0])
        assertEquals(FloatValue(2.5), values[1])
        assertEquals(DecimalValue(BigDecimal("12.34")), values[2])
        // CHAR rellena al leer: el largo lo dice el tipo, no el archivo.
        assertEquals(StringValue("ab "), values[3])
        assertEquals(StringValue("hola"), values[4])
        assertEquals(StringValue("un texto"), values[5])
        assertEquals(DateValue(LocalDate.of(2026, 3, 1)), values[6])
        assertEquals(TimeValue(LocalTime.of(10, 15)), values[7])
        assertEquals(BoolValue(true), values[8])
    }

    @Test
    fun `una fila entera en blanco se decodifica como nulos`(@TempDir root: File) {
        write(root, samplesTable, listOf(List(samplesTable.columns.size) { null }))

        val values = query(root, "SELECT * FROM samples;").result.rows.single().values

        assertEquals(List(9) { NullValue }, values)
    }

    // La misma tabla dos veces en un autojoin abre el archivo una sola vez.
    @Test
    fun `un autojoin abre el archivo una sola vez`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT a.name FROM users a JOIN users b ON a.id = b.id;")

        assertEquals(1, outcome.session.reads)
        assertEquals(4, outcome.result.rows.size)
    }

    @Test
    fun `scan sobre una tabla ya modificada devuelve las filas modificadas`(@TempDir root: File) {
        database(root)
        val diagnostics = Diagnostics()
        val directory = DataDirectory(root)
        val session = Session(directory, CatalogLoader(directory, diagnostics).load(), diagnostics)

        session.rowsOf("users").removeAt(0)

        assertEquals(3, Operators.scan(session, "users").rows.size)
    }

    // ── Filtrar y proyectar ────────────────────────────────────────────────

    @Test
    fun `el WHERE deja pasar solo lo que cumple`(@TempDir root: File) {
        database(root)

        assertEquals(listOf(listOf("Ana"), listOf("Beto")), query(root, "SELECT name FROM users WHERE city = 'GT';").grid)
    }

    @Test
    fun `el SELECT proyecta en el orden escrito`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, name FROM users WHERE id = 1;")

        assertEquals(listOf("city", "name"), outcome.names)
        assertEquals(listOf(listOf("GT", "Ana")), outcome.grid)
    }

    @Test
    fun `un asterisco trae todas las columnas de la tabla`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT * FROM users WHERE id = 1;")

        assertEquals(listOf("id", "name", "city", "age"), outcome.names)
        assertEquals(listOf(listOf("1", "Ana", "GT", "30")), outcome.grid)
    }

    // ── Ordenar ────────────────────────────────────────────────────────────

    @Test
    fun `ORDER BY ordena ascendente por omision`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("Dani", "Ana", "Beto", "Cora"),
            query(root, "SELECT name FROM users ORDER BY age;").grid.map { it.single() }
        )
    }

    @Test
    fun `DESC invierte`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("Cora", "Beto", "Ana", "Dani"),
            query(root, "SELECT name FROM users ORDER BY age DESC;").grid.map { it.single() }
        )
    }

    // Comparar un nulo con cualquier cosa da nulo, asi que el orden necesita una
    // regla explicita. Van al final en ASC y al principio en DESC, como SQLite.
    @Test
    fun `los nulos van al final en ascendente`(@TempDir root: File) {
        database(root)
        val names = query(root, "SELECT name FROM users ORDER BY age;").grid.map { it.single() }

        assertEquals("Cora", names.last())
    }

    @Test
    fun `los nulos van al principio en descendente`(@TempDir root: File) {
        database(root)
        val names = query(root, "SELECT name FROM users ORDER BY age DESC;").grid.map { it.single() }

        assertEquals("Cora", names.first())
    }

    @Test
    fun `se puede ordenar por una columna que no sale`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("Dani", "Ana", "Beto"),
            query(root, "SELECT name FROM users WHERE age IS NOT NULL ORDER BY age;").grid.map { it.single() }
        )
    }

    @Test
    fun `se puede ordenar por un alias del SELECT`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("80", "60", "50"),
            query(root, "SELECT age * 2 AS doble FROM users WHERE age IS NOT NULL ORDER BY doble DESC;")
                .grid.map { it.single() }
        )
    }

    @Test
    fun `dos criterios desempatan`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("Beto", "Ana", "Cora", "Dani"),
            query(root, "SELECT name FROM users ORDER BY city, name DESC;").grid.map { it.single() }
        )
    }

    // ── Quitar repetidos ───────────────────────────────────────────────────

    @Test
    fun `DISTINCT colapsa las filas repetidas`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("GT", "MX", "NULL"),
            query(root, "SELECT DISTINCT city FROM users ORDER BY city;").grid.map { it.single() }
        )
    }

    /**
     * BigDecimal distingue 1.0 de 1.00 por su escala, pero en SQL son el mismo
     * numero. DecimalValue ya compara por valor, asi que DISTINCT los colapsa sin
     * hacer nada aparte.
     */
    @Test
    fun `DISTINCT colapsa dos decimales de distinta escala`() {
        val rows = listOf(
            Row(listOf(DecimalValue(BigDecimal("1.0")))),
            Row(listOf(DecimalValue(BigDecimal("1.00")))),
            Row(listOf(DecimalValue(BigDecimal("2.5"))))
        )

        assertEquals(2, Operators.distinct(ResultSet(emptyList(), rows)).rows.size)
    }

    // ── Cortar ─────────────────────────────────────────────────────────────

    @Test
    fun `LIMIT corta despues de ordenar`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf("Cora", "Beto"),
            query(root, "SELECT name FROM users ORDER BY age DESC LIMIT 2;").grid.map { it.single() }
        )
    }

    @Test
    fun `un LIMIT mayor que las filas no corta nada`(@TempDir root: File) {
        database(root)

        assertEquals(4, query(root, "SELECT name FROM users ORDER BY id LIMIT 99;").result.rows.size)
    }
}
