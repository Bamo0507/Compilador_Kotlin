package org.compiler.lexicalAnalyzer.lexer.models

data class SymbolTableEntry(
    val index: Int,
    val value: String
)

object SymbolTable {
    private val entries = mutableListOf<SymbolTableEntry>()

    // Returns the index of the existing entry, or inserts a new one and returns its index.
    fun addOrGet(value: String): Int {
        val existing = entries.find { it.value == value }
        if (existing != null) return existing.index
        val index = entries.size + 1
        entries.add(SymbolTableEntry(index = index, value = value))
        return index
    }

    fun getAll(): List<SymbolTableEntry> = entries
    fun clear() = entries.clear()
}
