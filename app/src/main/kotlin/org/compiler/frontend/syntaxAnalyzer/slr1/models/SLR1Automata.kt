package org.compiler.frontend.syntaxAnalyzer.slr1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class SLR1Automata(
    val states: List<SLR1State>,
    val transitions: Map<Pair<Int, Symbol>, Int>,
    val initialState: SLR1State,
    val augmentedGrammar: Grammar
)
