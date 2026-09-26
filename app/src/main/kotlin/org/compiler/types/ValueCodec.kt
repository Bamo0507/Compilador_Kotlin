package org.compiler.types

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Traduce entre el texto del CSV y un Value, guiado por el tipo de la columna.
 *
 * El CSV solo guarda texto. Que `1250.00` sea un FLOAT, un DECIMAL o una cadena
 * lo dice el tipo, que viene del .json de esquema.
 *
 * `decode` devuelve null cuando el texto no sirve para ese tipo. Quien llama
 * decide si eso es un archivo corrupto o un INSERT mal escrito.
 */
object ValueCodec {

    fun decode(text: String?, type: Type): Value? {
        if (text == null) return NullValue

        return when (type) {
            IntType -> text.trim().toLongOrNull()?.let { IntValue(it) }
            FloatType -> text.trim().toDoubleOrNull()?.let { FloatValue(it) }
            is DecimalType -> decodeDecimal(text.trim(), type)

            // El relleno se aplica al leer: el largo lo dice el tipo, asi que el
            // archivo no necesita cargar espacios invisibles.
            is CharType -> if (text.length <= type.length) {
                StringValue(text.padEnd(type.length))
            } else {
                null
            }

            // Pasarse del tope es error, no truncamiento silencioso.
            is VarcharType -> if (text.length <= type.maxLength) StringValue(text) else null

            TextType -> StringValue(text)
            DateType -> parseDate(text.trim())
            TimeType -> parseTime(text.trim())

            BooleanType -> when (text.trim()) {
                "true" -> BoolValue(true)
                "false" -> BoolValue(false)
                else -> null
            }

            // No se escriben en una columna: no hay texto que representarlos.
            NullType, ErrorType -> null
        }
    }

    /**
     * Value al texto del CSV. null significa campo vacio, que es como se guarda
     * un NULL.
     */
    fun encode(value: Value, type: Type): String? = when (value) {
        NullValue -> null

        // CHAR se guarda SIN relleno. En semantica CHAR, un valor y ese mismo
        // valor con espacios al final son el mismo valor, asi que no se pierde
        // nada y el archivo queda legible.
        is StringValue -> if (type is CharType) value.value.trimEnd() else value.value

        is DecimalValue ->
            if (type is DecimalType) {
                value.value.setScale(type.scale, RoundingMode.HALF_UP).toPlainString()
            } else {
                value.value.toPlainString()
            }

        else -> value.display()
    }

    // La parte entera no puede pasarse de lo que permite la precision; los
    // decimales de mas se redondean, que es lo que hace un DBMS al insertar.
    private fun decodeDecimal(text: String, type: DecimalType): Value? {
        val parsed = runCatching { BigDecimal(text) }.getOrNull() ?: return null

        val integerDigits = parsed.precision() - parsed.scale()
        if (integerDigits > type.precision - type.scale) return null

        return DecimalValue(parsed.setScale(type.scale, RoundingMode.HALF_UP))
    }

    private fun parseDate(text: String): Value? =
        try {
            DateValue(LocalDate.parse(text))
        } catch (error: DateTimeParseException) {
            null
        }

    private fun parseTime(text: String): Value? =
        try {
            TimeValue(LocalTime.parse(text))
        } catch (error: DateTimeParseException) {
            null
        }
}
