package org.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.frontend.syntax.toTreeView
import org.compiler.models.LexemeLocation
import org.compiler.parser.SqlParser
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlSyntaxAnalyzerTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    // Un script completo que debe parsear sin un solo error.
    private fun parseOk(source: String): SqlParser.ScriptContext {
        val diagnostics = Diagnostics()
        val tree = SqlSyntaxAnalyzer.parse(source, diagnostics)
        assertEquals(emptyList(), diagnostics.all(), "No parsea: $source")
        return assertNotNull(tree)
    }

    // Los problemas de un script, como texto legible. Vacio = parseo limpio.
    private fun scriptProblems(source: String): List<String> {
        val diagnostics = Diagnostics()
        SqlSyntaxAnalyzer.parse(source, diagnostics)
        return diagnostics.all().map { describe(it) }
    }

    // Entra por una regla suelta —type, literal— y exige que la consuma ENTERA.
    //
    // Sin el chequeo de EOF, `INT basura` pasaria: ANTLR reconoce INT, se detiene
    // sin reportar nada, porque la regla `type` no pide nada despues.
    private fun ruleProblems(source: String, rule: (SqlParser) -> ParserRuleContext): List<String> {
        val diagnostics = Diagnostics()
        val parser = SqlSyntaxAnalyzer.parserFor(source, diagnostics)
        rule(parser)

        val problems = diagnostics.all().map { describe(it) }.toMutableList()
        if (parser.currentToken.type != Token.EOF) {
            problems += "se detuvo en '${parser.currentToken.text}' sin consumir todo"
        }
        return problems
    }

    private fun describe(error: CompilerError) =
        "${error.location.line}:${error.location.position} ${error.message}"

    // Corre TODOS los casos y falla una sola vez listando los que no pasaron, en vez
    // de detenerse en el primero: con una gramatica, casi nunca falla uno solo.
    private fun assertAllParse(cases: List<String>, problemsOf: (String) -> List<String>) {
        val failures = cases.mapNotNull { case ->
            problemsOf(case).takeIf { it.isNotEmpty() }?.let { "  $case\n    -> $it" }
        }
        assertTrue(failures.isEmpty(), "No parsearon:\n" + failures.joinToString("\n"))
    }

    // Todos los nodos de un tipo, a cualquier profundidad.
    private inline fun <reified T> ParseTree.findAll(): List<T> = findAllOf(this, T::class.java)

    private fun <T> findAllOf(node: ParseTree, type: Class<T>): List<T> {
        val here = if (type.isInstance(node)) listOf(type.cast(node)) else emptyList()
        return here + (0 until node.childCount).flatMap { findAllOf(node.getChild(it), type) }
    }

    // ── 3.1 · Tipos y literales ────────────────────────────────────────────

    @Test
    fun `los once nombres de tipo parsean`() {
        assertAllParse(
            listOf(
                "INT", "INTEGER", "FLOAT", "DECIMAL(10,2)", "NUMERIC(10,2)",
                "CHAR(5)", "VARCHAR(40)", "TEXT", "DATE", "TIME", "BOOLEAN"
            )
        ) { ruleProblems(it) { p -> p.type() } }
    }

    @Test
    fun `los siete literales parsean`() {
        assertAllParse(
            listOf(
                "42", "3.14", "'hola'", "DATE '2026-09-23'", "TIME '14:30:00'",
                "TRUE", "FALSE", "NULL"
            )
        ) { ruleProblems(it) { p -> p.literal() } }
    }

    // El estandar de SQL: la comilla simple se escapa duplicandola. Todo esto es UN
    // solo StringLit, no tres strings pegados.
    @Test
    fun `la comilla simple se escapa duplicandola`() {
        val parser = SqlSyntaxAnalyzer.parserFor("'dijo ''hola'''", Diagnostics())

        val literal = assertIs<SqlParser.StringLitContext>(parser.literal())

        assertEquals("'dijo ''hola'''", literal.text)
        assertEquals(Token.EOF, parser.currentToken.type)
    }

    // Decision del 3.1: una fecha lleva su palabra clave. Sin ella, '2026-09-23' es
    // un string como cualquier otro, y WHERE fecha > '...' y WHERE nombre > '...' no
    // se distinguirian hasta el semantico.
    @Test
    fun `una fecha sin la palabra clave es un string`() {
        val parser = SqlSyntaxAnalyzer.parserFor("'2026-09-23'", Diagnostics())

        assertIs<SqlParser.StringLitContext>(parser.literal())
    }

    @Test
    fun `las palabras clave no distinguen mayusculas`() {
        assertAllParse(listOf("varchar(40)", "Decimal(8,2)", "boolean")) {
            ruleProblems(it) { p -> p.type() }
        }
        assertAllParse(listOf("date '2026-01-01'", "true", "null")) {
            ruleProblems(it) { p -> p.literal() }
        }
    }

    // ── 3.2 · DDL y DML ────────────────────────────────────────────────────

    @Test
    fun `las sentencias de DDL y DML parsean`() {
        assertAllParse(
            listOf(
                "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(40) NOT NULL);",
                "ALTER TABLE users ADD email TEXT UNIQUE;",
                "ALTER TABLE users DROP COLUMN email;",
                "DROP TABLE users;",
                "INSERT INTO users VALUES (1, 'Ana');",
                "INSERT INTO users (id, name) VALUES (1, 'Ana');",
                "INSERT INTO users VALUES (1, 'Ana'), (2, 'Luis');",
                "INSERT INTO eventos VALUES (1, DATE '2026-09-23', TIME '14:30:00');",
                "UPDATE users SET name = 'Bea', age = age + 1 WHERE id = 1;",
                "DELETE FROM users WHERE id = 1;",
                "DELETE FROM users;"
            ),
            ::scriptProblems
        )
    }

    // Las siete en UNA columna, incluidas NOT NULL y NULL juntas: que se contradigan
    // es una regla semantica (fase 5), no sintactica.
    @Test
    fun `una columna acepta las siete restricciones a la vez`() {
        val tree = parseOk(
            "CREATE TABLE t (c INT PRIMARY KEY NOT NULL NULL UNIQUE AUTOINCREMENT " +
                "DEFAULT 0 REFERENCES otra(id));"
        )

        val column = tree.findAll<SqlParser.ColumnDefinitionContext>().single()
        assertEquals(7, column.constraint().size)
    }

    // ── 3.3 · Consultas ────────────────────────────────────────────────────

    @Test
    fun `las consultas del alcance parsean`() {
        assertAllParse(
            listOf(
                "SELECT * FROM users;",
                "SELECT u.name AS nombre, u.id n FROM users AS u;",
                "SELECT u.* FROM users u;",
                "SELECT DISTINCT city FROM users;",
                "SELECT * FROM users u INNER JOIN orders o ON u.id = o.user_id " +
                    "JOIN items i ON i.id = o.item;",
                "SELECT dept, COUNT(*), AVG(salary), COUNT(DISTINCT role) FROM emp " +
                    "GROUP BY dept HAVING COUNT(*) > 2 ORDER BY dept DESC LIMIT 10;",
                "SELECT * FROM a WHERE id NOT IN (SELECT id FROM b);",
                "SELECT * FROM a WHERE id IN (1, 2, 3);",
                "SELECT * FROM a WHERE b IS NOT NULL;",
                "SELECT (a + b) * c, nombre || ' ' || apellido FROM t;",
                "-- comentario de linea\nSELECT * /* de bloque */ FROM t;",
                "DROP TABLE a; SELECT * FROM b;",
                ""
            ),
            ::scriptProblems
        )
    }

    @Test
    fun `EXISTS se anida en tres niveles`() {
        val tree = parseOk(
            "SELECT * FROM a WHERE EXISTS (SELECT * FROM b WHERE EXISTS " +
                "(SELECT * FROM c WHERE EXISTS (SELECT * FROM d WHERE d.x = c.x)));"
        )

        assertEquals(3, tree.findAll<SqlParser.CmpExistsContext>().size)
        assertEquals(4, tree.findAll<SqlParser.QueryContext>().size)
    }

    @Test
    fun `una tabla derivada va en el FROM`() {
        val tree = parseOk("SELECT t.total FROM (SELECT SUM(x) AS total FROM v) AS t;")

        val derived = tree.findAll<SqlParser.DerivedSourceContext>().single()
        assertEquals("t", derived.Identifier().text)
    }

    // `'(' query ')'` va ANTES que `'(' expression ')'` en primaryExpression: una
    // subconsulta entre parentesis tiene que salir como subconsulta.
    @Test
    fun `una subconsulta entre parentesis no se confunde con una expresion`() {
        val tree = parseOk("SELECT * FROM a WHERE x > (SELECT MAX(x) FROM b);")

        assertEquals(1, tree.findAll<SqlParser.PrimSubqueryContext>().size)
        assertEquals(0, tree.findAll<SqlParser.PrimParenContext>().size)
    }

    // La precedencia sale de la TORRE: la multiplicacion vive un nivel mas abajo, asi
    // que la suma recibe `b * c` ya armado como su segundo operando.
    @Test
    fun `la multiplicacion liga mas fuerte que la suma`() {
        val tree = parseOk("SELECT a + b * c FROM t;")

        val sum = tree.findAll<SqlParser.AdditiveExpressionContext>().first()
        assertEquals(2, sum.multiplicativeExpression().size)
        assertEquals("a", sum.multiplicativeExpression(0).text)
        assertEquals("b*c", sum.multiplicativeExpression(1).text)
    }

    @Test
    fun `NOT envuelve la comparacion completa`() {
        val tree = parseOk("SELECT * FROM t WHERE NOT a = b;")

        val not = tree.findAll<SqlParser.NotExpressionContext>()
            .single { it.childCount == 2 }
        assertEquals("NOT", not.getChild(0).text)
        assertIs<SqlParser.CmpBinaryContext>(not.comparisonExpression())
        assertEquals("a=b", not.comparisonExpression().text)
    }

    // Decision del 3.3: WHERE va antes que GROUP BY, y la gramatica lo exige. Al
    // reves es error de SINTAXIS, con un mensaje claro, no uno semantico confuso.
    @Test
    fun `el orden de las clausulas es fijo`() {
        val diagnostics = Diagnostics()

        val tree = SqlSyntaxAnalyzer.parse("SELECT * FROM t GROUP BY a WHERE b = 1;", diagnostics)

        assertNull(tree)
        assertTrue(diagnostics.syntactic().isNotEmpty())
    }

    // ── 3.4 · SqlSyntaxAnalyzer ────────────────────────────────────────────

    @Test
    fun `mayusculas y minusculas dan el mismo arbol`() {
        val lower = parseOk("select * from users;").toTreeView()
        val upper = parseOk("SELECT * FROM users;").toTreeView()

        assertEquals(upper, lower)
    }

    @Test
    fun `un error de sintaxis reporta linea y columna`() {
        val diagnostics = Diagnostics()

        val tree = SqlSyntaxAnalyzer.parse("SELECT *\nFORM users;", diagnostics)

        assertNull(tree)
        val error = diagnostics.syntactic().single()
        assertEquals(LexemeLocation(line = 2, position = 1), error.location)
        assertTrue("'FROM'" in error.message, error.message)
    }

    // El lexer ve mayusculas, pero el mensaje cita lo que el usuario escribio:
    // `form`, no `FORM`. Es la otra mitad del UpperCaseCharStream.
    @Test
    fun `el mensaje de error conserva el texto original`() {
        val diagnostics = Diagnostics()

        SqlSyntaxAnalyzer.parse("select * form Users;", diagnostics)

        val message = diagnostics.syntactic().single().message
        assertTrue("'form'" in message, message)
    }

    @Test
    fun `un caracter fuera del lenguaje es error lexico`() {
        val diagnostics = Diagnostics()

        val tree = SqlSyntaxAnalyzer.parse("SELECT * FROM users WHERE @;", diagnostics)

        assertNull(tree)
        assertEquals(LexemeLocation(line = 1, position = 27), diagnostics.lexical().single().location)
    }

    // parse() mira los errores de SU llamada: un error de otra etapa que ya estaba
    // en el Diagnostics no debe hacer que un script valido devuelva null.
    @Test
    fun `un error previo en el diagnostics no anula un parseo valido`() {
        val diagnostics = Diagnostics()
        diagnostics.report(CompilerError.SemanticError(LexemeLocation(9, 1), "de otra etapa"))

        val tree = SqlSyntaxAnalyzer.parse("SELECT * FROM users;", diagnostics)

        assertNotNull(tree)
    }

    // Los listeners por omision de ANTLR imprimen "line 1:9 missing..." en la
    // consola. Se quitaron: nada debe salir ni por stdout ni por stderr.
    @Test
    fun `nada se escribe a la salida estandar`() {
        val captured = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        System.setOut(PrintStream(captured))
        System.setErr(PrintStream(captured))

        try {
            SqlSyntaxAnalyzer.parse("SELECT * FORM users WHERE @;", Diagnostics())
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        assertEquals("", captured.toString())
    }

    @Test
    fun `toTreeView conserva la forma del parse tree`() {
        val root = parseOk("SELECT * FROM Users;").toTreeView()

        assertEquals("script", root.label)
        assertEquals(listOf("statement", "<EOF>"), root.children.map { it.label })

        val statement = root.children[0]
        assertEquals(listOf("query", ";"), statement.children.map { it.label })

        val query = statement.children[0]
        assertEquals(listOf("SELECT", "selectList", "FROM", "source"), query.children.map { it.label })

        // La alternativa `#` va en el detalle: selectList es la alternativa selectAll.
        assertEquals("selectAll", query.children[1].detail)
        assertEquals("tableSource", query.children[3].detail)

        // Un identificador conserva su texto y dice que es; una palabra clave no.
        val table = query.children[3].children.single()
        assertEquals(TreeNodeView(label = "Users", detail = "Identifier"), table)
        assertNull(query.children[0].detail)
    }

    // El receptor de toTreeView es de ANTLR; el resultado no. Se revisa el TIPO de
    // cada campo, incluidos los genericos (children es List<TreeNodeView>).
    @Test
    fun `TreeNodeView no expone ningun tipo de ANTLR`() {
        val fieldTypes = TreeNodeView::class.java.declaredFields.map { it.genericType.typeName }

        val leaked = fieldTypes.filter { "org.antlr" in it || "org.compiler.parser" in it }
        assertTrue(leaked.isEmpty(), "TreeNodeView expone tipos de ANTLR: $leaked")
    }
}
