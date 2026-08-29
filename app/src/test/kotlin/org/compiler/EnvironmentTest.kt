package org.compiler

import org.compiler.interpreter.ArrayValue
import org.compiler.interpreter.BoolValue
import org.compiler.interpreter.Environment
import org.compiler.interpreter.IntValue
import org.compiler.interpreter.NullValue
import org.compiler.interpreter.ObjectValue
import org.compiler.interpreter.StringValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentTest {

    // ── display: la unica forma en que el usuario ve los valores ───────────

    @Test
    fun `los valores se muestran como los escribe el usuario`() {
        assertEquals("42", IntValue(42).display())
        assertEquals("true", BoolValue(true).display())
        assertEquals("false", BoolValue(false).display())
        assertEquals("null", NullValue.display())
        assertEquals("hola", StringValue("hola").display())
        assertEquals(
            "[1, 2, 3]",
            ArrayValue(mutableListOf(IntValue(1), IntValue(2), IntValue(3))).display()
        )
    }

    // ── Identidad vs valor ─────────────────────────────────────────────────

    // Test de que ObjectValue y ArrayValue NO son data class: dos objetos con el
    // mismo contenido son objetos DISTINTOS.
    @Test
    fun `dos objetos con los mismos campos no son iguales`() {
        val a = ObjectValue("Perro", mutableMapOf("nombre" to StringValue("Toby")))
        val b = ObjectValue("Perro", mutableMapOf("nombre" to StringValue("Toby")))

        assertNotEquals<Any>(a, b)
        assertEquals<Any>(a, a)     // consigo mismo si
    }

    @Test
    fun `dos arreglos con el mismo contenido no son iguales`() {
        val a = ArrayValue(mutableListOf(IntValue(1), IntValue(2)))
        val b = ArrayValue(mutableListOf(IntValue(1), IntValue(2)))

        assertNotEquals<Any>(a, b)
    }

    // Los primitivos SI se comparan por valor: son data class.
    @Test
    fun `los primitivos se comparan por valor`() {
        assertEquals(IntValue(42), IntValue(42))
        assertEquals(StringValue("a"), StringValue("a"))
        assertNotEquals(IntValue(1), IntValue(2))
    }

    // ── El arbol de entornos ───────────────────────────────────────────────

    @Test
    fun `un hijo ve las variables del padre`() {
        val padre = Environment()
        padre.define("x", IntValue(1))

        val hijo = padre.child()

        assertEquals(IntValue(1), hijo.get("x"))
    }

    // Es lo que hace que `let x = 1; { x = 2; } print(x);` imprima 2.
    @Test
    fun `assign en un hijo modifica la variable del padre`() {
        val padre = Environment()
        padre.define("x", IntValue(1))
        val hijo = padre.child()

        assertTrue(hijo.assign("x", IntValue(2)))

        assertEquals(IntValue(2), padre.get("x"))
    }

    // Y esto es lo que hace que `let x = 1; { let x = 2; } print(x);` imprima 1.
    @Test
    fun `define en un hijo tapa la del padre sin modificarla`() {
        val padre = Environment()
        padre.define("x", IntValue(1))
        val hijo = padre.child()

        hijo.define("x", IntValue(2))

        assertEquals(IntValue(2), hijo.get("x"))    // el hijo ve la suya
        assertEquals(IntValue(1), padre.get("x"))   // el padre conserva la original
    }

    @Test
    fun `assign a un nombre inexistente devuelve false`() {
        val padre = Environment()
        val hijo = padre.child()

        assertFalse(hijo.assign("fantasma", IntValue(1)))
        assertNull(padre.get("fantasma"))
    }
}
