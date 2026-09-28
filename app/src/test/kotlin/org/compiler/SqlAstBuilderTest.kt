package org.compiler

import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.Nullable
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Unique
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.*
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.models.LexemeLocation
import org.compiler.types.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlAstBuilderTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    // Parsea, construye el AST, y exige que ninguna de las dos etapas reporte.
    private fun script(sql: String): Script {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics), "No parsea: $sql")
        val ast = SqlAstBuilder(diagnostics).visit(tree) as Script
        assertEquals(emptyList(), diagnostics.all(), "Reporto errores: $sql")
        return ast
    }

    private fun statement(sql: String): Statement = script(sql).statements.single()

    private fun query(sql: String): Query = assertIs<Query>(statement(sql))

    // Una expresion suelta, puesta en el WHERE de una consulta minima.
    private fun expression(text: String): Expression =
        assertNotNull(query("SELECT * FROM t WHERE $text;").where)

    private fun render(expression: Expression): String = when (expression) {
        is Literal -> when (val value = expression.value) {
            is StringValue -> "'${value.value}'"
            else -> value.display()
        }
        is ColumnReference -> listOfNotNull(expression.qualifier, expression.name).joinToString(".")
        is Binary -> "(${expression.operator.symbol} ${render(expression.left)} ${render(expression.right)})"
        is Unary -> "(${expression.operator.symbol} ${render(expression.operand)})"
        is IsNull -> "(${if (expression.negated) "IS NOT NULL" else "IS NULL"} ${render(expression.operand)})"
        is InList -> "(${inWord(expression.negated)} ${render(expression.operand)} " +
            "[${expression.values.joinToString(" ") { render(it) }}])"
        is InSubquery -> "(${inWord(expression.negated)} ${render(expression.operand)} <consulta>)"
        is Exists -> "(EXISTS <consulta>)"
        is Aggregate -> "(${expression.function}${if (expression.distinct) " DISTINCT" else ""}" +
            (expression.argument?.let { " ${render(it)}" } ?: "") + ")"
        is Query -> "<consulta>"
    }

    private fun inWord(negated: Boolean) = if (negated) "NOT IN" else "IN"

    // ── DDL ────────────────────────────────────────────────────────────────

    @Test
    fun `CREATE TABLE traduce los nueve tipos`() {
        val create = assertIs<CreateTable>(
            statement(
                "CREATE TABLE t (a INT, b INTEGER, c FLOAT, d DECIMAL(10,2), e NUMERIC(8,3), " +
                    "f CHAR(5), g VARCHAR(40), h TEXT, i DATE, j TIME, k BOOLEAN);"
            )
        )

        assertEquals("t", create.name)
        assertEquals(
            listOf(
                IntType, IntType, FloatType, DecimalType(10, 2), DecimalType(8, 3),
                CharType(5), VarcharType(40), TextType, DateType, TimeType, BooleanType
            ),
            create.columns.map { it.type }
        )
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"), create.columns.map { it.name })
    }

    // Las restricciones se guardan EN ORDEN y sin validar: que NOT NULL y NULL se
    // contradigan lo decide la fase 5, no el builder.
    @Test
    fun `las siete restricciones se traducen en orden`() {
        val create = assertIs<CreateTable>(
            statement(
                "CREATE TABLE t (c INT PRIMARY KEY NOT NULL NULL UNIQUE AUTOINCREMENT " +
                    "DEFAULT 0 REFERENCES otra(id));"
            )
        )

        assertEquals(
            listOf(PrimaryKey, NotNull, Nullable, Unique, AutoIncrement, Default("0"), ForeignKey("otra", "id")),
            create.columns.single().constraints
        )
    }

    // Default guarda TEXTO, el mismo que ValueCodec.decode espera al cargar el
    // catalogo. Sin comillas, sin la palabra DATE, y el booleano en minusculas.
    @Test
    fun `DEFAULT guarda el valor como el texto que entiende ValueCodec`() {
        val create = assertIs<CreateTable>(
            statement(
                "CREATE TABLE t (a VARCHAR(20) DEFAULT 'dijo ''hola''', b BOOLEAN DEFAULT TRUE, " +
                    "c DATE DEFAULT DATE '2026-01-01', d DECIMAL(5,2) DEFAULT 1.50);"
            )
        )

        assertEquals(
            listOf(Default("dijo 'hola'"), Default("true"), Default("2026-01-01"), Default("1.50")),
            create.columns.map { it.constraints.single() }
        )
    }

    // Una columna sin DEFAULT ya arranca en NULL: DEFAULT NULL no agrega nada.
    @Test
    fun `DEFAULT NULL no produce restriccion`() {
        val create = assertIs<CreateTable>(statement("CREATE TABLE t (a INT DEFAULT NULL);"))

        assertTrue(create.columns.single().constraints.isEmpty())
    }

    @Test
    fun `ALTER y DROP`() {
        val add = assertIs<AlterTableAdd>(statement("ALTER TABLE users ADD email TEXT UNIQUE;"))
        assertEquals("users", add.table)
        assertEquals("email", add.column.name)
        assertEquals(TextType, add.column.type)
        assertEquals(listOf(Unique), add.column.constraints)

        val drop = assertIs<AlterTableDrop>(statement("ALTER TABLE users DROP COLUMN email;"))
        assertEquals("users", drop.table)
        assertEquals("email", drop.column)

        assertEquals("users", assertIs<DropTable>(statement("DROP TABLE users;")).table)
    }

    // ── DML ────────────────────────────────────────────────────────────────

    @Test
    fun `INSERT sin columnas deja null y conserva las filas`() {
        val insert = assertIs<Insert>(statement("INSERT INTO users VALUES (1, 'Ana'), (2, 'Luis');"))

        assertEquals("users", insert.table)
        assertNull(insert.columns)
        assertEquals(
            listOf(listOf("1", "'Ana'"), listOf("2", "'Luis'")),
            insert.rows.map { row -> row.map { render(it) } }
        )
    }

    @Test
    fun `INSERT con columnas las guarda en orden`() {
        val insert = assertIs<Insert>(statement("INSERT INTO users (name, id) VALUES ('Ana', 1);"))

        assertEquals(listOf("name", "id"), insert.columns)
    }

    @Test
    fun `UPDATE con varias asignaciones y WHERE`() {
        val update = assertIs<Update>(statement("UPDATE users SET name = 'Bea', age = age + 1 WHERE id = 1;"))

        assertEquals("users", update.table)
        assertEquals(listOf("name", "age"), update.assignments.map { it.column })
        assertEquals(listOf("'Bea'", "(+ age 1)"), update.assignments.map { render(it.value) })
        assertEquals("(= id 1)", render(assertNotNull(update.where)))
    }

    @Test
    fun `DELETE con y sin WHERE`() {
        val filtered = assertIs<Delete>(statement("DELETE FROM users WHERE id = 1;"))
        assertEquals("(= id 1)", render(assertNotNull(filtered.where)))

        assertNull(assertIs<Delete>(statement("DELETE FROM users;")).where)
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Test
    fun `una consulta completa llena todos sus campos`() {
        val query = query(
            "SELECT DISTINCT u.name, COUNT(*) AS total FROM users u " +
                "JOIN posts p ON p.uid = u.id WHERE u.active = TRUE " +
                "GROUP BY u.name HAVING COUNT(*) > 2 ORDER BY total DESC, u.name LIMIT 10;"
        )

        assertTrue(query.distinct)

        val (name, total) = query.selection.map { assertIs<SelectExpression>(it) }
        assertEquals("u.name", render(name.expression))
        assertNull(name.alias)
        assertEquals("(COUNT_ALL)", render(total.expression))
        assertEquals("total", total.alias)

        val source = assertIs<TableSource>(query.source)
        assertEquals("users", source.table)
        assertEquals("u", source.alias)

        val join = query.joins.single()
        assertEquals("posts", assertIs<TableSource>(join.source).table)
        assertEquals("(= p.uid u.id)", render(join.condition))

        assertEquals("(= u.active true)", render(assertNotNull(query.where)))
        assertEquals(listOf("u.name"), query.groupBy.map { render(it) })
        assertEquals("(> (COUNT_ALL) 2)", render(assertNotNull(query.having)))
        assertEquals(listOf("total" to true, "u.name" to false), query.orderBy.map { render(it.expression) to it.descending })
        assertEquals(10, query.limit)
    }

    // La consulta tiene dos `expression` sueltas posibles, WHERE y HAVING, y
    // cualquiera puede faltar. Con solo HAVING, esa expresion NO es el WHERE.
    @Test
    fun `WHERE y HAVING no se confunden cuando falta uno`() {
        val onlyHaving = query("SELECT dept FROM emp GROUP BY dept HAVING COUNT(*) > 1;")
        assertNull(onlyHaving.where)
        assertEquals("(> (COUNT_ALL) 1)", render(assertNotNull(onlyHaving.having)))

        val onlyWhere = query("SELECT * FROM emp WHERE a = 1;")
        assertEquals("(= a 1)", render(assertNotNull(onlyWhere.where)))
        assertNull(onlyWhere.having)
    }

    @Test
    fun `los tres tipos de item del SELECT`() {
        assertIs<SelectAll>(query("SELECT * FROM t;").selection.single())

        val tableAll = assertIs<SelectTableAll>(query("SELECT u.* FROM users u;").selection.single())
        assertEquals("u", tableAll.qualifier)

        // El alias se reconoce con y sin AS.
        val aliases = query("SELECT a AS x, b y, c FROM t;").selection
            .map { assertIs<SelectExpression>(it).alias }
        assertEquals(listOf("x", "y", null), aliases)
    }

    @Test
    fun `una tabla derivada lleva su consulta y su alias`() {
        val outer = query("SELECT t.total FROM (SELECT SUM(x) AS total FROM v) AS t;")

        val derived = assertIs<DerivedSource>(outer.source)
        assertEquals("t", derived.alias)
        assertEquals("v", assertIs<TableSource>(derived.query.source).table)
    }

    @Test
    fun `sin ORDER BY ni LIMIT quedan vacios`() {
        val query = query("SELECT * FROM t;")

        assertTrue(query.orderBy.isEmpty())
        assertTrue(query.groupBy.isEmpty())
        assertTrue(query.joins.isEmpty())
        assertNull(query.limit)
    }

    // ── La torre de precedencia ────────────────────────────────────────────

    @Test
    fun `la multiplicacion queda adentro de la suma`() {
        assertEquals("(+ a (* b c))", render(expression("a + b * c")))
    }

    // EL TEST MAS IMPORTANTE DEL PLEGADO. Al reves daria a - (b - c): otro numero,
    // y ningun error que lo delate.
    @Test
    fun `la resta y la division pliegan a la izquierda`() {
        assertEquals("(- (- a b) c)", render(expression("a - b - c")))
        assertEquals("(/ (/ a b) c)", render(expression("a / b / c")))
    }

    @Test
    fun `AND liga mas fuerte que OR`() {
        assertEquals("(OR a (AND b c))", render(expression("a OR b AND c")))
        assertEquals("(AND (AND a b) c)", render(expression("a AND b AND c")))
    }

    @Test
    fun `NOT envuelve la comparacion completa`() {
        assertEquals("(NOT (= a b))", render(expression("NOT a = b")))
    }

    @Test
    fun `los parentesis cambian la forma y desaparecen`() {
        assertEquals("(* (+ a b) c)", render(expression("(a + b) * c")))
    }

    @Test
    fun `menos unario y concatenacion`() {
        assertEquals("(- precio)", render(expression("-precio")))
        assertEquals("(|| (|| nombre ' ') apellido)", render(expression("nombre || ' ' || apellido")))
    }

    // `!=` es el mismo operador que `<>`: SQL acepta las dos formas.
    @Test
    fun `los seis operadores de comparacion`() {
        assertEquals(
            listOf("(= a 1)", "(<> a 1)", "(<> a 1)", "(< a 1)", "(> a 1)", "(<= a 1)", "(>= a 1)"),
            listOf("=", "<>", "!=", "<", ">", "<=", ">=").map { render(expression("a $it 1")) }
        )
    }

    // El lexer ve MAYUSCULAS, pero el texto del token es el original: si el
    // builder leyera los operadores de node.text, `and` no encontraria su enum.
    @Test
    fun `las palabras clave en minusculas dan el mismo arbol`() {
        val lower = render(expression("a and b or not c is null"))
        val upper = render(expression("a AND b OR NOT c IS NULL"))

        assertEquals("(OR (AND a b) (NOT (IS NULL c)))", upper)
        assertEquals(upper, lower)
        assertEquals("(COUNT DISTINCT x)", render(expression("count(distinct x) > 0").let { (it as Binary).left }))
    }

    // ── Predicados y subconsultas ──────────────────────────────────────────

    @Test
    fun `IS NULL, IN y EXISTS con su negacion`() {
        assertEquals("(IS NOT NULL a)", render(expression("a IS NOT NULL")))
        assertEquals("(IN a [1 2 3])", render(expression("a IN (1, 2, 3)")))
        assertEquals("(NOT IN a [1])", render(expression("a NOT IN (1)")))
        assertEquals("(NOT IN id <consulta>)", render(expression("id NOT IN (SELECT id FROM b)")))
        assertEquals("(EXISTS <consulta>)", render(expression("EXISTS (SELECT * FROM b)")))

        // NOT EXISTS no es un nodo aparte: es NOT sobre EXISTS.
        assertEquals("(NOT (EXISTS <consulta>))", render(expression("NOT EXISTS (SELECT * FROM b)")))
    }

    // Una subconsulta escalar es el mismo nodo Query, usado como expresion.
    @Test
    fun `una subconsulta escalar es una Query dentro de la expresion`() {
        val comparison = assertIs<Binary>(expression("x > (SELECT MAX(x) FROM b)"))

        val subquery = assertIs<Query>(comparison.right)
        assertEquals("(MAX x)", render(assertIs<SelectExpression>(subquery.selection.single()).expression))
    }

    @Test
    fun `EXISTS anidado en tres niveles conserva las tres consultas`() {
        var current = expression(
            "EXISTS (SELECT * FROM b WHERE EXISTS (SELECT * FROM c WHERE EXISTS (SELECT * FROM d)))"
        )

        val tables = mutableListOf<String>()
        while (current is Exists) {
            tables += assertIs<TableSource>(current.query.source).table
            current = current.query.where ?: break
        }

        assertEquals(listOf("b", "c", "d"), tables)
    }

    @Test
    fun `las cinco funciones de agregacion`() {
        assertEquals(
            listOf("(COUNT_ALL)", "(COUNT x)", "(SUM DISTINCT x)", "(AVG x)", "(MIN x)", "(MAX x)"),
            listOf("COUNT(*)", "COUNT(x)", "SUM(DISTINCT x)", "AVG(x)", "MIN(x)", "MAX(x)")
                .map { render(assertIs<SelectExpression>(query("SELECT $it FROM t;").selection.single()).expression) }
        )
    }

    // ── Literales ──────────────────────────────────────────────────────────

    private fun literal(text: String): Literal = assertIs<Literal>(expression("a = $text").let { (it as Binary).right })

    @Test
    fun `cada literal lleva su valor y su tipo`() {
        val cases = listOf(
            "42" to (IntValue(42) to IntType),
            "3.14" to (DecimalValue(BigDecimal("3.14")) to DecimalType(3, 2)),
            "0.05" to (DecimalValue(BigDecimal("0.05")) to DecimalType(3, 2)),
            "'hola'" to (StringValue("hola") to VarcharType(4)),
            "DATE '2026-09-23'" to (DateValue(LocalDate.of(2026, 9, 23)) to DateType),
            "TIME '14:30:00'" to (TimeValue(LocalTime.of(14, 30)) to TimeType),
            "TRUE" to (BoolValue(true) to BooleanType),
            "false" to (BoolValue(false) to BooleanType),
            "NULL" to (NullValue to NullType)
        )

        cases.forEach { (text, expected) ->
            val literal = literal(text)
            assertEquals(expected, literal.value to literal.literalType, "literal $text")
        }
    }

    // El largo del VARCHAR es el del contenido YA desdoblado: dijo 'hola' son
    // 11 caracteres, no los 13 que hay entre las comillas del fuente.
    @Test
    fun `la comilla duplicada se desdobla`() {
        val literal = literal("'dijo ''hola'''")

        assertEquals(StringValue("dijo 'hola'"), literal.value)
        assertEquals(VarcharType(11), literal.literalType)
    }

    // Un entero que no cabe en Long no revienta: pasa a DECIMAL.
    @Test
    fun `un entero enorme se guarda como DECIMAL`() {
        val literal = literal("99999999999999999999")

        assertEquals(DecimalValue(BigDecimal("99999999999999999999")), literal.value)
        assertEquals(DecimalType(20, 0), literal.literalType)
    }

    // ── Lo que parsea pero no se puede construir ───────────────────────────

    // `DATE '2026-02-30'` es sintacticamente perfecto: el error sale aqui, con
    // linea y columna, y el literal queda con ErrorType para cortar la cascada.
    @Test
    fun `una fecha imposible se reporta y no revienta`() {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse("SELECT * FROM t WHERE d = DATE '2026-02-30';", diagnostics))

        val ast = SqlAstBuilder(diagnostics).visit(tree) as Script

        val error = assertIs<CompilerError.SemanticError>(diagnostics.all().single())
        assertEquals(LexemeLocation(1, 27), error.location)
        assertTrue("2026-02-30" in error.message, error.message)

        val literal = assertIs<Literal>(assertIs<Binary>(assertIs<Query>(ast.statements.single()).where).right)
        assertEquals(ErrorType, literal.literalType)
    }

    @Test
    fun `una hora imposible se reporta`() {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse("INSERT INTO t VALUES (TIME '25:00');", diagnostics))

        SqlAstBuilder(diagnostics).visit(tree)

        assertTrue("25:00" in diagnostics.semantic().single().message)
    }

    // VARCHAR(99999999999) parsea, pero el largo no cabe en un Int.
    @Test
    fun `un largo que no cabe en Int se reporta y no revienta`() {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse("CREATE TABLE t (a VARCHAR(99999999999));", diagnostics))

        val ast = SqlAstBuilder(diagnostics).visit(tree) as Script

        assertTrue("demasiado grande" in diagnostics.semantic().single().message)
        assertEquals(ErrorType, assertIs<CreateTable>(ast.statements.single()).columns.single().type)
    }

    // ── Ubicaciones ────────────────────────────────────────────────────────

    @Test
    fun `cada nodo lleva la ubicacion de su primer token`() {
        val ast = script("DROP TABLE a;\n  SELECT x + y FROM t;")

        assertEquals(LexemeLocation(1, 1), ast.statements[0].location)
        assertEquals(LexemeLocation(2, 3), ast.statements[1].location)

        // Una operacion arranca donde arranca su operando izquierdo.
        val sum = assertIs<SelectExpression>(assertIs<Query>(ast.statements[1]).selection.single()).expression
        assertEquals(LexemeLocation(2, 10), sum.location)
    }

    @Test
    fun `un script vacio es un Script sin sentencias`() {
        assertTrue(script("").statements.isEmpty())
    }
}
