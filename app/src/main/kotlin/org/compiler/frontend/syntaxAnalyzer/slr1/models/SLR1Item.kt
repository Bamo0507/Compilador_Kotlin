package org.compiler.frontend.syntaxAnalyzer.slr1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class SLR1Item(
    val production: Production,
    val dotPosition: Int,
    val lookaheads: Set<Symbol>
) {
    val isComplete: Boolean
        get() = dotPosition == production.body.size

    val symbolAfterDot: Symbol?
        get() = production.body.getOrNull(dotPosition)

    val core: Pair<Production, Int>
        get() = production to dotPosition

    fun advance(): SLR1Item = copy(dotPosition = dotPosition + 1)
}
