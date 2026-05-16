package org.compiler.frontend.syntaxAnalyzer.lalr1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.slr1.models.ConflictType

data class LALR1Table(
    val action: Map<Pair<Int, Symbol>, Action>,
    val goto: Map<Pair<Int, Symbol>, Int>,
    val numStates: Int,
    val conflicts: List<LALR1Conflict> = emptyList()
) {
    val isLALR1: Boolean
        get() = conflicts.isEmpty()
}

data class LALR1Conflict(
    val state: Int,
    val terminal: Symbol,
    val type: ConflictType,
    val actions: List<Action>
)
