package org.compiler.symbolTable

import org.compiler.models.LexemeLocation

object SymbolTable {
    private val entries = mutableListOf<SymbolTableEntry>()

    // Returns the index of the existing entry for this name, or inserts a new entry
    // with the given location and returns its index. Location is stored only on first occurrence.
    fun addOrGet(name: String, location: LexemeLocation): Int {
        val existing = entries.find { it.name == name }
        if (existing != null) return existing.index
        val index = entries.size + 1
        entries.add(SymbolTableEntry(index = index, name = name, location = location))
        return index
    }

    fun getEntry(index: Int): SymbolTableEntry? = entries.find { it.index == index }
    fun getAll(): List<SymbolTableEntry> = entries.toList()
    fun clear() = entries.clear()
}
