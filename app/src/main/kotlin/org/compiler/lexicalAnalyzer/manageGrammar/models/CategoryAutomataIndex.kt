package org.compiler.lexicalAnalyzer.manageGrammar.models

data class MinimizedDFA(
    val initialState: Int,
    val transitions: Map<Int, Map<Char, Int>>,
    val acceptingStates: Set<Int>
)

object CategoryAutomataIndex {
    private val index = mutableMapOf<String, MinimizedDFA>()

    fun put(category: String, dfa: MinimizedDFA) {
        index[category] = dfa
    }

    fun get(category: String): MinimizedDFA? = index[category]

    fun getAll(): Map<String, MinimizedDFA> = index
}
