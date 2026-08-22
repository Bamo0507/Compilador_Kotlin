package org.compiler.frontend.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.compiler.frontend.ast.models.*
import org.compiler.frontend.semantic.symbols.*
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptBaseVisitor
import org.compiler.parser.CompiscriptParser.*   // los *Context estan anidados aqui

class AstBuilder : CompiscriptBaseVisitor<Node>() {

    //=============================================
    //  Helpers
    //=============================================

    // La ubicacion de un nodo es la de su PRIMER token.
    // ANTLR cuenta columnas desde 0; LexemeLocation desde 1.
    private fun locationOf(ctx: ParserRuleContext): LexemeLocation =
        LexemeLocation(
            line = ctx.start.line,
            position = ctx.start.charPositionInLine + 1
        )

    // Los operadores de una regla con forma  X (op X)*  son los hijos que NO son
    // sub-expresiones: los nodos terminales. Se leen en orden de aparicion.
    //
    //   a + b - c   ->  ["+", "-"]
    private fun operatorSymbolsOf(ctx: ParserRuleContext): List<String> =
        (0 until ctx.childCount)
            .map { ctx.getChild(it) }
            .filterIsInstance<TerminalNode>()
            .map { it.text }

    // Pliega a la IZQUIERDA una lista plana de operandos con sus operadores.
    //
    //   [a, b, c] con ["+", "-"]  ->  BinaryOperation(BinaryOperation(a,+,b), -, c)
    //
    // El `left = result` es todo el secreto: cada vuelta mete lo ACUMULADO a la
    // izquierda. Por eso queda ((a+b)-c) y no (a+(b-c)).
    private fun foldBinaryLeft(
        operands: List<Expression>,
        operatorSymbols: List<String>
    ): Expression {
        var result = operands.first()
        for (index in operatorSymbols.indices) {
            result = BinaryOperation(
                left = result,
                operator = BinaryOperator.fromSymbol(operatorSymbols[index]),
                right = operands[index + 1],
                location = result.location
            )
        }
        return result
    }

    // Las SEIS reglas binarias tienen la misma forma  X (op X)*, asi que comparten
    // esto. La unica diferencia entre ellas es el accesor del hijo.
    //
    // El `if (size == 1)` es la regla que COLAPSA LA TORRE, y vivir aqui garantiza que
    // las seis la tengan: no se puede olvidar en una.
    private fun foldFlatBinary(
        ctx: ParserRuleContext,
        operands: List<ParserRuleContext>
    ): Expression {
        val expressions = operands.map { visit(it) as Expression }
        if (expressions.size == 1) return expressions.single()
        return foldBinaryLeft(expressions, operatorSymbolsOf(ctx))
    }

    // arguments: expression (',' expression)*
    //
    // El `?` del llamador (`arguments?` en la gramatica) es lo que hace posible `f()`
    // sin argumentos, y por eso el parametro es nullable.
    private fun buildArguments(ctx: ArgumentsContext?): List<Expression> =
        ctx?.expression()?.map { visit(it) as Expression } ?: emptyList()

    // Quita las comillas de un StringLiteral.
    //
    // No hay escapes que procesar: la gramatica dice
    //   StringLiteral: '"' (~["\r\n])* '"'
    // o sea cualquier caracter menos comilla y salto de linea. Un backslash es un
    // backslash literal, no un escape.
    private fun unquote(text: String): String = text.substring(1, text.length - 1)

    //=============================================
    //  La asignacion como expresion, y el ternario
    //=============================================

    // lhs=leftHandSide '=' assignmentExpr            # AssignExpr
    //
    // El `lhs=` de la gramatica es una ETIQUETA DE CAMPO, y genera el accesor ctx.lhs.
    // Recursiva por la derecha, asi que `a = b = c` ya agrupa como `a = (b = c)`.
    override fun visitAssignExpr(ctx: AssignExprContext): Node =
        AssignmentExpression(
            target = visit(ctx.lhs) as Expression,
            value = visit(ctx.assignmentExpr()) as Expression,
            location = locationOf(ctx)
        )

    // lhs=leftHandSide '.' Identifier '=' assignmentExpr   # PropertyAssignExpr
    //
    // `obj.prop = valor`. El target se arma como PropertyAccess, asi que el resto del
    // compilador ve la misma forma que produce la cadena de sufijos.
    //
    // OJO: en la practica ANTLR casi nunca entra por aqui: leftHandSide absorbe el
    // `.prop` como suffixOp y `obj.prop = 5` entra por AssignExpr. Se cubre igual
    // porque la alternativa existe en la gramatica.
    override fun visitPropertyAssignExpr(ctx: PropertyAssignExprContext): Node {
        val location = locationOf(ctx)
        return AssignmentExpression(
            target = PropertyAccess(
                target = visit(ctx.lhs) as Expression,
                propertyName = ctx.Identifier().text,
                location = location
            ),
            value = visit(ctx.assignmentExpr()) as Expression,
            location = location
        )
    }

    // conditionalExpr    # ExprNoAssign
    //
    // Alternativa de puro paso. Habria que dejarla al default, PERO la regla tiene
    // etiquetas: sin este metodo, `visitExprNoAssign` no se escribe y el default de
    // ANTLR aplica igual. Se escribe explicito para que las tres alternativas de
    // assignmentExpr esten a la vista juntas.
    override fun visitExprNoAssign(ctx: ExprNoAssignContext): Node =
        visit(ctx.conditionalExpr())

    // conditionalExpr
    //   : logicalOrExpr ('?' expression ':' expression)?   # TernaryExpr
    //
    // El metodo se llama visitTernaryExpr, NO visitConditionalExpr: la regla tiene
    // una alternativa etiquetada y ANTLR genera el metodo por etiqueta.
    override fun visitTernaryExpr(ctx: TernaryExprContext): Node {
        val condition = visit(ctx.logicalOrExpr()) as Expression

        // Sin ternario: colapsa al hijo.
        if (ctx.expression().isEmpty()) return condition

        return TernaryOperation(
            condition = condition,
            ifTrue = visit(ctx.expression(0)) as Expression,
            ifFalse = visit(ctx.expression(1)) as Expression,
            location = locationOf(ctx)
        )
    }

    //=============================================
    //  Los seis niveles binarios
    //=============================================
    override fun visitLogicalOrExpr(ctx: LogicalOrExprContext): Node =
        foldFlatBinary(ctx, ctx.logicalAndExpr())

    override fun visitLogicalAndExpr(ctx: LogicalAndExprContext): Node =
        foldFlatBinary(ctx, ctx.equalityExpr())

    override fun visitEqualityExpr(ctx: EqualityExprContext): Node =
        foldFlatBinary(ctx, ctx.relationalExpr())

    override fun visitRelationalExpr(ctx: RelationalExprContext): Node =
        foldFlatBinary(ctx, ctx.additiveExpr())

    override fun visitAdditiveExpr(ctx: AdditiveExprContext): Node =
        foldFlatBinary(ctx, ctx.multiplicativeExpr())

    override fun visitMultiplicativeExpr(ctx: MultiplicativeExprContext): Node =
        foldFlatBinary(ctx, ctx.unaryExpr())

    override fun visitLeftHandSide(ctx: LeftHandSideContext): Node {
        var result = visit(ctx.primaryAtom()) as Expression

        // Cada sufijo envuelve el resultado acumulado. Así,
        //   perro.dueno.nombre  ->  PropertyAccess(PropertyAccess(perro, dueno), nombre)
        //   lista[0][1]         ->  IndexAccess(IndexAccess(lista, 0), 1)
        //   perro.hablar()      ->  FunctionCall(PropertyAccess(perro, hablar), [])
        for (suffix in ctx.suffixOp()) {
            result = when (suffix) {
                is CallExprContext -> FunctionCall(
                    callee = result,
                    arguments = buildArguments(suffix.arguments()),
                    location = result.location
                )
                is IndexExprContext -> IndexAccess(
                    target = result,
                    index = visit(suffix.expression()) as Expression,
                    location = result.location
                )
                is PropertyAccessExprContext -> PropertyAccess(
                    target = result,
                    propertyName = suffix.Identifier().text,
                    location = result.location
                )
                else -> error("Sufijo desconocido: ${suffix::class.simpleName}")
            }
        }

        return result
    }

    // unaryExpr: ('-' | '!') unaryExpr | primaryExpr
    override fun visitUnaryExpr(ctx: UnaryExprContext): Node {
        // Sin operador: colapsa al hijo.
        val inner = ctx.unaryExpr() ?: return visit(ctx.primaryExpr())

        return UnaryOperation(
            operator = UnaryOperator.fromSymbol(ctx.getChild(0).text),
            operand = visit(inner) as Expression,
            location = locationOf(ctx)
        )
    }

    // primaryExpr: literalExpr | leftHandSide | '(' expression ')'
    //
    // Las dos primeras alternativas funcionarian con el default (un hijo, es regla),
    // pero la tercera no: su ultimo hijo es ')' y devolveria null. Como la regla no
    // tiene etiquetas, ANTLR genera UN metodo para las tres, asi que hay que cubrirlas.
    override fun visitPrimaryExpr(ctx: PrimaryExprContext): Node = when {
        ctx.literalExpr() != null  -> visit(ctx.literalExpr())
        ctx.leftHandSide() != null -> visit(ctx.leftHandSide())

        // El caso `(expr)`. Los parentesis DESAPARECEN: ya hicieron su trabajo al
        // parsear, le dieron forma al arbol. El AST guarda la forma, no la notacion.
        else -> visit(ctx.expression())
    }

    // literalExpr: Literal | arrayLiteral | 'null' | 'true' | 'false'
    //
    // El tipo del literal se decide por la FORMA del texto: la gramatica no tiene
    // tokens separados para true/false/null, los declara como literales de cadena.
    override fun visitLiteralExpr(ctx: LiteralExprContext): Node {
        if (ctx.arrayLiteral() != null) return visit(ctx.arrayLiteral())

        val text = ctx.text
        val location = locationOf(ctx)

        return when {
            text == "null"        -> Literal(null, NullType, location)
            text == "true"        -> Literal(true, BooleanType, location)
            text == "false"       -> Literal(false, BooleanType, location)
            text.startsWith("\"") -> Literal(unquote(text), StringType, location)
            text.contains(".")    -> Literal(text.toDouble(), FloatType, location)

            // toLong y no toInt: 99999999999 es sintacticamente valido pero no cabe en
            // Int. Guardandolo en Long, la Fase 4 detecta el desborde y lo REPORTA con
            // linea y columna; con toInt el AstBuilder lanzaria antes de llegar ahi.
            else -> Literal(text.toLong(), IntegerType, location)
        }
    }

    // arrayLiteral: '[' (expression (',' expression)*)? ']'
    override fun visitArrayLiteral(ctx: ArrayLiteralContext): Node =
        ArrayLiteral(
            elements = ctx.expression().map { visit(it) as Expression },
            location = locationOf(ctx)
        )

    // =============================================
    //  Los tres átomos
    // =============================================
    // primaryAtom: Identifier   # IdentifierExpr
    override fun visitIdentifierExpr(ctx: IdentifierExprContext): Node =
        Identifier(ctx.Identifier().text, locationOf(ctx))

    // primaryAtom: 'new' Identifier '(' arguments? ')'   # NewExpr
    override fun visitNewExpr(ctx: NewExprContext): Node =
        ObjectCreation(
            className = ctx.Identifier().text,
            arguments = buildArguments(ctx.arguments()),
            location = locationOf(ctx)
        )

    // primaryAtom: 'this'   # ThisExpr
    override fun visitThisExpr(ctx: ThisExprContext): Node =
        ThisReference(locationOf(ctx))
}
