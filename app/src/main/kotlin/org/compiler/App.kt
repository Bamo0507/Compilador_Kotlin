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

    

        
    }
}
