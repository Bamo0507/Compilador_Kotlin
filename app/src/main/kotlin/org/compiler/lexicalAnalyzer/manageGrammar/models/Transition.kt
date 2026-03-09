package org.compiler.lexicalAnalyzer.manageGrammar.models

data class TransitionEntry(
    val fromState: Int,
    val input: Char,
    val positionsInNextState: List<Int>,
    val toState: Int,
    val isAccepting: Boolean
)

object TransitionTable {
    private val entries = mutableListOf<TransitionEntry>()

    fun add(entry: TransitionEntry) = entries.add(entry)
    fun getAll(): List<TransitionEntry> = entries
    fun clear() = entries.clear()
}
