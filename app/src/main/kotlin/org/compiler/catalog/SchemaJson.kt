package org.compiler.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DateType
import org.compiler.types.DecimalType
import org.compiler.types.ErrorType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.NullType
import org.compiler.types.TextType
import org.compiler.types.TimeType
import org.compiler.types.Type
import org.compiler.types.VarcharType

@Serializable
data class SchemaDefinition(val columns: Map<String, SchemaColumn>)

@Serializable
data class SchemaColumn(
    val type: String,
    val constraints: List<Constraint> = emptyList()
) {
    // Conservamos el texto original para que el cargador pueda reportar tipos desconocidos.
    fun toColumn(name: String): Column? =
        typeFromText(type)?.let { Column(name, it, constraints) }
}

object SchemaJson {
    fun encode(table: Table): String {
        val schema = SchemaDefinition(table.columns.associate { column ->
            column.name to SchemaColumn(column.type.name, column.constraints)
        })
        return catalogJson.encodeToString(schema)
    }

    fun decode(source: String): SchemaDefinition = catalogJson.decodeFromString(source)
}

private val parameterizedType = Regex(
    "([A-Z]+)\\s*\\(\\s*([0-9]+)\\s*(?:,\\s*([0-9]+)\\s*)?\\)",
    RegexOption.IGNORE_CASE
)

fun typeFromText(text: String): Type? {
    val normalized = text.trim().uppercase()
    when (normalized) {
        "INT", "INTEGER" -> return IntType
        "FLOAT" -> return FloatType
        "TEXT" -> return TextType
        "DATE" -> return DateType
        "TIME" -> return TimeType
        "BOOLEAN" -> return BooleanType
        "NULL" -> return NullType
        "<ERROR>" -> return ErrorType
    }

    val match = parameterizedType.matchEntire(normalized) ?: return null
    val first = match.groupValues[2].toIntOrNull() ?: return null
    val secondText = match.groupValues[3]
    if (first <= 0) return null

    return when (match.groupValues[1]) {
        "CHAR" -> if (secondText.isEmpty()) CharType(first) else null
        "VARCHAR" -> if (secondText.isEmpty()) VarcharType(first) else null
        "DECIMAL", "NUMERIC" -> {
            val scale = secondText.toIntOrNull() ?: return null
            if (scale <= first) DecimalType(first, scale) else null
        }
        else -> null
    }
}
