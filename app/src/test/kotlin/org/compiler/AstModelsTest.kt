
package org.compiler

import org.compiler.catalog.NotNull
import org.compiler.frontend.ast.models.*
import org.compiler.models.LexemeLocation
import org.compiler.types.BinaryOperator
import org.compiler.types.ErrorType
import org.compiler.types.IntType
import org.compiler.types.IntValue
import org.compiler.types.UnaryOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AstModelsTest {

    private val at = LexemeLocation(1, 1)

    private fun column(name: String) = ColumnReference(qualifier = null, name = name, location = at)

    private fun query(where: Expression? = null) = Query(
        distinct = false,
        selection = listOf(SelectAll(at)),
        source = TableSource("users", alias = null, location = at),
        joins = emptyList(),
        where = where,
        groupBy = emptyList(),
        having = null,
        orderBy = emptyList(),
        limit = null,
        location = at
    )

    // ── Las jerarquias estan completas ─────────────────────────────────────
    //
    // Estos `when` NO tienen rama `else`. Si alguien agrega una sentencia o una
    // expresion y olvida cubrirla, el test deja de COMPILAR: es la prueba de que
    // el `sealed` protege a las fases 4, 5 y 6.

    private fun kindOf(statement: Statement): String = when (statement) {
        is CreateTable -> "create"
        is AlterTableAdd -> "alter add"
        is AlterTableDrop -> "alter drop"
        is DropTable -> "drop"
        is Insert -> "insert"
        is Update -> "update"
        is Delete -> "delete"
        is Query -> "query"
    }

    private fun kindOf(expression: Expression): String = when (expression) {
        is Literal -> "literal"
        is ColumnReference -> "column"
        is Binary -> "binary"
        is Unary -> "unary"
        is IsNull -> "is null"
        is InList -> "in list"
        is InSubquery -> "in subquery"
        is Exists -> "exists"
        is Aggregate -> "aggregate"
        is Query -> "subquery"
    }

    private fun kindOf(source: FromSource): String = when (source) {
        is TableSource -> "table"
        is DerivedSource -> "derived"
    }

    private fun kindOf(item: SelectItem): String = when (item) {
        is SelectAll -> "*"
        is SelectTableAll -> "t.*"
        is SelectExpression -> "expression"
    }

    @Test
    fun `un when sin else cubre las ocho sentencias`() {
        val definition = ColumnDefinition("id", IntType, listOf(NotNull), at)
        val statements = listOf(
            CreateTable("users", listOf(definition), at),
            AlterTableAdd("users", definition, at),
            AlterTableDrop("users", "id", at),
            DropTable("users", at),
            Insert("users", columns = null, rows = listOf(listOf(column("x"))), location = at),
            Update("users", listOf(Assignment("id", column("x"), at)), where = null, location = at),
            Delete("users", where = null, location = at),
            query()
        )

        assertEquals(
            listOf("create", "alter add", "alter drop", "drop", "insert", "update", "delete", "query"),
            statements.map { kindOf(it) }
        )
    }

    @Test
    fun `un when sin else cubre las diez expresiones`() {
        val literal = Literal(IntValue(1), IntType, at)
        val expressions = listOf(
            literal,
            column("a"),
            Binary(BinaryOperator.ADD, literal, literal, at),
            Unary(UnaryOperator.NEGATE, literal, at),
            IsNull(literal, negated = true, location = at),
            InList(literal, listOf(literal), negated = false, location = at),
            InSubquery(literal, query(), negated = false, location = at),
            Exists(query(), at),
            Aggregate(AggregateFunction.COUNT_ALL, argument = null, distinct = false, location = at),
            query()
        )

        assertEquals(10, expressions.map { kindOf(it) }.toSet().size)
    }

    @Test
    fun `los origenes y los items del SELECT tambien son sellados`() {
        assertEquals("table", kindOf(TableSource("users", "u", at)))
        assertEquals("derived", kindOf(DerivedSource(query(), "t", at)))
        assertEquals("*", kindOf(SelectAll(at)))
        assertEquals("t.*", kindOf(SelectTableAll("u", at)))
        assertEquals("expression", kindOf(SelectExpression(column("a"), alias = "x", location = at)))
    }

    // ── Query es sentencia y expresion a la vez ────────────────────────────

    // Es lo que permite `WHERE x > (SELECT ...)` sin duplicar el nodo.
    @Test
    fun `una consulta es a la vez sentencia y expresion`() {
        val subquery: Expression = query()
        val statement: Statement = query()

        assertIs<Query>(subquery)
        assertIs<Query>(statement)
    }

    // ── Los campos que llenan las fases siguientes arrancan vacios ─────────

    @Test
    fun `el tipo de toda expresion arranca en ErrorType`() {
        val literal = Literal(IntValue(1), IntType, at)

        // literalType es lo que dice la sintaxis; type lo decide la fase 5.
        assertEquals(IntType, literal.literalType)
        assertEquals(ErrorType, literal.type)
        assertEquals(ErrorType, column("a").type)
        assertEquals(ErrorType, query().type)
    }

    @Test
    fun `lo que llena la fase 4 arranca sin resolver`() {
        val reference = column("a")
        assertEquals(-1, reference.level)
        assertEquals(-1, reference.index)

        val query = query()
        assertNull(query.scope)
        assertFalse(query.correlated)
    }

    // Los nodos son `class` y no `data class`: las fases 4 y 5 les pegan datos, y
    // dos `a` escritas en lugares distintos del script son nodos distintos.
    @Test
    fun `dos nodos iguales en apariencia son nodos distintos`() {
        assertNotEquals(column("a"), column("a"))
    }

    // ── El AST no conoce a ANTLR ───────────────────────────────────────────

    // Se revisa el TIPO de cada campo de cada nodo, genericos incluidos
    // (List<Expression>, List<List<Expression>>). Si alguno guardara un Context o un
    // Token de ANTLR, las fases 4 a 8 quedarian atadas a la gramatica.
    @Test
    fun `ningun nodo guarda un tipo de ANTLR`() {
        val nodes = listOf(
            Script::class, CreateTable::class, AlterTableAdd::class, AlterTableDrop::class,
            DropTable::class, Insert::class, Update::class, Delete::class,
            ColumnDefinition::class, Assignment::class,
            Query::class, TableSource::class, DerivedSource::class, Join::class,
            SelectAll::class, SelectTableAll::class, SelectExpression::class, OrderCriterion::class,
            Literal::class, ColumnReference::class, Binary::class, Unary::class, IsNull::class,
            InList::class, InSubquery::class, Exists::class, Aggregate::class
        )

        val leaked = nodes.flatMap { node ->
            node.java.declaredFields
                .map { "${node.simpleName}.${it.name}: ${it.genericType.typeName}" }
                .filter { "org.antlr" in it || "org.compiler.parser" in it }
        }

        assertTrue(leaked.isEmpty(), "Campos con tipos de ANTLR:\n" + leaked.joinToString("\n"))
    }
}
