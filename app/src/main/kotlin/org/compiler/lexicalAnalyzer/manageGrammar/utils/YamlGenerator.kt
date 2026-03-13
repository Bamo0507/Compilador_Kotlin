package org.compiler.lexicalAnalyzer.manageGrammar.utils

import org.compiler.lexicalAnalyzer.manageGrammar.models.MinimizedDFA
import org.compiler.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import java.io.File

fun dfaReader(CategoryAutomataIndex: CategoryAutomataIndex) {
    for(categoryAutomata in CategoryAutomataIndex.getAll()) {
        val categoy = categoryAutomata.key // KEYBWORD, INT, etc etc etc
        val acceptingStates = categoryAutomata.value.acceptingStates
        val initialState = categoryAutomata.value.initialState

        val transitions = categoryAutomata.value.transitions
        for(transition in transitions){
            val estadoOrigen = transition.key
            val transicionesDesdEstado = transition.value  // Map<Char, Int>

            for (entry in transicionesDesdEstado) {
                val caracterEntrada = entry.key
                val estadoDestino   = entry.value
                println("($estadoOrigen, ${caracterEntrada}) -> $estadoDestino")
            }
        }
    }
}

fun singleDfaReader(categoryAutomata: Map.Entry<String, MinimizedDFA>): List<Triple<Int, Char, Int>> {
    val transitions_list = mutableListOf<Triple<Int, Char, Int>>()
    val categoy = categoryAutomata.key // KEYBWORD, INT, etc etc etc
    val acceptingStates = categoryAutomata.value.acceptingStates
    val initialState = categoryAutomata.value.initialState

    val transitions = categoryAutomata.value.transitions
    for(transition in transitions){
        val estadoOrigen = transition.key
        val transicionesDesdEstado = transition.value  // Map<Char, Int>

        for (entry in transicionesDesdEstado) {
            val caracterEntrada = entry.key
            val estadoDestino   = entry.value
            transitions_list.add(Triple(estadoOrigen, caracterEntrada, estadoDestino))
        }
    }

    return transitions_list
}

private fun yamlQuoted(value: String): String = "'${value.replace("'", "''")}'"

private fun buildDfaYaml(
    qList: List<String>,
    initialState: String,
    finalState: String,
    alphabet: List<Char>,
    delta: List<Map<String, Any>>
): String {
    val builder = StringBuilder()

    builder.appendLine("---")
    builder.appendLine()
    builder.appendLine("q_states:")
    builder.appendLine("  q_list:")
    for (state in qList) {
        builder.appendLine("    - ${yamlQuoted(state)}")
    }
    builder.appendLine("  initial: ${yamlQuoted(initialState)}")
    builder.appendLine("  final: ${yamlQuoted(finalState)}")
    builder.appendLine()

    builder.appendLine("alphabet:")
    for (char in alphabet) {
        builder.appendLine("  - ${yamlQuoted(char.toString())}")
    }
    builder.appendLine()

    builder.appendLine("delta:")
    for (transition in delta) {
        val params = transition["params"] as Map<*, *>
        val output = transition["output"] as Map<*, *>
        val initial = params["initial_state"].toString()
        val input = params["input"].toString()
        val final = output["final_state"].toString()

        builder.appendLine(
            "  - params: { initial_state: ${yamlQuoted(initial)}, input: ${yamlQuoted(input)} }"
        )
        builder.appendLine(
            "    output: { final_state: ${yamlQuoted(final)} }"
        )
    }

    return builder.toString()
}



// q_states:
//   q_list:
//     - '0' 
//     - '1' 
//     - '2'
//     - '3'
//   initial: '0'
//   final: '3'

// alphabet:
//   - 'a'
//   - 'b'
//   - 'c'

// delta:
//   - params: { initial_state: '0', input: 'a' }
//     output: { final_state: '0'}
//   - params: { initial_state: '0', input: 'b' }
//     output: { final_state: '0' }
//   - params: { initial_state: '0', input: 'c' }
//     output: { final_state: '0' }


fun dfaToYaml(CategoryAutomataIndex: CategoryAutomataIndex){
    val outputDir = File("src/main/resources")
    outputDir.mkdirs()

    for(categoryAutomata in CategoryAutomataIndex.getAll()) {
        val category = categoryAutomata.key
        val acceptingStates = categoryAutomata.value.acceptingStates
        val initialState = categoryAutomata.value.initialState
        val transitions = singleDfaReader(categoryAutomata)

        val alphabet = mutableSetOf<Char>()
        val q_list = mutableListOf<String>()
        val delta = mutableListOf<Map<String, Any>>()

        for (transition in transitions) {
            val params = mapOf("initial_state" to transition.first.toString(), "input" to transition.second.toString())
            val output = mapOf("final_state" to transition.third.toString())
            val (estadoOrigen, caracterEntrada, estadoDestino) = transition
            alphabet.add(caracterEntrada)
            if (!q_list.contains(estadoOrigen.toString())) q_list.add(estadoOrigen.toString())
            if (!q_list.contains(estadoDestino.toString())) q_list.add(estadoDestino.toString())
            delta.add(mapOf("params" to params, "output" to output))
        }

        val yamlString = buildDfaYaml(
            qList = q_list,
            initialState = initialState.toString(),
            finalState = acceptingStates.first().toString(),
            alphabet = alphabet.toList(),
            delta = delta
        )

        val outputFile = File(outputDir, "${category}.yaml")
        outputFile.writeText(yamlString)

        
        // println("---")
        // println("q_states:")
        // println("  q_list:")
        // for (state in q_list) {
        //     println("    - '$state'")
        // }
        // println("  initial: '$initialState'")
        // println("  final: '${acceptingStates.first()}'")
        // println("alphabet:")
        // for (char in alphabet) {
        //     println("  - '$char'")
        // }
        // println("delta:")
        // for (transition in delta) {
        //     println("  - params: ${transition["params"]}")
        //     println("    output: ${transition["output"]}")
        // }

    }

}
