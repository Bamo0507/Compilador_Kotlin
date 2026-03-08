package org.compiler

import org.compiler.lexicalAnalyzer.manageGrammar.infixToPostfix
import org.compiler.lexicalAnalyzer.manageGrammar.utils.YalexReader
import org.compiler.lexicalAnalyzer.manageGrammar.utils.normalizeRegex

fun main() {
    val yalexPath = "src/main/resources/java_lang.yal"
    val mappings = YalexReader.read(yalexPath)

    mappings.forEach { mapping ->
        val normalized = normalizeRegex(mapping.pattern)
        println("Category: ${mapping.category}")
        println("Infix: $normalized")
        println("Postfix: ${infixToPostfix(normalized)}")
        println()
    }
}
