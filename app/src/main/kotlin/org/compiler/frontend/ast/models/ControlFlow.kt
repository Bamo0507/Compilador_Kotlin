package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

data class If(
    val condition: Expression,
    val thenBranch: Block,

    // null si no hay else.
    val elseBranch: Block?,

    override val location: LexemeLocation
) : Statement

data class While(
    val condition: Expression,
    val body: Block,
    override val location: LexemeLocation
) : Statement

data class DoWhile(
    val body: Block,
    val condition: Expression,
    override val location: LexemeLocation
) : Statement

// initializer es Statement y update es Expression porque la gramatica los declara
// distintos: el primero es un variableDeclaration o un assignment.
data class For(
    val initializer: Statement?,
    val condition: Expression?,
    val update: Expression?,
    val body: Block,
    override val location: LexemeLocation
) : Statement

// variableName no lleva tipo: se infiere del tipo de elemento del iterable. Junto con
// `let x = <expr>`, es el unico punto de inferencia del lenguaje.
data class ForEach(
    val variableName: String,
    val iterable: Expression,
    val body: Block,
    override val location: LexemeLocation
) : Statement

// switch (x) { case 1: ... default: ... }
data class Switch(
    val subject: Expression,
    val cases: List<SwitchCase>,

    // null si no hay default; lista vacia si hay uno vacio. La distincion importa: sin
    // default, un valor que no coincida con ningun case pasa de largo.
    val defaultBody: List<Statement>?,

    override val location: LexemeLocation
) : Statement

// case 1: ...
//
// El cuerpo es una lista y no un Block porque la gramatica no le pone llaves al case.
data class SwitchCase(
    val value: Expression,
    val body: List<Statement>,
    override val location: LexemeLocation
) : Node

data class TryCatch(
    val tryBlock: Block,
    val catchParameterName: String,
    val catchBlock: Block,
    override val location: LexemeLocation
) : Statement

data class Break(override val location: LexemeLocation) : Statement

data class Continue(override val location: LexemeLocation) : Statement

data class Return(
    val value: Expression?,
    override val location: LexemeLocation
) : Statement
