package org.compiler.catalog

/**
 * Un indice sobre UNA columna de una tabla.
 *
 * No guarda las entradas: esas viven en el `.idx` y se cargan solo cuando una
 * consulta las necesita. Esto es la ficha del catalogo, que si se carga siempre.
 *
 * `auto` distingue los que nacen de un PRIMARY KEY o un UNIQUE de los que escribio
 * alguien con CREATE INDEX. Los automaticos no se pueden borrar a mano: borrarlos
 * dejaria la restriccion sin su indice, y nada volveria a crearlo.
 */
class Index(
    val name: String,
    val table: String,
    val column: String,
    val auto: Boolean
) {
    // El archivo se llama como el indice, no como la tabla: dos tablas pueden
    // tener un indice sobre una columna con el mismo nombre.
    val fileName: String get() = "$name.$EXTENSION"

    companion object {
        const val EXTENSION = "idx"

        // Los nombres que genera el catalogo solo. El ticket 8.5 impide escribirlos
        // a mano, para que un CREATE INDEX no choque con uno automatico.
        fun primaryKeyName(table: String) = "pk_$table"

        fun uniqueName(table: String, column: String) = "uq_${table}_$column"

        fun isReserved(name: String): Boolean = name.startsWith("pk_") || name.startsWith("uq_")
    }
}
