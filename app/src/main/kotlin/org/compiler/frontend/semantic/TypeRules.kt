package org.compiler.frontend.semantic

import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.semantic.symbols.*

fun interface ClassHierarchy {
    fun superclassOf(className: String): String?
}

class TypeRules(private val hierarchy: ClassHierarchy) {

    fun isNumeric(type: Type): Boolean =
        type == IntegerType || type == FloatType
    fun widen(left: Type, right: Type): Type {
        require(isNumeric(left) && isNumeric(right)) {
            "widen solo aplica a tipos numéricos, recibió '${left.name}' y '${right.name}'"
        }
        return if (left == FloatType || right == FloatType) FloatType else IntegerType
    }

    fun isAssignable(target: Type, source: Type): Boolean = when {
        target == ErrorType || source == ErrorType -> true
        target == source -> true
        target == FloatType && source == IntegerType -> true
        source == NullType && (target is ClassType || target is ArrayType) -> true
        source is ArrayType && source.element == NullType && target is ArrayType -> true
        target is ClassType && source is ClassType -> isSubclassOf(source, target)
        else -> false
    }

    fun unify(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == right -> left
        isNumeric(left) && isNumeric(right) -> widen(left, right)
        left == NullType && (right is ClassType || right is ArrayType) -> right
        right == NullType && (left is ClassType || left is ArrayType) -> left
        left is ClassType && right is ClassType -> commonAncestor(left, right)

        left is ArrayType && right is ArrayType ->
            unify(left.element, right.element)?.let { ArrayType(it) }

        else -> null
    }

    // ── Regla A1/A2/A3: aritmética ──────────────────────────────────────────
    fun arithmetic(op: BinaryOperator, left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        op == BinaryOperator.MODULO ->
            if (left == IntegerType && right == IntegerType) IntegerType else null
        isNumeric(left) && isNumeric(right) -> widen(left, right)
        else -> null
    }

    // ── Regla C1: concatenación ─────────────────────────────────────────────
    fun concatenation(op: BinaryOperator, left: Type, right: Type): Type? =
        if (op == BinaryOperator.ADD && left == StringType && right == StringType) StringType
        else null

    // ── Regla L1: lógicos ───────────────────────────────────────────────────
    fun logical(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == BooleanType && right == BooleanType -> BooleanType
        else -> null
    }

    // ── Regla R1: relacionales ──────────────────────────────────────────────
    fun relational(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        isNumeric(left) && isNumeric(right) -> BooleanType
        left == StringType && right == StringType -> BooleanType
        else -> null
    }

    // ── Regla E1: igualdad ──────────────────────────────────────────────────
    fun equality(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        unify(left, right) != null -> BooleanType
        else -> null
    }

    // ── Regla L2/N1: unarios ────────────────────────────────────────────────
    fun unary(op: UnaryOperator, operand: Type): Type? = when {
        operand == ErrorType -> ErrorType
        op == UnaryOperator.NOT    -> if (operand == BooleanType) BooleanType else null
        op == UnaryOperator.NEGATE -> if (isNumeric(operand)) operand else null
        else -> null
    }

    private fun isSubclassOf(sub: ClassType, sup: ClassType): Boolean {
        val visited = mutableSetOf<String>()
        var current: String? = sub.name
        while (current != null && visited.add(current)) {
            if (current == sup.name) return true
            current = hierarchy.superclassOf(current)
        }
        return false
    }

    // El ancestro comun mas cercano de dos clases, o null si no comparten ninguno.
    private fun commonAncestor(left: ClassType, right: ClassType): Type? {
        val ancestrosDeLeft = mutableSetOf<String>()
        var current: String? = left.name
        while (current != null && ancestrosDeLeft.add(current)) {
            current = hierarchy.superclassOf(current)
        }

        current = right.name
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (current in ancestrosDeLeft) return ClassType(current)
            current = hierarchy.superclassOf(current)
        }
        return null
    }
}

