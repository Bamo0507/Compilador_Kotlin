package org.compiler.lexicalAnalyzer.manageGrammar

import org.compiler.lexicalAnalyzer.manageGrammar.models.*

fun buildTransitionTable(
    root: TreeNode,
    followPosTable: FollowingPositionTable
): Int {
    TransitionTable.clear()

    val entries = followPosTable.getAll()

    val posToSymbol = mutableMapOf<Int, Char>()
    val posToFollowPos = mutableMapOf<Int, List<Int>>()

    for ((entryIndex, followEntry) in entries.withIndex()) {
        val position = entryIndex + 1
        posToSymbol[position] = followEntry.entry
        posToFollowPos[position] = followEntry.transitions
    }

    var endMarkerPos = -1
    val alphabet = mutableSetOf<Char>()

    for ((position, symbol) in posToSymbol) {
        if (symbol == '#') endMarkerPos = position
        else alphabet.add(symbol)
    }

    // Map each unique position set to a state number
    val stateMap = mutableMapOf<Set<Int>, Int>()
    var stateCounter = 0

    val initialState = root.firstPos.toSet()
    stateMap[initialState] = stateCounter++

    val notTraveled = ArrayDeque<Set<Int>>()
    notTraveled.add(initialState)

    while (notTraveled.isNotEmpty()) {
        val current = notTraveled.removeFirst()
        val currentStateNum = stateMap[current]!!

        for (symbol in alphabet) {
            val next = current
                .filter { posToSymbol[it] == symbol }
                .flatMap { posToFollowPos[it] ?: emptyList() }
                .toSet()

            if (next.isEmpty()) continue

            if (next !in stateMap) {
                stateMap[next] = stateCounter++
                notTraveled.add(next)
            }

            TransitionTable.add(TransitionEntry(
                fromState = currentStateNum,
                input = symbol,
                positionsInNextState = next.sorted(),
                toState = stateMap[next]!!,
                isAccepting = endMarkerPos in next
            ))
        }
    }

    return stateMap[initialState]!!
}
