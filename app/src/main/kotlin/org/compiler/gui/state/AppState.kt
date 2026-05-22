package org.compiler.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1Parser
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1Parser
import org.compiler.frontend.syntaxAnalyzer.runtime.Pipeline
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import org.compiler.frontend.syntaxAnalyzer.runtime.models.PipelineResult
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1Parser

class AppState(
    initialYalexContent: String = loadResourceText("parser_test.yal"),
    initialYalpContent: String = loadResourceText("parser.yalp"),
    initialInputContent: String = DEFAULT_INPUT
) {
    var yalexContent by mutableStateOf(initialYalexContent)
    var yalpContent by mutableStateOf(initialYalpContent)
    var inputContent by mutableStateOf(initialInputContent)
    var yalexFilePath by mutableStateOf<String?>(null)
        private set
    var yalpFilePath by mutableStateOf<String?>(null)
        private set
    var inputFilePath by mutableStateOf<String?>(null)
        private set
    var selectedMethod by mutableStateOf(ParserMethod.SLR1)
        private set
    var pipelineResult by mutableStateOf<PipelineResult?>(null)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onPlay() {
        isRunning = true
        errorMessage = null
        try {
            pipelineResult = Pipeline.runFull(
                yalexContent = yalexContent,
                yalpContent = yalpContent,
                inputContent = inputContent,
                method = selectedMethod
            )
        } catch (exception: Exception) {
            pipelineResult = null
            errorMessage = exception.message ?: exception::class.simpleName ?: "Unknown error"
        } finally {
            isRunning = false
        }
    }

    fun updateYalexContent(content: String, filePath: String? = yalexFilePath) {
        yalexContent = content
        yalexFilePath = filePath
    }

    fun updateYalpContent(content: String, filePath: String? = yalpFilePath) {
        yalpContent = content
        yalpFilePath = filePath
    }

    fun updateInputContent(content: String, filePath: String? = inputFilePath) {
        inputContent = content
        inputFilePath = filePath
    }

    fun reportFileError(message: String) {
        errorMessage = message
    }

    fun clearError() {
        errorMessage = null
    }

    fun changeMethod(newMethod: ParserMethod) {
        selectedMethod = newMethod
        val currentResult = pipelineResult ?: return

        errorMessage = null
        try {
            val parseResult = parseWithCachedArtifacts(currentResult, newMethod)
            pipelineResult = currentResult.copy(
                method = newMethod,
                firstSets = if (newMethod == ParserMethod.LL1) currentResult.ll1FirstSets else currentResult.lrFirstSets,
                followSets = if (newMethod == ParserMethod.LL1) currentResult.ll1FollowSets else currentResult.lrFollowSets,
                parseResult = parseResult
            )
        } catch (exception: Exception) {
            errorMessage = exception.message ?: exception::class.simpleName ?: "Unknown error"
        }
    }

    private fun parseWithCachedArtifacts(
        result: PipelineResult,
        method: ParserMethod
    ): ParseResult {
        val ignoredCategories = result.precedenceRewrittenGrammar.ignoredTokens.map { it.name }.toSet()
        val entries = result.lexerResult.entries
        return when (method) {
            ParserMethod.LL1 -> LL1Parser.parse(entries, ignoredCategories, result.ll1Table)
            ParserMethod.SLR1 -> SLR1Parser.parse(entries, ignoredCategories, result.slr1Table)
            ParserMethod.LALR1 -> LALR1Parser.parse(entries, ignoredCategories, result.lalr1Table)
        }
    }

    private companion object {
        private val DEFAULT_INPUT = """
            public class Counter {
                private int count;

                public void increment() {
                    count = count + 1;
                }

                public int compute(int n) {
                    int total = 0;
                    int i = 0;
                    while (i < n) {
                        total = total + i * 2;
                        i = i + 1;
                    }
                    if (total > 100) {
                        return total;
                    } else {
                        return 0;
                    }
                }
            }
        """.trimIndent()

        private fun loadResourceText(name: String): String =
            Thread.currentThread()
                .contextClassLoader
                .getResource(name)
                ?.readText()
                ?: ""
    }
}
