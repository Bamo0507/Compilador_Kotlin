package org.compiler.catalog

import org.compiler.types.Type

class Catalog(tables: Map<String, Table>) {
    val tables: Map<String, Table> = tables.toMap()

    fun table(name: String): Table? = tables[name]
}

class Table(
    val name: String,
    columns: List<Column>
) {
    // El orden coincide con el header del CSV y con INSERT sin columnas nombradas.
    val columns: List<Column> = columns.toList()

    fun column(name: String): Column? = columns.firstOrNull { it.name == name }

    fun indexOf(name: String): Int = columns.indexOfFirst { it.name == name }

    val primaryKey: Column? get() = columns.firstOrNull { it.hasConstraint<PrimaryKey>() }
}

class Column(
    val name: String,
    val type: Type,
    constraints: List<Constraint>
) {
    val constraints: List<Constraint> = constraints.toList()

    inline fun <reified R : Constraint> hasConstraint(): Boolean = constraints.any { it is R }

    // PRIMARY KEY implica NOT NULL aunque no se declare por separado.
    val nullable: Boolean get() = !hasConstraint<NotNull>() && !hasConstraint<PrimaryKey>()

    val reference: ForeignKey? get() = constraints.filterIsInstance<ForeignKey>().firstOrNull()
}
