package org.compiler.backend

import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin

/**
 * Que temporales siguen vivos al salir de cada bloque basico de una funcion.
 *
 * El libro supone que un temporal nunca cruza bloques, pero en este TAC si: el resultado
 * de un ternario se escribe en dos ramas y se lee despues de la etiqueta, y el de una
 * llamada se escribe en un bloque y se lee en el siguiente. Se resuelve con la ecuacion
 * de vivacidad sobre el grafo de bloques:
 *   salida(B) = union de entrada(S) para cada sucesor S
 *   entrada(B) = leidos(B) + (salida(B) - escritos(B))
 * repetida hasta que nada cambie. Las variables del programa siempre se consideran vivas.
 */
class BlockLiveness(private val blocks: List<BasicBlock>) {

    // Por numero de bloque: los temporales que algun camino de salida vuelve a leer.
    private val liveOut: List<Set<Address>> = solve()

    /** Si una direccion sigue viva al salir del bloque numero `index`. */
    fun liveAtExitOf(index: Int): (Address) -> Boolean {
        val live = liveOut[index]
        return { address -> address is Name || address in live }
    }

    private fun solve(): List<Set<Address>> {
        val read = blocks.map { readBeforeWrittenIn(it) }
        val written = blocks.map { block ->
            block.instructions.mapNotNull { definitionOf(it) }.filterIsInstance<Temporary>().toSet()
        }
        val successors = blocks.indices.map { successorsOf(it) }

        val atExit = MutableList(blocks.size) { emptySet<Address>() }
        val atEntry = MutableList(blocks.size) { emptySet<Address>() }

        // De atras hacia adelante, que es el orden en que la informacion se propaga: asi
        // una pasada sola suele bastar para todo lo que no sea un ciclo.
        var changed = true
        while (changed) {
            changed = false
            for (index in blocks.indices.reversed()) {
                val exit = successors[index].flatMapTo(mutableSetOf()) { atEntry[it] }
                val entry = read[index] + (exit - written[index])
                if (exit != atExit[index] || entry != atEntry[index]) {
                    atExit[index] = exit
                    atEntry[index] = entry
                    changed = true
                }
            }
        }
        return atExit
    }

    // Los temporales que el bloque lee antes de escribirlos. Esos los necesita vivos al
    // entrar, pase lo que pase despues; uno que el bloque escribe primero, no.
    private fun readBeforeWrittenIn(block: BasicBlock): Set<Address> {
        val read = mutableSetOf<Address>()
        val written = mutableSetOf<Address>()
        block.instructions.forEach { quadruple ->
            usesOf(quadruple).filterIsInstance<Temporary>().forEach { if (it !in written) read += it }
            (definitionOf(quadruple) as? Temporary)?.let { written += it }
        }
        return read
    }

    private fun successorsOf(index: Int): List<Int> {
        val fallthrough = (index + 1).takeIf { it in blocks.indices }
        return when (val last = blocks[index].instructions.last()) {
            is Goto -> listOfNotNull(blockAt(last.label))
            is IfGoto -> listOfNotNull(blockAt(last.label), fallthrough)
            is IfFalseGoto -> listOfNotNull(blockAt(last.label), fallthrough)
            is IfRelationalGoto -> listOfNotNull(blockAt(last.label), fallthrough)
            is TryBegin -> listOfNotNull(blockAt(last.handler), fallthrough)
            is Return, is Throw -> emptyList()
            else -> listOfNotNull(fallthrough)
        }
    }

    // El bloque que abre esa etiqueta. Siempre abre uno, porque toda etiqueta es lider.
    private fun blockAt(label: Label): Int? =
        blocks.indexOfFirst { (it.instructions.first() as? LabelDefinition)?.label == label }
            .takeIf { it >= 0 }
}
