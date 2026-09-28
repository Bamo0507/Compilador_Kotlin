package org.compiler.frontend.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Constraint
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.NotNull
import org.compiler.catalog.Nullable
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Unique
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.*
import org.compiler.models.LexemeLocation
import org.compiler.parser.SqlBaseVisitor
import org.compiler.parser.SqlParser
import org.compiler.parser.SqlParser.*
import org.compiler.types.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

class SqlAstBuilder(private val diagnostics: Diagnostics) : SqlBaseVisitor<Node>() {

    // ══════════════════════════════════════════════════════════════════════
    //  Script y sentencias
    // ══════════════════════════════════════════════════════════════════════

    override fun visitScript(ctx: ScriptContext): Node =
        Script(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))

    override fun visitStatement(ctx: StatementContext): Node = visit(ctx.getChild(0))

    override fun visitCreateTable(ctx: CreateTableContext): Node =
        CreateTable(
            name = ctx.Identifier().text,
            columns = ctx.columnDefinition().map { visit(it) as ColumnDefinition },
            location = locationOf(ctx)
        )

    override fun visitAlterAdd(ctx: AlterAddContext): Node =
        AlterTableAdd(
            table = ctx.Identifier().text,
            column = visit(ctx.columnDefinition()) as ColumnDefinition,
            location = locationOf(ctx)
        )

    // 'ALTER' 'TABLE' Identifier 'DROP' 'COLUMN' Identifier: el primero es la tabla.
    override fun visitAlterDrop(ctx: AlterDropContext): Node =
        AlterTableDrop(
            table = ctx.Identifier(0).text,
            column = ctx.Identifier(1).text,
            location = locationOf(ctx)
        )

    override fun visitDropTable(ctx: DropTableContext): Node =
        DropTable(ctx.Identifier().text, locationOf(ctx))

    override fun visitInsert(ctx: InsertContext): Node =
        Insert(
            table = ctx.Identifier().text,

            // null si no se nombraron columnas; es la marca de "en el orden del esquema".
            columns = ctx.columnList()?.Identifier()?.map { it.text },

            rows = ctx.valuesRow().map { row -> row.expression().map { expression(it) } },
            location = locationOf(ctx)
        )

    override fun visitUpdate(ctx: UpdateContext): Node =
        Update(
            table = ctx.Identifier().text,
            assignments = ctx.assignment().map { visit(it) as Assignment },
            where = ctx.expression()?.let { expression(it) },
            location = locationOf(ctx)
        )

    override fun visitAssignment(ctx: AssignmentContext): Node =
        Assignment(ctx.Identifier().text, expression(ctx.expression()), locationOf(ctx))

    override fun visitDelete(ctx: DeleteContext): Node =
        Delete(
            table = ctx.Identifier().text,
            where = ctx.expression()?.let { expression(it) },
            location = locationOf(ctx)
        )

    // ══════════════════════════════════════════════════════════════════════
    //  DDL: columnas, tipos y restricciones
    // ══════════════════════════════════════════════════════════════════════

    override fun visitColumnDefinition(ctx: ColumnDefinitionContext): Node =
        ColumnDefinition(
            name = ctx.Identifier().text,
            type = typeOf(ctx.type()),
            constraints = ctx.constraint().mapNotNull { constraintOf(it) },
            location = locationOf(ctx)
        )

    private fun typeOf(ctx: TypeContext): Type = when (ctx) {
        is IntTypeContext -> IntType
        is FloatTypeContext -> FloatType
        is DecimalTypeContext -> {
            val precision = intOf(ctx.IntegerLit(0))
            val scale = intOf(ctx.IntegerLit(1))
            if (precision == null || scale == null) ErrorType else DecimalType(precision, scale)
        }
        is CharTypeContext -> intOf(ctx.IntegerLit())?.let { CharType(it) } ?: ErrorType
        is VarcharTypeContext -> intOf(ctx.IntegerLit())?.let { VarcharType(it) } ?: ErrorType
        is TextTypeContext -> TextType
        is DateTypeContext -> DateType
        is TimeTypeContext -> TimeType
        is BoolTypeContext -> BooleanType
        else -> error("Tipo sin traducir: ${ctx.javaClass.simpleName}")
    }

    // null solo para `DEFAULT NULL`: ver la rama de RDefault.
    private fun constraintOf(ctx: ConstraintContext): Constraint? = when (ctx) {
        is RPrimaryKeyContext -> PrimaryKey
        is RNotNullContext -> NotNull
        is RNullContext -> Nullable
        is RUniqueContext -> Unique
        is RAutoIncrementContext -> AutoIncrement

        // 'REFERENCES' Identifier '(' Identifier ')': la tabla, luego la columna.
        is RForeignKeyContext -> ForeignKey(table = ctx.Identifier(0).text, column = ctx.Identifier(1).text)

        // Default guarda el valor como TEXTO, que es como va al .json y lo que
        // ValueCodec.decode espera al cargarlo. El texto sale de codificar el
        // literal, asi DATE '2026-01-01' queda "2026-01-01" y TRUE queda "true".
        is RDefaultContext -> {
            val literal = visit(ctx.literal()) as Literal
            ValueCodec.encode(literal.value, literal.literalType)?.let { Default(it) }
        }

        else -> error("Restriccion sin traducir: ${ctx.javaClass.simpleName}")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Consultas
    // ══════════════════════════════════════════════════════════════════════

    override fun visitQuery(ctx: QueryContext): Node =
        Query(
            distinct = hasKeyword(ctx, "DISTINCT"),
            selection = selectionOf(ctx.selectList()),
            source = visit(ctx.source()) as FromSource,
            joins = ctx.join().map { visit(it) as Join },

            where = expressionAfter(ctx, "WHERE"),
            groupBy = ctx.expressionList()?.expression()?.map { expression(it) } ?: emptyList(),
            having = expressionAfter(ctx, "HAVING"),

            orderBy = ctx.orderCriterion().map { visit(it) as OrderCriterion },

            // El unico IntegerLit directo de `query` es el del LIMIT.
            limit = ctx.IntegerLit()?.let { intOf(it) },

            location = locationOf(ctx)
        )

    private fun selectionOf(ctx: SelectListContext): List<SelectItem> = when (ctx) {
        is SelectAllContext -> listOf(SelectAll(locationOf(ctx)))
        is SelectListItemsContext -> ctx.selectItem().map { visit(it) as SelectItem }
        else -> error("Lista de SELECT sin traducir: ${ctx.javaClass.simpleName}")
    }

    override fun visitItemTableAll(ctx: ItemTableAllContext): Node =
        SelectTableAll(ctx.Identifier().text, locationOf(ctx))

    // expression ('AS'? Identifier)? — el Identifier, si esta, es el alias.
    override fun visitItemExpression(ctx: ItemExpressionContext): Node =
        SelectExpression(
            expression = expression(ctx.expression()),
            alias = ctx.Identifier()?.text,
            location = locationOf(ctx)
        )

    // Identifier ('AS'? Identifier)? — el primero es la tabla, el segundo el alias.
    override fun visitTableSource(ctx: TableSourceContext): Node =
        TableSource(
            table = ctx.Identifier(0).text,
            alias = ctx.Identifier().getOrNull(1)?.text,
            location = locationOf(ctx)
        )

    override fun visitDerivedSource(ctx: DerivedSourceContext): Node =
        DerivedSource(
            query = visit(ctx.query()) as Query,
            alias = ctx.Identifier().text,
            location = locationOf(ctx)
        )

    override fun visitJoin(ctx: JoinContext): Node =
        Join(
            source = visit(ctx.source()) as FromSource,
            condition = expression(ctx.expression()),
            location = locationOf(ctx)
        )

    override fun visitOrderCriterion(ctx: OrderCriterionContext): Node =
        OrderCriterion(
            expression = expression(ctx.expression()),
            descending = hasKeyword(ctx, "DESC"),
            location = locationOf(ctx)
        )

    // ══════════════════════════════════════════════════════════════════════
    //  Expresiones: la torre de precedencia
    // ══════════════════════════════════════════════════════════════════════

    override fun visitExpression(ctx: ExpressionContext): Node = visit(ctx.orExpression())

    override fun visitOrExpression(ctx: OrExpressionContext): Node =
        foldBinary(ctx, ctx.andExpression())

    override fun visitAndExpression(ctx: AndExpressionContext): Node =
        foldBinary(ctx, ctx.notExpression())

    override fun visitAdditiveExpression(ctx: AdditiveExpressionContext): Node =
        foldBinary(ctx, ctx.multiplicativeExpression())

    override fun visitMultiplicativeExpression(ctx: MultiplicativeExpressionContext): Node =
        foldBinary(ctx, ctx.unaryExpression())

    // 'NOT'? comparisonExpression — sin NOT, colapsa al hijo.
    override fun visitNotExpression(ctx: NotExpressionContext): Node {
        val operand = expression(ctx.comparisonExpression())
        if (!hasKeyword(ctx, "NOT")) return operand
        return Unary(UnaryOperator.NOT, operand, locationOf(ctx))
    }

    // '-'? primaryExpression — sin signo, colapsa al hijo.
    override fun visitUnaryExpression(ctx: UnaryExpressionContext): Node {
        val operand = expression(ctx.primaryExpression())
        if (!hasKeyword(ctx, "-")) return operand
        return Unary(UnaryOperator.NEGATE, operand, locationOf(ctx))
    }

    // additiveExpression (comparisonOp additiveExpression)? — sin operador, colapsa.
    override fun visitCmpBinary(ctx: CmpBinaryContext): Node {
        val left = expression(ctx.additiveExpression(0))
        val operator = ctx.comparisonOp() ?: return left

        return Binary(
            operator = binaryOperatorOf(operator.getChild(0) as TerminalNode),
            left = left,
            right = expression(ctx.additiveExpression(1)),
            location = left.location
        )
    }

    override fun visitCmpIsNull(ctx: CmpIsNullContext): Node =
        IsNull(
            operand = expression(ctx.additiveExpression()),
            negated = hasKeyword(ctx, "NOT"),
            location = locationOf(ctx)
        )

    override fun visitCmpInSubquery(ctx: CmpInSubqueryContext): Node =
        InSubquery(
            operand = expression(ctx.additiveExpression()),
            query = visit(ctx.query()) as Query,
            negated = hasKeyword(ctx, "NOT"),
            location = locationOf(ctx)
        )

    override fun visitCmpInList(ctx: CmpInListContext): Node =
        InList(
            operand = expression(ctx.additiveExpression()),
            values = ctx.expressionList().expression().map { expression(it) },
            negated = hasKeyword(ctx, "NOT"),
            location = locationOf(ctx)
        )

    override fun visitCmpExists(ctx: CmpExistsContext): Node =
        Exists(visit(ctx.query()) as Query, locationOf(ctx))

    // ── Primarias ──────────────────────────────────────────────────────────

    override fun visitPrimLiteral(ctx: PrimLiteralContext): Node = visit(ctx.literal())

    // Identifier '.' Identifier — `u.name`: el primero califica al segundo.
    override fun visitPrimQualifiedColumn(ctx: PrimQualifiedColumnContext): Node =
        ColumnReference(
            qualifier = ctx.Identifier(0).text,
            name = ctx.Identifier(1).text,
            location = locationOf(ctx)
        )

    override fun visitPrimColumn(ctx: PrimColumnContext): Node =
        ColumnReference(qualifier = null, name = ctx.Identifier().text, location = locationOf(ctx))

    override fun visitPrimAggregate(ctx: PrimAggregateContext): Node = visit(ctx.aggregate())

    // Una subconsulta escalar: Query ya es Expression, no hace falta envolverla.
    override fun visitPrimSubquery(ctx: PrimSubqueryContext): Node = visit(ctx.query())

    // Los parentesis DESAPARECEN: ya le dieron forma al arbol al parsear.
    override fun visitPrimParen(ctx: PrimParenContext): Node = visit(ctx.expression())

    override fun visitAgCountAll(ctx: AgCountAllContext): Node =
        Aggregate(AggregateFunction.COUNT_ALL, argument = null, distinct = false, location = locationOf(ctx))

    // El nombre de la funcion es el primer token. Se lee en forma CANONICA: el
    // usuario pudo escribir `count` o `Count`, y el enum solo tiene COUNT.
    override fun visitAgFunction(ctx: AgFunctionContext): Node =
        Aggregate(
            function = AggregateFunction.valueOf(keywordOf(ctx.getChild(0) as TerminalNode)),
            argument = expression(ctx.expression()),
            distinct = hasKeyword(ctx, "DISTINCT"),
            location = locationOf(ctx)
        )

    // ══════════════════════════════════════════════════════════════════════
    //  Literales
    // ══════════════════════════════════════════════════════════════════════

    // Un entero que no cabe en Long no se pierde ni revienta: pasa a DECIMAL, que
    // es de precision arbitraria. Es lo que hace PostgreSQL con un literal enorme.
    override fun visitIntLit(ctx: IntLitContext): Node {
        val text = ctx.text
        val location = locationOf(ctx)

        return text.toLongOrNull()
            ?.let { Literal(IntValue(it), IntType, location) }
            ?: BigDecimal(text).let { Literal(DecimalValue(it), DecimalType(it.precision(), 0), location) }
    }

    // `3.14` es DECIMAL y no FLOAT: el estandar lo define como numerico EXACTO, y
    // como FLOAT, 0.1 + 0.2 ya no daria 0.3. El tipo sale de los digitos escritos:
    // 3.14 es DECIMAL(3,2), 0.05 es DECIMAL(3,2), 1250.00 es DECIMAL(6,2).
    override fun visitDecimalLit(ctx: DecimalLitContext): Node {
        val value = BigDecimal(ctx.text)
        val integerDigits = maxOf(value.precision() - value.scale(), 1)
        val type = DecimalType(integerDigits + value.scale(), value.scale())
        return Literal(DecimalValue(value), type, locationOf(ctx))
    }

    // Un literal de texto es VARCHAR de SU largo exacto: es lo que necesita la
    // fase 5 para reportar `'abcdefgh' no cabe en VARCHAR(5)`.
    override fun visitStringLit(ctx: StringLitContext): Node {
        val content = unquote(ctx.StringLit().text)
        return Literal(StringValue(content), VarcharType(content.length), locationOf(ctx))
    }

    override fun visitDateLit(ctx: DateLitContext): Node {
        val content = unquote(ctx.StringLit().text)
        val location = locationOf(ctx)

        return try {
            Literal(DateValue(LocalDate.parse(content)), DateType, location)
        } catch (error: DateTimeParseException) {
            invalidLiteral(location, "'$content' no es una fecha valida: se espera AAAA-MM-DD")
        }
    }

    override fun visitTimeLit(ctx: TimeLitContext): Node {
        val content = unquote(ctx.StringLit().text)
        val location = locationOf(ctx)

        return try {
            Literal(TimeValue(LocalTime.parse(content)), TimeType, location)
        } catch (error: DateTimeParseException) {
            invalidLiteral(location, "'$content' no es una hora valida: se espera HH:MM o HH:MM:SS")
        }
    }

    override fun visitBoolLit(ctx: BoolLitContext): Node {
        val value = keywordOf(ctx.getChild(0) as TerminalNode) == "TRUE"
        return Literal(BoolValue(value), BooleanType, locationOf(ctx))
    }

    override fun visitNullLit(ctx: NullLitContext): Node =
        Literal(NullValue, NullType, locationOf(ctx))

    // Se reporta UNA vez y el literal queda con ErrorType, que corta la cascada:
    // la fase 5 acepta en silencio cualquier operacion con el.
    private fun invalidLiteral(location: LexemeLocation, message: String): Literal {
        diagnostics.report(CompilerError.SemanticError(location, message))
        return Literal(NullValue, ErrorType, location)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ayudantes
    // ══════════════════════════════════════════════════════════════════════

    private fun expression(ctx: ParserRuleContext): Expression = visit(ctx) as Expression

    // La ubicacion de un nodo es la de su PRIMER token. ANTLR cuenta columnas desde
    // 0; LexemeLocation desde 1.
    private fun locationOf(ctx: ParserRuleContext): LexemeLocation = locationOf(ctx.start)

    private fun locationOf(token: Token): LexemeLocation =
        LexemeLocation(line = token.line, position = token.charPositionInLine + 1)

    // Pliega a la IZQUIERDA una lista plana de operandos.
    //
    //   [a, b, c] con [+, -]  ->  Binary(Binary(a, +, b), -, c)
    //
    // Los operadores son los hijos terminales de la regla, en orden de aparicion.
    // Con un solo operando no hay operacion: se devuelve el hijo y la torre de
    // niveles colapsa.
    private fun foldBinary(ctx: ParserRuleContext, operands: List<ParserRuleContext>): Expression {
        val expressions = operands.map { expression(it) }
        val operators = terminalsOf(ctx).map { binaryOperatorOf(it) }

        var result = expressions.first()
        operators.forEachIndexed { index, operator ->
            result = Binary(operator, result, expressions[index + 1], result.location)
        }
        return result
    }

    private fun binaryOperatorOf(node: TerminalNode): BinaryOperator =
        BinaryOperator.fromSymbol(keywordOf(node))
            ?: error("Operador sin traducir: '${node.text}'")

    /**
     * El texto CANONICO de un token: `AND` aunque el usuario haya escrito `and`.
     *
     * El UpperCaseCharStream hace que el lexer reconozca `and` como la palabra
     * clave, pero el texto del token sigue siendo el original. Comparar contra
     * node.text haria que `a and b` no encontrara su operador. Por eso toda
     * palabra clave se lee desde el vocabulario de la gramatica, nunca del fuente.
     */
    private fun keywordOf(node: TerminalNode): String =
        SqlParser.VOCABULARY.getLiteralName(node.symbol.type)?.removeSurrounding("'") ?: node.text

    // La palabra clave aparece como hijo DIRECTO de esta regla. Sirve para las que
    private fun hasKeyword(ctx: ParserRuleContext, keyword: String): Boolean =
        terminalsOf(ctx).any { keywordOf(it) == keyword }

    // La expresion que sigue inmediatamente a una palabra clave: WHERE x, HAVING y.
    private fun expressionAfter(ctx: ParserRuleContext, keyword: String): Expression? {
        val children = (0 until ctx.childCount).map { ctx.getChild(it) }
        val index = children.indexOfFirst { it is TerminalNode && keywordOf(it) == keyword }
        if (index < 0) return null
        return expression(children[index + 1] as ParserRuleContext)
    }

    private fun terminalsOf(ctx: ParserRuleContext): List<TerminalNode> =
        (0 until ctx.childCount).map { ctx.getChild(it) }.filterIsInstance<TerminalNode>()

    private fun intOf(node: TerminalNode): Int? =
        node.text.toIntOrNull() ?: run {
            diagnostics.report(
                CompilerError.SemanticError(locationOf(node.symbol), "el numero ${node.text} es demasiado grande")
            )
            null
        }

    private fun unquote(text: String): String =
        text.substring(1, text.length - 1).replace("''", "'")
}
