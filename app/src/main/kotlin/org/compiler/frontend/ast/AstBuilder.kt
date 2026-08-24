package org.compiler.frontend.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.compiler.frontend.ast.models.*
import org.compiler.frontend.semantic.symbols.*
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptBaseVisitor
import org.compiler.parser.CompiscriptParser.*   // los *Context estan anidados aqui

/**
 * Convierte el arbol de ANTLR en el AST propio.
 *
 * El <Node> es el tipo de retorno de todos los metodos, asi que las expresiones llevan
 * `as Expression` y las sentencias `as Statement` al consumir un hijo.
 *
 * Un metodo por regla, salvo las de puro paso —expression, statement, classMember—,
 * donde el visitChildren por defecto ya propaga el nodo del hijo.
 */
class AstBuilder : CompiscriptBaseVisitor<Node>() {
    //  Ayudantes
    // La ubicacion de un nodo es la de su PRIMER token.
    // ANTLR cuenta columnas desde 0; LexemeLocation desde 1.
    private fun locationOf(ctx: ParserRuleContext): LexemeLocation =
        LexemeLocation(
            line = ctx.start.line,
            position = ctx.start.charPositionInLine + 1
        )

    // En una regla con forma  X (op X)*  los unicos hijos terminales son los
    // operadores:  a + b - c  ->  ["+", "-"]
    private fun operatorSymbolsOf(ctx: ParserRuleContext): List<String> =
        (0 until ctx.childCount)
            .map { ctx.getChild(it) }
            .filterIsInstance<TerminalNode>()
            .map { it.text }

    // Pliega a la IZQUIERDA:  [a, b, c] con ["+", "-"]  ->  ((a + b) - c)
    //
    // El `left = result` es todo el secreto. Plegado al reves, 10 - 3 - 2 daria 9.
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

    // Lo comparten las seis reglas binarias: solo cambia el accesor del hijo.
    //
    // El `if (size == 1)` colapsa la torre de precedencia, y vivir aqui garantiza que
    // las seis lo tengan.
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
    // Nullable porque el llamador declara `arguments?`, y eso es lo que permite `f()`.
    private fun buildArguments(ctx: ArgumentsContext?): List<Expression> =
        ctx?.expression()?.map { visit(it) as Expression } ?: emptyList()

    // Quita las comillas. No hay escapes que procesar: la gramatica acepta cualquier
    // caracter menos comilla y salto de linea, asi que un backslash es literal.
    private fun unquote(text: String): String = text.substring(1, text.length - 1)

    //=============================================
    //  Asignacion como expresion, y ternario
    //=============================================

    // lhs=leftHandSide '=' assignmentExpr   # AssignExpr
    //
    // El `lhs=` genera el accesor ctx.lhs. Recursiva por la derecha, asi que
    // `a = b = c` ya agrupa como `a = (b = c)`.
    override fun visitAssignExpr(ctx: AssignExprContext): Node =
        AssignmentExpression(
            target = visit(ctx.lhs) as Expression,
            value = visit(ctx.assignmentExpr()) as Expression,
            location = locationOf(ctx)
        )

    // lhs=leftHandSide '.' Identifier '=' assignmentExpr   # PropertyAssignExpr
    //
    // ANTLR casi nunca entra por aqui: leftHandSide absorbe el `.prop` como suffixOp y
    // `obj.prop = 5` cae en AssignExpr. Se cubre porque la alternativa existe.
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

    // conditionalExpr   # ExprNoAssign
    //
    // El default de ANTLR haria lo mismo. Se escribe para que las tres alternativas de
    // assignmentExpr esten a la vista juntas.
    override fun visitExprNoAssign(ctx: ExprNoAssignContext): Node =
        visit(ctx.conditionalExpr())

    // conditionalExpr: logicalOrExpr ('?' expression ':' expression)?   # TernaryExpr
    //
    // Se llama visitTernaryExpr y no visitConditionalExpr: la regla esta etiquetada.
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

    //=============================================
    //  Sufijos, unario y primarias
    //=============================================

    // leftHandSide: primaryAtom (suffixOp)*
    //
    // Cada sufijo envuelve el resultado acumulado:
    //   perro.dueno.nombre  ->  PropertyAccess(PropertyAccess(perro, dueno), nombre)
    //   lista[0][1]         ->  IndexAccess(IndexAccess(lista, 0), 1)
    //   perro.hablar()      ->  FunctionCall(PropertyAccess(perro, hablar), [])
    //
    // Los tres visitX de suffixOp no se sobrescriben: un sufijo suelto no significa
    // nada sin el lado izquierdo, asi que se inspeccionan por tipo aqui.
    override fun visitLeftHandSide(ctx: LeftHandSideContext): Node {
        var result = visit(ctx.primaryAtom()) as Expression

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
    // Se sobrescribe por la tercera alternativa: su ultimo hijo es ')' y el default
    // devolveria null.
    override fun visitPrimaryExpr(ctx: PrimaryExprContext): Node = when {
        ctx.literalExpr() != null -> visit(ctx.literalExpr())
        ctx.leftHandSide() != null -> visit(ctx.leftHandSide())

        // Los parentesis DESAPARECEN: ya le dieron forma al arbol al parsear.
        else -> visit(ctx.expression())
    }

    // literalExpr: Literal | arrayLiteral | 'null' | 'true' | 'false'
    //
    // El tipo se decide por la forma del texto: la gramatica declara true, false y null
    // como literales de cadena, sin tokens propios.
    override fun visitLiteralExpr(ctx: LiteralExprContext): Node {
        if (ctx.arrayLiteral() != null) return visit(ctx.arrayLiteral())

        val text = ctx.text
        val location = locationOf(ctx)

        return when {
            text == "null" -> Literal(null, NullType, location)
            text == "true" -> Literal(true, BooleanType, location)
            text == "false" -> Literal(false, BooleanType, location)
            text.startsWith("\"") -> Literal(unquote(text), StringType, location)
            text.contains(".") -> Literal(text.toDouble(), FloatType, location)

            // toLong y no toInt: 99999999999 no cabe en Int, y guardarlo en Long deja
            // que la Fase 4 reporte el desborde en vez de reventar aqui.
            else -> Literal(text.toLong(), IntegerType, location)
        }
    }

    // arrayLiteral: '[' (expression (',' expression)*)? ']'
    override fun visitArrayLiteral(ctx: ArrayLiteralContext): Node =
        ArrayLiteral(
            elements = ctx.expression().map { visit(it) as Expression },
            location = locationOf(ctx)
        )

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

    //=============================================
    //  Tipos y parametros
    //=============================================

    // type: baseType ('[' ']')*
    //
    // Las dimensiones son los pares de corchetes. El /2 funciona porque baseType es una
    // regla y no un token: los unicos hijos terminales de `type` son los corchetes.
    private fun buildTypeReference(ctx: TypeContext): TypeReference {
        val bracketPairs = (0 until ctx.childCount)
            .map { ctx.getChild(it) }
            .filterIsInstance<TerminalNode>()
            .count() / 2

        return TypeReference(
            baseName = ctx.baseType().text,
            arrayDimensions = bracketPairs,
            location = locationOf(ctx)
        )
    }

    // parameter: Identifier (':' type)?
    //
    // Ayudante y no visitor porque Parameter no es Statement ni Expression, y el unico
    // que lo construye es visitFunctionDeclaration.
    private fun buildParameter(ctx: ParameterContext): Parameter =
        Parameter(
            name = ctx.Identifier().text,
            declaredType = ctx.type()?.let { buildTypeReference(it) },
            location = locationOf(ctx)
        )

    //=============================================
    //  Declaraciones
    //=============================================

    // variableDeclaration: ('let' | 'var') Identifier typeAnnotation? initializer? ';'
    override fun visitVariableDeclaration(ctx: VariableDeclarationContext): Node =
        VariableDeclaration(
            name = ctx.Identifier().text,
            declaredType = ctx.typeAnnotation()?.let { buildTypeReference(it.type()) },
            initializer = ctx.initializer()?.let { visit(it.expression()) as Expression },
            isConstant = false,
            location = locationOf(ctx)
        )

    // constantDeclaration: 'const' Identifier typeAnnotation? '=' expression ';'
    //
    // Mismo nodo que la variable. El inicializador no lleva `?`: la gramatica lo exige.
    override fun visitConstantDeclaration(ctx: ConstantDeclarationContext): Node =
        VariableDeclaration(
            name = ctx.Identifier().text,
            declaredType = ctx.typeAnnotation()?.let { buildTypeReference(it.type()) },
            initializer = visit(ctx.expression()) as Expression,
            isConstant = true,
            location = locationOf(ctx)
        )

    // functionDeclaration: 'function' Identifier '(' parameters? ')' (':' type)? block
    override fun visitFunctionDeclaration(ctx: FunctionDeclarationContext): Node =
        FunctionDeclaration(
            name = ctx.Identifier().text,
            parameters = ctx.parameters()?.parameter()?.map { buildParameter(it) } ?: emptyList(),
            returnType = ctx.type()?.let { buildTypeReference(it) },
            body = visit(ctx.block()) as Block,
            location = locationOf(ctx)
        )

    // classDeclaration: 'class' Identifier (':' Identifier)? '{' classMember* '}'
    //
    // Dos Identifier posibles; el segundo, si existe, es la superclase.
    override fun visitClassDeclaration(ctx: ClassDeclarationContext): Node {
        val identifiers = ctx.Identifier()
        return ClassDeclaration(
            name = identifiers[0].text,
            superclassName = identifiers.getOrNull(1)?.text,
            members = ctx.classMember().map { visit(it) as Statement },
            location = locationOf(ctx)
        )
    }

    //=============================================
    //  Sentencias simples
    //=============================================

    // assignment
    //   : Identifier '=' expression ';'
    //   | expression '.' Identifier '=' expression ';'
    //
    // La regla no tiene etiquetas, asi que hay un solo metodo y la alternativa se
    // deduce de cuantos `expression` hijos hay.
    override fun visitAssignment(ctx: AssignmentContext): Node {
        val location = locationOf(ctx)

        // Forma 1: x = 5;
        if (ctx.expression().size == 1) {
            return Assignment(
                target = Identifier(ctx.Identifier().text, location),
                value = visit(ctx.expression(0)) as Expression,
                location = location
            )
        }

        // Forma 2: obj.prop = 5;
        return Assignment(
            target = PropertyAccess(
                target = visit(ctx.expression(0)) as Expression,
                propertyName = ctx.Identifier().text,
                location = location
            ),
            value = visit(ctx.expression(1)) as Expression,
            location = location
        )
    }

    // expressionStatement: expression ';'
    //
    // Si la expresion es una asignacion se rearma como Assignment, para que el AST tenga
    // una sola forma de asignar a nivel de sentencia. Es lo que atrapa `lista[0] = 5;`,
    // que la regla `assignment` no acepta.
    override fun visitExpressionStatement(ctx: ExpressionStatementContext): Node {
        val expr = visit(ctx.expression()) as Expression
        val location = locationOf(ctx)

        return if (expr is AssignmentExpression) {
            Assignment(target = expr.target, value = expr.value, location = location)
        } else {
            ExpressionStatement(expr = expr, location = location)
        }
    }

    // printStatement: 'print' '(' expression ')' ';'
    override fun visitPrintStatement(ctx: PrintStatementContext): Node =
        Print(visit(ctx.expression()) as Expression, locationOf(ctx))

    // block: '{' statement* '}'
    override fun visitBlock(ctx: BlockContext): Node =
        Block(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))

    // program: statement* EOF
    override fun visitProgram(ctx: ProgramContext): Node =
        Program(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))

    //=============================================
    //  Control de flujo
    //=============================================

    // ifStatement: 'if' '(' expression ')' block ('else' block)?
    override fun visitIfStatement(ctx: IfStatementContext): Node =
        If(
            condition = visit(ctx.expression()) as Expression,
            thenBranch = visit(ctx.block(0)) as Block,
            elseBranch = ctx.block(1)?.let { visit(it) as Block },
            location = locationOf(ctx)
        )

    // whileStatement: 'while' '(' expression ')' block
    override fun visitWhileStatement(ctx: WhileStatementContext): Node =
        While(
            condition = visit(ctx.expression()) as Expression,
            body = visit(ctx.block()) as Block,
            location = locationOf(ctx)
        )

    // doWhileStatement: 'do' block 'while' '(' expression ')' ';'
    override fun visitDoWhileStatement(ctx: DoWhileStatementContext): Node =
        DoWhile(
            body = visit(ctx.block()) as Block,
            condition = visit(ctx.expression()) as Expression,
            location = locationOf(ctx)
        )

    // forStatement
    //   : 'for' '(' (variableDeclaration | assignment | ';') expression? ';' expression? ')' block
    override fun visitForStatement(ctx: ForStatementContext): Node {
        val initializer: Statement? = when {
            ctx.variableDeclaration() != null -> visit(ctx.variableDeclaration()) as Statement
            ctx.assignment() != null -> visit(ctx.assignment()) as Statement
            else -> null
        }

        // ctx.expression() es una lista de 0, 1 o 2 elementos, y con uno solo no dice si
        // es la condicion o la actualizacion. Se decide por posicion de token contra el
        // ';' separador, que es el ULTIMO ';' hijo directo:
        //
        //   for (let i = 0; i < 3; i = i + 1)   un ';' directo (el del init va adentro)
        //   for (;; i = i + 1)                  dos, y la unica expresion es el update
        val separatorIndex = ctx.children
            .filterIsInstance<TerminalNode>()
            .last { it.text == ";" }
            .symbol.tokenIndex

        val expressions = ctx.expression()

        return For(
            initializer = initializer,
            condition = expressions.firstOrNull { it.start.tokenIndex < separatorIndex }
                ?.let { visit(it) as Expression },
            update = expressions.firstOrNull { it.start.tokenIndex > separatorIndex }
                ?.let { visit(it) as Expression },
            body = visit(ctx.block()) as Block,
            location = locationOf(ctx)
        )
    }

    // foreachStatement: 'foreach' '(' Identifier 'in' expression ')' block
    override fun visitForeachStatement(ctx: ForeachStatementContext): Node =
        ForEach(
            variableName = ctx.Identifier().text,
            iterable = visit(ctx.expression()) as Expression,
            body = visit(ctx.block()) as Block,
            location = locationOf(ctx)
        )

    // switchStatement: 'switch' '(' expression ')' '{' switchCase* defaultCase? '}'
    override fun visitSwitchStatement(ctx: SwitchStatementContext): Node =
        Switch(
            subject = visit(ctx.expression()) as Expression,
            cases = ctx.switchCase().map { visit(it) as SwitchCase },

            // El `?` distingue "sin default" (null) de "default vacio" (lista vacia), y
            // la Fase 5 usa esa diferencia.
            defaultBody = ctx.defaultCase()?.statement()?.map { visit(it) as Statement },
            location = locationOf(ctx)
        )

    // switchCase: 'case' expression ':' statement*
    //
    // Sin este metodo, el default devolveria el ULTIMO statement del cuerpo y el
    // `as SwitchCase` del llamador lanzaria ClassCastException.
    override fun visitSwitchCase(ctx: SwitchCaseContext): Node =
        SwitchCase(
            value = visit(ctx.expression()) as Expression,
            body = ctx.statement().map { visit(it) as Statement },
            location = locationOf(ctx)
        )

    // tryCatchStatement: 'try' block 'catch' '(' Identifier ')' block
    override fun visitTryCatchStatement(ctx: TryCatchStatementContext): Node =
        TryCatch(
            tryBlock = visit(ctx.block(0)) as Block,
            catchParameterName = ctx.Identifier().text,
            catchBlock = visit(ctx.block(1)) as Block,
            location = locationOf(ctx)
        )

    // returnStatement: 'return' expression? ';'
    override fun visitReturnStatement(ctx: ReturnStatementContext): Node =
        Return(ctx.expression()?.let { visit(it) as Expression }, locationOf(ctx))

    // breakStatement: 'break' ';'
    override fun visitBreakStatement(ctx: BreakStatementContext): Node =
        Break(locationOf(ctx))

    // continueStatement: 'continue' ';'
    override fun visitContinueStatement(ctx: ContinueStatementContext): Node =
        Continue(locationOf(ctx))
}
