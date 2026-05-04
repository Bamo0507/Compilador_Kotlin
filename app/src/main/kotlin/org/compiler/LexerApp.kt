package org.compiler

import org.compiler.frontend.lexicalAnalyzer.scanner.YamlLoader
import org.compiler.frontend.lexicalAnalyzer.scanner.scan
import org.compiler.frontend.lexicalAnalyzer.scanner.models.TokenEntrys
import org.compiler.frontend.lexicalAnalyzer.lexer.models.SymbolTable
import java.io.File

// Program 2 — Scanner
// Loads the minimized DFAs from the YAML files produced by Program 1 (PreprocessorApp),
// runs the scanner on a source file, and writes tokens, errors, and symbol table to output files.
fun main() {
    YamlLoader("src/main/resources/")

    val sourceFile = File("src/main/resources/input.java")
    val sourceCode = if (sourceFile.exists()) sourceFile.readText() else "int x = 10;"
    SymbolTable.clear()
    scan(sourceCode)

    val outputDir = File("src/main/resources/output")
    outputDir.mkdirs()

    File(outputDir, "tokens.txt").bufferedWriter().use { writer ->
        TokenEntrys.tokens.forEach { token ->
            val printedValue = if (token.category == "KEYWORD") {
                token.lexeme
            } else {
                SymbolTable.addOrGet(token.lexeme).toString()
            }
            writer.write("<${token.category}, $printedValue>")
            writer.newLine()
        }
    }

    File(outputDir, "errors.txt").bufferedWriter().use { writer ->
        if (TokenEntrys.errors.isEmpty()) {
            writer.write("No errors.")
            writer.newLine()
        } else {
            TokenEntrys.errors.forEach { error ->
                writer.write("Line ${error.line}: \"${error.consumed}\"")
                writer.newLine()
            }
        }
    }

    File(outputDir, "symbolTable.txt").bufferedWriter().use { writer ->
        if (SymbolTable.getAll().isEmpty()) {
            writer.write("Empty.")
            writer.newLine()
        } else {
            SymbolTable.getAll().forEach { entry ->
                writer.write("${entry.index}|${entry.value}")
                writer.newLine()
            }
        }
    }

    println("Output written to ${outputDir.path}/")
}
