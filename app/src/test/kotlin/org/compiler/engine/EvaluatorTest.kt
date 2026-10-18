package org.compiler.engine

import org.compiler.types.BoolValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.Value
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El valor de una expresion sobre una fila.
 *
 * Casi todo gira alrededor del nulo: en SQL se propaga por las cuentas y las
 * comparaciones, y AND y OR son la excepcion deliberada.
 */
class EvaluatorTest {

    // La expresion evaluada sobre la fila de Ana, que es la de id 1.
    private fun value(root: File, text: String): Value {
        database(root)
        return query(root, "SELECT $text FROM users WHERE id = 1;").column(0).single()
    }

    private fun filtered(root: File, condition: String): List<String> {
        database(root)
        return query(root, "SELECT name FROM users WHERE $condition;").grid.map { it.single() }
    }

    // ── Aritmetica ─────────────────────────────────────────────────────────

    @Test
    fun `las cuatro cuentas sobre enteros`(@TempDir root: File) {
        assertEquals(IntValue(33), value(root, "age + 3"))
        assertEquals(IntValue(27), value(root, "age - 3"))
        assertEquals(IntValue(60), value(root, "age * 2"))
        assertEquals(IntValue(10), value(root, "age / 3"))
    }

    // INT / INT trunca, como en SQL estandar: 10 / 3 da 3.
    @Test
    fun `dividir enteros trunca`(@TempDir root: File) {
        assertEquals(IntValue(3), value(root, "10 / 3"))
    }

    // La escala sale del tipo: un decimal de un decimal mas los seis extra de la
    // division, que es donde 1/3 tiene que terminar.
    @Test
    fun `dividir decimales usa la escala que calculo la fase 5`(@TempDir root: File) {
        assertEquals("3.3333333", value(root, "10.0 / 3.0").display())
        assertEquals("0.3333333", value(root, "1.0 / 3.0").display())
    }

    // No puede reventar en la mitad de una corrida: reporta y sigue con un nulo.
    @Test
    fun `dividir entre cero reporta y da nulo`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "SELECT age / 0 FROM users WHERE id = 1;")

        assertEquals(NullValue, outcome.column(0).single())
        assertEquals(listOf("division entre cero"), outcome.errors)
    }

    @Test
    fun `negar un numero`(@TempDir root: File) {
        assertEquals(IntValue(-30), value(root, "-age"))
    }

    @Test
    fun `concatenar texto`(@TempDir root: File) {
        assertEquals(StringValue("Ana-GT"), value(root, "name || '-' || city"))
    }

    // ── El nulo se propaga ─────────────────────────────────────────────────

    @Test
    fun `una cuenta con nulo da nulo`(@TempDir root: File) {
        assertEquals(NullValue, value(root, "1 + NULL"))
        assertEquals(NullValue, value(root, "age * NULL"))
    }

    @Test
    fun `una comparacion con nulo da nulo`(@TempDir root: File) {
        assertEquals(NullValue, value(root, "age > NULL"))
        assertEquals(NullValue, value(root, "name = NULL"))
    }

    // Por eso `= NULL` nunca filtra nada, y por eso la fase 5 lo advierte.
    @Test
    fun `una condicion en nulo no pasa el filtro`(@TempDir root: File) {
        assertEquals(emptyList(), filtered(root, "city = NULL"))
        assertEquals(emptyList(), filtered(root, "age > NULL"))
    }

    @Test
    fun `una fila con la columna en nulo no pasa una comparacion`(@TempDir root: File) {
        assertEquals(listOf("Ana", "Beto"), filtered(root, "age > 26"))
    }

    // ── Los logicos son la excepcion ───────────────────────────────────────

    // Ya no puede ser verdadero, asi que la respuesta se sabe sin mirar el otro lado.
    @Test
    fun `falso AND nulo da falso`(@TempDir root: File) {
        assertEquals(BoolValue(false), value(root, "FALSE AND NULL"))
    }

    @Test
    fun `verdadero AND nulo da nulo`(@TempDir root: File) {
        assertEquals(NullValue, value(root, "TRUE AND NULL"))
    }

    @Test
    fun `verdadero OR nulo da verdadero`(@TempDir root: File) {
        assertEquals(BoolValue(true), value(root, "TRUE OR NULL"))
    }

    @Test
    fun `falso OR nulo da nulo`(@TempDir root: File) {
        assertEquals(NullValue, value(root, "FALSE OR NULL"))
    }

    @Test
    fun `NOT de un nulo sigue siendo nulo`(@TempDir root: File) {
        assertEquals(NullValue, value(root, "NOT NULL"))
    }

    // ── IS NULL, que es la unica forma de preguntar ────────────────────────

    @Test
    fun `IS NULL encuentra lo que la comparacion no`(@TempDir root: File) {
        assertEquals(listOf("Dani"), filtered(root, "city IS NULL"))
        assertEquals(listOf("Cora"), filtered(root, "age IS NULL"))
    }

    @Test
    fun `IS NOT NULL es el complemento`(@TempDir root: File) {
        assertEquals(listOf("Ana", "Beto", "Cora"), filtered(root, "city IS NOT NULL"))
    }

    // ── Pertenencia ────────────────────────────────────────────────────────

    @Test
    fun `IN con una lista de valores`(@TempDir root: File) {
        assertEquals(listOf("Ana", "Cora"), filtered(root, "id IN (1, 3)"))
    }

    @Test
    fun `NOT IN excluye`(@TempDir root: File) {
        assertEquals(listOf("Beto", "Dani"), filtered(root, "id NOT IN (1, 3)"))
    }

    /**
     * Si no esta pero habia un nulo entre los candidatos, la respuesta es NULL y no
     * falso: el valor podria ser ese nulo y no hay como saberlo. Por eso la fila no
     * pasa el filtro.
     */
    @Test
    fun `un nulo en la lista hace que lo que no esta quede en nulo`(@TempDir root: File) {
        assertEquals(listOf("Ana"), filtered(root, "id IN (1, NULL)"))
    }

    // ── Comparaciones entre tipos ──────────────────────────────────────────

    @Test
    fun `el texto se compara alfabeticamente`(@TempDir root: File) {
        assertEquals(listOf("Ana", "Beto"), filtered(root, "name < 'C'"))
    }

    @Test
    fun `un entero se compara con un decimal`(@TempDir root: File) {
        assertEquals(BoolValue(true), value(root, "age > 29.5"))
    }

    @Test
    fun `las fechas se comparan como fechas`(@TempDir root: File) {
        assertEquals(BoolValue(true), value(root, "DATE '2026-01-01' < DATE '2026-02-01'"))
        assertTrue((value(root, "TIME '10:00:00' > TIME '09:00:00'") as BoolValue).value)
    }
}
