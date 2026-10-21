package org.compiler.engine

import org.compiler.catalog.Column
import org.compiler.catalog.Index
import org.compiler.catalog.addIndex
import org.compiler.catalog.dropIndex
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
import org.compiler.frontend.ast.models.CreateIndex
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DropIndex
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

    /**
     * Ejecuta la sentencia y devuelve, en una linea, que cambio.
     *
     * Esa linea es lo que la interfaz muestra donde un SELECT mostraria su
     * rejilla. Sale de aqui y no de la GUI porque las cuentas —cuantas filas
     * entraron, cuantas cambiaron— solo se saben en el momento de aplicarlas.
     */
    fun execute(statement: Statement): String = when (statement) {
        is Insert -> insert(statement)
        is Update -> update(statement)
        is Delete -> delete(statement)
        is CreateTable -> create(statement)
        is AlterTableAdd -> addColumn(statement)
        is AlterTableDrop -> dropColumn(statement)
        is DropTable -> drop(statement)
        is CreateIndex -> createIndex(statement)
        is DropIndex -> dropIndex(statement)

        // Las consultas las ejecuta el motor, no el escritor.
        is Query -> ""
    }

    // ── DML ────────────────────────────────────────────────────────────────

    private fun insert(statement: Insert): String {
        val table = session.catalog.table(statement.table) ?: return NOTHING
        val named = statement.columns?.mapNotNull { table.column(it) } ?: table.columns
        val rows = session.rowsOf(table.name)
        var added = 0

        statement.rows.forEach { values ->
            val evaluated = values.map { engine.valueOf(it) }
            val row = rules.buildRow(table, named, evaluated, statement.location)
                ?: return rowsOf(added, "insertada")

            rows.add(row)
            added++
            session.markModified(table.name)
        }
        return rowsOf(added, "insertada")
    }

    private fun update(statement: Update): String {
        val table = session.catalog.table(statement.table) ?: return NOTHING
        val rows = session.rowsOf(table.name)
        var touched = 0

        rows.indices.forEach { index ->
            val current = rows[index]
            val context = RowContext.of(current)
            if (!matches(statement.where, context)) return@forEach

            val changed = assign(table, current, statement.assignments, context)
            val ready = rules.prepare(table, changed, statement.location)
                ?: return rowsOf(touched, "modificada")
            if (!rules.validate(table, ready, statement.location, excluding = index)) {
                return rowsOf(touched, "modificada")
            }

            rows[index] = ready
            touched++
        }

        if (touched > 0) session.markModified(table.name)
        return rowsOf(touched, "modificada")
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
    private fun delete(statement: Delete): String {
        val table = session.catalog.table(statement.table) ?: return NOTHING
        val rows = session.rowsOf(table.name)

        val doomed = rows.indices.filter { matches(statement.where, RowContext.of(rows[it])) }
        if (doomed.isEmpty()) return rowsOf(0, "borrada")

        doomed.forEach { index ->
            val blocker = rules.referencesTo(table, rows[index]) ?: return@forEach
            val key = table.primaryKey ?: table.columns.first()
            val value = rows[index].values[table.indexOf(key.name)]

            report(
                statement.location,
                "no se puede borrar ${table.name}.${key.name} = ${value.display()}: " +
                    "lo referencian ${blocker.second} filas de ${blocker.first}"
            )
            return rowsOf(0, "borrada")
        }

        val survivors = rows.filterIndexed { index, _ -> index !in doomed }
        rows.clear()
        rows.addAll(survivors)
        session.markModified(table.name)
        return rowsOf(doomed.size, "borrada")
    }

    // ── DDL ────────────────────────────────────────────────────────────────

    // La tabla nace con los indices que sus restricciones implican: el propio
    // Table los deriva de PRIMARY KEY y UNIQUE.
    private fun create(statement: CreateTable): String {
        val table = Table(statement.name, statement.columns.map { it.toColumn() })

        session.applyCatalog(session.catalog.addTable(table))
        session.createTable(table)
        return "table creada"
    }

    // El indice se marca como modificado para que el volcado lo escriba: la tabla
    // existe y sus filas no cambiaron, pero su .idx todavia no.
    private fun createIndex(statement: CreateIndex): String {
        val column = statement.columns.singleOrNull() ?: return NOTHING
        val index = Index(statement.name, statement.table, column, auto = false)

        session.applyCatalog(session.catalog.addIndex(index))
        session.markModified(statement.table)
        return "indice creado sobre '${statement.table}.$column'"
    }

    private fun dropIndex(statement: DropIndex): String {
        val index = session.catalog.index(statement.name) ?: return NOTHING

        session.applyCatalog(session.catalog.dropIndex(statement.name))
        session.dropIndexFile(index)
        return "indice eliminado"
    }

    /**
     * La columna nueva se llena con su valor por omision, no con nulo. Es lo que
     * permite que la fase 5 acepte agregar una columna NOT NULL si trae DEFAULT.
     */
    private fun addColumn(statement: AlterTableAdd): String {
        val table = session.catalog.table(statement.table) ?: return NOTHING
        val column = statement.column.toColumn()

        // Las filas se cargan ANTES de cambiar el catalogo: si no, se decodificaria
        // el archivo viejo con el esquema nuevo y la columna aparecería dos veces.
        val rows = session.rowsOf(table.name)
        session.applyCatalog(session.catalog.addColumn(table.name, column))

        val filler = rules.fillerFor(column)
        rows.replaceAll { Row(it.values + filler) }
        session.markModified(table.name)
        return "columna '${column.name}' agregada"
    }

    private fun dropColumn(statement: AlterTableDrop): String {
        val table = session.catalog.table(statement.table) ?: return NOTHING
        val position = table.indexOf(statement.column)
        if (position < 0) return NOTHING

        val rows = session.rowsOf(table.name)
        session.applyCatalog(session.catalog.dropColumn(table.name, statement.column))

        rows.replaceAll { row -> Row(row.values.filterIndexed { index, _ -> index != position }) }
        session.markModified(table.name)
        return "columna '${statement.column}' eliminada"
    }

    // Los .idx de la tabla se van con ella: un indice es parte de su esquema, y
    // dejarlos seria dejar metadatos huerfanos.
    private fun drop(statement: DropTable): String {
        session.catalog.table(statement.table)?.indexes?.forEach { session.dropIndexFile(it) }

        session.applyCatalog(session.catalog.dropTable(statement.table))
        session.forgetTable(statement.table)
        return "table eliminada"
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

    private companion object {
        // Lo que se reporta cuando la sentencia no llego a aplicarse. Nunca se
        // muestra: si una sentencia no se aplico es porque fallo, y entonces la
        // sesion descarta su reporte.
        const val NOTHING = "sin cambios"

        /**
         * "1 row insertada" y "2 rows insertadas".
         *
         * El numero manda sobre las dos palabras, no solo sobre la primera: la
         * linea la lee una persona, no un log.
         */
        fun rowsOf(count: Int, participle: String): String {
            val plural = if (count == 1) "" else "s"
            return "$count row$plural $participle$plural"
        }
    }
}

private fun ColumnDefinition.toColumn() = Column(name, type, constraints)
