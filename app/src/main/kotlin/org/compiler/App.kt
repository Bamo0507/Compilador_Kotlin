package org.compiler

import org.compiler.lexicalAnalyzer.manageGrammar.buildMinimizedDFA
import org.compiler.lexicalAnalyzer.manageGrammar.buildSyntaxTree
import org.compiler.lexicalAnalyzer.manageGrammar.buildTransitionTable
import org.compiler.lexicalAnalyzer.manageGrammar.computeFollowPos
import org.compiler.lexicalAnalyzer.manageGrammar.infixToPostfix
import org.compiler.lexicalAnalyzer.manageGrammar.minimizeDFA
import org.compiler.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import org.compiler.lexicalAnalyzer.manageGrammar.utils.YalexReader
import org.compiler.lexicalAnalyzer.manageGrammar.utils.normalizeRegex
import org.compiler.lexicalAnalyzer.manageGrammar.utils.visualizeTree
import org.compiler.lexicalAnalyzer.manageGrammar.utils.dfaToYaml
//import org.compiler.lexicalAnalyzer.scanner.loadYamlDfas
//import org.compiler.lexicalAnalyzer.scanner.loadYamlsFromPath
import org.compiler.lexicalAnalyzer.scanner.YamlLoader

fun main() {
    // .yal consumption for building token patterns and categories to work with
    val yalexPath = "src/main/resources/java_lang.yal"
    val mappings = YalexReader.read(yalexPath)

    mappings.forEach { mapping ->
        if (mapping.category == "EOF") return@forEach

        val normalized = normalizeRegex(mapping.pattern)
        val postfix = infixToPostfix(normalized)
        val augmented = "$postfix#."

        val tree = buildSyntaxTree(augmented)
        val followPos = computeFollowPos(tree)

        visualizeTree(tree, mapping.category)
        buildTransitionTable(tree, followPos)

        val partitions = minimizeDFA()
        val minimizedDFA = buildMinimizedDFA(partitions)
        CategoryAutomataIndex.put(mapping.category, minimizedDFA)

        val dfa = dfaToYaml(CategoryAutomataIndex)

        // val path = "src/main/resources/${mapping.category}.yaml"
        // val loadedDFA = loadYamlDfas(path)
        // println("DFA for category '${mapping.category}' has been generated and saved to '$path'")

        // println("DFA for category '${mapping.category}':")
        // loadedDFA.getAll().forEach { (category, dfa) ->
        //     println("Category: $category")
        //     println("Initial State: ${dfa.initialState}")
        //     println("Accepting States: ${dfa.acceptingStates}")
        //     println("Transitions:")
        //     dfa.transitions.forEach { (state, transitions) ->
        //         transitions.forEach { (input, nextState) ->
        //             println("  ($state, '$input') -> $nextState")
        //         }
        //     }
        // }

        val read = YamlLoader("src/main/resources/")  // TODO: Handle multiple YAMLs if needed
        println("DFA loaded from YAML files:")
        read.getAll().forEach { (category, dfa) ->
            println("Category: $category")
            println("Initial State: ${dfa.initialState}")
            println("Accepting States: ${dfa.acceptingStates}")
            println("Transitions:")
            dfa.transitions.forEach { (state, transitions) ->
                transitions.forEach { (input, nextState) ->
                    println("  ($state, '$input') -> $nextState")
                }
            }
        }
        // println("YAML files found in 'src/main/resources/':")
        // paths.forEach { println(it) }

        
    }
}
