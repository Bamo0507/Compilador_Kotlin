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

    // The augmented regex always appends "#." at the end, so the '#' with the
    // highest position is always the end marker. Any '#' at a lower position
    // came from a user pattern (like inside a comment) and is a regular char.
    val endMarkerPos = posToSymbol.entries
        .filter { it.value == '#' }
        .maxOfOrNull { it.key } ?: -1

    val alphabet = mutableSetOf<Char>()
    for ((position, symbol) in posToSymbol) {
        if (position == endMarkerPos) continue
        alphabet.add(symbol)
    }

    // Map each unique position set to a state number
    val stateMap = mutableMapOf<Set<Int>, Int>()
    var stateCounter = 0

    // The initial state is the one that contains all the first positions
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
