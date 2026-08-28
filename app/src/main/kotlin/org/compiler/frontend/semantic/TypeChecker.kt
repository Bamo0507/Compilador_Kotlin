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
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
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

    internal fun checkExpression(expr: Expression): TypedValue = when (expr) {
        is Literal -> checkLiteral(expr)
        is Identifier -> checkIdentifier(expr)
        is BinaryOperation -> checkBinaryOperation(expr)
        is UnaryOperation -> checkUnaryOperation(expr)
        is TernaryOperation -> checkTernaryOperation(expr)
        is ArrayLiteral -> checkArrayLiteral(expr)

        // Estos nodos se implementan en el ticket 4.3 y las asignaciones en 4.4.
        is FunctionCall, is IndexAccess, is PropertyAccess, is ObjectCreation,
        is ThisReference, is AssignmentExpression ->
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

        if (!symbol.initialized && symbol.kind != org.compiler.frontend.semantic.symbols.DeclarationKind.FUNCTION) {
            report(expr, "La variable '${expr.name}' se usa antes de tener un valor")
        }

        val constant = if (symbol.kind == org.compiler.frontend.semantic.symbols.DeclarationKind.CONSTANT) {
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
