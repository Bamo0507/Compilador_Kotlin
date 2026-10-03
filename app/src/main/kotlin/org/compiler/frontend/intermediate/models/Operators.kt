package org.compiler.frontend.intermediate.models

// Propios y no los del AST: alla existen && y ||, que en el TAC no son operaciones
// sino saltos. Con enums aparte, un cuadruplo con && no se puede escribir.

enum class ArithmeticOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%")
}

enum class RelationalOperator(val symbol: String) {
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    EQUAL("=="),
    NOT_EQUAL("!=")
}

// Con prefijo porque el AST ya tiene un UnaryOperator. INT_TO_FLOAT esta aqui porque
// tiene la forma x = op y: una operacion de un operando que produce un valor.
enum class TacUnaryOperator(val symbol: String) {
    NEGATE("-"),
    NOT("!"),
    INT_TO_FLOAT("inttofloat")
}

// El tipo de los operandos. Decide que instruccion de maquina corresponde: sumar
// enteros y sumar flotantes son instrucciones distintas.
enum class OperandKind(val suffix: String) {
    INTEGER(""),
    FLOAT("f"),
    STRING("s"),
    BOOLEAN(""),
    REFERENCE("")
}
