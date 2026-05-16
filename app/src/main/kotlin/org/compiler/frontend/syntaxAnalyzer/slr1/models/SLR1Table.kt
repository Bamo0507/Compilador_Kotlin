package org.compiler.frontend.syntaxAnalyzer.slr1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class SLR1Table(
    val action: Map<Pair<Int, Symbol>, Action>,
    val goto: Map<Pair<Int, Symbol>, Int>,
    val numStates: Int,
    val conflicts: List<SLR1Conflict> = emptyList()
) {
    val isSLR1: Boolean
        get() = conflicts.isEmpty()
}

data class SLR1Conflict(
    val state: Int,
    val terminal: Symbol,
    val type: ConflictType,
    val actions: List<Action>
)

enum class ConflictType {
    SHIFT_REDUCE,
    REDUCE_REDUCE
}
