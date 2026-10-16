package org.compiler.frontend.semantic.symbols

import org.compiler.catalog.Table
import org.compiler.models.LexemeLocation
import org.compiler.types.Type

/**
 * La ficha de un nombre visible. Es la unidad de la tabla de simbolos.
 *
 * Puede ser una tabla del catalogo, un alias del FROM, o una columna de alguna de
 * las tablas que la consulta tiene a la vista.
 */
data class Symbol(
    val name: String,
    val kind: DeclarationKind,
    val type: Type,

    // Donde aparece el nombre en el script. Para una tabla del catalogo no hay
    // posicion real, asi que se usa el inicio.
    val location: LexemeLocation,

    val scopeName: String,

    // De que tabla real viene. Para `u.name` con `FROM users u`, es "users".
    // Null en una tabla o un alias, que no vienen de ninguna otra.
    val sourceTable: String? = null,

    // Posicion dentro de la fila de ESTE nivel. En una columna es la suya; en un
    // origen del FROM, donde arrancan las suyas dentro de la fila concatenada de
    // un JOIN. Vale -1 en lo que no ocupa lugar en la fila.
    val index: Int = -1,

    // La tabla que un origen del FROM pone a la vista. Puede venir del catalogo o
    // ser el esquema de salida de una subconsulta. Null en lo que no es un origen.
    val table: Table? = null
)
