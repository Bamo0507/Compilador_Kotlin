package org.compiler.backend.models

import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.RelationalOperator

// Los sufijos de condicion: un cmp deja su resultado en las banderas, y la instruccion
// con el sufijo solo se ejecuta si las banderas cumplen la condicion. `b` + `lt` es
// "salta si es menor".
enum class Condition(val suffix: String) {
    EQUAL("eq"),
    NOT_EQUAL("ne"),
    LESS("lt"),
    LESS_EQUAL("le"),
    GREATER("gt"),
    GREATER_EQUAL("ge"),

    // Despues de comparar flotantes, menor y menor o igual usan estos dos: lt y le
    // tambien se cumplen cuando un operando no es un numero (NaN).
    MINUS("mi"),
    LOWER_SAME("ls");

    companion object {
        // La condicion de una relacion del TAC, segun el tipo de lo que se compara.
        fun of(operator: RelationalOperator, kind: OperandKind): Condition {
            val isFloat = kind == OperandKind.FLOAT
            return when (operator) {
                RelationalOperator.EQUAL -> EQUAL
                RelationalOperator.NOT_EQUAL -> NOT_EQUAL
                RelationalOperator.LESS -> if (isFloat) MINUS else LESS
                RelationalOperator.LESS_EQUAL -> if (isFloat) LOWER_SAME else LESS_EQUAL
                RelationalOperator.GREATER -> GREATER
                RelationalOperator.GREATER_EQUAL -> GREATER_EQUAL
            }
        }
    }
}
