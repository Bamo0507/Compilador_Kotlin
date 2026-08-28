package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.ArrayLiteral
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Node
import org.compiler.frontend.ast.models.ObjectCreation
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.ThisReference
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
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
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Type

data class TypedValue(
    val type: Type,
    val constant: Any? = null
) {
    val isConstant: Boolean get() = constant != null
}

/**
 * Pasada 2: verifica tipos y decora las expresiones del AST.
 *
 * El recorrido completo de sentencias se incorpora en el ticket 4.4. Mientras tanto,
 * el punto de entrada interno permite probar las reglas de expresiones de este ticket
 * sin adelantar ese despachador.
 */
class TypeChecker(
    private val globalScope: Scope,
    private val diagnostics: Diagnostics
) {
    private var currentScope: Scope = globalScope

    private val typeRules = TypeRules { className ->
        globalScope.lookupLocal(className)?.memberScope?.superclass?.name
    }

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

        // Las asignaciones se implementan en el ticket 4.4.
        is AssignmentExpression ->
            error("La expresión '${expr::class.simpleName}' aún no pertenece al ticket 4.2")
    }

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

    private fun checkIdentifier(expr: Identifier): TypedValue {
        val symbol = currentScope.lookup(expr.name)
        if (symbol == null) {
            report(expr, "La variable '${expr.name}' no está declarada")
            return decorate(expr, TypedValue(ErrorType))
        }

        expr.resolvedSymbol = symbol
        symbol.useCount += 1
        symbol.lastUseLine = expr.location.line

        if (currentScope.functionDepth() > symbol.declarationFunctionDepth &&
            symbol.declarationFunctionDepth > 0
        ) {
            symbol.usedInNestedFunction = true
        }

        if (!symbol.initialized && symbol.kind != DeclarationKind.FUNCTION) {
            report(expr, "La variable '${expr.name}' se usa antes de tener un valor")
        }

        val constant = if (symbol.kind == DeclarationKind.CONSTANT) {
            symbol.constantValue
        } else {
            null
        }
        return decorate(expr, TypedValue(symbol.type, constant))
    }

    private fun checkBinaryOperation(expr: BinaryOperation): TypedValue {
        val left = checkExpression(expr.left)
        val right = checkExpression(expr.right)

        val resultType = when (expr.operator.group) {
            org.compiler.frontend.ast.models.OperatorGroup.ARITHMETIC ->
                typeRules.arithmetic(expr.operator, left.type, right.type)
                    ?: typeRules.concatenation(expr.operator, left.type, right.type)
            org.compiler.frontend.ast.models.OperatorGroup.LOGICAL -> typeRules.logical(left.type, right.type)
            org.compiler.frontend.ast.models.OperatorGroup.RELATIONAL -> typeRules.relational(left.type, right.type)
            org.compiler.frontend.ast.models.OperatorGroup.EQUALITY -> typeRules.equality(left.type, right.type)
        }

        if (resultType == null) {
            report(expr, "El operador '${expr.operator.symbol}' no se puede aplicar a " +
                "'${left.type.name}' y '${right.type.name}'")
            return decorate(expr, TypedValue(ErrorType))
        }

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

    private fun checkArrayLiteral(expr: ArrayLiteral): TypedValue {
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

    private fun checkFunctionCall(expr: FunctionCall): TypedValue {
        val calleeType = checkExpression(expr.callee).type

        if (calleeType == ErrorType) {
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

    private fun describeCallee(callee: Expression): String = when (callee) {
        is Identifier -> callee.name
        is PropertyAccess -> "${describeCallee(callee.target)}.${callee.propertyName}"
        is ThisReference -> "this"
        else -> "la expresión"
    }

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

    private fun classScopeOf(className: String): Scope? =
        globalScope.lookupLocal(className)?.memberScope

    // Se invoca desde checkFunctionDeclaration en el ticket 4.4, cuando se entra al
    // ámbito de una clase y las firmas de todos los métodos ya están disponibles.
    private fun checkOverride(declaration: org.compiler.frontend.ast.models.FunctionDeclaration, classScope: Scope) {
        val inherited = classScope.superclass?.lookupMember(declaration.name) ?: return
        val ownType = classScope.lookupLocal(declaration.name)?.type ?: return
        if (ownType != inherited.type) {
            report(declaration, "El método '${declaration.name}' sobrescribe el de la superclase " +
                "con otra firma: se esperaba '${inherited.type.name}' y es '${ownType.name}'")
        }
    }

    private fun checkObjectCreation(expr: ObjectCreation): TypedValue {
        val classSymbol = globalScope.lookupLocal(expr.className)
        if (classSymbol == null || classSymbol.kind != DeclarationKind.CLASS) {
            report(expr, "La clase '${expr.className}' no está declarada")
            expr.arguments.forEach { checkExpression(it) }
            return decorate(expr, TypedValue(ErrorType))
        }

        val constructor = classSymbol.memberScope!!.lookupMember(CONSTRUCTOR_NAME)
        val expectedParameters = (constructor?.type as? FunctionType)?.parameters ?: emptyList()
        checkArguments(expr, expectedParameters, expr.arguments)
        return decorate(expr, TypedValue(ClassType(expr.className)))
    }

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
        if (index.constant is Long && index.constant < 0) {
            report(expr.index, "El índice no puede ser negativo: ${index.constant}")
        }
        return decorate(expr, TypedValue(targetType.element))
    }

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

    private fun isDivisionByZero(operator: BinaryOperator, right: TypedValue): Boolean {
        if (operator != BinaryOperator.DIVIDE && operator != BinaryOperator.MODULO) return false
        return when (val divisor = right.constant) {
            is Long -> divisor == 0L
            is Double -> divisor == 0.0
            else -> false
        }
    }

    private fun asDouble(value: TypedValue): Double = when (val constant = value.constant) {
        is Double -> constant
        is Long -> constant.toDouble()
        else -> error("Se esperaba un número constante, no '$constant'")
    }

    private fun asLong(value: TypedValue): Long = value.constant as? Long
        ?: error("Se esperaba un entero constante, no '${value.constant}'")

    private fun asBoolean(value: TypedValue): Boolean = value.constant as? Boolean
        ?: error("Se esperaba un booleano constante, no '${value.constant}'")

    private fun areConstantsEqual(left: TypedValue, right: TypedValue): Boolean =
        if (isNumericConstant(left) && isNumericConstant(right)) {
            asDouble(left) == asDouble(right)
        } else {
            left.constant == right.constant
        }

    private fun compareConstants(left: TypedValue, right: TypedValue): Int =
        if (isNumericConstant(left) && isNumericConstant(right)) {
            asDouble(left).compareTo(asDouble(right))
        } else {
            (left.constant as String).compareTo(right.constant as String)
        }

    private fun isNumericConstant(value: TypedValue): Boolean =
        value.constant is Long || value.constant is Double

    private fun decorate(expr: Expression, value: TypedValue): TypedValue {
        expr.type = value.type
        expr.constantValue = value.constant
        return value
    }

    private fun report(node: Node, message: String) {
        diagnostics.report(CompilerError.SemanticError(node.location, message))
    }
}
