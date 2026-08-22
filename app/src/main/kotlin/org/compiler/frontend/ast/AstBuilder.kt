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

    // =============================================
    // TypeReference
    // =============================================

    // "integer[][]" -> TypeReference("integer", 2)
    //
    // Las dimensiones son la cantidad de pares '[' ']'. Como cada par son DOS hijos
    // terminales, se divide entre 2.
    //
    // El /2 funciona porque `baseType` es una REGLA, no un token: los unicos hijos
    // terminales de `type` son los corchetes.
    private fun buildTypeReference(ctx: TypeContext): TypeReference {
        val bracketPairs = (0 until ctx.childCount)
            .map { ctx.getChild(it) }
            .filterIsInstance<TerminalNode>()
            .count() / 2

        return TypeReference(
            // ctx.baseType().text es seguro aqui: baseType es siempre UN token, sea
            // 'integer' o un Identifier. La advertencia sobre ctx.text aplica a nodos
            // con varios hijos, no a este.
            baseName = ctx.baseType().text,
            arrayDimensions = bracketPairs,
            location = locationOf(ctx)
        )
    }

    // parameter: Identifier (':' type)?
    //
    // Es un ayudante y no un visitor porque Parameter no es Statement ni Expression: es
    // un Node aparte, y el unico que lo construye es visitFunctionDeclaration.
    private fun buildParameter(ctx: ParameterContext): Parameter =
        Parameter(
            name = ctx.Identifier().text,
            declaredType = ctx.type()?.let { buildTypeReference(it) },   // null si no se anoto
            location = locationOf(ctx)
        )

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
    // Mismo nodo que la variable, con isConstant = true. La `const` SIEMPRE tiene
    // inicializador: la gramática lo exige, no hace falta validarlo aquí.
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
    // El segundo Identifier, si existe, es la superclase.
    override fun visitClassDeclaration(ctx: ClassDeclarationContext): Node {
        val identifiers = ctx.Identifier()
        return ClassDeclaration(
            name = identifiers[0].text,
            superclassName = identifiers.getOrNull(1)?.text,
            members = ctx.classMember().map { visit(it) as Statement },
            location = locationOf(ctx)
        )
    }

    // assignment (la regla de statement): dos formas.
    override fun visitAssignment(ctx: AssignmentContext): Node {
        val location = locationOf(ctx)

        // Forma 1:  Identifier '=' expression ';'
        //           (un solo `expression` hijo y ningún '.')
        if (ctx.expression().size == 1) {
            return Assignment(
                target = Identifier(ctx.Identifier().text, location),
                value = visit(ctx.expression(0)) as Expression,
                location = location
            )
        }

        // Forma 2:  expression '.' Identifier '=' expression ';'
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
    // Si la expresion resulta ser una asignacion, se DESARMA y se rearma como el mismo
    // nodo Assignment que produce la regla `assignment`. Asi el AST tiene una sola forma
    // de asignar A NIVEL DE SENTENCIA, sin importar por que camino de la gramatica llego.
    //
    // AssignmentExpression sigue existiendo para el caso ANIDADO —`let y = (x = 5);`,
    // `if (x = 1)`—, asi que la Fase 4 tiene las dos funciones. Las tres reglas comunes
    // (lvalue, constante, asignabilidad) viven en un ayudante compartido; ver ticket 4.4.
    override fun visitExpressionStatement(ctx: ExpressionStatementContext): Node {
        val expr = visit(ctx.expression()) as Expression
        val location = locationOf(ctx)

        return if (expr is AssignmentExpression) {
            Assignment(target = expr.target, value = expr.value, location = location)
        } else {
            ExpressionStatement(expr = expr, location = location)
        }
    }

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

    override fun visitForStatement(ctx: ForStatementContext): Node {
        // El inicializador es opcional: si es solo ';', ambos hijos vienen nulos.
        val initializer: Statement? = when {
            ctx.variableDeclaration() != null -> visit(ctx.variableDeclaration()) as Statement
            ctx.assignment() != null          -> visit(ctx.assignment()) as Statement
            else                              -> null
        }

        // Las dos expresiones opcionales son, en orden: condición y actualización.
        // Si solo viene una, la LISTA no dice cuál es. Se resuelve por posición del
        // token: la que está antes del ';' separador es la condición.
        //
        // El ';' separador es SIEMPRE el último ';' hijo directo de forStatement:
        //   for (let i = 0; i < 3; i++)   -> hijos ';': uno (el separador)
        //   for (; i < 3; i++)            -> hijos ';': dos (el del init vacío y el separador)
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

            // El `default` NO necesita su propio visitor: aqui se leen sus statements
            // directo. El `?` es lo que distingue "sin default" (null) de "default
            // vacio" (lista vacia), y la Fase 5 usa esa diferencia para decidir si un
            // switch garantiza retorno en todos los caminos.
            defaultBody = ctx.defaultCase()?.statement()?.map { visit(it) as Statement },
            location = locationOf(ctx)
        )

    // switchCase: 'case' expression ':' statement*
    //
    // HACE FALTA sobrescribirlo. Sin este metodo corre el default, y visitChildren
    // devuelve el ULTIMO `statement` del cuerpo: un Statement, no un SwitchCase. El
    // `as SwitchCase` del llamador lanzaria ClassCastException en ejecucion.
    //
    // El cuerpo es List<Statement> y no un Block porque la gramatica no le pone llaves
    // al case. Por eso un `case` no abre ambito por si mismo: lo abre el TypeChecker de
    // la Fase 4, con el nombre "case@N".
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

    // printStatement: 'print' '(' expression ')' ';'
    override fun visitPrintStatement(ctx: PrintStatementContext): Node =
        Print(visit(ctx.expression()) as Expression, locationOf(ctx))

    // returnStatement: 'return' expression? ';'
    override fun visitReturnStatement(ctx: ReturnStatementContext): Node =
        Return(ctx.expression()?.let { visit(it) as Expression }, locationOf(ctx))

    override fun visitBreakStatement(ctx: BreakStatementContext): Node =
        Break(locationOf(ctx))

    override fun visitContinueStatement(ctx: ContinueStatementContext): Node =
        Continue(locationOf(ctx))

    // block: '{' statement* '}'
    override fun visitBlock(ctx: BlockContext): Node =
        Block(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))

    // program: statement* EOF
    override fun visitProgram(ctx: ProgramContext): Node =
        Program(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))
}
