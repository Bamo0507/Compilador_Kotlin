package org.compiler.semantic

import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Catalog
import org.compiler.catalog.Column
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Table
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.Aggregate
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.DerivedSource
import org.compiler.frontend.ast.models.Exists
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.InSubquery
import org.compiler.frontend.ast.models.IsNull
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.Unary
import org.compiler.diagnostics.Severity
import org.compiler.frontend.semantic.NameResolver
import org.compiler.frontend.semantic.SqlChecker
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.storage.DataDirectory
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * La base de prueba y el camino parser -> AST -> resolutor, que comparten las tres
 * clases de la fase 4.
 *
 * `city` esta en las dos tablas a proposito: es lo que hace ambiguo un nombre sin
 * calificar cuando se las une.
 */
val users = Table("users", listOf(
    Column("id", IntType, listOf(PrimaryKey, AutoIncrement)),
    Column("name", VarcharType(80), listOf(NotNull)),
    Column("city", VarcharType(40), emptyList())
))

val posts = Table("posts", listOf(
    Column("uid", IntType, listOf(ForeignKey("users", "id"))),
    Column("title", VarcharType(120), listOf(NotNull, Default("sin titulo"))),
    Column("city", VarcharType(40), emptyList())
))

val catalog = Catalog(mapOf("users" to users, "posts" to posts))

class Resolved(val global: Scope, val script: Script, val diagnostics: Diagnostics) {
    val errors: List<String> get() = diagnostics.all().map { it.message }
    val failures: List<String> get() = diagnostics.all().filter { it.severity == Severity.ERROR }.map { it.message }
    val warnings: List<String> get() = diagnostics.warnings().map { it.message }
    val queries: List<Query> get() = script.statements.filterIsInstance<Query>()
    val query: Query get() = queries.single()
    val scope: Scope get() = assertNotNull(query.scope)
}

// Escribe el CSV con el encabezado que el esquema espera, que es el caso sano.
fun healthy(root: File, vararg tables: Table) = tables.forEach { table ->
    File(root, "${table.name}.csv").writeText(table.columns.joinToString(",") { it.name } + "\r\n")
}

fun resolve(root: File, sql: String): Resolved {
    val diagnostics = Diagnostics()
    val tree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics), "No parsea: $sql")
    val script = SqlAstBuilder(diagnostics).visit(tree) as Script
    assertFalse(diagnostics.hasErrors, "El AST reporto: ${diagnostics.all()}")

    val global = NameResolver(catalog, DataDirectory(root), diagnostics).resolve(script)
    return Resolved(global, script, diagnostics)
}

// Resuelve y exige que no se haya reportado nada.
fun clean(root: File, sql: String): Resolved {
    val resolved = resolve(root, sql)
    kotlin.test.assertEquals(emptyList(), resolved.errors, "Reporto: $sql")
    return resolved
}

// Las dos etapas semanticas, que es como corren de verdad.
fun checked(root: File, sql: String): Resolved {
    val resolved = resolve(root, sql)
    SqlChecker(catalog, DataDirectory(root), resolved.diagnostics).check(resolved.script)
    return resolved
}

// Un CSV con encabezado y filas, para las reglas que necesitan saber si la tabla
// ya tiene datos.
fun withRows(root: File, table: Table, rows: Int) {
    val header = table.columns.joinToString(",") { it.name }
    val row = table.columns.joinToString(",") { "x" }
    File(root, "${table.name}.csv").writeText(header + "\r\n" + (row + "\r\n").repeat(rows))
}

// ── Para hurgar en el AST ya resuelto ──────────────────────────────────────

// Las referencias de ESTA consulta, sin bajar a sus subconsultas: una subconsulta
// tiene las suyas, y mezclarlas haria que un test de nivel mintiera.
fun Query.ownReferences(): List<ColumnReference> = clauses().flatMap { it.references() }

fun Query.reference(name: String): ColumnReference =
    assertNotNull(ownReferences().firstOrNull { it.name == name }, "sin referencia a '$name'")

// Las subconsultas directas, incluidas las de las tablas derivadas del FROM.
fun Query.subqueries(): List<Query> =
    (listOf(source) + joins.map { it.source }).filterIsInstance<DerivedSource>().map { it.query } +
        clauses().flatMap { it.queries() }

private fun Query.clauses(): List<Expression> =
    joins.map { it.condition } +
        selection.filterIsInstance<SelectExpression>().map { it.expression } +
        listOfNotNull(where, having) +
        groupBy +
        orderBy.map { it.expression }

private fun Expression.references(): List<ColumnReference> = when (this) {
    is ColumnReference -> listOf(this)
    is Binary -> left.references() + right.references()
    is Unary -> operand.references()
    is IsNull -> operand.references()
    is InList -> operand.references() + values.flatMap { it.references() }
    is InSubquery -> operand.references()
    is Aggregate -> argument?.references().orEmpty()
    else -> emptyList()
}

private fun Expression.queries(): List<Query> = when (this) {
    is Query -> listOf(this)
    is Exists -> listOf(query)
    is InSubquery -> listOf(query)
    is Binary -> left.queries() + right.queries()
    is Unary -> operand.queries()
    is IsNull -> operand.queries()
    is InList -> operand.queries() + values.flatMap { it.queries() }
    is Aggregate -> argument?.queries().orEmpty()
    else -> emptyList()
}
