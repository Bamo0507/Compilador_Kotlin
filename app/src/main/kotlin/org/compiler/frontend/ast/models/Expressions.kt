package org.compiler.frontend.ast.models

// Si la expresion llama a una funcion de agregacion en algun lado. Lo usan el
// checker, para la regla del GROUP BY, y el motor, para saber si una consulta
// agrupa.
fun Expression.hasAggregate(): Boolean = when (this) {
    is Aggregate -> true
    is Binary -> left.hasAggregate() || right.hasAggregate()
    is Unary -> operand.hasAggregate()
    is IsNull -> operand.hasAggregate()
    is InList -> operand.hasAggregate() || values.any { it.hasAggregate() }
    is InSubquery -> operand.hasAggregate()
    else -> false
}

fun SelectItem.hasAggregate(): Boolean = this is SelectExpression && expression.hasAggregate()
