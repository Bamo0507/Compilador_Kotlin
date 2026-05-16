package org.compiler.frontend.syntaxAnalyzer.runtime

import org.compiler.frontend.models.Token

class TokenStream(
    allTokens: List<Token>,
    ignored: Set<String>
) {
    private val tokens: List<Token> = allTokens.filterNot { it.category in ignored }
    private var index = 0

    fun peek(): Token? = tokens.getOrNull(index)

    fun consume(): Token? {
        val current = tokens.getOrNull(index)
        if (current != null) index++
        return current
    }

    fun hasNext(): Boolean = index < tokens.size

    fun position(): Int = index

    fun remaining(): List<Token> = tokens.subList(index, tokens.size)
}
