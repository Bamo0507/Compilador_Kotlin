package org.compiler.frontend.lexicalAnalyzer.lexer

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.DiagnosticsTable
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildMinimizedDFA
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildSyntaxTree
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.buildTransitionTable
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.computeFollowPos
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.infixToPostfix
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.minimizeDFA
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.models.MinimizedDFA
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.YalexReader
import org.compiler.frontend.lexicalAnalyzer.manageGrammar.utils.normalizeRegex
import org.compiler.frontend.lexicalAnalyzer.scanner.models.TokenEntrys
import org.compiler.frontend.lexicalAnalyzer.scanner.scan
import org.compiler.frontend.models.TokenEntry
import org.compiler.symbolTable.SymbolTable

data class LexerResult(
    val entries: List<TokenEntry>,
    val errors: List<CompilerError.LexerError>,
    val automata: Map<String, MinimizedDFA>
)

object Lexer {
    fun tokenize(yalexContent: String, source: String): LexerResult {
        CategoryAutomataIndex.clear()
        SymbolTable.clear()

        val mappings = YalexReader.parse(yalexContent)

        mappings.forEach { mapping ->
            if (mapping.category == "EOF") return@forEach

            val normalized = normalizeRegex(mapping.pattern)
            val postfix = infixToPostfix(normalized)
            val augmented = "$postfix#."

            val tree = buildSyntaxTree(augmented)
            val followPos = computeFollowPos(tree)
            buildTransitionTable(tree, followPos)

            val partitions = minimizeDFA()
            val minimizedDFA = buildMinimizedDFA(partitions)
            CategoryAutomataIndex.put(mapping.category, minimizedDFA)
        }

        scan(source)

        return LexerResult(
            entries = TokenEntrys.entries.toList(),
            errors = DiagnosticsTable.lexerErrors(),
            automata = CategoryAutomataIndex.getAll().toMap()
        )
    }
}
