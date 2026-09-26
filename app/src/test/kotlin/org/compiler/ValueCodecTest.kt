package org.compiler

import org.compiler.types.BoolValue
import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DateType
import org.compiler.types.DateValue
import org.compiler.types.DecimalType
import org.compiler.types.DecimalValue
import org.compiler.types.FloatType
import org.compiler.types.FloatValue
import org.compiler.types.IntType
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.TextType
import org.compiler.types.TimeType
import org.compiler.types.TimeValue
import org.compiler.types.Type
import org.compiler.types.Value
import org.compiler.types.ValueCodec
import org.compiler.types.VarcharType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * El puente entre el texto del CSV y los valores.
 *
 * La prueba que mas cubre es la de ida y vuelta: si un valor sobrevive a
 * escribirse y volverse a leer, el formato, el relleno y la escala estan bien.
 */
class ValueCodecTest {

    private fun roundTrip(value: Value, type: Type): Value? =
        ValueCodec.decode(ValueCodec.encode(value, type), type)

    @Test
    fun `los ocho valores sobreviven la ida y vuelta`() {
        val casos: List<Pair<Value, Type>> = listOf(
            IntValue(42) to IntType,
            FloatValue(3.14) to FloatType,
            DecimalValue(BigDecimal("1250.00")) to DecimalType(10, 2),
            StringValue("Ana") to VarcharType(80),
            StringValue("texto largo") to TextType,
            DateValue(LocalDate.of(2026, 9, 26)) to DateType,
            TimeValue(LocalTime.of(14, 30)) to TimeType,
            BoolValue(true) to BooleanType,
            NullValue to IntType
        )

        casos.forEach { (value, type) ->
            assertEquals(value, roundTrip(value, type), "fallo ${type.name}")
        }
    }

    // El relleno vive en el tipo, no en el archivo: se pone al leer y se quita al
    // escribir, asi el CSV no lleva espacios invisibles al final.
    @Test
    fun `CHAR se rellena al leer y se recorta al escribir`() {
        assertEquals(StringValue("ab   "), ValueCodec.decode("ab", CharType(5)))
        assertEquals("ab", ValueCodec.encode(StringValue("ab   "), CharType(5)))
    }

    @Test
    fun `un CHAR que no cabe se rechaza`() {
        assertNull(ValueCodec.decode("abcdef", CharType(5)))
    }

    // Truncar en silencio esconde el error hasta que alguien nota que falta texto.
    @Test
    fun `un VARCHAR que se pasa del tope se rechaza`() {
        assertEquals(StringValue("abcde"), ValueCodec.decode("abcde", VarcharType(5)))
        assertNull(ValueCodec.decode("abcdefgh", VarcharType(5)))
    }

    @Test
    fun `un TEXT no tiene tope`() {
        val largo = "x".repeat(5000)
        assertEquals(StringValue(largo), ValueCodec.decode(largo, TextType))
    }

    @Test
    fun `un DECIMAL toma la escala de su tipo`() {
        assertEquals(
            DecimalValue(BigDecimal("12.50")),
            ValueCodec.decode("12.5", DecimalType(10, 2))
        )
        assertEquals("12.50", ValueCodec.encode(DecimalValue(BigDecimal("12.5")), DecimalType(10, 2)))
    }

    @Test
    fun `los decimales de mas se redondean`() {
        assertEquals(
            DecimalValue(BigDecimal("12.57")),
            ValueCodec.decode("12.567", DecimalType(10, 2))
        )
    }

    // DECIMAL(5,2) deja tres digitos enteros. 1234.56 no cabe y no se puede
    // redondear para que quepa.
    @Test
    fun `una parte entera que no cabe en la precision se rechaza`() {
        assertNull(ValueCodec.decode("1234.56", DecimalType(5, 2)))
        assertEquals(
            DecimalValue(BigDecimal("123.45")),
            ValueCodec.decode("123.45", DecimalType(5, 2))
        )
    }

    @Test
    fun `las fechas y horas se leen en ISO-8601`() {
        assertEquals(DateValue(LocalDate.of(2026, 9, 26)), ValueCodec.decode("2026-09-26", DateType))
        assertEquals(TimeValue(LocalTime.of(14, 30, 5)), ValueCodec.decode("14:30:05", TimeType))
    }

    @Test
    fun `una fecha imposible devuelve null en vez de lanzar`() {
        assertNull(ValueCodec.decode("2026-13-45", DateType))
        assertNull(ValueCodec.decode("26/09/2026", DateType))
        assertNull(ValueCodec.decode("25:00:00", TimeType))
    }

    @Test
    fun `el booleano solo acepta true y false`() {
        assertEquals(BoolValue(true), ValueCodec.decode("true", BooleanType))
        assertEquals(BoolValue(false), ValueCodec.decode("false", BooleanType))
        assertNull(ValueCodec.decode("TRUE", BooleanType))
        assertNull(ValueCodec.decode("1", BooleanType))
    }

    @Test
    fun `un texto que no es numero se rechaza`() {
        assertNull(ValueCodec.decode("abc", IntType))
        assertNull(ValueCodec.decode("3.5", IntType))
        assertNull(ValueCodec.decode("abc", FloatType))
        assertNull(ValueCodec.decode("abc", DecimalType(10, 2)))
    }

    // Un campo vacio del CSV llega como null y es NULL. Uno que trae comillas
    // llega como "" y es la cadena vacia. Esa diferencia la hace CsvReader; aqui
    // solo hay que no perderla.
    @Test
    fun `null es NULL y la cadena vacia es un valor`() {
        assertEquals(NullValue, ValueCodec.decode(null, IntType))
        assertEquals(NullValue, ValueCodec.decode(null, TextType))
        assertEquals(StringValue(""), ValueCodec.decode("", TextType))

        assertNull(ValueCodec.encode(NullValue, IntType))
        assertEquals("", ValueCodec.encode(StringValue(""), TextType))
    }
}
