package org.compiler.frontend.syntaxAnalyzer.slr1

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.slr1.models.ConflictType
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Conflict
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table

object SLR1TableBuilder {

    fun build(automata: SLR1Automata): SLR1Table {
        val actionCandidates = mutableMapOf<Pair<Int, Symbol>, MutableList<Action>>()
        val goto = mutableMapOf<Pair<Int, Symbol>, Int>()
        val augmentedStart = automata.augmentedGrammar.startSymbol

        for (state in automata.states) {
            for (item in state.items) {
                val symbol = item.symbolAfterDot
                val isLogicallyComplete = item.isComplete || symbol is Symbol.Epsilon

                when {
                    isLogicallyComplete && item.production.head == augmentedStart -> {
                        for (lookahead in item.lookaheads) {
                            actionCandidates
                                .getOrPut(state.id to lookahead) { mutableListOf() }
                                .add(Action.Accept)
                        }
                    }
                    isLogicallyComplete -> {
                        for (lookahead in item.lookaheads) {
                            actionCandidates
                                .getOrPut(state.id to lookahead) { mutableListOf() }
                                .add(Action.Reduce(item.production))
                        }
                    }
                    symbol is Symbol.Terminal -> {
                        val nextState = automata.transitions[state.id to symbol]!!
                        actionCandidates
                            .getOrPut(state.id to symbol) { mutableListOf() }
                            .add(Action.Shift(nextState))
                    }
                    symbol is Symbol.NonTerminal -> {
                        val nextState = automata.transitions[state.id to symbol]!!
                        goto[state.id to symbol] = nextState
                    }
                }
            }
        }

        // Fase 2 -- resolver candidatos en una accion final por celda.
        val action = mutableMapOf<Pair<Int, Symbol>, Action>()
        val conflicts = mutableListOf<SLR1Conflict>()
        for ((cell, candidates) in actionCandidates) {
            val distinct = candidates.distinct()
            if (distinct.size == 1) {
                action[cell] = distinct.first()
            } else {
                action[cell] = resolveConflict(distinct)
                conflicts.add(SLR1Conflict(
                    state = cell.first,
                    terminal = cell.second,
                    type = classifyConflict(distinct),
                    actions = distinct
                ))
            }
        }

        return SLR1Table(
            action = action,
            goto = goto,
            numStates = automata.states.size,
            conflicts = conflicts
        )
    }

    private fun classifyConflict(actions: List<Action>): ConflictType =
        if (actions.any { it is Action.Shift }) ConflictType.SHIFT_REDUCE
        else ConflictType.REDUCE_REDUCE

    // Politica de resolucion de conflictos (convencion del Dragon Book; NO derivable del codigo):
    //   - Shift-reduce: gana Shift. Resuelve el dangling-else al estilo C/Java/Kotlin -- el
    //     ELSE se asocia al IF mas cercano leyendo en lugar de cerrar la produccion corta.
    //   - Reduce-reduce: gana la produccion con menor id, que corresponde a la que aparecio
    //     primero en el .yalp. Da control al usuario via el orden de declaracion.
    // Cualquier conflict resuelto aca queda registrado en SLR1Table.conflicts para diagnostico,
    // pero la tabla sigue siendo usable por el driver con la accion ganadora.
    private fun resolveConflict(actions: List<Action>): Action {
        val shift = actions.firstOrNull { it is Action.Shift }
        if (shift != null) return shift

        val reduces = actions.filterIsInstance<Action.Reduce>()
        if (reduces.isNotEmpty()) return reduces.minBy { it.production.id }

        return actions.first()
    }
}
