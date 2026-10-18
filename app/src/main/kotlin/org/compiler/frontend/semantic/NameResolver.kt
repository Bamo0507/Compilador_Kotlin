package org.compiler.frontend.semantic

import org.compiler.catalog.Catalog
import org.compiler.catalog.Column
import org.compiler.catalog.Table
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.Aggregate
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DerivedSource
import org.compiler.frontend.ast.models.Exists
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FromSource
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.InSubquery
import org.compiler.frontend.ast.models.Insert
import org.compiler.frontend.ast.models.IsNull
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.SelectTableAll
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.TableSource
import org.compiler.frontend.ast.models.Unary
import org.compiler.frontend.ast.models.Update
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.DeclareResult
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import org.compiler.storage.CsvReader
import org.compiler.storage.DataDirectory
import org.compiler.types.ErrorType
import java.io.IOException

/**
 * Arma el arbol de ambitos de un script, resuelve contra el catalogo las tablas
 * de cada FROM y contra esas tablas cada columna que se nombra.
 *
 * Nunca corta: un nombre que no existe se reporta y el recorrido sigue, para que
 * una corrida liste todos los nombres malos de una vez y no el primero.
 */
class NameResolver(
    private val catalog: Catalog,
    private val directory: DataDirectory,
    private val diagnostics: Diagnostics
) {

    // El ambito raiz, con la base como estaba al arrancar. Es lo que el IDE dibuja.
    private val global = Scope(ScopeKind.GLOBAL, GLOBAL_SCOPE, parent = null)

    // El catalogo como va quedando: un script puede crear una tabla y usarla en la
    // linea siguiente.
    private var working = catalog

    // Una tabla se revisa una sola vez por corrida, aunque el script la use diez.
    private val checked = mutableSetOf<String>()

    init {
        catalog.tables.values.forEach { table ->
            global.declare(
                Symbol(
                    name = table.name,
                    kind = DeclarationKind.TABLE,
                    type = ErrorType,
                    location = CATALOG_LOCATION,
                    scopeName = GLOBAL_SCOPE,
                    sourceTable = table.name,
                    table = table
                )
            )
        }
    }

    fun resolve(script: Script): Scope {
        script.statements.forEach { statement ->
            when (statement) {
                is Query -> resolveQuery(statement, global, emptyList())
                is Insert, is Update, is Delete -> resolveStatement(statement)
                else -> {}
            }
            working = working.after(statement)
        }
        return global
    }

    /**
     * El WHERE de un UPDATE o un DELETE nombra columnas de su tabla, igual que el
     * de un SELECT, solo que sin alias ni JOIN.
     *
     * Que la tabla no exista lo reporta la fase 5: si ademas se resolvieran sus
     * columnas, un nombre de tabla mal escrito daria un error por cada columna.
     */
    private fun resolveStatement(statement: Statement) {
        when (statement) {
            is Insert -> {
                checkStructure(statement.table, statement.location)
                // Un INSERT no puede nombrar columnas de la tabla que llena, pero
                // si traer una subconsulta.
                statement.rows.flatten().forEach { resolveExpression(it, global, emptyList(), Clause("VALUES")) }
            }

            is Update -> {
                val scope = tableScope(statement.table, statement.location) ?: return
                statement.assignments.forEach { resolveExpression(it.value, scope, emptyList(), Clause("SET")) }
                resolveExpression(statement.where, scope, emptyList(), Clause("WHERE"))
            }

            is Delete -> {
                val scope = tableScope(statement.table, statement.location) ?: return
                resolveExpression(statement.where, scope, emptyList(), Clause("WHERE"))
            }

            else -> {}
        }
    }

    private fun tableScope(name: String, location: LexemeLocation): Scope? {
        val table = working.table(name) ?: return null

        checkStructure(name, location)
        val scope = global.openChild(ScopeKind.QUERY, "statement@${location.line}")
        declare(scope, name, DeclarationKind.TABLE, table, offset = 0, location = location)
        return scope
    }

    /**
     * El orden importa: primero el FROM, porque todo lo demas se resuelve contra
     * el, y el ORDER BY de ultimo, porque es el unico que ademas ve los alias del
     * SELECT y para eso el SELECT ya tiene que estar resuelto.
     *
     * `enclosing` son las consultas que contienen a esta, de la mas externa a la
     * mas interna. Solo sirve para marcar correlacion.
     */
    private fun resolveQuery(query: Query, parent: Scope, enclosing: List<Query>) {
        val scope = parent.openChild(ScopeKind.QUERY, "query@${query.location.line}")
        query.scope = scope
        val stack = enclosing + query
        val aliases = query.selection.filterIsInstance<SelectExpression>().mapNotNull { it.alias }.toSet()

        // El desplazamiento se acumula: en un JOIN la fila de trabajo es la
        // concatenacion de las dos, y el segundo origen arranca donde acaba el primero.
        var offset = declareSource(query.source, scope, offset = 0)
        query.joins.forEach { offset = declareSource(it.source, scope, offset) }

        // El SELECT va con el conjunto vacio: un alias no se puede usar en la
        // misma lista donde se define.
        query.selection.filterIsInstance<SelectExpression>()
            .forEach { resolveExpression(it.expression, scope, stack, Clause("el SELECT")) }
        query.selection.filterIsInstance<SelectTableAll>().forEach { checkQualifier(it, scope) }

        query.joins.forEach { resolveExpression(it.condition, scope, stack, Clause("la condicion del JOIN", aliases)) }
        resolveExpression(query.where, scope, stack, Clause("WHERE", aliases))
        query.groupBy.forEach { resolveExpression(it, scope, stack, Clause("GROUP BY", aliases)) }
        resolveExpression(query.having, scope, stack, Clause("HAVING", aliases))

        val output = schemaOf(query)
        query.orderBy.forEach {
            resolveExpression(it.expression, scope, stack, Clause("ORDER BY", aliases, output))
        }
    }

    // ── El FROM ────────────────────────────────────────────────────────────

    private fun declareSource(source: FromSource, scope: Scope, offset: Int): Int = when (source) {
        is TableSource -> declareTable(source, scope, offset)
        is DerivedSource -> declareDerived(source, scope, offset)
    }

    /**
     * La tabla se busca en el catalogo y no subiendo por los ambitos: un alias de
     * la consulta de afuera no es una tabla, y `FROM u` tiene que fallar aunque
     * afuera exista `FROM users u`.
     */
    private fun declareTable(source: TableSource, scope: Scope, offset: Int): Int {
        val table = working.table(source.table)
        if (table == null) {
            report(source.location, "la tabla '${source.table}' no existe")
            return offset
        }

        checkStructure(source.table, source.location)
        declare(
            scope = scope,
            name = source.alias ?: source.table,
            kind = if (source.alias != null) DeclarationKind.ALIAS else DeclarationKind.TABLE,
            table = table,
            offset = offset,
            location = source.location
        )
        return offset + table.columns.size
    }

    /**
     * Una tabla derivada se resuelve ENTERA primero y recien ahi se le pregunta
     * que columnas produce. No es un ciclo: cuando se llama a schemaOf, su FROM ya
     * quedo resuelto.
     *
     * Su ambito cuelga del global y no del de afuera, porque se ejecuta una sola
     * vez: si pudiera mirar columnas de la consulta que la contiene, el motor
     * tendria que volver a correrla por cada fila.
     */
    private fun declareDerived(source: DerivedSource, scope: Scope, offset: Int): Int {
        resolveQuery(source.query, global, emptyList())

        val columns = schemaOf(source.query).map { Column(it.name.orEmpty(), it.type, emptyList()) }
        declare(
            scope = scope,
            name = source.alias,
            kind = DeclarationKind.ALIAS,
            table = Table(source.alias, columns),
            offset = offset,
            location = source.location
        )
        return offset + columns.size
    }

    private fun declare(
        scope: Scope,
        name: String,
        kind: DeclarationKind,
        table: Table,
        offset: Int,
        location: LexemeLocation
    ) {
        val symbol = Symbol(
            name = name,
            kind = kind,
            type = ErrorType,
            location = location,
            scopeName = scope.name,
            sourceTable = table.name,
            index = offset,
            table = table
        )
        if (scope.declare(symbol) is DeclareResult.AlreadyDeclared) {
            report(location, "el nombre '$name' se repite en el FROM")
        }
    }

    // ── Las columnas ───────────────────────────────────────────────────────

    private fun resolveExpression(expression: Expression?, scope: Scope, stack: List<Query>, clause: Clause) {
        when (expression) {
            null, is Literal -> {}
            is ColumnReference -> resolveReference(expression, scope, stack, clause)
            is Query -> resolveQuery(expression, scope, stack)
            is Binary -> {
                resolveExpression(expression.left, scope, stack, clause)
                resolveExpression(expression.right, scope, stack, clause)
            }
            is Unary -> resolveExpression(expression.operand, scope, stack, clause)
            is IsNull -> resolveExpression(expression.operand, scope, stack, clause)
            is InList -> {
                resolveExpression(expression.operand, scope, stack, clause)
                expression.values.forEach { resolveExpression(it, scope, stack, clause) }
            }
            is InSubquery -> {
                resolveExpression(expression.operand, scope, stack, clause)
                resolveQuery(expression.query, scope, stack)
            }
            is Exists -> resolveQuery(expression.query, scope, stack)
            is Aggregate -> resolveExpression(expression.argument, scope, stack, clause)
        }
    }

    private fun resolveReference(reference: ColumnReference, scope: Scope, stack: List<Query>, clause: Clause) {
        // Solo el ORDER BY llega con un esquema de salida, y solo un nombre sin
        // calificar puede ser un alias del SELECT.
        if (reference.qualifier == null) {
            val position = clause.output.indexOfFirst { it.name == reference.name }
            if (position >= 0) {
                bindOutputAlias(reference, clause.output[position], position, scope)
                return
            }
        }

        val matches = scope.findColumn(reference.qualifier, reference.name)
        when (matches.size) {
            1 -> bind(reference, matches.single(), stack)
            0 -> reportMissing(reference, scope, clause)
            else -> report(
                reference.location,
                "'${reference.name}' existe en " + matches.joinToString(" y en ") { "'${it.source.sourceTable}'" }
            )
        }
    }

    private fun bind(reference: ColumnReference, match: ColumnMatch, stack: List<Query>) {
        reference.symbol = match.toSymbol(reference.location)
        reference.level = match.level
        reference.index = match.index
        markCorrelated(stack, match.level)
    }

    /**
     * Un alias del SELECT no nombra una columna de la fila que entra sino de la que
     * sale, asi que el simbolo queda como ALIAS y su indice es el de la fila ya
     * proyectada. La fase 6 mira el `kind` para saber en cual de las dos indexar.
     */
    private fun bindOutputAlias(
        reference: ColumnReference,
        column: ResultColumn,
        position: Int,
        scope: Scope
    ) {
        reference.symbol = Symbol(
            name = reference.name,
            kind = DeclarationKind.ALIAS,
            type = column.type,
            location = reference.location,
            scopeName = scope.name,
            index = position
        )
        reference.level = 0
        reference.index = position
    }

    /**
     * Una referencia que subio de ambito correlaciona a TODAS las consultas por
     * las que paso, no solo a la suya: ninguna de ellas se puede ejecutar una vez
     * y guardar, porque todas cambian con la fila de afuera.
     */
    private fun markCorrelated(stack: List<Query>, level: Int) {
        stack.takeLast(level).forEach { it.correlated = true }
    }

    private fun reportMissing(reference: ColumnReference, scope: Scope, clause: Clause) {
        val qualifier = reference.qualifier
        if (qualifier == null) {
            // El nombre existe, pero todavia no: el SELECT se calcula despues del
            // WHERE, asi que decirlo asi ahorra la pregunta.
            val message = if (reference.name in clause.aliases) {
                "'${reference.name}' se define en el SELECT y no esta disponible en ${clause.name}"
            } else {
                "no existe la columna '${reference.name}'"
            }
            report(reference.location, message)
            return
        }

        val source = scope.findSource(qualifier)
        if (source == null) {
            report(reference.location, "no hay ninguna tabla llamada '$qualifier'")
        } else {
            report(reference.location, "'${source.sourceTable}' no tiene columna '${reference.name}'")
        }
    }

    // `SELECT u.*` con una `u` que nadie puso en el FROM. Sin esto, la columna
    // simplemente no saldria y nadie diria por que.
    private fun checkQualifier(item: SelectTableAll, scope: Scope) {
        if (scope.findSource(item.qualifier) == null) {
            report(item.location, "no hay ninguna tabla llamada '${item.qualifier}'")
        }
    }

    // ── La guarda de cordura ───────────────────────────────────────────────

    /**
     * Compara el encabezado del CSV contra las columnas del esquema.
     *
     * El DBMS es el unico que escribe, asi que esto no deberia pasar nunca. Existe
     * para que cuando alguien edite el archivo a mano, el mensaje diga que paso en
     * vez de reventar tres etapas mas adelante con otra cosa.
     */
    private fun checkStructure(name: String, location: LexemeLocation) {
        if (!checked.add(name)) return

        // Se compara contra el esquema que hay EN DISCO, no contra el que va
        // quedando: despues de un ALTER, el archivo todavia tiene las columnas
        // viejas y eso es correcto hasta que se vuelque. Una tabla creada en este
        // mismo script ni siquiera tiene archivo.
        val table = catalog.table(name) ?: return
        val file = directory.csv(name)
        if (!file.isFile) return

        val header = try {
            CsvReader.header(file)
        } catch (error: IOException) {
            report(location, "${file.name} no se pudo leer: ${error.message}")
            return
        }

        val expected = table.columns.map { it.name }
        if (header == expected) return

        val extra = header.firstOrNull { it !in expected }
        val missing = expected.firstOrNull { it !in header }
        val detail = when {
            extra != null -> "el archivo tiene la columna '$extra', que no esta en el esquema"
            missing != null -> "al archivo le falta la columna '$missing'"
            else -> "las columnas del archivo estan en otro orden"
        }
        report(location, "${file.name} fue editado fuera del DBMS: $detail")
    }

    private fun report(location: LexemeLocation, message: String) {
        diagnostics.report(CompilerError.SemanticError(location, message))
    }

    /**
     * Donde se esta resolviendo.
     *
     * El nombre sale en el mensaje de un alias usado antes de tiempo, y el esquema
     * de salida solo lo lleva el ORDER BY, que es la unica clausula que corre
     * despues del SELECT.
     */
    private class Clause(
        val name: String,
        val aliases: Set<String> = emptySet(),
        val output: List<ResultColumn> = emptyList()
    )

    private companion object {
        const val GLOBAL_SCOPE = "global"

        // Una tabla del catalogo no se escribio en el script, asi que no tiene
        // posicion real.
        val CATALOG_LOCATION = LexemeLocation(1, 1)
    }
}
