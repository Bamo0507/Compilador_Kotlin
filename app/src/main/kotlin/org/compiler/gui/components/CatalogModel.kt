package org.compiler.gui.components

import java.io.File
import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Catalog
import org.compiler.catalog.Column
import org.compiler.catalog.Constraint
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.Nullable
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Table
import org.compiler.catalog.Unique
import org.compiler.storage.DataDirectory


class CatalogEntry(
    val table: String,

    // El resumen de la derecha: "3 columns · 12 rows".
    val summary: String,

    val columns: List<CatalogColumn>,

    // Las tablas que el cargador no pudo leer salen marcadas con su mensaje. Un
    // directorio roto no se puede ver igual que uno sano.
    val problem: String? = null
) {
    val isBroken: Boolean get() = problem != null
}

class CatalogColumn(
    val name: String,
    val type: String,

    // "PRIMARY KEY, AUTOINCREMENT". Vacio si la columna no exige nada.
    val constraints: String,

    // La tabla y la columna a las que apunta su clave foranea, para que la flecha
    // sea clicable y salte hasta alla.
    val referenceTo: Pair<String, String>? = null,

    // El indice que la cubre, si hay. Es lo que la fase 8 agrego al esquema.
    val indexedBy: String? = null
)

fun catalogEntriesOf(
    catalog: Catalog,
    directory: DataDirectory,
    problems: Map<String, String> = emptyMap()
): List<CatalogEntry> {
    // Una tabla puede cargar A MEDIAS: si una de sus columnas tiene un tipo que
    // nadie reconoce, el resto si entra al catalogo. Igual va marcada, porque lo
    // que se ve no es lo que el .json dice.
    val loaded = catalog.tables.values.map { table ->
        CatalogEntry(
            table = table.name,
            summary = summaryOf(table.columns.size, rowCountOf(directory.csv(table.name))),
            columns = table.columns.map { columnOf(it, table) },
            problem = problems[table.name]
        )
    }

    // Y una tabla puede no cargar del todo: un .csv sin su .json no esta en el
    // catalogo, asi que entra solo por aqui.
    val missing = problems.keys
        .filter { it !in catalog.tables }
        .map { CatalogEntry(it, "no se pudo leer", emptyList(), problems[it]) }

    return (loaded + missing).sortedBy { it.table }
}

private fun columnOf(column: Column, table: Table) = CatalogColumn(
    name = column.name,
    type = column.type.name,
    constraints = column.constraints.joinToString(", ") { labelOf(it) },
    referenceTo = column.reference?.let { it.table to it.column },
    indexedBy = table.indexOn(column.name)?.name
)

private fun summaryOf(columns: Int, rows: Int): String =
    "$columns ${if (columns == 1) "column" else "columns"} · $rows ${if (rows == 1) "row" else "rows"}"

fun rowCountOf(file: File): Int {
    if (!file.isFile) return 0
    return file.useLines(Charsets.UTF_8) { lines -> (lines.count() - 1).coerceAtLeast(0) }
}

// La flecha de la FK se escribe aparte, en su columna, asi que aqui no se repite.
private fun labelOf(constraint: Constraint): String = when (constraint) {
    PrimaryKey -> "PRIMARY KEY"
    NotNull -> "NOT NULL"
    Nullable -> "NULL"
    Unique -> "UNIQUE"
    AutoIncrement -> "AUTOINCREMENT"
    is ForeignKey -> "-> ${constraint.table}.${constraint.column}"
    is Default -> "DEFAULT ${constraint.value}"
}
