package org.compiler.lexicalAnalyzer.lexer.models

data class SymbolTableEntry(
    val id: String,
    val value: String,
    val scope: String? = null
)

object SymbolTable {
    private val entries = mutableListOf<SymbolTableEntry>()

    fun add(entry: SymbolTableEntry) = entries.add(entry)
    fun getAll(): List<SymbolTableEntry> = entries
    fun clear() = entries.clear()
}
