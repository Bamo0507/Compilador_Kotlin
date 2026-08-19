package org.compiler.frontend.ast.models

/**
 * A que familia pertenece un operador binario.
 */
enum class OperatorGroup {
    ARITHMETIC,
    EQUALITY,
    RELATIONAL,
    LOGICAL
}

enum class BinaryOperator(val symbol: String, val group: OperatorGroup) {
    ADD("+", OperatorGroup.ARITHMETIC),
    SUBTRACT("-", OperatorGroup.ARITHMETIC),
    MULTIPLY("*", OperatorGroup.ARITHMETIC),
    DIVIDE("/", OperatorGroup.ARITHMETIC),
    MODULO("%", OperatorGroup.ARITHMETIC),

    EQUAL("==", OperatorGroup.EQUALITY),
    NOT_EQUAL("!=", OperatorGroup.EQUALITY),

    LESS("<", OperatorGroup.RELATIONAL),
    LESS_EQUAL("<=", OperatorGroup.RELATIONAL),
    GREATER(">", OperatorGroup.RELATIONAL),
    GREATER_EQUAL(">=", OperatorGroup.RELATIONAL),

    AND("&&", OperatorGroup.LOGICAL),
    OR("||", OperatorGroup.LOGICAL);

    companion object {
        // El AstBuilder lee los operadores como texto del arbol de ANTLR.
        fun fromSymbol(symbol: String): BinaryOperator =
            entries.first { it.symbol == symbol }
    }
}

enum class UnaryOperator(val symbol: String) {
    NEGATE("-"),
    NOT("!");

    companion object {
        fun fromSymbol(symbol: String): UnaryOperator =
            entries.first { it.symbol == symbol }
    }
}
