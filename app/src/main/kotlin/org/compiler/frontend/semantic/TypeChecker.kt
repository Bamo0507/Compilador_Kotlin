package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.ArrayLiteral
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.Block
import org.compiler.frontend.ast.models.Break
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.Continue
import org.compiler.frontend.ast.models.DoWhile
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.ExpressionStatement
import org.compiler.frontend.ast.models.For
import org.compiler.frontend.ast.models.ForEach
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.If
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Node
import org.compiler.frontend.ast.models.OperatorGroup
import org.compiler.frontend.ast.models.ObjectCreation
import org.compiler.frontend.ast.models.Print
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.Return
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.ThisReference
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.CONSTRUCTOR_NAME
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.NullType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.frontend.semantic.symbols.VoidType
import org.compiler.models.LexemeLocation

// Lo que devuelve cada funcion de expresion.
//
// El tipo SIEMPRE viene. El valor solo cuando se puede
// calcular en compilacion:  3 + 5 -> 8,  x + 5 -> null.
data class TypedValue(
    val type: Type,
    val constant: Any? = null
) {
    val isConstant: Boolean get() = constant != null
}

/**
 * Pasada 2: entra a los cuerpos, verifica tipos y decora el AST.
 *
 * Escribe `type` y `constantValue` en cada Expression, y completa el arbol de
 * ambitos con lo que la Pasada 1 dejo pendiente.
 *
 * Las expresiones devuelven un TypedValue; las sentencias no devuelven nada.
 */
class TypeChecker(
    private val globalScope: Scope,
    private val diagnostics: Diagnostics
) {
    // El cursor del recorrido, igual que en la Pasada 1.
    private var currentScope: Scope = globalScope

    // El tipo de retorno de la funcion en la que estoy.
    //
    // Va en un campo porque el Scope no lo guarda: ese dato vive en el Symbol de la
    // funcion, que esta en el ambito padre.
    private var currentReturnType: Type? = null

    // El unico punto que conecta TypeRules con el arbol de
    // ambitos. TypeRules no conoce Scope: recibe esta
    // lambda y con eso contesta sobre subtipado.
    private val typeRules = TypeRules { className ->
        globalScope.lookupLocal(className)?.memberScope?.superclass?.name
    }

    // El mismo que usa la Pasada 1: convierte el tipo escrito en un Type resuelto.
    private val typeResolver = TypeResolver(globalScope, diagnostics)

    // El punto de entrada. Lo llama el CompilerPipeline.
    //
    // No abre ningun ambito: el programa ES globalScope, que ya creo la Pasada 1.
    fun check(program: Program) {
        program.statements.forEach { checkStatement(it) }
    }

    // Las 17 sentencias. Sin rama `else`: si se agrega una y se olvida su funcion,
    // Kotlin no compila.
    private fun checkStatement(stmt: Statement) {
        when (stmt) {
            is VariableDeclaration -> checkVariableDeclaration(stmt)
            is FunctionDeclaration -> checkFunctionDeclaration(stmt)
            is ClassDeclaration -> checkClassDeclaration(stmt)
            is Assignment -> checkAssignment(stmt)
            is ExpressionStatement -> checkExpression(stmt.expr)
            is Print -> checkExpression(stmt.expr)
            is Block -> checkBlock(stmt)
            is If -> checkIfStatement(stmt)
            is While -> checkWhileStatement(stmt)
            is DoWhile -> checkDoWhileStatement(stmt)
            is For -> checkForStatement(stmt)
            is ForEach -> checkForEach(stmt)
            is Switch -> checkSwitch(stmt)
            is TryCatch -> checkTryCatch(stmt)
            is Return -> checkReturn(stmt)

            // Su unica regla es de ubicacion, y la valida el FlowAnalyzer.
            is Break, is Continue -> Unit
        }
    }

    // Entra, ejecuta, sale. La funcion ES el ambito.
    //
    // El nombre lo pasa quien abre, y nombra la construccion —if@4, while@7— para que
    // el arbol de la GUI se lea contra el codigo fuente.
    private inline fun withScope(kind: ScopeKind, name: String, body: () -> Unit) {
        currentScope = currentScope.openChild(kind, name)
        body()
        currentScope = currentScope.parent!!
    }

    // La usan if, while, do-while y for. Es lo que hace que `if (x = 1)` sea error:
    // la asignacion devuelve el tipo de la variable, no boolean.
    private fun requireBooleanCondition(condition: Expression, construct: String) {
        val type = checkExpression(condition).type
        if (type != BooleanType && type != ErrorType) {
            report(condition, "La condición de '$construct' debe ser boolean, no '${type.name}'")
        }
    }

    // El parametro `scope` lo usan los tests para posicionar el cursor sin pasar por
    // checkStatement.
    internal fun checkExpression(expr: Expression, scope: Scope = currentScope): TypedValue {
        val previousScope = currentScope
        currentScope = scope
        return try {
            checkExpressionInCurrentScope(expr)
        } finally {
            currentScope = previousScope
        }
    }

    private fun checkExpressionInCurrentScope(expr: Expression): TypedValue = when (expr) {
        is Literal -> checkLiteral(expr)
        is Identifier -> checkIdentifier(expr)
        is BinaryOperation -> checkBinaryOperation(expr)
        is UnaryOperation -> checkUnaryOperation(expr)
        is TernaryOperation -> checkTernaryOperation(expr)
        is ArrayLiteral -> checkArrayLiteral(expr)

        is FunctionCall -> checkFunctionCall(expr)
        is IndexAccess -> checkIndexAccess(expr)
        is PropertyAccess -> checkPropertyAccess(expr)
        is ObjectCreation -> checkObjectCreation(expr)
        is ThisReference -> checkThisReference(expr)

        is AssignmentExpression -> checkAssignmentExpression(expr)
    }

    // El tipo ya viene del AstBuilder. Lo unico que agrega es
    // el rango: el AstBuilder guardo los enteros como Long
    // para que el desborde se REPORTE aqui en vez de reventar
    // alla.
    private fun checkLiteral(expr: Literal): TypedValue {
        val value = expr.value
        if (expr.literalType == IntegerType && value is Long &&
            (value > Int.MAX_VALUE || value < Int.MIN_VALUE)
        ) {
            report(expr, "El literal entero '$value' no cabe en un integer")
            return decorate(expr, TypedValue(ErrorType))
        }

        return decorate(expr, TypedValue(expr.literalType, value))
    }

    // Resuelve el nombre y hace cinco cosas con el simbolo: lo
    // guarda en el nodo, cuenta el uso, detecta captura, valida
    // que ya tenga valor, y propaga la constante.
    private fun checkIdentifier(expr: Identifier): TypedValue {
        val symbol = currentScope.lookup(expr.name)
        if (symbol == null) {
            report(expr, "La variable '${expr.name}' no está declarada")
            return decorate(expr, TypedValue(ErrorType))
        }

        // Se guarda para que ninguna fase posterior tenga que
        // volver a resolver este nombre.
        expr.resolvedSymbol = symbol

        // Los contadores de vivacidad se llevan aqui y solo
        // aqui: la Fase 5 ya no recorre el AST.
        symbol.useCount += 1
        symbol.lastUseLine = expr.location.line

        // Captura: el uso esta en una funcion mas anidada que la
        // declaracion, y la declaracion no es global.
        if (currentScope.functionDepth() > symbol.declarationFunctionDepth &&
            symbol.declarationFunctionDepth > 0
        ) {
            symbol.usedInNestedFunction = true
        }

        if (!symbol.initialized && symbol.kind != DeclarationKind.FUNCTION) {
            report(expr, "La variable '${expr.name}' se usa antes de tener un valor")
        }

        // Solo las CONSTANTES propagan su valor: una const no se
        // puede reasignar, asi que plegarla nunca miente. Con
        // una `let` bastaria un `if (cond) { x = 10; }` para que
        // el plegado diera una respuesta falsa.
        val constant = if (symbol.kind == DeclarationKind.CONSTANT) {
            symbol.constantValue
        } else {
            null
        }
        return decorate(expr, TypedValue(symbol.type, constant))
    }

    // Trece operadores, cuatro reglas: la que aplica sale del
    // GRUPO. El `?:` de ARITHMETIC es porque el + esta
    // sobrecargado — si no es aritmetica, se prueba
    // concatenacion.
    private fun checkBinaryOperation(expr: BinaryOperation): TypedValue {
        val left = checkExpression(expr.left)
        val right = checkExpression(expr.right)

        val resultType = when (expr.operator.group) {
            OperatorGroup.ARITHMETIC ->
                typeRules.arithmetic(expr.operator, left.type, right.type)
                    ?: typeRules.concatenation(expr.operator, left.type, right.type)
            OperatorGroup.LOGICAL -> typeRules.logical(left.type, right.type)
            OperatorGroup.RELATIONAL -> typeRules.relational(left.type, right.type)
            OperatorGroup.EQUALITY -> typeRules.equality(left.type, right.type)
        }

        if (resultType == null) {
            report(expr, "El operador '${expr.operator.symbol}' no se puede aplicar a " +
                "'${left.type.name}' y '${right.type.name}'")
            return decorate(expr, TypedValue(ErrorType))
        }

        // Solo con divisor CONSTANTE. Con `1 / x` no se puede
        // afirmar nada, y ese caso va al chequeo dinamico del
        // interprete: es la propiedad realizable.
        if (isDivisionByZero(expr.operator, right)) {
            report(expr.right, "No se puede dividir entre cero")
            return decorate(expr, TypedValue(ErrorType))
        }

        return decorate(expr, TypedValue(resultType, foldBinaryOperation(expr.operator, left, right, resultType)))
    }

    private fun checkUnaryOperation(expr: UnaryOperation): TypedValue {
        val operand = checkExpression(expr.operand)
        val resultType = typeRules.unary(expr.operator, operand.type)
        if (resultType == null) {
            report(expr, "El operador '${expr.operator.symbol}' no se puede aplicar a " +
                "'${operand.type.name}'")
            return decorate(expr, TypedValue(ErrorType))
        }

        val folded = when {
            !operand.isConstant -> null
            expr.operator == UnaryOperator.NOT -> !asBoolean(operand)
            operand.constant is Double -> -asDouble(operand)
            else -> -asLong(operand)
        }
        return decorate(expr, TypedValue(resultType, folded))
    }

    // Dos verificaciones independientes: la condicion debe ser
    // boolean, y las ramas deben unificar. Si la condicion es
    // constante, se sabe que rama se toma.
    private fun checkTernaryOperation(expr: TernaryOperation): TypedValue {
        val condition = checkExpression(expr.condition)
        val ifTrue = checkExpression(expr.ifTrue)
        val ifFalse = checkExpression(expr.ifFalse)

        if (condition.type != BooleanType && condition.type != ErrorType) {
            report(expr.condition, "La condición del operador ternario debe ser boolean, " +
                "no '${condition.type.name}'")
        }

        val resultType = typeRules.unify(ifTrue.type, ifFalse.type)
        if (resultType == null) {
            report(expr, "Las dos ramas del ternario tienen tipos incompatibles: " +
                "'${ifTrue.type.name}' y '${ifFalse.type.name}'")
            return decorate(expr, TypedValue(ErrorType))
        }

        val folded = when (condition.constant) {
            true -> ifTrue.constant
            false -> ifFalse.constant
            else -> null
        }
        return decorate(expr, TypedValue(resultType, folded))
    }

    // Unifica los elementos de a pares:  [1, 2.5] -> float[]
    private fun checkArrayLiteral(expr: ArrayLiteral): TypedValue {
        // Un [] vacio no dice de que es. ArrayType(NullType) es
        // la marca que isAssignable reconoce como "toma el tipo
        // del contexto".
        if (expr.elements.isEmpty()) return decorate(expr, TypedValue(ArrayType(NullType)))

        val elementTypes = expr.elements.map { checkExpression(it).type }
        val unified = elementTypes.reduce { accumulated, next ->
            typeRules.unify(accumulated, next) ?: run {
                report(expr, "Los elementos de la lista tienen tipos incompatibles: " +
                    "'${accumulated.name}' y '${next.name}'")
                ErrorType
            }
        }
        return decorate(expr, TypedValue(ArrayType(unified)))
    }

    // Un solo camino para funcion suelta y para metodo:
    // perro.hablar() es FunctionCall(callee = PropertyAccess),
    // asi que el callee ya trae su FunctionType.
    private fun checkFunctionCall(expr: FunctionCall): TypedValue {
        val calleeType = checkExpression(expr.callee).type

        if (calleeType == ErrorType) {
            // Los argumentos se verifican igual: si no, quedarian
            // sin decorar y romperian el criterio de que todo
            // nodo tiene type != null.
            expr.arguments.forEach { checkExpression(it) }
            return decorate(expr, TypedValue(ErrorType))
        }

        if (calleeType !is FunctionType) {
            report(expr, "'${describeCallee(expr.callee)}' no es una función")
            expr.arguments.forEach { checkExpression(it) }
            return decorate(expr, TypedValue(ErrorType))
        }

        checkArguments(expr, calleeType.parameters, expr.arguments)
        return decorate(expr, TypedValue(calleeType.returns))
    }

    // El nombre legible de lo que se intento llamar:
    //   f * 2      -> "f"
    //   perro.x()  -> "perro.x"
    private fun describeCallee(callee: Expression): String = when (callee) {
        is Identifier -> callee.name
        is PropertyAccess -> "${describeCallee(callee.target)}.${callee.propertyName}"
        is ThisReference -> "this"
        else -> "la expresión"
    }

    // Cantidad primero, despues tipo por POSICION.
    // Compartida entre las llamadas y `new`.
    private fun checkArguments(node: Expression, expected: List<Type>, arguments: List<Expression>) {
        val actual = arguments.map { checkExpression(it).type }
        if (actual.size != expected.size) {
            report(node, "Se esperaban ${expected.size} argumentos y se recibieron ${actual.size}")
            return
        }

        expected.zip(actual).forEachIndexed { index, (expectedType, actualType) ->
            if (!typeRules.isAssignable(expectedType, actualType)) {
                report(arguments[index], "El argumento ${index + 1} debe ser '${expectedType.name}', " +
                    "no '${actualType.name}'")
            }
        }
    }

    // lookupMember busca en la clase y sus superclases, pero NO
    // sale al ambito exterior: un campo heredado si, una
    // variable global llamada igual no.
    private fun checkPropertyAccess(expr: PropertyAccess): TypedValue {
        val targetType = checkExpression(expr.target).type
        if (targetType == ErrorType) return decorate(expr, TypedValue(ErrorType))

        if (targetType !is ClassType) {
            report(expr, "No se puede acceder a '.${expr.propertyName}' sobre " +
                "'${targetType.name}': no es un objeto")
            return decorate(expr, TypedValue(ErrorType))
        }

        val member = classScopeOf(targetType.className)?.lookupMember(expr.propertyName)
        if (member == null) {
            report(expr, "La clase '${targetType.className}' no tiene un miembro " +
                "llamado '${expr.propertyName}'")
            return decorate(expr, TypedValue(ErrorType))
        }

        expr.resolvedMember = member
        member.useCount += 1
        member.lastUseLine = expr.location.line
        return decorate(expr, TypedValue(member.type))
    }

    // El salto que obliga la decision 2: ClassType guarda solo
    // el nombre para no crear un ciclo Type -> Scope -> Symbol,
    // asi que llegar a los miembros pasa por el Symbol.
    private fun classScopeOf(className: String): Scope? =
        globalScope.lookupLocal(className)?.memberScope

    // Se invoca desde checkFunctionDeclaration en el ticket 4.4, cuando se entra al
    // ámbito de una clase y las firmas de todos los métodos ya están disponibles.
    private fun checkOverride(declaration: FunctionDeclaration, classScope: Scope) {
        val inherited = classScope.superclass?.lookupMember(declaration.name) ?: return
        val ownType = classScope.lookupLocal(declaration.name)?.type ?: return
        if (ownType != inherited.type) {
            report(declaration, "El método '${declaration.name}' sobrescribe el de la superclase " +
                "con otra firma: se esperaba '${inherited.type.name}' y es '${ownType.name}'")
        }
    }

    // El constructor se reconoce POR NOMBRE: la gramatica no
    // tiene sintaxis propia para el.
    private fun checkObjectCreation(expr: ObjectCreation): TypedValue {
        val classSymbol = globalScope.lookupLocal(expr.className)
        if (classSymbol == null || classSymbol.kind != DeclarationKind.CLASS) {
            report(expr, "La clase '${expr.className}' no está declarada")
            expr.arguments.forEach { checkExpression(it) }
            return decorate(expr, TypedValue(ErrorType))
        }

        // lookupMember y no lookupLocal: el constructor se hereda si la clase no
        // declara uno propio (decision 16).
        val constructor = classSymbol.memberScope?.lookupMember(CONSTRUCTOR_NAME)
        val expectedParameters = (constructor?.type as? FunctionType)?.parameters ?: emptyList()
        checkArguments(expr, expectedParameters, expr.arguments)
        return decorate(expr, TypedValue(ClassType(expr.className)))
    }

    // `this` no dice de que clase es: hay que subir por parent
    // hasta la clase que lo contiene.
    private fun checkThisReference(expr: ThisReference): TypedValue {
        val classScope = currentScope.enclosingClass()
        if (classScope == null) {
            report(expr, "'this' solo se puede usar dentro de una clase")
            return decorate(expr, TypedValue(ErrorType))
        }
        return decorate(expr, TypedValue(ClassType(classScope.name)))
    }

    private fun checkIndexAccess(expr: IndexAccess): TypedValue {
        val targetType = checkExpression(expr.target).type
        val index = checkExpression(expr.index)
        if (targetType == ErrorType) return decorate(expr, TypedValue(ErrorType))

        if (targetType !is ArrayType) {
            report(expr, "No se puede indexar sobre '${targetType.name}': no es una lista")
            return decorate(expr, TypedValue(ErrorType))
        }

        if (index.type != IntegerType && index.type != ErrorType) {
            report(expr.index, "El índice debe ser integer, no '${index.type.name}'")
        }
        // Propiedad REALIZABLE: el indice negativo constante se
        // rechaza aqui; con `lista[i]` es imposible saberlo y va
        // al chequeo dinamico del interprete.
        if (index.constant is Long && index.constant < 0) {
            report(expr.index, "El índice no puede ser negativo: ${index.constant}")
        }
        return decorate(expr, TypedValue(targetType.element))
    }

    // Un { } suelto. if, while y los demas abren el suyo con su propio nombre.
    private fun checkBlock(block: Block) {
        withScope(ScopeKind.BLOCK, "block@${block.location.line}") {
            block.statements.forEach { checkStatement(it) }
        }
    }

    private fun checkIfStatement(stmt: If) {
        requireBooleanCondition(stmt.condition, "if")

        withScope(ScopeKind.BLOCK, "if@${stmt.location.line}") {
            stmt.thenBranch.statements.forEach { checkStatement(it) }
        }

        stmt.elseBranch?.let { elseBranch ->
            withScope(ScopeKind.BLOCK, "else@${elseBranch.location.line}") {
                elseBranch.statements.forEach { checkStatement(it) }
            }
        }
    }

    // Los bucles abren LOOP, no BLOCK: es lo que permite validar break y continue.
    //
    // No se puede delegar en checkBlock aunque el cuerpo sea un `block`: quedaria
    // marcado BLOCK y la GUI mostraria "block@7" donde debe decir "while@7".
    private fun checkWhileStatement(stmt: While) {
        requireBooleanCondition(stmt.condition, "while")
        withScope(ScopeKind.LOOP, "while@${stmt.location.line}") {
            stmt.body.statements.forEach { checkStatement(it) }
        }
    }

    private fun checkDoWhileStatement(stmt: DoWhile) {
        withScope(ScopeKind.LOOP, "do@${stmt.location.line}") {
            stmt.body.statements.forEach { checkStatement(it) }
        }
        requireBooleanCondition(stmt.condition, "do-while")
    }

    // El inicializador declara DENTRO del ambito del for, asi que `for (let i = 0; ...)`
    // deja `i` visible solo en el ciclo.
    private fun checkForStatement(stmt: For) {
        withScope(ScopeKind.LOOP, "for@${stmt.location.line}") {
            stmt.initializer?.let { checkStatement(it) }
            stmt.condition?.let { requireBooleanCondition(it, "for") }
            stmt.update?.let { checkExpression(it) }
            stmt.body.statements.forEach { checkStatement(it) }
        }
    }

    // El unico punto de inferencia que no viene de un inicializador: la gramatica no
    // permite anotar el tipo de la variable del ciclo.
    private fun checkForEach(stmt: ForEach) {
        val iterableType = checkExpression(stmt.iterable).type

        val elementType = when {
            iterableType is ArrayType -> iterableType.element
            iterableType == ErrorType -> ErrorType
            else -> {
                report(stmt.iterable, "'foreach' solo recorre listas, y '${iterableType.name}' no lo es")
                ErrorType
            }
        }

        withScope(ScopeKind.LOOP, "foreach@${stmt.location.line}") {
            declare(
                name = stmt.variableName,
                kind = DeclarationKind.VARIABLE,
                type = elementType,
                location = stmt.location,
                initialized = true
            )
            stmt.body.statements.forEach { checkStatement(it) }
        }
    }

    // El enunciado dice que la condicion del switch debe ser boolean, pero su propio
    // ejemplo hace `switch (x) { case 1: }` con x entero. Lo que ocurre adentro es
    // `x == 1`: la regla real es que el sujeto y los case sean COMPARABLES.
    private fun checkSwitch(stmt: Switch) {
        val subject = checkExpression(stmt.subject)

        stmt.cases.forEach { case ->
            val caseType = checkExpression(case.value).type

            if (typeRules.equality(subject.type, caseType) == null) {
                report(case.value, "El case de tipo '${caseType.name}' no se puede comparar " +
                    "con el switch de tipo '${subject.type.name}'")
            }

            withScope(ScopeKind.BLOCK, "case@${case.location.line}") {
                case.body.forEach { checkStatement(it) }
            }
        }

        stmt.defaultBody?.let { body ->
            withScope(ScopeKind.BLOCK, "default@${stmt.location.line}") {
                body.forEach { checkStatement(it) }
            }
        }
    }

    private fun checkTryCatch(stmt: TryCatch) {
        withScope(ScopeKind.BLOCK, "try@${stmt.location.line}") {
            stmt.tryBlock.statements.forEach { checkStatement(it) }
        }

        withScope(ScopeKind.BLOCK, "catch@${stmt.catchBlock.location.line}") {
            // Siempre string: la gramatica no deja anotarlo y el interprete le liga el
            // MENSAJE del error. Es lo que hace funcionar `"Error: " + err`.
            declare(
                name = stmt.catchParameterName,
                kind = DeclarationKind.VARIABLE,
                type = StringType,
                location = stmt.location,
                initialized = true
            )
            stmt.catchBlock.statements.forEach { checkStatement(it) }
        }
    }

    private fun checkVariableDeclaration(decl: VariableDeclaration) {
        val declaredType = typeResolver.resolve(decl.declaredType)
        val initializer = decl.initializer?.let { checkExpression(it) }

        if (decl.isConstant && initializer == null) {
            report(decl, "La constante '${decl.name}' debe inicializarse en su declaración")
        }

        val finalType = when {
            declaredType != null -> {
                if (initializer != null && !typeRules.isAssignable(declaredType, initializer.type)) {
                    report(decl, "No se puede asignar '${initializer.type.name}' a " +
                        "'${decl.name}', declarada como '${declaredType.name}'")
                }
                declaredType
            }

            // Sin tipo anotado se infiere del inicializador.
            initializer != null -> initializer.type

            else -> {
                report(decl, "'${decl.name}' necesita un tipo anotado o un valor inicial")
                ErrorType
            }
        }

        declare(
            name = decl.name,
            kind = if (decl.isConstant) DeclarationKind.CONSTANT else DeclarationKind.VARIABLE,
            type = finalType,
            location = decl.location,
            initialized = initializer != null,

            // Solo las constantes guardan su valor: ver checkIdentifier.
            constantValue = if (decl.isConstant) initializer?.constant else null
        )
    }

    // Las tres reglas de toda asignacion, en un solo lugar.
    //
    // Existe porque una asignacion llega por dos nodos: Assignment (sentencia) y
    // AssignmentExpression (anidada). Copiar las reglas seria pedir que se desincronicen.
    //
    // Devuelve el tipo del DESTINO: el valor de `x = 5` es 5, y su tipo es el de x.
    private fun checkAssignmentRules(node: Node, target: Expression, value: Expression): TypedValue {
        val targetValue = checkExpression(target)
        val valueValue = checkExpression(value)

        if (!isLValue(target)) {
            report(node, "El lado izquierdo de una asignación debe ser una variable, " +
                "un campo o un elemento de lista")
            return TypedValue(ErrorType)
        }

        val symbol = symbolOf(target)
        if (symbol?.kind == DeclarationKind.CONSTANT) {
            report(node, "No se puede reasignar la constante '${symbol.name}'")
            return TypedValue(ErrorType)
        }

        if (!typeRules.isAssignable(targetValue.type, valueValue.type)) {
            report(node, "No se puede asignar '${valueValue.type.name}' a '${targetValue.type.name}'")
            return TypedValue(ErrorType)
        }

        symbol?.initialized = true
        return TypedValue(targetValue.type)
    }

    // La sentencia descarta el tipo.
    private fun checkAssignment(stmt: Assignment) {
        checkAssignmentRules(stmt, stmt.target, stmt.value)
    }

    // La expresion lo devuelve y decora el nodo. El tipo de `x = 5` es el de x, y por
    // eso `if (x = 1)` es error: devuelve integer, no boolean.
    private fun checkAssignmentExpression(expr: AssignmentExpression): TypedValue =
        decorate(expr, checkAssignmentRules(expr, expr.target, expr.value))

    private fun isLValue(expr: Expression): Boolean =
        expr is Identifier || expr is PropertyAccess || expr is IndexAccess

    private fun symbolOf(expr: Expression): Symbol? = when (expr) {
        is Identifier -> expr.resolvedSymbol
        is PropertyAccess -> expr.resolvedMember

        // Asignar a lista[0] no reasigna `lista`: modifica su contenido. Devolver su
        // simbolo haria que `const lista = [1,2]; lista[0] = 5;` diera error.
        else -> null
    }

    private fun checkFunctionDeclaration(decl: FunctionDeclaration) {
        // Si es un metodo de una clase que hereda, su firma debe coincidir con la del
        // padre. Va aqui y no en la Pasada 1, donde la superclase puede no tener sus
        // miembros registrados todavia.
        if (currentScope.kind == ScopeKind.CLASS) {
            checkOverride(decl, currentScope)
        }

        val functionType = currentScope.lookup(decl.name)?.type as? FunctionType

        val previousScope = currentScope
        val previousReturnType = currentReturnType

        currentScope = currentScope.openChild(ScopeKind.FUNCTION, decl.name)
        currentReturnType = functionType?.returns ?: VoidType

        decl.parameters.forEachIndexed { index, parameter ->
            declare(
                name = parameter.name,
                kind = DeclarationKind.PARAMETER,
                type = functionType?.parameters?.getOrNull(index) ?: ErrorType,
                location = parameter.location,
                offset = index,
                initialized = true
            )
        }

        // El cuerpo NO abre otro ambito: los parametros y las locales del primer nivel
        // comparten el de la funcion, asi que `function f(x) { let x; }` es un choque.
        decl.body.statements.forEach { checkStatement(it) }

        currentScope = previousScope
        currentReturnType = previousReturnType
    }

    // Solo el TIPO. La ubicacion —dentro o fuera de una funcion— la valida la Fase 5.
    private fun checkReturn(stmt: Return) {
        val expected = currentReturnType ?: return
        val actual = stmt.value?.let { checkExpression(it).type } ?: VoidType

        if (!typeRules.isAssignable(expected, actual)) {
            report(stmt, "La función debe devolver '${expected.name}', no '${actual.name}'")
        }
    }

    // RECUPERA el ambito que creo la Pasada 1. Abrir uno nuevo duplicaria la clase en
    // el arbol y declararia los metodos en el vacio.
    private fun checkClassDeclaration(decl: ClassDeclaration) {
        val classScope = classScopeOf(decl.name) ?: return

        val previousScope = currentScope
        currentScope = classScope

        decl.members.forEach { member ->
            when (member) {
                // Los campos ya los declaro la Pasada 1: aqui solo se verifica su
                // inicializador, que esa pasada no evalua.
                is VariableDeclaration -> checkFieldInitializer(member)
                is FunctionDeclaration -> checkFunctionDeclaration(member)
                else -> Unit
            }
        }

        currentScope = previousScope
    }

    // Sin esto, `class A { let x: integer = "hola"; }` pasaria sin error.
    private fun checkFieldInitializer(decl: VariableDeclaration) {
        val initializer = decl.initializer?.let { checkExpression(it) } ?: return
        val declaredType = currentScope.lookupLocal(decl.name)?.type ?: return

        if (!typeRules.isAssignable(declaredType, initializer.type)) {
            report(decl, "No se puede asignar '${initializer.type.name}' al campo " +
                "'${decl.name}', declarado como '${declaredType.name}'")
        }
    }

    // Declara en currentScope y reporta si el nombre ya existia. El offset real y el
    // isMember los pone Scope.declare.
    private fun declare(
        name: String,
        kind: DeclarationKind,
        type: Type,
        location: LexemeLocation,
        offset: Int = 0,
        initialized: Boolean = false,
        constantValue: Any? = null
    ) {
        currentScope.declareOrReport(
            Symbol(
                name = name,
                kind = kind,
                type = type,
                location = location,
                scopeName = currentScope.name,
                offset = offset,
                initialized = initialized,
                constantValue = constantValue
            ),
            diagnostics
        )
    }

    // Calcula el valor si AMBOS operandos son constantes.
    //
    // El `when` no tiene `else` a proposito: si se agrega un
    // operador y se olvida su plegado, Kotlin no compila.
    private fun foldBinaryOperation(
        operator: BinaryOperator,
        left: TypedValue,
        right: TypedValue,
        resultType: Type
    ): Any? {
        if (!left.isConstant || !right.isConstant || resultType == ErrorType) return null

        return when (operator) {
            BinaryOperator.ADD -> when {
                resultType == StringType -> "${left.constant}${right.constant}"
                resultType == FloatType -> asDouble(left) + asDouble(right)
                else -> asLong(left) + asLong(right)
            }
            BinaryOperator.SUBTRACT -> if (resultType == FloatType) asDouble(left) - asDouble(right) else asLong(left) - asLong(right)
            BinaryOperator.MULTIPLY -> if (resultType == FloatType) asDouble(left) * asDouble(right) else asLong(left) * asLong(right)
            BinaryOperator.DIVIDE -> if (resultType == FloatType) asDouble(left) / asDouble(right) else asLong(left) / asLong(right)
            BinaryOperator.MODULO -> asLong(left) % asLong(right)
            BinaryOperator.EQUAL -> areConstantsEqual(left, right)
            BinaryOperator.NOT_EQUAL -> !areConstantsEqual(left, right)
            BinaryOperator.LESS -> compareConstants(left, right) < 0
            BinaryOperator.LESS_EQUAL -> compareConstants(left, right) <= 0
            BinaryOperator.GREATER -> compareConstants(left, right) > 0
            BinaryOperator.GREATER_EQUAL -> compareConstants(left, right) >= 0
            BinaryOperator.AND -> asBoolean(left) && asBoolean(right)
            BinaryOperator.OR -> asBoolean(left) || asBoolean(right)
        }
    }

    // Cubre / y % — el modulo entre cero falla igual.
    private fun isDivisionByZero(operator: BinaryOperator, right: TypedValue): Boolean {
        if (operator != BinaryOperator.DIVIDE && operator != BinaryOperator.MODULO) return false
        return when (val divisor = right.constant) {
            is Long -> divisor == 0L
            is Double -> divisor == 0.0
            else -> false
        }
    }

    // Los tres `as*` desempaquetan. Sus error(...) son para
    // BUGS DEL COMPILADOR: si llega el valor equivocado,
    // TypeRules aprobo algo que no debia.
    //
    // El caso is Long es la trampa: en 1 + 2.5 el 1 esta
    // guardado como Long, y sin convertirlo revienta.
    private fun asDouble(value: TypedValue): Double = when (val constant = value.constant) {
        is Double -> constant
        is Long -> constant.toDouble()
        else -> error("Se esperaba un número constante, no '$constant'")
    }

    private fun asLong(value: TypedValue): Long = value.constant as? Long
        ?: error("Se esperaba un entero constante, no '${value.constant}'")

    private fun asBoolean(value: TypedValue): Boolean = value.constant as? Boolean
        ?: error("Se esperaba un booleano constante, no '${value.constant}'")

    // El == de Kotlin dice que 1L != 1.0, pero en Compiscript
    // `1 == 1.0` debe ser true.
    private fun areConstantsEqual(left: TypedValue, right: TypedValue): Boolean =
        if (isNumericConstant(left) && isNumericConstant(right)) {
            asDouble(left) == asDouble(right)
        } else {
            left.constant == right.constant
        }

    // El `as String` es seguro: TypeRules.relational solo
    // aprueba dos numericos o dos strings.
    private fun compareConstants(left: TypedValue, right: TypedValue): Int =
        if (isNumericConstant(left) && isNumericConstant(right)) {
            asDouble(left).compareTo(asDouble(right))
        } else {
            (left.constant as String).compareTo(right.constant as String)
        }

    private fun isNumericConstant(value: TypedValue): Boolean =
        value.constant is Long || value.constant is Double

    // "Decorar el arbol" es literalmente esto, y todo pasa por
    // aqui una sola vez. Devuelve el mismo TypedValue para
    // poder escribir `return decorate(...)`.
    private fun decorate(expr: Expression, value: TypedValue): TypedValue {
        expr.type = value.type
        expr.constantValue = value.constant
        return value
    }

    private fun report(node: Node, message: String) {
        diagnostics.report(CompilerError.SemanticError(node.location, message))
    }
}
