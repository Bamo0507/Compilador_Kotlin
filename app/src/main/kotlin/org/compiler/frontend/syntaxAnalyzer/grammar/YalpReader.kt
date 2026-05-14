package org.compiler.frontend.syntaxAnalyzer.grammar

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import java.io.File

object YalpReader {
    fun read(filePath: String): Grammar = parse(File(filePath).readText())

    fun parse(content: String): Grammar {
        val cleaned = stripComments(content)
        val parts = cleaned.split("%%", limit = 2)
        require(parts.size == 2) { "Missing %% separator in .yalp file" }

        val (terminals, ignoredTokens) = parseTokensSection(parts[0].lines())
        val terminalNames = terminals.map { it.name }.toSet()

        val productions = parseProductionsSection(parts[1], terminalNames)

        val nonTerminals = productions.map { it.head }.toSet()
        val startSymbol = productions.first().head

        return Grammar(
            terminals = terminals,
            nonTerminals = nonTerminals,
            productions = productions,
            startSymbol = startSymbol,
            ignoredTokens = ignoredTokens
        )
    }

    private fun stripComments(content: String): String =
        content.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

    private fun parseTokensSection(lines: List<String>): Pair<Set<Symbol.Terminal>, Set<Symbol.Terminal>> {
        val terminals = mutableSetOf<Symbol.Terminal>()
        val ignored = mutableSetOf<Symbol.Terminal>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("%token") -> {
                    trimmed.removePrefix("%token").trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .forEach { terminals.add(Symbol.Terminal(it)) }
                }
                trimmed.startsWith("IGNORE") -> {
                    trimmed.removePrefix("IGNORE").trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .forEach {
                            val t = Symbol.Terminal(it)
                            terminals.add(t)
                            ignored.add(t)
                        }
                }
            }
        }

        return terminals to ignored
    }

    private fun parseProductionsSection(text: String, terminalNames: Set<String>): List<Production> {
        val productions = mutableListOf<Production>()
        var idCounter = 1

        val blocks = text.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        for (block in blocks) {
            val colonIdx = block.indexOf(':')
            if (colonIdx < 0) continue

            val head = Symbol.NonTerminal(block.substring(0, colonIdx).trim())
            val alternatives = block.substring(colonIdx + 1).split("|").map { it.trim() }

            for (alt in alternatives) {
                val body = alt.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .map { classifySymbol(it, terminalNames) }
                productions.add(Production(idCounter++, head, body))
            }
        }

        return productions
    }

    private fun classifySymbol(token: String, terminalNames: Set<String>): Symbol = when {
        token == "ε" -> Symbol.Epsilon
        token == "$" -> Symbol.EndMarker
        token in terminalNames -> Symbol.Terminal(token)
        token[0].isUpperCase() -> Symbol.Terminal(token)
        else -> Symbol.NonTerminal(token)
    }
}
