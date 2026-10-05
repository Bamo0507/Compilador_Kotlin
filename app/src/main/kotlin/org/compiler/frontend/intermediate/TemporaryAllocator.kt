package org.compiler.frontend.intermediate

import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Temporary

/**
 * Entrega temporales y los recicla cuando su ultimo lector los consume.
 *
 * Un contador alcanza mientras los temporales mueren en orden de pila, que es lo que
 * pasa en un arbol. En un GDA un nodo compartido tiene varios lectores y puede morir
 * despues de temporales mas nuevos, asi que hace falta saber cuantos lectores le
 * quedan a cada uno.
 *
 * En un arbol, donde todo temporal tiene `uses = 1`, el pool entrega exactamente los
 * mismos nombres que el contador clasico: el contador es el caso particular.
 */
class TemporaryAllocator {

    // Los indices libres, de menor a mayor: se reutiliza siempre el mas bajo. Eso hace
    // el resultado determinista, que es lo que permite fijar el TAC exacto en un test.
    private val free = sortedSetOf<Int>()

    private var nextIndex = 1

    // Cuantas lecturas le faltan a cada temporal vivo. Al llegar a cero, se libera.
    private val remainingUses = mutableMapOf<Int, Int>()

    // `uses` es cuantas veces se va a leer. En un arbol siempre es 1; en el GDA, la
    // cantidad de aristas que llegan al nodo.
    fun newTemp(uses: Int): Temporary {
        require(uses > 0) { "Un temporal que nadie va a leer no deberia pedirse" }

        val index = free.pollFirst() ?: nextIndex++
        remainingUses[index] = uses
        return Temporary(index)
    }

    // Se llama cada vez que una instruccion lee el operando, ANTES de pedir el
    // temporal de su resultado: asi el resultado puede reutilizar el nombre.
    //
    // Una variable o una constante no se recicla: solo los temporales tienen dueño.
    fun consume(address: Address) {
        if (address !is Temporary) return

        val left = remainingUses.getValue(address.index) - 1
        if (left == 0) {
            remainingUses.remove(address.index)
            free.add(address.index)
        } else {
            remainingUses[address.index] = left
        }
    }

    // Cuantos temporales distintos hicieron falta: la medida que el enunciado pide
    // minimizar.
    val temporaryCount: Int get() = nextIndex - 1

    // Al terminar una sentencia no debe quedar ninguno vivo.
    val hasLiveTemporaries: Boolean get() = remainingUses.isNotEmpty()
}
