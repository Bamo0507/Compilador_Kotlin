package org.compiler

import org.compiler.lexicalAnalyzer.scanner.YamlLoader
import org.compiler.lexicalAnalyzer.scanner.scan
import org.compiler.lexicalAnalyzer.scanner.models.TokenEntrys
import org.compiler.lexicalAnalyzer.lexer.models.SymbolTable
import java.io.File

// Program 2 — Scanner
// Loads the minimized DFAs from the YAML files produced by Program 1 (PreprocessorApp),
// runs the scanner on a source file, and prints tokens, errors, and symbol table.
fun main() {
    YamlLoader("src/main/resources/")

    val sourceFile = File("src/main/resources/input.txt")
    val sourceCode = if (sourceFile.exists()) sourceFile.readText() else "int x = 10;"
    scan(sourceCode)

    println("Tokens")
    TokenEntrys.tokens.forEach { token ->
        println("<${token.attribute}, ${token.value}>")
    }

    println("\nErrors")
    if (TokenEntrys.errors.isEmpty()) {
        println("No errors.")
    } else {
        TokenEntrys.errors.forEach { error ->
            println("Line ${error.line}: \"${error.consumed}\"")
        }
    }

    println("\nSymbol Table")
    if (SymbolTable.getAll().isEmpty()) {
        println("Empty.")
    } else {
        SymbolTable.getAll().forEach { entry ->
            println("${entry.index}|${entry.value}")
        }
    }
}
