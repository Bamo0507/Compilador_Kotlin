package org.compiler

import org.compiler.types.BoolValue
import org.compiler.types.DateValue
import org.compiler.types.DecimalValue
import org.compiler.types.FloatValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.TimeValue
import org.compiler.types.Value
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Los valores en ejecucion: igualdad y como se muestran.
 *
 * El grueso del archivo es DECIMAL, que es donde estan las trampas.
 */
class ValueTest {

    // La razon de que DECIMAL exista aparte de FLOAT. Si fueran lo mismo,
    // el tipo estaria mintiendo sobre lo que promete.
    @Test
    fun `DECIMAL suma exacto donde FLOAT no`() {
        val decimal = BigDecimal("0.1").add(BigDecimal("0.2"))
        assertEquals(BigDecimal("0.3"), decimal)

        val flotante = 0.1 + 0.2
        assertNotEquals(0.3, flotante)
    }

    // 1.0 y 1.00 son el MISMO numero en SQL. El equals de BigDecimal dice que no,
    // porque mira la escala, asi que DecimalValue lo corrige con compareTo.
    @Test
    fun `dos DECIMAL con distinta escala son el mismo valor`() {
        assertEquals(DecimalValue(BigDecimal("1.0")), DecimalValue(BigDecimal("1.00")))
        assertEquals(DecimalValue(BigDecimal("10")), DecimalValue(BigDecimal("10.0")))
    }

    // Lo anterior no sirve de nada si el hash no acompania: un HashSet, que es lo
    // que usa DISTINCT y la revision de clave primaria, se guia por hashCode.
    @Test
    fun `dos DECIMAL iguales comparten hash y colapsan en un set`() {
        val uno = DecimalValue(BigDecimal("1.0"))
        val otro = DecimalValue(BigDecimal("1.00"))

        assertEquals(uno.hashCode(), otro.hashCode())
        assertEquals(1, setOf<Value>(uno, otro).size)
    }

    @Test
    fun `dos DECIMAL distintos siguen siendo distintos`() {
        assertNotEquals(DecimalValue(BigDecimal("1.5")), DecimalValue(BigDecimal("1.50001")))
        assertEquals(2, setOf<Value>(
            DecimalValue(BigDecimal("1.5")),
            DecimalValue(BigDecimal("2.5"))
        ).size)
    }

    // Sin toPlainString, un BigDecimal grande sale en notacion cientifica y la
    // rejilla mostraria 1E+3 en vez de 1000.
    @Test
    fun `un DECIMAL se muestra sin notacion cientifica`() {
        assertEquals("1000.00", DecimalValue(BigDecimal("1000.00")).display())
        assertEquals("0.30", DecimalValue(BigDecimal("0.30")).display())
    }

    @Test
    fun `las fechas y horas se muestran en ISO-8601`() {
        assertEquals("2026-09-26", DateValue(LocalDate.of(2026, 9, 26)).display())
        assertEquals("14:30", TimeValue(LocalTime.of(14, 30)).display())
        assertEquals("14:30:05", TimeValue(LocalTime.of(14, 30, 5)).display())
    }

    // ISO-8601 ordena alfabeticamente igual que cronologicamente, que es lo que
    // deja que un ORDER BY sobre fechas no necesite nada especial.
    @Test
    fun `el orden alfabetico de las fechas es el cronologico`() {
        val fechas = listOf("2026-01-15", "2025-12-31", "2026-01-02")

        assertEquals(listOf("2025-12-31", "2026-01-02", "2026-01-15"), fechas.sorted())
    }

    @Test
    fun `los demas valores comparan por su contenido`() {
        assertEquals(IntValue(42), IntValue(42))
        assertEquals(StringValue("Ana"), StringValue("Ana"))
        assertEquals(BoolValue(true), BoolValue(true))
        assertNotEquals<Value>(IntValue(42), StringValue("42"))
    }

    @Test
    fun `NULL se muestra en mayusculas, como en SQL`() {
        assertEquals("NULL", NullValue.display())
    }

    @Test
    fun `el when sellado cubre los ocho`() {
        val todos: List<Value> = listOf(
            IntValue(1), FloatValue(1.0), DecimalValue(BigDecimal.ONE),
            StringValue("x"), DateValue(LocalDate.now()), TimeValue(LocalTime.now()),
            BoolValue(true), NullValue
        )

        assertEquals(8, todos.size)
        assertTrue(todos.all { familia(it).isNotEmpty() })
    }

    private fun familia(value: Value): String = when (value) {
        is IntValue, is FloatValue, is DecimalValue -> "numerico"
        is StringValue -> "caracter"
        is DateValue, is TimeValue -> "temporal"
        is BoolValue -> "logico"
        NullValue -> "nulo"
    }
}
