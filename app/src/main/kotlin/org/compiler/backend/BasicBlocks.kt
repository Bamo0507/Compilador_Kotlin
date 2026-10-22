package org.compiler.backend

import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.IndirectCall
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd

/**
 * Un bloque basico: un tramo de instrucciones en el que se entra solo por la primera y
 * se sale solo por la ultima. Adentro, despues de una instruccion siempre viene la
 * siguiente, y por eso ahi se puede confiar en lo que dicen los descriptores.
 *
 * start: la posicion de la primera instruccion dentro del cuerpo de la funcion.
 */
data class BasicBlock(
    val instructions: List<Quadruple>,
    val start: Int
)

/**
 * Parte el cuerpo de una funcion en bloques basicos (Dragon Book 8.4.1). Un bloque
 * nuevo empieza en cada lider:
 *   - la primera instruccion
 *   - cada etiqueta, porque alguien puede saltar ahi desde otro lado
 *   - la instruccion que sigue a un salto, a una llamada, a un return, a un throw o a
 *     un try: despues de ellas el flujo puede no llegar en orden, o los registros dejan
 *     de ser confiables
 */
fun basicBlocksOf(body: List<Quadruple>): List<BasicBlock> {
    val blocks = mutableListOf<BasicBlock>()
    var current = mutableListOf<Quadruple>()
    var start = 0

    fun closeBlock(nextStart: Int) {
        if (current.isNotEmpty()) blocks += BasicBlock(current, start)
        current = mutableListOf()
        start = nextStart
    }

    body.forEachIndexed { index, quadruple ->
        if (quadruple is LabelDefinition) closeBlock(index)
        current += quadruple
        if (endsBlock(quadruple)) closeBlock(index + 1)
    }
    closeBlock(body.size)
    return blocks
}

// Las instrucciones despues de las cuales empieza un bloque nuevo.
fun endsBlock(quadruple: Quadruple): Boolean = when (quadruple) {
    is Goto, is IfGoto, is IfFalseGoto, is IfRelationalGoto, is Call, is IndirectCall,
    is Return, is Throw, is TryBegin, TryEnd -> true
    else -> false
}
