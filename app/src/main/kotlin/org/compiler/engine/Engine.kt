package org.compiler.engine

import org.compiler.diagnostics.CompilerError
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.DerivedSource
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FromSource
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.SelectAll
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.SelectTableAll
import org.compiler.frontend.ast.models.TableSource
import org.compiler.frontend.ast.models.hasAggregate
import org.compiler.frontend.semantic.schemaOf
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.models.LexemeLocation
import org.compiler.types.BoolValue
import org.compiler.types.NullValue
import org.compiler.types.Value

/**
 * Arma la cadena de operadores de una consulta y la ejecuta.
 *
 * Toda lectura pasa por la sesion, asi que el motor nunca abre un archivo por su
 * cuenta. Uno por sentencia: el cache de subconsultas no debe sobrevivir a la
 * sentencia que lo lleno.
 */
class Engine(private val session: Session) {

    private val evaluator = Evaluator(this)

    // Una subconsulta no correlacionada da siempre lo mismo, asi que se ejecuta
    // una vez. Sin esto, un IN dentro de un WHERE sobre 10,000 filas se ejecutaria
    // 10,000 veces.
    private val cache = mutableMapOf<Query, ResultSet>()
    private val existsCache = mutableMapOf<Query, Boolean>()

    fun execute(query: Query): ResultSet = run(query, RowContext.EMPTY)

    // El valor de una expresion suelta. Lo usa el escritor para los valores de un
    // INSERT y de un SET, que pueden traer una subconsulta adentro.
    fun valueOf(expression: Expression, context: RowContext = RowContext.EMPTY): Value =
        evaluator.evaluate(expression, context)

    /**
     * El orden es el de evaluacion de SQL: FROM, JOIN, WHERE, GROUP BY, HAVING,
     * SELECT, ORDER BY, DISTINCT, LIMIT.
     *
     * Sin agrupar, el ORDER BY se resuelve ANTES de proyectar, porque
     * `SELECT name FROM users ORDER BY city` ordena por una columna que no sale.
     */
    fun run(query: Query, outer: RowContext): ResultSet {
        session.countExecution()

        val input = from(query, outer)
        val filtered = Operators.filter(input) { passes(query.where, outer.nest(it)) }

        val projected = if (groups(query)) grouped(query, filtered, outer) else plain(query, filtered, outer)
        val unique = if (query.distinct) Operators.distinct(projected) else projected

        return Operators.limit(unique, query.limit)
    }

    // ── El FROM ────────────────────────────────────────────────────────────

    private fun from(query: Query, outer: RowContext): ResultSet {
        var result = sourceOf(query.source, outer)

        query.joins.forEach { join ->
            val right = sourceOf(join.source, outer)
            result = Operators.join(result, right) { passes(join.condition, outer.nest(it)) }
        }
        return result
    }

    private fun sourceOf(source: FromSource, outer: RowContext): ResultSet = when (source) {
        is TableSource -> Operators.scan(session, source.table)

        // Una tabla derivada no puede ser correlacionada, asi que se ejecuta una
        // vez y el cache la sirve de ahi en adelante.
        is DerivedSource -> subquery(source.query, outer)
    }

    // ── Sin agrupar ────────────────────────────────────────────────────────

    private fun plain(query: Query, input: ResultSet, outer: RowContext): ResultSet {
        val ordered = Operators.sort(input.rows, sortKeys(query, outer))
        val rows = ordered.map { Row(projectValues(query, it, outer.nest(it), group = null)) }

        return Operators.project(schemaOf(query), rows)
    }

    private fun sortKeys(query: Query, outer: RowContext): List<Operators.SortKey<Row>> =
        query.orderBy.map { criterion ->
            val expression = unaliased(query, criterion.expression)
            Operators.SortKey(criterion.descending) { row -> evaluate(expression, outer.nest(row)) }
        }

    /**
     * Un ORDER BY puede nombrar un alias del SELECT. Antes de proyectar ese alias
     * todavia no existe, asi que se ordena por la expresion que lo define.
     */
    private fun unaliased(query: Query, expression: Expression): Expression {
        val reference = expression as? ColumnReference ?: return expression
        if (reference.symbol?.kind != DeclarationKind.ALIAS) return expression

        return query.selection.filterIsInstance<SelectExpression>()
            .firstOrNull { it.alias == reference.name }?.expression ?: expression
    }

    // ── Agrupando ──────────────────────────────────────────────────────────

    /**
     * Sin GROUP BY pero con agregacion, todo es un solo grupo: `SELECT COUNT(*)`
     * devuelve una fila aunque la tabla este vacia.
     *
     * Con GROUP BY sobre una tabla vacia no hay grupos, y por eso no hay filas.
     */
    private fun grouped(query: Query, input: ResultSet, outer: RowContext): ResultSet {
        val groups = if (query.groupBy.isEmpty()) {
            listOf(input.rows)
        } else {
            input.rows.groupBy { row ->
                query.groupBy.map { evaluate(it, outer.nest(row)) }
            }.values.toList()
        }

        val kept = groups.filter { group ->
            passes(query.having, outer.nest(sample(group, input)), group)
        }

        val keys = query.orderBy.map { criterion ->
            val expression = unaliased(query, criterion.expression)
            Operators.SortKey<List<Row>>(criterion.descending) { group ->
                evaluate(expression, outer.nest(sample(group, input)), group)
            }
        }

        val rows = Operators.sort(kept, keys).map { group ->
            Row(projectValues(query, sample(group, input), outer.nest(sample(group, input)), group))
        }
        return Operators.project(schemaOf(query), rows)
    }

    // Una fila cualquiera del grupo: todas comparten los valores agrupados, y la
    // fase 5 ya exigio que lo que sale sea eso o una agregacion.
    private fun sample(group: List<Row>, input: ResultSet): Row =
        group.firstOrNull() ?: Row(List(input.columns.size) { NullValue })

    // ── La proyeccion ──────────────────────────────────────────────────────

    private fun projectValues(query: Query, row: Row, context: RowContext, group: List<Row>?): List<Value> =
        query.selection.flatMap { item ->
            when (item) {
                is SelectAll -> row.values
                is SelectTableAll -> slice(query, item.qualifier, row)
                is SelectExpression -> listOf(evaluate(item.expression, context, group))
            }
        }

    // El tramo de la fila que aporta un origen del FROM. El simbolo ya trae donde
    // arranca y su tabla cuantas columnas ocupa.
    private fun slice(query: Query, qualifier: String, row: Row): List<Value> {
        val symbol = query.scope?.lookupLocal(qualifier) ?: return emptyList()
        val width = symbol.table?.columns?.size ?: return emptyList()

        return row.values.subList(symbol.index, symbol.index + width)
    }

    // ── Subconsultas ───────────────────────────────────────────────────────

    fun subquery(query: Query, outer: RowContext): ResultSet =
        if (query.correlated) run(query, outer) else cache.getOrPut(query) { run(query, outer) }

    /**
     * Si trae cero filas, NULL. Si trae mas de una, error de ejecucion: que
     * devuelva a lo sumo una fila depende de los datos, asi que no se podia saber
     * en el analisis.
     */
    fun scalar(query: Query, outer: RowContext): Value {
        val result = subquery(query, outer)
        if (result.rows.isEmpty()) return NullValue

        if (result.rows.size > 1) {
            report(query.location, "la subconsulta devolvio ${result.rows.size} filas, se esperaba a lo sumo una")
            return NullValue
        }
        return result.rows.first().values.firstOrNull() ?: NullValue
    }

    /**
     * Corta al encontrar la primera fila.
     *
     * Es la unica optimizacion que vale la pena aqui: un EXISTS correlacionado
     * corre por cada fila de afuera, y sin el corte recorreria la tabla entera
     * cada vez solo para descartar el resultado.
     */
    fun exists(query: Query, outer: RowContext): Boolean {
        if (!canCut(query)) return subquery(query, outer).rows.isNotEmpty()

        return if (query.correlated) firstMatch(query, outer) else existsCache.getOrPut(query) { firstMatch(query, outer) }
    }

    // Agrupar o cortar con LIMIT cambia si hay filas o no, asi que ahi se ejecuta
    // la consulta entera.
    private fun canCut(query: Query): Boolean = query.limit == null && !groups(query)

    private fun firstMatch(query: Query, outer: RowContext): Boolean {
        session.countExecution()

        return from(query, outer).rows.any { passes(query.where, outer.nest(it)) }
    }

    fun reportDivisionByZero() {
        report(LexemeLocation(1, 1), "division entre cero")
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────

    private fun groups(query: Query): Boolean =
        query.groupBy.isNotEmpty() ||
            query.selection.any { it.hasAggregate() } ||
            query.having?.hasAggregate() == true

    // Una fila pasa solo si la condicion da verdadero. NullValue no pasa, y por eso
    // `= NULL` nunca filtra nada.
    private fun passes(condition: Expression?, context: RowContext, group: List<Row>? = null): Boolean {
        session.countExamined()
        if (condition == null) return true

        return evaluate(condition, context, group) == BoolValue(true)
    }

    private fun evaluate(expression: Expression, context: RowContext, group: List<Row>? = null): Value =
        evaluator.evaluate(expression, context, group)

    private fun report(location: LexemeLocation, message: String) {
        session.diagnostics.report(CompilerError.ExecutionError(location, message))
    }
}
