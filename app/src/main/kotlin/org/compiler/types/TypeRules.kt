package org.compiler.types

import kotlin.math.max

/**
 * Que se puede comparar con que, y de que tipo sale el resultado.
 *
 * Vive aparte de Type a proposito: Type describe los datos, TypeRules describe
 * lo que se puede hacer con ellos.
 */
object TypeRules {

    /**
     * Entre familias distintas no hay conversion implicita.
     *
     * DATE y TIME comparten familia pero NO se comparan entre si: son cosas
     * distintas, y dejarlo pasar solo esconde errores.
     */
    enum class Family { NUMERIC, CHARACTER, TEMPORAL, LOGICAL, NULL, ERROR }

    fun familyOf(type: Type): Family = when (type) {
        IntType, FloatType, is DecimalType -> Family.NUMERIC
        is CharType, is VarcharType, TextType -> Family.CHARACTER
        DateType, TimeType -> Family.TEMPORAL
        BooleanType -> Family.LOGICAL
        NullType -> Family.NULL
        ErrorType -> Family.ERROR
    }

    /**
     * Cabe un valor de `source` en una columna de `target`?
     *
     * Es DIRECCIONAL, al reves que `unify`: un INT se guarda en una columna
     * FLOAT, pero un FLOAT en una INT perderia la parte fraccionaria.
     *
     * Para CHAR, VARCHAR y DECIMAL el largo y la escala NO se revisan aqui: el
     * tipo deja pasar y el valor concreto se verifica al escribir, que es donde
     * de verdad se sabe si cabe.
     */
    fun isAssignable(source: Type, target: Type): Boolean = when {
        source == ErrorType || target == ErrorType -> true
        source == NullType -> true
        familyOf(source) != familyOf(target) -> false
        familyOf(source) == Family.TEMPORAL -> source == target
        familyOf(source) == Family.NUMERIC -> widthOf(source) <= widthOf(target)
        else -> true
    }

    /**
     * El tipo comun de dos tipos, para compararlos. Es SIMETRICA.
     *
     * Devuelve null cuando no hay tipo comun, que es como se reporta que la
     * comparacion no tiene sentido.
     */
    fun unify(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == NullType -> right
        right == NullType -> left
        familyOf(left) != familyOf(right) -> null

        familyOf(left) == Family.NUMERIC ->
            if (left is DecimalType && right is DecimalType) {
                commonDecimal(left, right)
            } else {
                if (widthOf(left) >= widthOf(right)) left else right
            }

        familyOf(left) == Family.TEMPORAL -> if (left == right) left else null
        familyOf(left) == Family.CHARACTER -> TextType
        else -> left
    }

    /**
     * El tipo del resultado de `left op right`, o null si la operacion no tiene
     * sentido con esos tipos.
     */
    fun binaryResultType(operator: BinaryOperator, left: Type, right: Type): Type? {
        if (left == ErrorType || right == ErrorType) return ErrorType

        return when (operator.category) {
            OperatorCategory.ARITHMETIC -> arithmeticType(operator, left, right)

            OperatorCategory.CONCATENATION -> TextType.takeIf {
                bothAre(Family.CHARACTER, left, right)
            }

            // BOOLEAN queda fuera a proposito: `true > false` no significa nada,
            // y permitirlo solo deja pasar comparaciones escritas por error.
            OperatorCategory.ORDER -> BooleanType.takeIf {
                unify(left, right) != null && familyOf(left) != Family.LOGICAL
            }

            OperatorCategory.EQUALITY -> BooleanType.takeIf { unify(left, right) != null }

            OperatorCategory.LOGICAL -> BooleanType.takeIf {
                bothAre(Family.LOGICAL, left, right)
            }
        }
    }

    fun unaryResultType(operator: UnaryOperator, operand: Type): Type? = when {
        operand == ErrorType -> ErrorType
        operand == NullType -> NullType
        operator == UnaryOperator.NEGATE ->
            operand.takeIf { familyOf(it) == Family.NUMERIC }
        else -> BooleanType.takeIf { operand == BooleanType }
    }

    // IS NULL es la UNICA forma de preguntar por un nulo, porque `= NULL` da
    // NULL y no true. Acepta cualquier tipo.
    fun isNullResultType(): Type = BooleanType

    // ── Aritmetica ─────────────────────────────────────────────────────────

    private fun arithmeticType(operator: BinaryOperator, left: Type, right: Type): Type? {
        if (left == NullType) return right.takeIf { familyOf(it) == Family.NUMERIC }
        if (right == NullType) return left.takeIf { familyOf(it) == Family.NUMERIC }
        if (!bothAre(Family.NUMERIC, left, right)) return null

        // FLOAT contagia: mezclar exacto con inexacto da inexacto.
        if (left == FloatType || right == FloatType) return FloatType

        // INT / INT trunca, como en SQL estandar: 10 / 3 da 3.
        if (left == IntType && right == IntType) return IntType

        return decimalArithmetic(operator, asDecimal(left), asDecimal(right))
    }

    /**
     * Las reglas del estandar. La precision crece porque multiplicar dos numeros
     * de dos decimales necesita cuatro: quedarse con la escala mayor haria que
     * 0.05 * 0.05 diera 0.00.
     *
     * Aqui no hay tope de digitos porque BigDecimal es de precision arbitraria,
     * y un tipo intermedio nunca se guarda: solo existe mientras se evalua.
     */
    private fun decimalArithmetic(
        operator: BinaryOperator,
        left: DecimalType,
        right: DecimalType
    ): DecimalType {
        val leftInteger = left.precision - left.scale
        val rightInteger = right.precision - right.scale

        return when (operator) {
            BinaryOperator.MULTIPLY ->
                DecimalType(left.precision + right.precision + 1, left.scale + right.scale)

            // El estandar la deja sin definir porque 1/3 no termina, asi que se
            // eligen 6 decimales de mas, como la escala minima de SQL Server.
            BinaryOperator.DIVIDE -> {
                val scale = max(left.scale, right.scale) + DIVISION_EXTRA_SCALE
                DecimalType(left.precision + right.scale + DIVISION_EXTRA_SCALE, scale)
            }

            // Suma y resta. El +1 es el acarreo.
            else -> {
                val scale = max(left.scale, right.scale)
                DecimalType(max(leftInteger, rightInteger) + scale + 1, scale)
            }
        }
    }

    // Un INT entra a la aritmetica decimal como sus 19 digitos sin escala, que es
    // lo que cabe en un Long.
    private fun asDecimal(type: Type): DecimalType =
        type as? DecimalType ?: DecimalType(INT_DIGITS, 0)

    private fun bothAre(family: Family, left: Type, right: Type): Boolean =
        familyOf(left) == family && familyOf(right) == family

    private const val DIVISION_EXTRA_SCALE = 6
    private const val INT_DIGITS = 19

    // La torre numerica: INT -> DECIMAL -> FLOAT. Ensancha en un solo sentido.
    private fun widthOf(type: Type): Int = when (type) {
        IntType -> 0
        is DecimalType -> 1
        FloatType -> 2
        else -> -1
    }

    // El DECIMAL mas chico que puede contener a cualquiera de los dos. No lleva
    // el +1 de la suma, porque comparar no acarrea.
    private fun commonDecimal(left: DecimalType, right: DecimalType): DecimalType {
        val scale = max(left.scale, right.scale)
        val integerDigits = max(left.precision - left.scale, right.precision - right.scale)
        return DecimalType(integerDigits + scale, scale)
    }
}
