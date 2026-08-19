package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

// x = 5;   perro.nombre = "Toby";   lista[0] = 1;
data class Assignment(
    val target: Expression,
    val value: Expression,
    override val location: LexemeLocation
) : Statement

// saludar("mundo");   una expresion cuyo valor se descarta.
data class ExpressionStatement(
    val expr: Expression,
    override val location: LexemeLocation
) : Statement

// print(x);
data class Print(
    val expr: Expression,
    override val location: LexemeLocation
) : Statement

// { ... }
data class Block(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Statement
