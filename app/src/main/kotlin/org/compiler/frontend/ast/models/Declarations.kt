package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

// let x: integer = 5;   var y;   const PI: integer = 314;
data class VariableDeclaration(
    val name: String,

    // null si no se anoto: hay que inferirlo del inicializador.
    val declaredType: TypeReference?,

    val initializer: Expression?,
    val isConstant: Boolean,
    override val location: LexemeLocation
) : Statement

// function saludar(nombre: string): string
data class FunctionDeclaration(
    val name: String,
    val parameters: List<Parameter>,

    val returnType: TypeReference?,

    val body: Block,
    override val location: LexemeLocation
) : Statement

data class Parameter(
    val name: String,
    val declaredType: TypeReference?,
    override val location: LexemeLocation
) : Node

data class ClassDeclaration(
    val name: String,

    // null si no hereda.
    val superclassName: String?,

    // VariableDeclaration para los campos, FunctionDeclaration para los metodos. No
    // hay un tipo ClassMember propio porque duplicaria esos dos sin agregar nada.
    val members: List<Statement>,

    override val location: LexemeLocation
) : Statement
