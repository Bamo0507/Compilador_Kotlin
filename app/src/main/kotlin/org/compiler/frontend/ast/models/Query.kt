package org.compiler.frontend.ast.models

import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.models.LexemeLocation
import org.compiler.types.ErrorType
import org.compiler.types.Type


class Query(
    val distinct: Boolean,
    val selection: List<SelectItem>,
    val source: FromSource,
    val joins: List<Join>,
    val where: Expression?,
    val groupBy: List<Expression>,
    val having: Expression?,
    val orderBy: List<OrderCriterion>,

    // null si no hay LIMIT. Que sea positivo lo valida la fase 5.
    val limit: Int?,

    override val location: LexemeLocation
) : Statement, Expression {

    // El tipo, cuando se usa como subconsulta escalar. Lo llena la fase 5.
    override var type: Type = ErrorType

    // El ambito de ESTA consulta, con sus tablas y alias. Lo llena la fase 4.
    var scope: Scope? = null

    // Si mira columnas de una consulta de afuera. Lo llena la fase 4 (ticket 4.4)
    // y lo lee la fase 6: una subconsulta no correlacionada se ejecuta una vez.
    var correlated: Boolean = false
}

// El FROM ----------------------------------------------------------------------

sealed interface FromSource : Node

// `users` o `users u`.
class TableSource(
    val table: String,
    val alias: String?,
    override val location: LexemeLocation
) : FromSource

// `(SELECT ...) AS t`. El alias es obligatorio: la gramatica lo exige.
class DerivedSource(
    val query: Query,
    val alias: String,
    override val location: LexemeLocation
) : FromSource

class Join(
    val source: FromSource,
    val condition: Expression,
    override val location: LexemeLocation
) : Node

// El SELECT --------------------------------------------------------------------

sealed interface SelectItem : Node

// `SELECT *`. Es clase y no `data object` porque, como todo Node, necesita su
// ubicacion: la fase 5 reporta `*` con GROUP BY apuntando a ella.
class SelectAll(
    override val location: LexemeLocation
) : SelectItem

// `SELECT u.*`. Se llama qualifier y no alias porque tambien puede ser el nombre
// de la tabla (`users.*`), igual que el qualifier de ColumnReference.
class SelectTableAll(
    val qualifier: String,
    override val location: LexemeLocation
) : SelectItem

// `SELECT edad * 2 AS doble`. Sin `AS`, el alias es null.
class SelectExpression(
    val expression: Expression,
    val alias: String?,
    override val location: LexemeLocation
) : SelectItem

// ORDER BY ---------------------------------------------------------------------

class OrderCriterion(
    val expression: Expression,

    // ASC es lo que se asume sin decir nada, asi que solo DESC cambia algo.
    val descending: Boolean,

    override val location: LexemeLocation
) : Node
