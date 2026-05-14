package org.compiler.symbolTable

import org.compiler.models.LexemeLocation

data class SymbolTableEntry(
    val index: Int,
    val name: String,
    val location: LexemeLocation
)
