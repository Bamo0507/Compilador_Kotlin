package org.compiler.frontend.semantic

import org.compiler.catalog.Catalog
import org.compiler.catalog.Column
import org.compiler.catalog.Index
import org.compiler.catalog.Table
import org.compiler.catalog.addColumn
import org.compiler.catalog.addIndex
import org.compiler.catalog.addTable
import org.compiler.catalog.dropColumn
import org.compiler.catalog.dropIndex
import org.compiler.catalog.dropTable
import org.compiler.frontend.ast.models.AlterTableAdd
import org.compiler.frontend.ast.models.AlterTableDrop
import org.compiler.frontend.ast.models.ColumnDefinition
import org.compiler.frontend.ast.models.CreateIndex
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.DropIndex
import org.compiler.frontend.ast.models.DropTable
import org.compiler.frontend.ast.models.Statement

/**
 * El catalogo como queda DESPUES de una sentencia.
 *
 * Un script puede crear una tabla y usarla en la linea siguiente, asi que el
 * analisis tiene que ver la base como la vera el motor y no como esta en disco.
 * Las sentencias que no son DDL lo dejan igual.
 */
fun Catalog.after(statement: Statement): Catalog = when (statement) {
    is CreateTable ->
        if (table(statement.name) != null) this
        else addTable(Table(statement.name, statement.columns.map { it.toColumn() }))

    is DropTable -> dropTable(statement.table)
    is AlterTableAdd -> addColumn(statement.table, statement.column.toColumn())
    is AlterTableDrop -> dropColumn(statement.table, statement.column)

    // Una sola columna: el ticket 8.5 reporta las demas, y aqui se toma la unica
    // que puede llegar sin error.
    is CreateIndex -> statement.columns.singleOrNull()
        ?.let { addIndex(Index(statement.name, statement.table, it, auto = false)) } ?: this

    is DropIndex -> dropIndex(statement.name)
    else -> this
}

private fun ColumnDefinition.toColumn() = Column(name, type, constraints)
