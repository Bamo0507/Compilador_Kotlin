package org.compiler.backend

import org.compiler.frontend.intermediate.models.Address

/**
 * Lo que se sabe de una variable justo despues de una instruccion: si su valor actual se
 * va a necesitar (live), y en que instruccion del bloque se vuelve a leer (nextUse),
 * o null si no se lee otra vez dentro del bloque.
 */
data class Usage(val live: Boolean, val nextUse: Int?)

/**
 * La informacion de proximo uso de un bloque (Dragon Book 8.4.2). Se calcula recorriendo
 * el bloque de abajo hacia arriba. Para `x = y op z`:
 *   1. se anota en la instruccion lo que se sabe en ese momento de x, y y z
 *   2. x pasa a "no vivo, sin proximo uso": su valor de antes ya no le sirve a nadie
 *   3. y y z pasan a "vivos, proximo uso aqui"
 * El orden importa cuando x es tambien y o z, como en `t1 = t1 * t2`.
 *
 * liveAtExit dice que variables siguen vivas al salir del bloque; lo resuelve
 * BlockLiveness sobre el grafo de flujo de la funcion.
 */
class NextUseAnalysis(
    block: BasicBlock,
    private val liveAtExit: (Address) -> Boolean
) {
    // Lo que se sabe despues de cada instruccion, solo de las variables que el bloque
    // toca de ahi en adelante. Las demas estan como al salir del bloque.
    private val usageAfter: List<Map<Address, Usage>>

    init {
        val current = mutableMapOf<Address, Usage>()
        val snapshots = arrayOfNulls<Map<Address, Usage>>(block.instructions.size)
        for (index in block.instructions.indices.reversed()) {
            val quadruple = block.instructions[index]
            snapshots[index] = current.toMap()
            definitionOf(quadruple)?.takeIf { isVariable(it) }?.let {
                current[it] = Usage(live = false, nextUse = null)
            }
            usesOf(quadruple).filter { isVariable(it) }.forEach {
                current[it] = Usage(live = true, nextUse = index)
            }
        }
        usageAfter = snapshots.map { checkNotNull(it) }
    }

    fun usageAfter(index: Int, variable: Address): Usage =
        usageAfter[index][variable] ?: Usage(live = liveAtExit(variable), nextUse = null)
}
