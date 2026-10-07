// El pool de temporales con conteo de usos.
//
// Cada test simula a mano lo que hace el generador: pedir un temporal por resultado y
// consumir cada operando al leerlo, SIEMPRE consumiendo antes de pedir el siguiente.
package org.compiler

import org.compiler.frontend.intermediate.TemporaryAllocator
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TemporaryAllocatorTest {

    private val variable = Name(
        Symbol(
            name = "a",
            kind = DeclarationKind.VARIABLE,
            type = IntegerType,
            location = LexemeLocation(1, 1),
            scopeName = "global"
        )
    )

    // (a + b) * (c + d): las dos sumas viven a la vez, asi que hacen falta dos
    // temporales. El producto reutiliza el mas bajo.
    @Test
    fun `dos sumas vivas a la vez usan dos temporales y el resultado reutiliza t1`() {
        val pool = TemporaryAllocator()

        val sum1 = pool.newTemp(uses = 1)          // t1 = a + b
        val sum2 = pool.newTemp(uses = 1)          // t2 = c + d
        pool.consume(sum1)
        pool.consume(sum2)
        val product = pool.newTemp(uses = 1)       // t1 = t1 * t2

        assertEquals(listOf(Temporary(1), Temporary(2), Temporary(1)), listOf(sum1, sum2, product))
        assertEquals(2, pool.temporaryCount)
    }

    // a + b * c - d: cada resultado muere al ser leido por el siguiente, asi que un
    // solo nombre alcanza, el t1 = t1 * t2 clasico.
    @Test
    fun `una cadena que consume su resultado usa un solo temporal`() {
        val pool = TemporaryAllocator()

        val product = pool.newTemp(uses = 1)       // t1 = b * c
        pool.consume(product)
        val sum = pool.newTemp(uses = 1)           // t1 = a + t1
        pool.consume(sum)
        val difference = pool.newTemp(uses = 1)    // t1 = t1 - d
        pool.consume(difference)                   // x = t1

        assertEquals(setOf(Temporary(1)), setOf(product, sum, difference))
        assertEquals(1, pool.temporaryCount)
        assertFalse(pool.hasLiveTemporaries)
    }

    // Un nodo compartido del GDA tiene dos lectores: consumido una vez, sigue vivo.
    @Test
    fun `un temporal con dos usos consumido una vez sigue vivo`() {
        val pool = TemporaryAllocator()
        val shared = pool.newTemp(uses = 2)

        pool.consume(shared)

        assertTrue(pool.hasLiveTemporaries)
        assertNotEquals(shared, pool.newTemp(uses = 1))
    }

    @Test
    fun `consumido tantas veces como usos tiene, queda libre`() {
        val pool = TemporaryAllocator()
        val shared = pool.newTemp(uses = 2)

        pool.consume(shared)
        pool.consume(shared)

        assertFalse(pool.hasLiveTemporaries)
        assertEquals(shared, pool.newTemp(uses = 1))
    }

    // Con el GDA los temporales dejan de morir en orden de pila: t1 puede liberarse
    // antes que t2. El pool entrega igual el mas bajo libre.
    @Test
    fun `liberar t1 antes que t2 hace que el siguiente sea t1`() {
        val pool = TemporaryAllocator()
        val first = pool.newTemp(uses = 1)
        pool.newTemp(uses = 1)

        pool.consume(first)

        assertEquals(Temporary(1), pool.newTemp(uses = 1))
    }

    // Solo los temporales tienen dueño: leer una variable o una constante no libera nada.
    @Test
    fun `consumir una variable o una constante no hace nada`() {
        val pool = TemporaryAllocator()
        val live = pool.newTemp(uses = 1)

        pool.consume(variable)
        pool.consume(Constant(5L))

        assertTrue(pool.hasLiveTemporaries)
        assertNotEquals(live, pool.newTemp(uses = 1))
    }

    // EL BUG DEL CONTADOR mal ordenado: un resultado queda vivo mientras se calculan dos
    // temporales mas. Un contador que solo baja al liberar podria volver a entregar
    // t1 y pisarlo. El pool sabe que t1 sigue vivo.
    @Test
    fun `un temporal vivo nunca se vuelve a entregar`() {
        val pool = TemporaryAllocator()
        val kept = pool.newTemp(uses = 1)          // t1, se va a leer al final

        val a = pool.newTemp(uses = 1)
        val b = pool.newTemp(uses = 1)
        pool.consume(a)
        pool.consume(b)
        val c = pool.newTemp(uses = 1)

        assertTrue(kept !in listOf(a, b, c), "Se entrego t1 estando vivo: $a, $b, $c")
        assertEquals(Temporary(2), c)
    }

    // Un temporal que nadie va a leer es un error del generador: nunca se liberaria.
    @Test
    fun `pedir un temporal sin usos es un error`() {
        assertFailsWith<IllegalArgumentException> { TemporaryAllocator().newTemp(uses = 0) }
    }
}
