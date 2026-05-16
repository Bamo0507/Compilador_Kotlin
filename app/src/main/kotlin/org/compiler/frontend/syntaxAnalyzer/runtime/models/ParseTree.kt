package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.models.Token
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

sealed interface ParseTree {
    data class LeafNode(
        val symbol: Symbol.Terminal,
        val token: Token
    ) : ParseTree

    data class InternalNode(
        val symbol: Symbol.NonTerminal,
        val production: Production,
        val children: List<ParseTree>
    ) : ParseTree

    data object EpsilonNode : ParseTree
}
