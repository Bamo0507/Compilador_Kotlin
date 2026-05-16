package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.models.Token
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.models.LexemeLocation

data class ParseError(
    val message: String,
    val location: LexemeLocation?,
    val foundToken: Token?,
    val expectedTokens: Set<Symbol>
)
