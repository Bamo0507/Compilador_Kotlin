package org.compiler.frontend.ast

import org.compiler.frontend.ast.models.Aggregate
import org.compiler.frontend.ast.models.AlterTableAdd
import org.compiler.frontend.ast.models.AlterTableDrop
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnDefinition
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.CreateIndex
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DerivedSource
import org.compiler.frontend.ast.models.DropIndex
import org.compiler.frontend.ast.models.DropTable
import org.compiler.frontend.ast.models.Exists
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FromSource
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.InSubquery
import org.compiler.frontend.ast.models.Insert
import org.compiler.frontend.ast.models.IsNull
import org.compiler.frontend.ast.models.Join
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.OrderCriterion
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.SelectAll
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.SelectItem
import org.compiler.frontend.ast.models.SelectTableAll
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.TableSource
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.ast.models.Unary
import org.compiler.frontend.ast.models.Update

/**
 * El AST propio, en la forma que dibuja el lienzo.
 *
 * Es el hermano de `ParseTreeView`, y la comparacion entre los dos arboles es la
 * que hace visible lo que el AST aporta: el de ANTLR tiene un nivel por regla de
 * la gramatica, y este tiene un nodo por cosa que existe en el lenguaje.
 *
 * Las expresiones TRIVIALES se colapsan en una sola etiqueta: un literal o una
 * columna no abren rama, porque el AST de una consulta con subconsultas ya crece
 * bastante hacia abajo sin eso.
 */
fun Script.toTreeView(): TreeNodeView = TreeNodeView(
    label = "script",
    detail = countOf(statements.size, "sentencia", "sentencias"),
    children = statements.map { it.toTreeView() }
)

fun Statement.toTreeView(): TreeNodeView = when (this) {
    is Query -> queryView(this)
    is Insert -> TreeNodeView(
        label = "insert",
        detail = table,
        children = listOfNotNull(
            columns?.let { TreeNodeView("columnas", it.joinToString(", ")) }
        ) + rows.mapIndexed { index, values ->
            TreeNodeView("fila ${index + 1}", children = values.map { it.toTreeView() })
        }
    )

    is Update -> TreeNodeView(
        label = "update",
        detail = table,
        children = assignments.map { it.toTreeView() } + whereView(where)
    )

    is Delete -> TreeNodeView(label = "delete", detail = table, children = whereView(where))

    is CreateTable -> TreeNodeView(
        label = "create table",
        detail = name,
        children = columns.map { it.toTreeView() }
    )

    is AlterTableAdd -> TreeNodeView("alter table add", table, listOf(column.toTreeView()))
    is AlterTableDrop -> TreeNodeView("alter table drop", "$table.$column")
    is DropTable -> TreeNodeView("drop table", table)
    is CreateIndex -> TreeNodeView("create index", "$name on $table(${columns.joinToString(", ")})")
    is DropIndex -> TreeNodeView("drop index", name)
}

// ── La consulta ────────────────────────────────────────────────────────────

/**
 * Las clausulas van en el orden en que SQL las EVALUA, no en el que se escriben.
 *
 * Es el mismo orden que sigue el motor, asi que el arbol se lee como la lista de
 * pasos que va a ejecutar.
 */
// Se nombra la funcion y no el receptor porque Query es Statement Y Expression a la
// vez: `query.toTreeView()` seria ambiguo.
private fun queryView(query: Query): TreeNodeView = TreeNodeView(
    label = "select",
    detail = if (query.distinct) "distinct" else null,
    children = buildList {
        add(TreeNodeView("from", children = listOf(query.source.toTreeView())))
        query.joins.forEach { add(it.toTreeView()) }
        addAll(whereView(query.where))

        if (query.groupBy.isNotEmpty()) {
            add(TreeNodeView("group by", children = query.groupBy.map { it.toTreeView() }))
        }
        query.having?.let { add(TreeNodeView("having", children = listOf(it.toTreeView()))) }

        add(TreeNodeView("columnas", children = query.selection.map { it.toTreeView() }))

        if (query.orderBy.isNotEmpty()) {
            add(TreeNodeView("order by", children = query.orderBy.map { it.toTreeView() }))
        }
        query.limit?.let { add(TreeNodeView("limit", it.toString())) }
    }
)

private fun whereView(where: Expression?): List<TreeNodeView> =
    if (where == null) emptyList() else listOf(TreeNodeView("where", children = listOf(where.toTreeView())))

private fun FromSource.toTreeView(): TreeNodeView = when (this) {
    is TableSource -> TreeNodeView("tabla", listOfNotNull(table, alias).joinToString(" as "))
    is DerivedSource -> TreeNodeView("subconsulta", "as $alias", listOf(queryView(query)))
}

private fun Join.toTreeView(): TreeNodeView =
    TreeNodeView("join", children = listOf(source.toTreeView(), TreeNodeView("on", children = listOf(condition.toTreeView()))))

private fun SelectItem.toTreeView(): TreeNodeView = when (this) {
    is SelectAll -> TreeNodeView("*")
    is SelectTableAll -> TreeNodeView("$qualifier.*")
    is SelectExpression ->
        if (alias == null) expression.toTreeView()
        else TreeNodeView("as", alias, listOf(expression.toTreeView()))
}

private fun OrderCriterion.toTreeView(): TreeNodeView =
    TreeNodeView(if (descending) "desc" else "asc", children = listOf(expression.toTreeView()))

private fun Assignment.toTreeView(): TreeNodeView =
    TreeNodeView("set", column, listOf(value.toTreeView()))

private fun ColumnDefinition.toTreeView(): TreeNodeView = TreeNodeView(
    label = name,
    detail = (listOf(type.name) + constraints.map { constraintLabel(it) }).joinToString(" ")
)

// ── Las expresiones ────────────────────────────────────────────────────────

/**
 * Un literal y una columna son HOJAS con su valor en la etiqueta.
 *
 * Sin eso, `id = 1` serian tres niveles para decir algo que cabe en uno, y el
 * arbol de una consulta con subconsultas se volveria ilegible a fuerza de nodos
 * que no aportan.
 */
fun Expression.toTreeView(): TreeNodeView = when (this) {
    is Literal -> TreeNodeView(value.display(), literalType.name)
    is ColumnReference -> TreeNodeView(listOfNotNull(qualifier, name).joinToString("."), "columna")

    is Binary -> TreeNodeView(operator.symbol, children = listOf(left.toTreeView(), right.toTreeView()))
    is Unary -> TreeNodeView(operator.symbol, children = listOf(operand.toTreeView()))

    is IsNull -> TreeNodeView(if (negated) "is not null" else "is null", children = listOf(operand.toTreeView()))

    is InList -> TreeNodeView(
        label = if (negated) "not in" else "in",
        children = listOf(operand.toTreeView()) + values.map { it.toTreeView() }
    )

    is InSubquery -> TreeNodeView(
        label = if (negated) "not in" else "in",
        children = listOf(operand.toTreeView(), queryView(query))
    )

    is Exists -> TreeNodeView("exists", children = listOf(queryView(query)))

    is Aggregate -> TreeNodeView(
        label = function.label,
        detail = if (distinct) "distinct" else null,
        children = listOfNotNull(argument?.toTreeView())
    )

    // Una subconsulta trae su propio arbol entero debajo.
    is Query -> queryView(this)
}

// ── Apoyo ──────────────────────────────────────────────────────────────────

private fun constraintLabel(constraint: org.compiler.catalog.Constraint): String = when (constraint) {
    org.compiler.catalog.PrimaryKey -> "PRIMARY KEY"
    org.compiler.catalog.NotNull -> "NOT NULL"
    org.compiler.catalog.Nullable -> "NULL"
    org.compiler.catalog.Unique -> "UNIQUE"
    org.compiler.catalog.AutoIncrement -> "AUTOINCREMENT"
    is org.compiler.catalog.ForeignKey -> "-> ${constraint.table}.${constraint.column}"
    is org.compiler.catalog.Default -> "DEFAULT ${constraint.value}"
}

private fun countOf(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"
