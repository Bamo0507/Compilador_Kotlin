package org.compiler.engine

import org.compiler.types.FloatValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.Value
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La agregacion.
 *
 * El trato de los nulos es lo que mas se equivoca, asi que hay un caso por celda
 * de la tabla: COUNT(*) cuenta filas, el resto cuenta valores presentes.
 */
class AggregatorTest {

    // La tabla trae cuatro filas, con age en 30, 40, nulo y 25.
    private fun single(root: File, select: String, where: String = ""): Value {
        database(root)
        val filter = if (where.isEmpty()) "" else " WHERE $where"
        return query(root, "SELECT $select FROM users$filter;").column(0).single()
    }

    // ── COUNT ──────────────────────────────────────────────────────────────

    /**
     * La diferencia clasica: el primero cuenta filas, el segundo cuenta valores
     * presentes. De cuatro usuarios, uno no tiene edad.
     */
    @Test
    fun `COUNT asterisco cuenta filas y COUNT de columna cuenta valores`(@TempDir root: File) {
        assertEquals(IntValue(4), single(root, "COUNT(*)"))
        assertEquals(IntValue(3), single(root, "COUNT(age)"))
    }

    @Test
    fun `COUNT DISTINCT no cuenta repetidos`(@TempDir root: File) {
        assertEquals(IntValue(2), single(root, "COUNT(DISTINCT city)"))
        assertEquals(IntValue(3), single(root, "COUNT(DISTINCT city) + 1"))
    }

    // ── SUM, AVG, MIN y MAX ────────────────────────────────────────────────

    @Test
    fun `SUM ignora los nulos`(@TempDir root: File) {
        assertEquals(IntValue(95), single(root, "SUM(age)"))
    }

    // Divide entre los no nulos: 95 entre 3, no entre 4.
    @Test
    fun `AVG divide entre los valores presentes`(@TempDir root: File) {
        assertEquals(FloatValue(95.0 / 3), single(root, "AVG(age)"))
    }

    @Test
    fun `MIN y MAX ignoran los nulos`(@TempDir root: File) {
        assertEquals(IntValue(25), single(root, "MIN(age)"))
        assertEquals(IntValue(40), single(root, "MAX(age)"))
    }

    @Test
    fun `MIN y MAX tambien ordenan texto`(@TempDir root: File) {
        assertEquals("Ana", single(root, "MIN(name)").display())
        assertEquals("Dani", single(root, "MAX(name)").display())
    }

    // ── Sobre cero filas ───────────────────────────────────────────────────

    // Sin GROUP BY todo es un solo grupo, asi que hay una fila aunque no haya datos.
    @Test
    fun `COUNT sobre cero filas da una fila con cero`(@TempDir root: File) {
        assertEquals(IntValue(0), single(root, "COUNT(*)", "id > 100"))
    }

    /**
     * No hay nada que sumar, y decir 0 seria inventar un dato. Es lo que dice el
     * estandar y lo que hace SQLite.
     */
    @Test
    fun `SUM sobre cero filas da NULL y no cero`(@TempDir root: File) {
        assertEquals(NullValue, single(root, "SUM(age)", "id > 100"))
    }

    @Test
    fun `AVG, MIN y MAX sobre cero filas dan NULL`(@TempDir root: File) {
        assertEquals(NullValue, single(root, "AVG(age)", "id > 100"))
        assertEquals(NullValue, single(root, "MIN(age)", "id > 100"))
        assertEquals(NullValue, single(root, "MAX(age)", "id > 100"))
    }

    @Test
    fun `una columna entera en nulo se comporta como cero filas`(@TempDir root: File) {
        assertEquals(NullValue, single(root, "SUM(age)", "age IS NULL"))
        assertEquals(IntValue(0), single(root, "COUNT(age)", "age IS NULL"))
        assertEquals(IntValue(1), single(root, "COUNT(*)", "age IS NULL"))
    }

    // ── GROUP BY ───────────────────────────────────────────────────────────

    @Test
    fun `GROUP BY hace un grupo por valor distinto`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, COUNT(*) FROM users GROUP BY city ORDER BY city;")

        assertEquals(listOf(listOf("GT", "2"), listOf("MX", "1"), listOf("NULL", "1")), outcome.grid)
    }

    // Con GROUP BY sobre cero filas no hay grupos, y por eso no hay filas. Es lo
    // contrario de la agregacion sin agrupar.
    @Test
    fun `GROUP BY sobre cero filas no devuelve nada`(@TempDir root: File) {
        database(root)

        assertEquals(emptyList(), query(root, "SELECT city FROM users WHERE id > 100 GROUP BY city;").grid)
    }

    @Test
    fun `cada grupo agrega solo sus filas`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, SUM(age) FROM users GROUP BY city ORDER BY city;")

        assertEquals(listOf(listOf("GT", "70"), listOf("MX", "NULL"), listOf("NULL", "25")), outcome.grid)
    }

    @Test
    fun `se puede agrupar por mas de una columna`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, age, COUNT(*) FROM users GROUP BY city, age ORDER BY city, age;")

        assertEquals(4, outcome.result.rows.size)
    }

    // ── HAVING ─────────────────────────────────────────────────────────────

    @Test
    fun `HAVING filtra grupos, no filas`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, COUNT(*) FROM users GROUP BY city HAVING COUNT(*) > 1;")

        assertEquals(listOf(listOf("GT", "2")), outcome.grid)
    }

    @Test
    fun `HAVING puede mirar otra agregacion`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city FROM users GROUP BY city HAVING SUM(age) > 30 ORDER BY city;")

        assertEquals(listOf(listOf("GT")), outcome.grid)
    }

    // ── Ordenar grupos ─────────────────────────────────────────────────────

    @Test
    fun `se puede ordenar por una agregacion`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, COUNT(*) AS cuantos FROM users GROUP BY city ORDER BY cuantos DESC, city;")

        assertEquals(listOf(listOf("GT", "2"), listOf("MX", "1"), listOf("NULL", "1")), outcome.grid)
    }

    // ── El WHERE corre antes de agrupar ────────────────────────────────────

    @Test
    fun `el WHERE descarta filas antes de que lleguen al grupo`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT city, COUNT(*) FROM users WHERE age > 26 GROUP BY city ORDER BY city;")

        assertEquals(listOf(listOf("GT", "2")), outcome.grid)
    }
}
