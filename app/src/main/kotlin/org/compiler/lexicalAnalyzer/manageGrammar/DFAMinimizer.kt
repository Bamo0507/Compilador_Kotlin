package org.compiler.lexicalAnalyzer.manageGrammar

import org.compiler.lexicalAnalyzer.manageGrammar.models.MinimizedDFA
import org.compiler.lexicalAnalyzer.manageGrammar.models.PartitionSet
import org.compiler.lexicalAnalyzer.manageGrammar.models.TransitionTable

// Generates the final partition set
fun minimizeDFA(): Set<PartitionSet> {
    val entries = TransitionTable.getAll()

    val allStates = mutableSetOf<Int>()
    val alphabet  = mutableSetOf<Char>()
    val accepting = mutableSetOf<Int>()

    for (entry in entries) {
        allStates.add(entry.fromState)
        allStates.add(entry.toState)
        alphabet.add(entry.input)
        if (entry.isAccepting) accepting.add(entry.toState)
    }

    // Transition function: state - symbol, destination state
    val transitions = mutableMapOf<Int, MutableMap<Char, Int>>()

    for (entry in entries) {
        transitions
            .getOrPut(entry.fromState) { mutableMapOf() }
            .put(entry.input, entry.toState)
    }

    // Initial partition: accepting vs non-accepting
    var partitions = mutableListOf<Set<Int>>().apply {
        val accepted = allStates.filter { it in accepting }.toSet()
        val nonAccepted = allStates.filter { it !in accepting }.toSet()
        if (accepted.isNotEmpty()) add(accepted)
        if (nonAccepted.isNotEmpty()) add(nonAccepted)
    }

    fun stateToGroupMap(parts: List<Set<Int>>): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for ((groupIndex, group) in parts.withIndex()) {
            for (state in group) {
                map[state] = groupIndex
            }
        }
        return map
    }

    // Refine partitions until no new groups are made
    while (true) {
        val mapping = stateToGroupMap(partitions)
        val newPartition = mutableListOf<Set<Int>>()

        for (group in partitions) {
            if (group.size <= 1) {
                newPartition += group
                continue
            }
            val newPartitionGroups = mutableMapOf<List<Pair<Char, Int>>, MutableSet<Int>>()
            for (state in group) {
                val signature = mutableListOf<Pair<Char, Int>>()
                for (symbol in alphabet.sorted()) {
                    val destinationState = transitions[state]?.get(symbol)
                    val destinationGroup = when (destinationState) {
                        null -> -1
                        else -> mapping[destinationState]!!
                    }
                    signature.add(symbol to destinationGroup)
                }
                val groupForSignature = newPartitionGroups.getOrPut(signature) { mutableSetOf() }
                groupForSignature.add(state)
            }
            newPartition += newPartitionGroups.values.map { it.toSet() }
        }

        if (newPartition.size == partitions.size && newPartition.all { it in partitions }) break
        partitions = newPartition.toMutableList()
    }

    return partitions.mapIndexed { idx, group ->
        PartitionSet(
            tag = idx,
            members = group,
            isAcceptedBlock = group.any { it in accepting }
        )
    }.toSet()
}

// Builds a MinimizedDFA from the partition sets produced by minimizeDFA()
fun buildMinimizedDFA(partitions: Set<PartitionSet>): MinimizedDFA {
    val entries  = TransitionTable.getAll()
    val alphabet = mutableSetOf<Char>()

    val originalTransitions = mutableMapOf<Int, MutableMap<Char, Int>>()
    for (entry in entries) {
        alphabet.add(entry.input)
        originalTransitions
            .getOrPut(entry.fromState) { mutableMapOf() }
            .put(entry.input, entry.toState)
    }

    // Map each original state to its partition tag
    val stateToPartitionTag = mutableMapOf<Int, Int>()
    for (partition in partitions) {
        for (state in partition.members) {
            stateToPartitionTag[state] = partition.tag
        }
    }

    // Build minimized transitions using one representative per partition
    val minimizedTransitions = mutableMapOf<Int, MutableMap<Char, Int>>()
    for (partition in partitions) {
        val representative = partition.members.first()
        val transitionsFromPartition = mutableMapOf<Char, Int>()

        for (symbol in alphabet) {
            val destinationState    = originalTransitions[representative]?.get(symbol) ?: continue
            val destinationPartition = stateToPartitionTag[destinationState] ?: continue
            transitionsFromPartition[symbol] = destinationPartition
        }

        if (transitionsFromPartition.isNotEmpty()) {
            minimizedTransitions[partition.tag] = transitionsFromPartition
        }
    }

    val initialPartition = stateToPartitionTag[0] ?: error("No partition contains the initial state 0")
    val acceptingPartitions = mutableSetOf<Int>()
    for (partition in partitions) {
        if (partition.isAcceptedBlock) acceptingPartitions.add(partition.tag)
    }

    return MinimizedDFA(
        initialState = initialPartition,
        transitions = minimizedTransitions,
        acceptingStates = acceptingPartitions
    )
}
