package org.compiler.engine

import org.compiler.types.IntValue
import org.compiler.types.StringValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * La fila, el resultado y la pila de filas con la que se evalua.
 *
 * La pila es la consecuencia de las subconsultas correlacionadas: al evaluar
 * `p.uid = u.id` dentro de un EXISTS el motor esta parado en una fila de posts y
 * necesita tambien la de users.
 */
class ResultSetTest {

    private fun row(vararg values: Int) = Row(values.map { IntValue(it.toLong()) })

    @Test
    fun `una fila es igual a otra con los mismos valores`() {
        assertEquals(row(1, 2), row(1, 2))
    }

    // Es lo que hace que DISTINCT funcione con un set.
    @Test
    fun `dos filas iguales comparten hash`() {
        assertEquals(row(1, 2).hashCode(), row(1, 2).hashCode())
    }

    @Test
    fun `un valor sale por su posicion, no por su nombre`() {
        val context = RowContext.of(Row(listOf(IntValue(7), StringValue("Ana"))))

        assertEquals(IntValue(7), context.value(0, 0))
        assertEquals(StringValue("Ana"), context.value(0, 1))
    }

    @Test
    fun `anidar no toca el contexto original`() {
        val outer = RowContext.of(row(1))
        val inner = outer.nest(row(2))

        assertNotSame(outer, inner)
        assertEquals(1, outer.depth)
        assertEquals(2, inner.depth)
        assertEquals(IntValue(1), outer.value(0, 0))
    }

    // El nivel 0 es siempre la consulta que se esta evaluando ahora.
    @Test
    fun `el nivel uno es la fila de la consulta de afuera`() {
        val context = RowContext.of(row(10)).nest(row(20))

        assertEquals(IntValue(20), context.value(0, 0))
        assertEquals(IntValue(10), context.value(1, 0))
    }

    @Test
    fun `tres niveles cuentan de adentro hacia afuera`() {
        val context = RowContext.of(row(1)).nest(row(2)).nest(row(3))

        assertEquals(listOf(3, 2, 1), (0..2).map { (context.value(it, 0) as IntValue).value.toInt() })
    }

    // La agregacion recorre las filas de un grupo sin perder las de afuera.
    @Test
    fun `cambiar la fila de este nivel deja las de afuera`() {
        val context = RowContext.of(row(10)).nest(row(20))
        val moved = context.withTop(row(99))

        assertEquals(IntValue(99), moved.value(0, 0))
        assertEquals(IntValue(10), moved.value(1, 0))
    }
}
