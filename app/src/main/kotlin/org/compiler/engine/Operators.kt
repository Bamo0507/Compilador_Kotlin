package org.compiler.engine

import org.compiler.frontend.semantic.ResultColumn

/**
 * Las piezas sueltas de las que se arma una consulta.
 *
 * Cada una va de ResultSet a ResultSet, asi que un SELECT es una cadena, y la
 * cadena sigue el orden de evaluacion de SQL. Ninguna sabe de expresiones: el
 * motor les pasa ya resuelto que hacer con cada fila.
 */
object Operators {

    /**
     * Las filas de una tabla.
     *
     * No abre el archivo: se las pide a la sesion. De ahi salen dos cosas a la
     * vez, que la misma tabla usada dos veces en un script se lea una sola, y que
     * un SELECT despues de un INSERT vea la fila nueva.
     */
    fun scan(session: Session, table: String): ResultSet {
        val schema = session.catalog.table(table) ?: return ResultSet.EMPTY
        val columns = schema.columns.map { ResultColumn(it.name, it.type, table) }

        return ResultSet(columns, session.rowsOf(table).toList())
    }

    fun filter(input: ResultSet, keep: (Row) -> Boolean): ResultSet = input.with(input.rows.filter(keep))

    fun project(columns: List<ResultColumn>, rows: List<Row>): ResultSet = ResultSet(columns, rows)

    // Mantiene la primera aparicion de cada fila. DecimalValue ya compara por
    // valor y no por escala, asi que 1.0 y 1.00 colapsan sin hacer nada aparte.
    fun distinct(input: ResultSet): ResultSet = input.with(input.rows.distinct())

    fun limit(input: ResultSet, count: Int?): ResultSet =
        if (count == null) input else input.with(input.rows.take(count))

    /**
     * Bucles anidados: por cada fila de la izquierda se recorre la derecha entera
     * y se conserva el par que cumple la condicion.
     *
     * Es cuadratico, y para el tamano de datos de este proyecto es correcto. Es el
     * unico lugar que habria que tocar si el volumen creciera.
     *
     * La fila resultante es la concatenacion de las dos, en el orden del FROM, que
     * es justo donde apuntan los indices que dejo la fase 4.
     */
    fun join(left: ResultSet, right: ResultSet, matches: (Row) -> Boolean): ResultSet {
        val rows = left.rows.flatMap { outer ->
            right.rows.map { inner -> Row(outer.values + inner.values) }.filter(matches)
        }
        return ResultSet(left.columns + right.columns, rows)
    }

    // Estable: dos filas que empatan en todas las claves quedan en el orden en que
    // venian.
    fun <T> sort(items: List<T>, keys: List<SortKey<T>>): List<T> {
        if (keys.isEmpty()) return items

        return items.sortedWith { left, right ->
            keys.firstNotNullOfOrNull { key ->
                ValueOrder.forSort(key.of(left), key.of(right), key.descending).takeIf { it != 0 }
            } ?: 0
        }
    }

    class SortKey<T>(val descending: Boolean, val of: (T) -> org.compiler.types.Value)
}
