package org.compiler.frontend.semantic

import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Catalog
import org.compiler.catalog.Column
import org.compiler.catalog.Constraint
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.Nullable
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Table
import org.compiler.catalog.Unique
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity
import org.compiler.frontend.ast.models.Aggregate
import org.compiler.frontend.ast.models.AggregateFunction
import org.compiler.frontend.ast.models.AlterTableAdd
import org.compiler.frontend.ast.models.AlterTableDrop
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnDefinition
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DerivedSource
import org.compiler.frontend.ast.models.DropTable
import org.compiler.frontend.ast.models.Exists
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FromSource
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.InSubquery
import org.compiler.frontend.ast.models.Insert
import org.compiler.frontend.ast.models.hasAggregate
import org.compiler.frontend.ast.models.IsNull
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.Unary
import org.compiler.frontend.ast.models.Update
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.models.LexemeLocation
import org.compiler.storage.DataDirectory
import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DecimalType
import org.compiler.types.ErrorType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.NullType
import org.compiler.types.StringValue
import org.compiler.types.Type
import org.compiler.types.TypeRules
import org.compiler.types.UnaryOperator
import org.compiler.types.ValueCodec
import org.compiler.types.VarcharType

/**
 * Tipa cada expresion y verifica las reglas propias de SQL.
 *
 * Corre despues del NameResolver, que ya dejo cada nombre apuntando a su columna.
 * Aqui no se busca nada: se pregunta si lo que se hace con lo encontrado tiene
 * sentido.
 *
 * Nunca corta. Los errores salen todos juntos, y `ErrorType` se propaga sin
 * reportar de nuevo para que `(1 + 'a') * 2` de un solo mensaje.
 */
class SqlChecker(
    initial: Catalog,
    private val directory: DataDirectory,
    private val diagnostics: Diagnostics
) {

    // El catalogo como va quedando: una tabla creada arriba existe mas abajo.
    private var catalog = initial

    // El WHERE corre antes de agrupar, asi que ahi una agregacion no tiene de
    // donde salir.
    private var aggregatesAllowed = true

    // La agregacion que se esta tipando, para atrapar SUM(COUNT(x)).
    private var enclosingAggregate: AggregateFunction? = null

    fun check(script: Script) {
        script.statements.forEach { statement ->
            when (statement) {
                is Query -> checkQuery(statement)
                is Insert -> checkInsert(statement)
                is Update -> checkUpdate(statement)
                is Delete -> checkDelete(statement)
                is CreateTable -> checkCreateTable(statement)
                is AlterTableAdd -> checkAlterAdd(statement)
                is AlterTableDrop -> checkAlterDrop(statement)
                is DropTable -> checkDropTable(statement)
            }
            catalog = catalog.after(statement)
        }
    }

    // ── La consulta ────────────────────────────────────────────────────────

    /**
     * El orden sigue al de evaluacion de SQL, no al de escritura: FROM, ON,
     * WHERE, GROUP BY, HAVING, SELECT y ORDER BY.
     *
     * De ahi sale casi todo lo demas, empezando por que un alias del SELECT se
     * pueda usar en el ORDER BY y no en el WHERE.
     */
    private fun checkQuery(query: Query) {
        checkSource(query.source, query)
        query.joins.forEach { checkSource(it.source, query) }
        refreshReferences(query)

        query.joins.forEach { expect(it.condition, "la condicion del JOIN") }
        withoutAggregates { expect(query.where, "WHERE") }
        query.groupBy.forEach { typeOf(it) }
        query.selection.filterIsInstance<SelectExpression>().forEach { typeOf(it.expression) }
        expect(query.having, "HAVING")

        refreshAliases(query)
        query.orderBy.forEach { typeOf(it.expression) }

        checkAggregation(query)
        checkOutputNames(query)
        checkLimit(query)
    }

    /**
     * Una tabla derivada se tipa primero y recien ahi se sabe de que tipo son sus
     * columnas calculadas: la fase 4 las dejo en ErrorType porque todavia no se
     * tipaba nada.
     */
    private fun checkSource(source: FromSource, query: Query) {
        if (source !is DerivedSource) return
        checkQuery(source.query)

        val schema = schemaOf(source.query)
        schema.forEachIndexed { position, column ->
            if (column.name == null) {
                report(source.location, "la columna ${position + 1} de la subconsulta no tiene nombre, use AS")
            }
        }

        val scope = query.scope ?: return
        val symbol = scope.lookupLocal(source.alias) ?: return
        val columns = schema.map { Column(it.name.orEmpty(), it.type, emptyList()) }
        scope.redeclare(symbol.copy(table = Table(source.alias, columns)))
    }

    // Las referencias a una tabla derivada quedaron pegadas a su esquema viejo.
    private fun refreshReferences(query: Query) {
        val scope = query.scope ?: return
        query.expressions().flatMap { it.references() }
            .filter { it.symbol?.type == ErrorType }
            .forEach { reference ->
                scope.findColumn(reference.qualifier, reference.name).singleOrNull()?.let {
                    reference.symbol = it.toSymbol(reference.location)
                }
            }
    }

    // Un alias del SELECT solo tiene tipo cuando el SELECT quedo tipado.
    private fun refreshAliases(query: Query) {
        val schema = schemaOf(query)
        query.orderBy.mapNotNull { it.expression as? ColumnReference }.forEach { reference ->
            val symbol = reference.symbol ?: return@forEach
            if (symbol.kind != DeclarationKind.ALIAS) return@forEach
            schema.getOrNull(symbol.index)?.let { reference.symbol = symbol.copy(type = it.type) }
        }
    }

    private fun checkLimit(query: Query) {
        val limit = query.limit ?: return
        if (limit <= 0) {
            report(query.location, "LIMIT espera un entero mayor que cero")
        } else if (query.orderBy.isEmpty()) {
            warn(query.location, "LIMIT sin ORDER BY no garantiza que filas salen")
        }
    }

    // Dos columnas del resultado con el mismo nombre no se pueden distinguir desde
    // afuera. Solo se revisan los alias escritos: que `SELECT *` sobre un join
    // repita un nombre de columna es normal.
    private fun checkOutputNames(query: Query) {
        val seen = mutableSetOf<String>()
        query.selection.filterIsInstance<SelectExpression>().forEach { item ->
            val alias = item.alias ?: return@forEach
            if (!seen.add(alias)) report(item.location, "hay dos columnas llamadas '$alias' en el resultado")
        }
    }

    // ── Agregacion ─────────────────────────────────────────────────────────

    /**
     * En una consulta agregada, toda columna tiene que estar en el GROUP BY o
     * dentro de una agregacion.
     *
     * Si un departamento tiene cinco empleados hay un grupo y cinco nombres: la
     * consulta no dice cual de los cinco deberia salir, asi que no tiene respuesta.
     */
    private fun checkAggregation(query: Query) {
        val calls = query.selection.any { it.hasAggregate() } || query.having?.hasAggregate() == true
        if (query.having != null && query.groupBy.isEmpty() && !calls) {
            report(query.having.location, "HAVING solo tiene sentido con GROUP BY")
        }
        if (query.groupBy.isEmpty() && !calls) return

        val grouped = query.groupBy.filterIsInstance<ColumnReference>().map { it.level to it.index }.toSet()
        query.selection.filterNot { it is SelectExpression }.forEach {
            report(it.location, "'*' no se puede usar en una consulta agrupada, nombre las columnas")
        }

        val free = query.selection.filterIsInstance<SelectExpression>().flatMap { it.expression.freeReferences() } +
            query.having?.freeReferences().orEmpty() +
            query.orderBy.flatMap { it.expression.freeReferences() }

        free.filter { it.level == 0 && it.symbol?.kind == DeclarationKind.COLUMN }
            .filterNot { (it.level to it.index) in grouped }
            .forEach { report(it.location, "'${it.name}' no esta en el GROUP BY ni dentro de una agregacion") }
    }

    private fun aggregateType(expression: Aggregate): Type {
        if (!aggregatesAllowed) {
            report(expression.location, "WHERE no puede usar funciones de agregacion, se esperaba HAVING")
        }
        enclosingAggregate?.let {
            report(expression.location, "no se puede anidar ${expression.function.label} dentro de ${it.label}")
        }

        val previous = enclosingAggregate
        enclosingAggregate = expression.function
        val argument = expression.argument?.let { typeOf(it) } ?: NullType
        enclosingAggregate = previous

        val function = expression.function
        if (argument == ErrorType) return if (function == AggregateFunction.COUNT_ALL) IntType else ErrorType

        return when (function) {
            AggregateFunction.COUNT, AggregateFunction.COUNT_ALL -> IntType

            AggregateFunction.SUM -> argument.takeIf { numeric(it) }
                ?: fail(expression.location, "SUM espera un tipo numerico, recibio ${argument.name}")

            AggregateFunction.AVG -> FloatType.takeIf { numeric(argument) }
                ?: fail(expression.location, "AVG espera un tipo numerico, recibio ${argument.name}")

            // El orden de BOOLEAN no significa nada, asi que su minimo tampoco.
            AggregateFunction.MIN, AggregateFunction.MAX -> argument.takeIf { ordered(it) }
                ?: fail(expression.location, "${function.label} no acepta ${argument.name}")
        }
    }

    private fun numeric(type: Type) = TypeRules.familyOf(type) == TypeRules.Family.NUMERIC

    private fun ordered(type: Type) = TypeRules.familyOf(type) in setOf(
        TypeRules.Family.NUMERIC,
        TypeRules.Family.CHARACTER,
        TypeRules.Family.TEMPORAL
    )

    private fun withoutAggregates(block: () -> Unit) {
        aggregatesAllowed = false
        block()
        aggregatesAllowed = true
    }

    // ── Tipado ─────────────────────────────────────────────────────────────

    /**
     * El tipo sube de los hijos al padre: es el atributo sintetizado del proyecto.
     * El ambito, en cambio, bajo de arriba hacia abajo en la fase 4.
     */
    private fun typeOf(expression: Expression): Type {
        val type = when (expression) {
            is Literal -> expression.literalType
            is ColumnReference -> expression.symbol?.type ?: ErrorType
            is Binary -> binaryType(expression)
            is Unary -> unaryType(expression)
            is IsNull -> { typeOf(expression.operand); TypeRules.isNullResultType() }
            is InList -> inListType(expression)
            is InSubquery -> inSubqueryType(expression)
            is Exists -> { checkQuery(expression.query); BooleanType }
            is Aggregate -> aggregateType(expression)
            is Query -> scalarType(expression)
        }
        expression.type = type
        return type
    }

    private fun binaryType(expression: Binary): Type {
        val left = typeOf(expression.left)
        val right = typeOf(expression.right)
        warnNullComparison(expression, left, right)

        return TypeRules.binaryResultType(expression.operator, left, right)
            ?: fail(
                expression.location,
                "no se puede aplicar ${expression.operator.symbol} entre ${left.name} y ${right.name}"
            )
    }

    // `x = NULL` da NULL, no true, asi que la condicion nunca se cumple. El motor
    // no lo puede detectar porque tecnicamente es valido.
    private fun warnNullComparison(expression: Binary, left: Type, right: Type) {
        val comparesNull = (expression.left is Literal && left == NullType) ||
            (expression.right is Literal && right == NullType)
        if (comparesNull) {
            warn(expression.location, "${expression.operator.symbol} NULL nunca es verdadero, se esperaba IS NULL")
        }
    }

    private fun unaryType(expression: Unary): Type {
        val operand = typeOf(expression.operand)
        return TypeRules.unaryResultType(expression.operator, operand)
            ?: fail(expression.location, operandMessage(expression.operator, operand))
    }

    private fun operandMessage(operator: UnaryOperator, operand: Type) =
        if (operator == UnaryOperator.NEGATE) "no se puede negar ${operand.name}, se esperaba un tipo numerico"
        else "NOT espera BOOLEAN, recibio ${operand.name}"

    private fun inListType(expression: InList): Type {
        val operand = typeOf(expression.operand)
        expression.values.forEach { value ->
            val type = typeOf(value)
            if (TypeRules.unify(operand, type) == null) {
                report(value.location, "no se puede comparar ${operand.name} con ${type.name}")
            }
        }
        return BooleanType
    }

    private fun inSubqueryType(expression: InSubquery): Type {
        val operand = typeOf(expression.operand)
        val column = singleColumnOf(expression.query, expression.location) ?: return BooleanType

        if (TypeRules.unify(operand, column.type) == null) {
            report(expression.location, "no se puede comparar ${operand.name} con ${column.type.name}")
        }
        return BooleanType
    }

    private fun scalarType(query: Query): Type =
        singleColumnOf(query, query.location)?.type ?: ErrorType

    /**
     * Que devuelva a lo sumo una FILA no se puede saber sin leer los datos, asi
     * que eso lo verifica el motor. La forma si se valida aqui.
     */
    private fun singleColumnOf(query: Query, location: LexemeLocation): ResultColumn? {
        checkQuery(query)

        val schema = schemaOf(query)
        if (schema.size == 1) return schema.single()
        report(location, "la subconsulta debe devolver una sola columna, devuelve ${schema.size}")
        return null
    }

    private fun expect(expression: Expression?, clause: String) {
        if (expression == null) return

        val actual = typeOf(expression)
        if (actual == BooleanType || actual == ErrorType || actual == NullType) return
        report(expression.location, "$clause espera BOOLEAN, recibio ${actual.name}")
    }

    // ── INSERT, UPDATE, DELETE ─────────────────────────────────────────────

    private fun checkInsert(statement: Insert) {
        val table = tableOf(statement.table, statement.location) ?: return
        val columns = targetColumns(statement, table) ?: return

        statement.rows.forEach { row ->
            if (row.size != columns.size) {
                report(statement.location, "se esperaban ${columns.size} valores, se recibieron ${row.size}")
                return@forEach
            }
            row.forEachIndexed { position, value -> checkValue(value, columns[position]) }
        }

        table.columns.filterNot { it in columns }.forEach { column ->
            if (!column.nullable && !column.hasConstraint<AutoIncrement>() && column.defaultValue == null) {
                report(statement.location, "la columna '${column.name}' no acepta nulos y no tiene valor por omision")
            }
        }
    }

    // Las columnas que recibe la fila. Sin nombrarlas son todas, en el orden del
    // esquema, que es el mismo del CSV.
    private fun targetColumns(statement: Insert, table: Table): List<Column>? {
        val names = statement.columns ?: return table.columns

        val seen = mutableSetOf<String>()
        return names.map { name ->
            if (!seen.add(name)) {
                report(statement.location, "la columna '$name' se nombra dos veces")
                return null
            }
            table.column(name) ?: run {
                report(statement.location, "'${table.name}' no tiene columna '$name'")
                return null
            }
        }
    }

    private fun checkUpdate(statement: Update) {
        val table = tableOf(statement.table, statement.location) ?: return

        val seen = mutableSetOf<String>()
        statement.assignments.forEach { assignment ->
            val column = table.column(assignment.column)
            if (column == null) {
                report(assignment.location, "'${table.name}' no tiene columna '${assignment.column}'")
                return@forEach
            }
            if (!seen.add(assignment.column)) {
                report(assignment.location, "la columna '${assignment.column}' se asigna dos veces")
            }
            if (column.hasConstraint<AutoIncrement>()) {
                report(assignment.location, "'${column.name}' es AUTOINCREMENT y no se puede asignar")
            }
            checkValue(assignment.value, column)
        }

        checkFilter(statement.where, table, statement.location)
    }

    private fun checkDelete(statement: Delete) {
        val table = tableOf(statement.table, statement.location) ?: return
        checkFilter(statement.where, table, statement.location)
    }

    // Sin WHERE la sentencia toca la tabla entera. Es valido y a veces es lo que
    // se quiere, asi que avisa y no detiene el script.
    private fun checkFilter(where: Expression?, table: Table, location: LexemeLocation) {
        if (where == null) {
            warn(location, "esto afecta todas las filas de '${table.name}'")
            return
        }
        withoutAggregates { expect(where, "WHERE") }
    }

    private fun checkValue(value: Expression, column: Column) {
        val type = typeOf(value)
        if (type == NullType) {
            if (!column.nullable) report(value.location, "la columna '${column.name}' no acepta nulos")
            return
        }
        if (!TypeRules.isAssignable(type, column.type)) {
            report(
                value.location,
                "no se puede guardar ${type.name} en la columna '${column.name}' de tipo ${column.type.name}"
            )
            return
        }
        checkLength(value, column.type)
    }

    /**
     * El largo se revisa solo sobre literales. `name || apellido` no tiene largo
     * conocido hasta ejecutarlo, y ese caso lo atrapa la validacion de escritura.
     */
    private fun checkLength(value: Expression, target: Type) {
        val text = ((value as? Literal)?.value as? StringValue)?.value ?: return
        val limit = when (target) {
            is CharType -> target.length
            is VarcharType -> target.maxLength
            else -> return
        }
        if (text.length > limit) report(value.location, "'$text' no cabe en ${target.name}")
    }

    // ── CREATE, ALTER, DROP ────────────────────────────────────────────────

    private fun checkCreateTable(statement: CreateTable) {
        if (catalog.table(statement.name) != null) {
            report(statement.location, "la tabla '${statement.name}' ya existe")
        }

        val seen = mutableSetOf<String>()
        statement.columns.forEach { definition ->
            if (!seen.add(definition.name)) {
                report(definition.location, "'${statement.name}' declara dos columnas llamadas '${definition.name}'")
            }
            checkDefinition(definition, statement.name)
        }

        if (statement.columns.count { it.has<PrimaryKey>() } > 1) {
            report(statement.location, "'${statement.name}' declara dos claves primarias")
        }
    }

    private fun checkAlterAdd(statement: AlterTableAdd) {
        val table = tableOf(statement.table, statement.location) ?: return
        val definition = statement.column

        if (table.column(definition.name) != null) {
            report(definition.location, "'${table.name}' ya tiene una columna '${definition.name}'")
            return
        }
        checkDefinition(definition, table.name)

        // La unica regla del DDL que necesita mirar los datos: sin valor por
        // omision, las filas que ya estan se quedarian con un nulo prohibido.
        val rejectsNull = definition.has<NotNull>() || definition.has<PrimaryKey>()
        if (!rejectsNull || definition.constraints.any { it is Default }) return

        val rows = rowCount(table.name)
        if (rows > 0) {
            report(
                definition.location,
                "agregar '${definition.name}' NOT NULL dejaria $rows filas invalidas, use DEFAULT"
            )
        }
    }

    private fun checkAlterDrop(statement: AlterTableDrop) {
        val table = tableOf(statement.table, statement.location) ?: return
        if (table.column(statement.column) == null) {
            report(statement.location, "'${table.name}' no tiene columna '${statement.column}'")
            return
        }

        if (table.columns.size == 1) {
            report(statement.location, "una tabla no puede quedarse sin columnas")
        }
        referenceTo(table.name, statement.column)?.let {
            report(statement.location, "'${table.name}.${statement.column}' es referenciada por '$it'")
        }
    }

    private fun checkDropTable(statement: DropTable) {
        val table = tableOf(statement.table, statement.location) ?: return
        referenceTo(table.name)?.let {
            report(statement.location, "'${table.name}' es referenciada por '$it'")
        }
    }

    private fun checkDefinition(definition: ColumnDefinition, owner: String) {
        checkTypeShape(definition)

        if (definition.has<NotNull>() && definition.has<Nullable>()) {
            report(definition.location, "'${definition.name}' se declara NOT NULL y NULL a la vez")
        }
        if (definition.has<AutoIncrement>() && definition.type != IntType) {
            report(definition.location, "AUTOINCREMENT solo aplica a INT")
        }

        val default = definition.constraints.filterIsInstance<Default>().firstOrNull()
        if (default != null && definition.has<AutoIncrement>()) {
            report(definition.location, "'${definition.name}' no puede tener DEFAULT y AUTOINCREMENT a la vez")
        }
        if (default != null && definition.type != ErrorType &&
            ValueCodec.decode(default.value, definition.type) == null
        ) {
            report(definition.location, "el valor por omision de '${definition.name}' no es ${definition.type.name}")
        }

        definition.constraints.filterIsInstance<ForeignKey>().forEach { checkForeignKey(definition, it, owner) }
    }

    private fun checkTypeShape(definition: ColumnDefinition) {
        val type = definition.type
        val invalid = when (type) {
            is DecimalType -> when {
                type.precision <= 0 -> "la precision debe ser mayor que cero"
                type.scale < 0 -> "la escala no puede ser negativa"
                type.scale > type.precision -> "la escala excede la precision"
                else -> null
            }
            is CharType -> "el largo debe ser mayor que cero".takeIf { type.length <= 0 }
            is VarcharType -> "el largo debe ser mayor que cero".takeIf { type.maxLength <= 0 }
            else -> null
        }
        if (invalid != null) report(definition.location, "${type.name} no es valido: $invalid")
    }

    /**
     * Una FK tiene que apuntar a algo con lo que se pueda identificar una fila: si
     * la columna destino no es unica, un valor podria casar con varias.
     */
    private fun checkForeignKey(definition: ColumnDefinition, reference: ForeignKey, owner: String) {
        val source = "'$owner.${definition.name}'"
        val target = catalog.table(reference.table)
        if (target == null) {
            report(definition.location, "$source referencia la tabla '${reference.table}', que no existe")
            return
        }

        val column = target.column(reference.column)
        if (column == null) {
            report(
                definition.location,
                "$source referencia la columna '${reference.table}.${reference.column}', que no existe"
            )
            return
        }

        if (!column.hasConstraint<PrimaryKey>() && !column.hasConstraint<Unique>()) {
            report(definition.location, "$source referencia '${reference.table}.${reference.column}', que no es unica")
        }
        if (definition.type != column.type) {
            report(
                definition.location,
                "$source es ${definition.type.name} y '${reference.table}.${reference.column}' es ${column.type.name}"
            )
        }
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────

    private fun tableOf(name: String, location: LexemeLocation): Table? =
        catalog.table(name) ?: run {
            report(location, "la tabla '$name' no existe")
            null
        }

    // Quien apunta a esa tabla, o a esa columna si se nombra. Una tabla que se
    // referencia a si misma no se estorba para borrarse.
    private fun referenceTo(table: String, column: String? = null): String? = catalog.tables.values
        .filterNot { it.name == table }
        .firstNotNullOfOrNull { other ->
            other.columns.firstOrNull { candidate ->
                candidate.reference?.let { it.table == table && (column == null || it.column == column) } == true
            }?.let { "${other.name}.${it.name}" }
        }

    // Las filas que ya tiene el archivo, sin decodificar nada.
    private fun rowCount(table: String): Int {
        val file = directory.csv(table)
        if (!file.isFile) return 0
        return file.useLines(Charsets.UTF_8) { lines -> (lines.count() - 1).coerceAtLeast(0) }
    }

    private fun report(location: LexemeLocation, message: String) {
        diagnostics.report(CompilerError.SemanticError(location, message))
    }

    private fun warn(location: LexemeLocation, message: String) {
        diagnostics.report(CompilerError.SemanticError(location, message, Severity.WARNING))
    }

    private fun fail(location: LexemeLocation, message: String): Type {
        report(location, message)
        return ErrorType
    }
}

// ── Recorridos sobre el AST ────────────────────────────────────────────────

private inline fun <reified R : Constraint> ColumnDefinition.has(): Boolean = constraints.any { it is R }

private val Column.defaultValue: Default? get() = constraints.filterIsInstance<Default>().firstOrNull()

// Las expresiones propias de la consulta, sin bajar a sus subconsultas.
private fun Query.expressions(): List<Expression> =
    joins.map { it.condition } +
        selection.filterIsInstance<SelectExpression>().map { it.expression } +
        listOfNotNull(where, having) +
        groupBy +
        orderBy.map { it.expression }

// Las referencias que NO estan dentro de una agregacion: son las que el GROUP BY
// tiene que cubrir.
private fun Expression.freeReferences(): List<ColumnReference> = when (this) {
    is ColumnReference -> listOf(this)
    is Aggregate -> emptyList()
    is Binary -> left.freeReferences() + right.freeReferences()
    is Unary -> operand.freeReferences()
    is IsNull -> operand.freeReferences()
    is InList -> operand.freeReferences() + values.flatMap { it.freeReferences() }
    is InSubquery -> operand.freeReferences()
    else -> emptyList()
}

private fun Expression.references(): List<ColumnReference> = when (this) {
    is ColumnReference -> listOf(this)
    is Aggregate -> argument?.references().orEmpty()
    is Binary -> left.references() + right.references()
    is Unary -> operand.references()
    is IsNull -> operand.references()
    is InList -> operand.references() + values.flatMap { it.references() }
    is InSubquery -> operand.references()
    else -> emptyList()
}
