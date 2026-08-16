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

    // Disables the Run button right away; the GUI calls this before dispatching
    // onPlay to a background thread, so a double click cannot start two runs.
    fun markRunning() {
        isRunning = true
    }

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
        } catch (throwable: Throwable) {
            // Throwable, not Exception: a StackOverflowError from a deeply nested
            // input must surface as a banner instead of killing the window.
            pipelineResult = null
            errorMessage = throwable.message ?: throwable::class.simpleName ?: "Unknown error"
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
        } catch (throwable: Throwable) {
            errorMessage = throwable.message ?: throwable::class.simpleName ?: "Unknown error"
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
            package com.example.app;

            import java.util.List;
            import java.io.*;

            public class Counter implements Runnable {
                private int count, step = 1;
                private int offset = -1;
                private static final int LIMIT = 0xFF;

                public Counter(int start) throws Exception {
                    count = start;
                }

                public void run() {
                    char sep = ':';
                    String label = "count:\t";
                    do {
                        count = count + step;
                    } while (count < LIMIT);

                    switch (count % 3) {
                        case 0:
                            label = "multiple\n";
                            break;
                        default:
                            break;
                    }

                    try {
                        process(label, sep);
                    } catch (Exception e) {
                        count = 0;
                    } finally {
                        count = count + 1;
                    }
                }

                public int process(String label, char sep) {
                    int total = -offset;
                    for (int i = 0; i < count; i++) {
                        total += i * 2 - offset;
                    }
                    if (total > 100) {
                        return total;
                    } else {
                        return 0;
                    }
                }

                public static void main(String[] args) {
                    int[] seeds = new int[3];
                    for (int seed : seeds) {
                        seed++;
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
