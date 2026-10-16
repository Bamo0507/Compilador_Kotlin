package org.compiler.frontend.semantic

import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.SelectAll
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.frontend.ast.models.SelectTableAll
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.types.Type

/**
 * Una columna de lo que devuelve un SELECT.
 *
 * El nombre es nulo cuando la columna no tiene uno natural, como en
 * `SELECT edad * 2` sin AS: se puede mostrar en la rejilla, pero nadie la puede
 * nombrar desde afuera.
 */
data class ResultColumn(val name: String?, val type: Type)

/**
 * Que columnas produce una consulta, con que nombre y de que tipo.
 *
 * Se llama despues de resolver el FROM, porque expandir `*` necesita saber que
 * tablas quedaron a la vista.
 */
fun schemaOf(query: Query): List<ResultColumn> {
    val scope = query.scope ?: return emptyList()

    return query.selection.flatMap { item ->
        when (item) {
            is SelectAll -> scope.sources().flatMap { columnsOf(it) }
            is SelectTableAll -> columnsOf(scope.sources().firstOrNull { it.name == item.qualifier })
            is SelectExpression -> listOf(resultColumnOf(item))
        }
    }
}

// El orden es el del FROM, que es el mismo de la fila concatenada de un JOIN.
private fun columnsOf(source: Symbol?): List<ResultColumn> =
    source?.table?.columns.orEmpty().map { ResultColumn(it.name, it.type) }

/**
 * El nombre sale del AS; si no hay, de la columna cuando el item es una
 * referencia pelada. Una expresion calculada sin AS se queda sin nombre.
 *
 * El tipo de una expresion calculada lo llena la fase 5, asi que aqui todavia es
 * ErrorType. El de una referencia ya esta, porque viene del simbolo al que se
 * resolvio.
 */
private fun resultColumnOf(item: SelectExpression): ResultColumn {
    val reference = item.expression as? ColumnReference
        ?: return ResultColumn(item.alias, item.expression.type)

    return ResultColumn(item.alias ?: reference.name, reference.symbol?.type ?: item.expression.type)
}
