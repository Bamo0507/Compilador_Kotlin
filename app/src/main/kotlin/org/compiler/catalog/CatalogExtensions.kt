package org.compiler.catalog

fun Catalog.addIndex(index: Index): Catalog {
    val current = table(index.table) ?: return this
    if (current.indexes.any { it.name == index.name }) return this
    return addTable(Table(index.table, current.columns, current.indexes + index))
}

fun Catalog.dropIndex(name: String): Catalog {
    val owner = indexes().firstOrNull { it.name == name } ?: return this
    val current = table(owner.table) ?: return this
    return addTable(Table(owner.table, current.columns, current.indexes.filterNot { it.name == name }))
}

/**
 * Los indices que las restricciones de una tabla implican: uno por la clave
 * primaria y uno por cada UNIQUE.
 *
 * Lo que gana no son las escrituras sino las consultas: `WHERE id = 7` usa un
 * indice sin que nadie haya escrito un CREATE INDEX, y la validacion de clave
 * foranea pasa de cargar la tabla referida a un salto.
 *
 * Lo llama el propio `Table`, asi que no hay forma de construir una con PRIMARY
 * KEY y sin su indice.
 */
fun automaticIndexesOf(name: String, columns: List<Column>): List<Index> = columns.mapNotNull { column ->
    when {
        column.hasConstraint<PrimaryKey>() -> Index(Index.primaryKeyName(name), name, column.name, auto = true)
        column.hasConstraint<Unique>() -> Index(Index.uniqueName(name, column.name), name, column.name, auto = true)
        else -> null
    }
}

fun Catalog.addConstraint(table: String, column: String, constraint: Constraint): Catalog {
    val currentTable = table(table) ?: return this
    val currentColumn = currentTable.column(column) ?: return this
    if (constraint in currentColumn.constraints) return this

    val updatedColumn = Column(
        currentColumn.name,
        currentColumn.type,
        currentColumn.constraints + constraint
    )
    val updatedColumns = currentTable.columns.map {
        if (it.name == column) updatedColumn else it
    }
    return addTable(Table(table, updatedColumns, currentTable.indexes))
}

fun Catalog.addColumn(table: String, column: Column): Catalog {
    val current = table(table) ?: return this
    if (current.column(column.name) != null) return this
    return addTable(Table(table, current.columns + column, current.indexes))
}

// Los indices sobre la columna se van con ella. La regla del ticket 8.5 obliga a
// borrarlos antes, asi que esto solo alcanza a los automaticos.
fun Catalog.dropColumn(table: String, column: String): Catalog {
    val current = table(table) ?: return this
    if (current.column(column) == null) return this
    return addTable(
        Table(
            table,
            current.columns.filterNot { it.name == column },
            current.indexes.filterNot { it.column == column }
        )
    )
}

fun Catalog.addTable(table: Table): Catalog {
    if (tables[table.name] === table) return this
    return Catalog(tables + (table.name to table))
}

fun Catalog.dropTable(name: String): Catalog {
    if (name !in tables) return this
    return Catalog(tables - name)
}
