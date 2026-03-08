package org.compiler

import org.compiler.lexicalAnalyzer.manageGrammar.utils.YalexReader

fun main() {
    val yalexPath = "src/main/resources/java_lang.yal"
    val mappings = YalexReader.read(yalexPath)

    mappings.forEach { mapping ->
        println("Category: ${mapping.category}")
        println("Pattern: ${mapping.pattern}")
        println()
    }
}
