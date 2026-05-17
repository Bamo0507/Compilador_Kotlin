package org.compiler.frontend.syntaxAnalyzer.ll1.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production

data class LL1Conflict(
    val nonTerminal: Symbol.NonTerminal,
    val terminal: Symbol,
    val productions: List<Production>
)