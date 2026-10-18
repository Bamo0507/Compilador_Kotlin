package org.compiler.engine

import org.compiler.catalog.Catalog
import org.compiler.catalog.CatalogLoader
import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Column
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.SchemaJson
import org.compiler.catalog.Table
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.semantic.NameResolver
import org.compiler.frontend.semantic.SqlChecker
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.runtime.DbmsPipeline
import org.compiler.runtime.models.CompilationResult
import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DateType
import org.compiler.types.DecimalType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.TextType
import org.compiler.types.TimeType
import org.compiler.types.Value
import org.compiler.types.VarcharType
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Una base de prueba escrita a disco y el camino completo hasta el motor:
 * parser, AST, nombres, tipos y ejecucion.
 *
 * Se escribe de verdad porque el punto de la fase es que el motor lea lo que hay
 * en el directorio, no un objeto armado en memoria.
 */
val usersTable = Table("users", listOf(
    Column("id", IntType, listOf(PrimaryKey)),
    Column("name", VarcharType(80), listOf(NotNull)),
    Column("city", VarcharType(40), emptyList()),
    Column("age", IntType, emptyList())
))

val postsTable = Table("posts", listOf(
    Column("uid", IntType, listOf(ForeignKey("users", "id"))),
    Column("title", VarcharType(120), emptyList()),
    Column("views", IntType, emptyList())
))

// Una columna por tipo que se puede guardar. NULL y ERROR no tienen texto que los
// represente, asi que el nulo viaja como campo vacio.
val samplesTable = Table("samples", listOf(
    Column("whole", IntType, emptyList()),
    Column("loose", FloatType, emptyList()),
    Column("exact", DecimalType(10, 2), emptyList()),
    Column("fixed", CharType(3), emptyList()),
    Column("short", VarcharType(10), emptyList()),
    Column("long", TextType, emptyList()),
    Column("day", DateType, emptyList()),
    Column("hour", TimeType, emptyList()),
    Column("flag", BooleanType, emptyList())
))

// Para las reglas de escritura: una clave que se genera sola, un valor por
// omision, un largo que se puede pasar y una escala que se puede redondear.
val notesTable = Table("notes", listOf(
    Column("id", IntType, listOf(PrimaryKey, AutoIncrement)),
    Column("label", VarcharType(20), listOf(NotNull, Default("sin texto"))),
    Column("score", DecimalType(5, 2), emptyList())
))

val usersRows = listOf(
    listOf("1", "Ana", "GT", "30"),
    listOf("2", "Beto", "GT", "40"),
    listOf("3", "Cora", "MX", null),
    listOf("4", "Dani", null, "25")
)

val postsRows = listOf(
    listOf("1", "Hola", "150"),
    listOf("1", "Chao", "50"),
    listOf("2", "Algo", "200"),
    listOf("3", "Solo", "10")
)

fun write(root: File, table: Table, rows: List<List<String?>>) {
    File(root, "${table.name}.json").writeText(SchemaJson.encode(table))
    CsvWriter.write(File(root, "${table.name}.csv"), table.columns.map { it.name }, rows)
}

// Las dos tablas con las que se prueba casi todo.
fun database(root: File) {
    write(root, usersTable, usersRows)
    write(root, postsTable, postsRows)
}

// El pipeline completo, con volcado al directorio.
fun pipeline(root: File, sql: String, write: Boolean = true): CompilationResult =
    DbmsPipeline.run(sql, DataDirectory(root), write)

// El archivo tal como quedo en disco, para ver que de verdad se escribio.
fun lines(root: File, table: String): List<String> = File(root, "$table.csv").readLines()

// Las filas de una tabla despues de correr un script, sin volcar.
fun Outcome.rowsOf(table: String): List<Row> = session.rowsOf(table)

class Outcome(
    val results: List<ResultSet>,
    val diagnostics: Diagnostics,
    val session: Session,
    val catalog: Catalog
) {
    val errors: List<String> get() = diagnostics.all().map { it.message }
    val failures: List<String> get() = diagnostics.all().filter { it.severity == Severity.ERROR }.map { it.message }

    val result: ResultSet get() = results.single()
    val names: List<String?> get() = result.columns.map { it.name }

    // La rejilla, como se veria en la interfaz.
    val grid: List<List<String>> get() = result.rows.map { row -> row.values.map { it.display() } }

    fun column(index: Int): List<Value> = result.rows.map { it.values[index] }
}

/**
 * Carga el catalogo del directorio y corre el script entero.
 *
 * Exige que el analisis no reporte: un test del motor que falla por un error de
 * nombres o de tipos no esta probando el motor.
 */
fun runScript(root: File, sql: String): Outcome {
    val diagnostics = Diagnostics()
    val directory = DataDirectory(root)
    val catalog = CatalogLoader(directory, diagnostics).load()

    val tree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics), "No parsea: $sql")
    val script = SqlAstBuilder(diagnostics).visit(tree) as Script
    NameResolver(catalog, directory, diagnostics).resolve(script)
    SqlChecker(catalog, directory, diagnostics).check(script)
    assertFalse(diagnostics.hasErrors, "El analisis reporto: ${diagnostics.all().map { it.message }}")

    val session = Session(directory, catalog, diagnostics)
    val execution = session.run(script)
    return Outcome(execution.results, diagnostics, session, catalog)
}

// Igual que runScript, pero exigiendo que nada falle. Las advertencias si pasan:
// un LIMIT sin ORDER BY avisa y no es un problema del motor.
fun query(root: File, sql: String): Outcome {
    val outcome = runScript(root, sql)
    assertEquals(emptyList(), outcome.failures, "Fallo: $sql")
    return outcome
}
