package org.compiler.frontend.ast.models

import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation

// let x: integer = 5;   var y: string;   const PI: integer = 314;
data class VariableDeclaration(
    val name: String,

    // null si no se anoto: hay que inferirlo del inicializador.
    val declaredType: TypeReference?,

    val initializer: Expression?,
    val isConstant: Boolean,
    override val location: LexemeLocation
) : Statement {

    // El Symbol que declaro el TypeChecker. Lo lee el generador de TAC: `let x = e`
    // escribe en Name(symbol), y sin esto tendria que volver a buscar el nombre. Es
    // el mismo motivo por el que Identifier guarda su resolvedSymbol.
    var symbol: Symbol? = null
}

// function saludar(nombre: string): string
data class FunctionDeclaration(
    val name: String,
    val parameters: List<Parameter>,

    val returnType: TypeReference?,

    val body: Block,
    override val location: LexemeLocation
) : Statement {

    // El Symbol de la funcion y el ambito de su cuerpo, que los deja el TypeChecker. El
    // generador de TAC los usa para saber la etiqueta de cada llamada y encontrar el
    // registro de activacion de la funcion.
    var symbol: Symbol? = null
    var scope: Scope? = null
}

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
