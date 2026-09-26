package org.compiler.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que se le puede exigir a una columna.
 *
 * Cinco son un si o un no; dos cargan datos. Es una jerarquia sellada y no un
 * enum porque con un enum mas campos opcionales, `Constraint(NOT_NULL, table =
 * "users")` compilaria y no significa nada.
 *
 * El @SerialName de cada una es lo que sale en el .json de esquema, bajo la
 * clave "constraint".
 */
@Serializable
sealed interface Constraint

@Serializable
@SerialName("PRIMARY_KEY")
data object PrimaryKey : Constraint

@Serializable
@SerialName("NOT_NULL")
data object NotNull : Constraint

// Acepta nulos. Es lo que una columna hace por omision, asi que declararlo solo
// sirve para dejarlo explicito en el CREATE TABLE.
@Serializable
@SerialName("NULL")
data object Nullable : Constraint

@Serializable
@SerialName("UNIQUE")
data object Unique : Constraint

@Serializable
@SerialName("AUTOINCREMENT")
data object AutoIncrement : Constraint

@Serializable
@SerialName("FOREIGN_KEY")
data class ForeignKey(val table: String, val column: String) : Constraint

/**
 * El valor por omision, guardado como TEXTO.
 *
 * No guarda un Value porque tres de ellos envuelven BigDecimal, LocalDate y
 * LocalTime, que no traen serializador. Ademas el archivo queda legible: se lee
 * "2026-01-01" y no una marca de tiempo.
 *
 * El texto se convierte con ValueCodec.decode al cargar el catalogo, usando el
 * tipo de la columna. Si no cuadra, el error dice que columna y que tipo
 * esperaba, que es mejor mensaje del que daria un fallo al deserializar.
 */
@Serializable
@SerialName("DEFAULT")
data class Default(val value: String) : Constraint
