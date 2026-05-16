package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.models.TokenEntry
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

data class ParseStep(
    val stack: List<Int>,
    val symbols: List<Symbol>,
    val remainingInput: List<TokenEntry>,
    val action: Action
)
