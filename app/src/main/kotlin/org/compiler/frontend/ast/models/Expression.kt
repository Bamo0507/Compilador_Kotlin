package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation
import org.compiler.types.BinaryOperator
import org.compiler.types.ErrorType
import org.compiler.types.Type
import org.compiler.types.UnaryOperator
import org.compiler.types.Value


sealed interface Expression : Node {
    // Lo llena la fase 5. Arranca en ErrorType para que un nodo sin tipar no se
    // confunda con uno bien tipado.
    var type: Type
}


class Literal(
    val value: Value,
    val literalType: Type,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}


class ColumnReference(
    // El "u" de `u.name`, o null si venia sin calificar.
    val qualifier: String?,
    val name: String,
    override val location: LexemeLocation
) : Expression {
    // Cuantos ambitos subio el lookup: 0 = esta consulta, 1 = la de afuera.
    var level: Int = -1

    // Posicion dentro de la fila de ESE nivel.
    var index: Int = -1

    override var type: Type = ErrorType
}

// `a + b`, `x = 1`, `p AND q`, `nombre || apellido`. Siempre DOS operandos: la
// gramatica entrega listas planas y el SqlAstBuilder las pliega a la izquierda.
class Binary(
    val operator: BinaryOperator,
    val left: Expression,
    val right: Expression,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

// `-precio`, `NOT activo`.
class Unary(
    val operator: UnaryOperator,
    val operand: Expression,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

// `x IS NULL` o, con negated, `x IS NOT NULL`.
class IsNull(
    val operand: Expression,
    val negated: Boolean,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

// `x IN (1, 2, 3)` o `x NOT IN (...)`.
class InList(
    val operand: Expression,
    val values: List<Expression>,
    val negated: Boolean,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

// `x IN (SELECT ...)`. Va separado de InList porque la subconsulta se evalua
// distinto: puede estar correlacionada y devolver un conjunto de filas.
class InSubquery(
    val operand: Expression,
    val query: Query,
    val negated: Boolean,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

// `EXISTS (SELECT ...)`. `NOT EXISTS` llega como Unary(NOT, Exists).
class Exists(
    val query: Query,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}


class Aggregate(
    val function: AggregateFunction,
    val argument: Expression?,
    val distinct: Boolean,
    override val location: LexemeLocation
) : Expression {
    override var type: Type = ErrorType
}

enum class AggregateFunction { COUNT, COUNT_ALL, SUM, AVG, MIN, MAX }
