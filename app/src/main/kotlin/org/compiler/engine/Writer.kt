package org.compiler.engine

import org.compiler.catalog.Column
import org.compiler.catalog.Table
import org.compiler.catalog.addColumn
import org.compiler.catalog.addTable
import org.compiler.catalog.dropColumn
import org.compiler.catalog.dropTable
import org.compiler.diagnostics.CompilerError
import org.compiler.frontend.ast.models.AlterTableAdd
import org.compiler.frontend.ast.models.AlterTableDrop
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.ColumnDefinition
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DropTable
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.Insert
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Update
import org.compiler.models.LexemeLocation
import org.compiler.types.BoolValue

/**
 * Las sentencias que cambian algo.
 *
 * Nada toca el disco aqui: todo se aplica sobre las filas y el catalogo de la
 * sesion, y el volcado ocurre una sola vez al final. Si una sentencia falla, lo
 * que quedo en memoria se descarta sin que el directorio se haya enterado.
 */
class Writer(private val session: Session) {

    private val engine = Engine(session)
    private val rules = WriteRules(session)

    fun execute(statement: Statement) {
        when (statement) {
            is Insert -> insert(statement)
            is Update -> update(statement)
            is Delete -> delete(statement)
            is CreateTable -> create(statement)
            is AlterTableAdd -> addColumn(statement)
            is AlterTableDrop -> dropColumn(statement)
            is DropTable -> drop(statement)

            // Las consultas las ejecuta el motor, no el escritor.
            is Query -> {}
        }
    }

    // ── DML ────────────────────────────────────────────────────────────────

    private fun insert(statement: Insert) {
        val table = session.catalog.table(statement.table) ?: return
        val named = statement.columns?.mapNotNull { table.column(it) } ?: table.columns
        val rows = session.rowsOf(table.name)

        statement.rows.forEach { values ->
            val evaluated = values.map { engine.valueOf(it) }
            val row = rules.buildRow(table, named, evaluated, statement.location) ?: return

            rows.add(row)
            session.markModified(table.name)
        }
    }

    private fun update(statement: Update) {
        val table = session.catalog.table(statement.table) ?: return
        val rows = session.rowsOf(table.name)
        var touched = false

        rows.indices.forEach { index ->
            val current = rows[index]
            val context = RowContext.of(current)
            if (!matches(statement.where, context)) return@forEach

            val changed = assign(table, current, statement.assignments, context)
            val ready = rules.prepare(table, changed, statement.location) ?: return
            if (!rules.validate(table, ready, statement.location, excluding = index)) return

            rows[index] = ready
            touched = true
        }

        if (touched) session.markModified(table.name)
    }

    private fun assign(table: Table, row: Row, assignments: List<Assignment>, context: RowContext): Row {
        val values = row.values.toMutableList()

        assignments.forEach { assignment ->
            val position = table.indexOf(assignment.column)
            if (position >= 0) values[position] = engine.valueOf(assignment.value, context)
        }
        return Row(values)
    }

    /**
     * Las llaves foraneas se revisan al reves: borrar un usuario es error si
     * alguna fila de otra tabla lo referencia.
     */
    private fun delete(statement: Delete) {
        val table = session.catalog.table(statement.table) ?: return
        val rows = session.rowsOf(table.name)

        val doomed = rows.indices.filter { matches(statement.where, RowContext.of(rows[it])) }
        if (doomed.isEmpty()) return

        doomed.forEach { index ->
            val blocker = rules.referencesTo(table, rows[index]) ?: return@forEach
            val key = table.primaryKey ?: table.columns.first()
            val value = rows[index].values[table.indexOf(key.name)]

            report(
                statement.location,
                "no se puede borrar ${table.name}.${key.name} = ${value.display()}: " +
                    "lo referencian ${blocker.second} filas de ${blocker.first}"
            )
            return
        }

        val survivors = rows.filterIndexed { index, _ -> index !in doomed }
        rows.clear()
        rows.addAll(survivors)
        session.markModified(table.name)
    }

    // ── DDL ────────────────────────────────────────────────────────────────

    private fun create(statement: CreateTable) {
        val table = Table(statement.name, statement.columns.map { it.toColumn() })

        session.applyCatalog(session.catalog.addTable(table))
        session.createTable(table)
    }

    /**
     * La columna nueva se llena con su valor por omision, no con nulo. Es lo que
     * permite que la fase 5 acepte agregar una columna NOT NULL si trae DEFAULT.
     */
    private fun addColumn(statement: AlterTableAdd) {
        val table = session.catalog.table(statement.table) ?: return
        val column = statement.column.toColumn()

        // Las filas se cargan ANTES de cambiar el catalogo: si no, se decodificaria
        // el archivo viejo con el esquema nuevo y la columna aparecería dos veces.
        val rows = session.rowsOf(table.name)
        session.applyCatalog(session.catalog.addColumn(table.name, column))

        val filler = rules.fillerFor(column)
        rows.replaceAll { Row(it.values + filler) }
        session.markModified(table.name)
    }

    private fun dropColumn(statement: AlterTableDrop) {
        val table = session.catalog.table(statement.table) ?: return
        val position = table.indexOf(statement.column)
        if (position < 0) return

        val rows = session.rowsOf(table.name)
        session.applyCatalog(session.catalog.dropColumn(table.name, statement.column))

        rows.replaceAll { row -> Row(row.values.filterIndexed { index, _ -> index != position }) }
        session.markModified(table.name)
    }

    private fun drop(statement: DropTable) {
        session.applyCatalog(session.catalog.dropTable(statement.table))
        session.forgetTable(statement.table)
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────

    // Sin WHERE la sentencia alcanza a todas las filas. La fase 5 ya aviso.
    private fun matches(condition: Expression?, context: RowContext): Boolean {
        if (condition == null) return true
        return engine.valueOf(condition, context) == BoolValue(true)
    }

    private fun report(location: LexemeLocation, message: String) {
        session.diagnostics.report(CompilerError.ExecutionError(location, message))
    }
}

private fun ColumnDefinition.toColumn() = Column(name, type, constraints)
