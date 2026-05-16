package org.compiler.frontend.lexicalAnalyzer.scanner.models

import org.compiler.frontend.models.TokenEntry
import java.util.LinkedList

object TokenEntrys {
    val entries: LinkedList<TokenEntry> = LinkedList()

    fun addEntry(entry: TokenEntry) = entries.add(entry)
    fun clear() = entries.clear()
}
