package org.compiler.frontend.syntaxAnalyzer.slr1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class SLR1State(
    val id: Int,
    val items: Set<SLR1Item>
) {
    val core: Set<Pair<Production, Int>>
        get() = items.map { it.core }.toSet()

    val symbolsAfterDot: Set<Symbol>
        get() = items.mapNotNull { it.symbolAfterDot }
            .filter { it !is Symbol.Epsilon }
            .toSet()
}
