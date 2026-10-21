package org.compiler.gui.components

import org.compiler.engine.ResultSet
import org.compiler.types.DecimalType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.NullValue
import org.compiler.types.Type


class GridColumn(
    val name: String,
    val type: String,

    // Los numeros se alinean a la derecha, para que las unidades queden una
    // debajo de otra y se puedan comparar de un vistazo.
    val numeric: Boolean
)

class GridCell(val text: String, val isNull: Boolean)

class Grid(
    val columns: List<GridColumn>,
    val rows: List<List<GridCell>>
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    val widest: List<String> = columns.indices.map { position ->
        val cells = rows.asSequence().mapNotNull { it.getOrNull(position)?.text }
        (cells + sequenceOf(columns[position].name, columns[position].type))
            .maxByOrNull { it.length }
            .orEmpty()
    }
}

// El texto de una celda mas largo que esto se recorta al dibujar, con elipsis. El
// tope es de la COLUMNA, no del texto: la celda guarda su texto completo.
const val MAX_CELL_CHARS = 40

fun gridOf(result: ResultSet): Grid {
    val columns = result.columns.mapIndexed { position, column ->
        GridColumn(
            name = column.name ?: "columna ${position + 1}",
            type = column.type.name,
            numeric = isNumeric(column.type)
        )
    }

    val rows = result.rows.map { row ->
        row.values.map { value ->
            // NullValue.display() ya dice "NULL"; la bandera es lo que deja
            // pintarlo en gris y cursiva, distinto de un texto que diga "NULL".
            GridCell(text = value.display(), isNull = value == NullValue)
        }
    }
    return Grid(columns, rows)
}

// El tope se aplica al MEDIR, asi una columna con un TEXT larguisimo no empuja a
// las demas fuera de la pantalla.
fun measuredText(widest: String): String =
    if (widest.length <= MAX_CELL_CHARS) widest else widest.take(MAX_CELL_CHARS)

fun rowCountOf(rows: Int): String = if (rows == 1) "1 row" else "$rows rows"

private fun isNumeric(type: Type): Boolean =
    type == IntType || type == FloatType || type is DecimalType
