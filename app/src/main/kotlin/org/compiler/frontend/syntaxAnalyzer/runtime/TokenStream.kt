package org.compiler.frontend.syntaxAnalyzer.runtime

import org.compiler.frontend.models.TokenEntry

class TokenStream(
    allEntries: List<TokenEntry>,
    ignored: Set<String>
) {
    private val entries: List<TokenEntry> = allEntries.filterNot { it.token.category in ignored }
    private var index = 0

    fun peek(): TokenEntry? = entries.getOrNull(index)

    fun consume(): TokenEntry? {
        val current = entries.getOrNull(index)
        if (current != null) index++
        return current
    }

    fun hasNext(): Boolean = index < entries.size

    fun position(): Int = index

    fun remaining(): List<TokenEntry> = entries.subList(index, entries.size)
}
