package org.compiler.catalog

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
    return addTable(Table(table, updatedColumns))
}

fun Catalog.addColumn(table: String, column: Column): Catalog {
    val current = table(table) ?: return this
    if (current.column(column.name) != null) return this
    return addTable(Table(table, current.columns + column))
}

fun Catalog.dropColumn(table: String, column: String): Catalog {
    val current = table(table) ?: return this
    if (current.column(column) == null) return this
    return addTable(Table(table, current.columns.filterNot { it.name == column }))
}

fun Catalog.addTable(table: Table): Catalog {
    if (tables[table.name] === table) return this
    return Catalog(tables + (table.name to table))
}

fun Catalog.dropTable(name: String): Catalog {
    if (name !in tables) return this
    return Catalog(tables - name)
}
