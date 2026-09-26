package org.compiler.types

/**
 * A que familia de operacion pertenece un operador binario.
 *
 * Agrupa porque las reglas se escriben por categoria y no por operador: los
 * cuatro aritmeticos comparten regla, los cuatro de orden tambien.
 */
enum class OperatorCategory {
    ARITHMETIC,
    CONCATENATION,
    ORDER,
    EQUALITY,
    LOGICAL
}

enum class BinaryOperator(val symbol: String, val category: OperatorCategory) {
    ADD("+", OperatorCategory.ARITHMETIC),
    SUBTRACT("-", OperatorCategory.ARITHMETIC),
    MULTIPLY("*", OperatorCategory.ARITHMETIC),
    DIVIDE("/", OperatorCategory.ARITHMETIC),

    // En SQL `||` concatena. En un lenguaje imperativo es el OR logico, y
    // confundirlos hace que 'hola' || ' mundo' se parsee como un OR entre textos.
    CONCAT("||", OperatorCategory.CONCATENATION),

    EQUAL("=", OperatorCategory.EQUALITY),
    NOT_EQUAL("<>", OperatorCategory.EQUALITY),

    LESS("<", OperatorCategory.ORDER),
    LESS_EQUAL("<=", OperatorCategory.ORDER),
    GREATER(">", OperatorCategory.ORDER),
    GREATER_EQUAL(">=", OperatorCategory.ORDER),

    AND("AND", OperatorCategory.LOGICAL),
    OR("OR", OperatorCategory.LOGICAL);

    companion object {
        // El AstBuilder lee los operadores como texto del arbol de ANTLR.
        // `!=` es el mismo operador que `<>`: SQL acepta las dos formas.
        fun fromSymbol(symbol: String): BinaryOperator? = when (symbol) {
            "!=" -> NOT_EQUAL
            else -> entries.firstOrNull { it.symbol == symbol }
        }
    }
}

enum class UnaryOperator(val symbol: String) {
    NEGATE("-"),
    NOT("NOT");

    companion object {
        fun fromSymbol(symbol: String): UnaryOperator? =
            entries.firstOrNull { it.symbol == symbol }
    }
}
