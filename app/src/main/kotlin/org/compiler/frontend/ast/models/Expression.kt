package org.compiler.frontend.ast.models

import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.models.LexemeLocation

/**
 * Una expresion: algo que produce un valor.
 *
 * Es sealed class y no interface porque los dos campos de abajo son mutables, y una
 * clase los declara una vez en vez de repetirlos en cada subclase.
 */
sealed class Expression : Node {

    // El tipo de esta expresion. Lo rellena el TypeChecker.
    var type: Type? = null

    var constantValue: Any? = null
}

// 123   3.14   "texto"   true   false   null
data class Literal(
    // Long, Double, String, Boolean o null, segun literalType.
    val value: Any?,
    val literalType: Type,
    override val location: LexemeLocation
) : Expression()

// [1, 2, 3]
data class ArrayLiteral(
    val elements: List<Expression>,
    override val location: LexemeLocation
) : Expression()

// x   nombre   miFuncion
data class Identifier(
    val name: String,
    override val location: LexemeLocation
) : Expression() {

    // A que declaracion se resolvio. Lo pone el TypeChecker, para que ninguna fase
    // posterior tenga que volver a buscar el nombre.
    var resolvedSymbol: Symbol? = null
}

// this
data class ThisReference(
    override val location: LexemeLocation
) : Expression()

// -x   !bandera
data class UnaryOperation(
    val operator: UnaryOperator,
    val operand: Expression,
    override val location: LexemeLocation
) : Expression()

// a + b   x < y   p && q
//
// Siempre DOS operandos. ANTLR entrega listas planas —`a + b + c` llega como un nodo
// con tres hijos—, y el AstBuilder las pliega a la izquierda.
data class BinaryOperation(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    override val location: LexemeLocation
) : Expression()

// condicion ? siVerdadero : siFalso
data class TernaryOperation(
    val condition: Expression,
    val ifTrue: Expression,
    val ifFalse: Expression,
    override val location: LexemeLocation
) : Expression()

// x = 5 usado DENTRO de otra expresion: `let y = (x = 5);`, `if (x = 1)`.
// A nivel de sentencia el AstBuilder produce un Assignment.
data class AssignmentExpression(
    val target: Expression,
    val value: Expression,
    override val location: LexemeLocation
) : Expression()

// saludar("mundo")   perro.hablar()
//
// Para `perro.hablar()` el callee es un PropertyAccess: la llamada a metodo no es un
// nodo aparte.
data class FunctionCall(
    val callee: Expression,
    val arguments: List<Expression>,
    override val location: LexemeLocation
) : Expression()

// lista[0]
data class IndexAccess(
    val target: Expression,
    val index: Expression,
    override val location: LexemeLocation
) : Expression()

// perro.nombre
data class PropertyAccess(
    val target: Expression,
    val propertyName: String,
    override val location: LexemeLocation
) : Expression() {

    // El miembro de la clase al que se resolvio, propio o heredado. Lo pone el
    // TypeChecker.
    var resolvedMember: Symbol? = null
}

// new Perro("Toby")
data class ObjectCreation(
    val className: String,
    val arguments: List<Expression>,
    override val location: LexemeLocation
) : Expression()
