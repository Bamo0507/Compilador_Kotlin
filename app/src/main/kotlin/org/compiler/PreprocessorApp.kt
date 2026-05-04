package org.compiler

import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildMinimizedDFA
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildSyntaxTree
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildTransitionTable
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.computeFollowPos
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.infixToPostfix
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.minimizeDFA
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.YalexReader
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.normalizeRegex
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.visualizeTree
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.dfaToYaml

// Program 1 — Preprocessor
// Reads the .yal spec, builds one minimized DFA per category, and writes each to a YAML file.
// Run this first to generate the YAML files consumed by Program 2 (LexerApp).
fun main() {
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
    }

    dfaToYaml(CategoryAutomataIndex)
    println("Automata written to src/main/resources/")
}
