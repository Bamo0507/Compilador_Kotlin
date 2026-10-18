package org.compiler.engine

import org.compiler.frontend.semantic.ResultColumn
import org.compiler.types.Value

/**
 * Una fila, posicional.
 *
 * No es un mapa de nombre a valor porque es lo que ya es una linea de CSV, y
 * porque en un JOIN puede haber dos columnas llamadas `id`: en un mapa se
 * pisarian.
 *
 * Es data class para que DISTINCT funcione con un set. DecimalValue ya compara
 * por valor y no por escala, asi que 1.0 y 1.00 colapsan solos.
 */
data class Row(val values: List<Value>)

/**
 * Lo que devuelve una consulta, y tambien lo que viaja entre operadores.
 *
 * Las columnas no las inventa el motor: salen de `schemaOf`, que ya resolvio la
 * expansion de `*`, los nombres de salida y los tipos.
 */
class ResultSet(
    val columns: List<ResultColumn>,
    val rows: List<Row>
) {
    fun with(rows: List<Row>) = ResultSet(columns, rows)

    companion object {
        val EMPTY = ResultSet(emptyList(), emptyList())
    }
}

/**
 * Las filas visibles en un punto de la evaluacion, una por nivel de anidamiento.
 *
 * El nivel 0 es la consulta que se esta evaluando ahora. Hace falta una pila y no
 * una sola fila por las subconsultas correlacionadas: al evaluar `p.uid = u.id`
 * dentro de un EXISTS el motor esta parado en una fila de posts y necesita
 * tambien la de users.
 *
 * Es el mismo mecanismo que un ambiente encadenado, con una diferencia: aqui el
 * salto NO se recorre, porque la fase 4 ya dejo el numero. Buscar un valor son
 * dos indexaciones de arreglo.
 */
class RowContext(private val stack: List<Row>) {

    fun value(level: Int, index: Int): Value = stack[level].values[index]

    // Devuelve uno nuevo: el de afuera lo sigue usando la consulta de afuera.
    fun nest(row: Row) = RowContext(listOf(row) + stack)

    // Cambia la fila de este nivel sin tocar las de afuera. Lo usa la agregacion,
    // que recorre las filas de un grupo con el mismo contexto exterior.
    fun withTop(row: Row) = RowContext(listOf(row) + stack.drop(1))

    val depth: Int get() = stack.size

    companion object {
        val EMPTY = RowContext(emptyList())

        fun of(row: Row) = RowContext(listOf(row))
    }
}
