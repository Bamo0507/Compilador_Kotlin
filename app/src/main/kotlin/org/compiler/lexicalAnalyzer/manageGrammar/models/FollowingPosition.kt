package org.compiler.lexicalAnalyzer.manageGrammar.models

data class FollowingPositionEntry(
    val entry: Char,
    val transitions: MutableList<Int> = mutableListOf()
)

class FollowingPositionTable {
    private val table = mutableListOf<FollowingPositionEntry>()

    fun add(entry: FollowingPositionEntry) {
        table.add(entry)
    }

    fun getAll(): List<FollowingPositionEntry> = table
}
