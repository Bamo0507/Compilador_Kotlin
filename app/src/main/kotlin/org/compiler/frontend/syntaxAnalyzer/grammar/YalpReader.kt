package org.compiler.frontend.syntaxAnalyzer.grammar

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Associativity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.PrecedenceLevel
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import java.io.File

object YalpReader {
    fun read(filePath: String): Grammar = parse(File(filePath).readText())

    fun parse(content: String): Grammar {
        val cleaned = stripComments(content)
        val parts = cleaned.split("%%", limit = 2)
        require(parts.size == 2) { "Missing %% separator in .yalp file" }

        val header = parseTokensSection(parts[0].lines())
        val terminalNames = header.terminals.map { it.name }.toSet()

        val productions = parseProductionsSection(parts[1], terminalNames)

        val nonTerminals = productions.map { it.head }.toSet()
        val startSymbol = productions.first().head

        return Grammar(
            terminals = header.terminals,
            nonTerminals = nonTerminals,
            productions = productions,
            startSymbol = startSymbol,
            ignoredTokens = header.ignoredTokens,
            precedenceTable = header.precedenceTable
        )
    }

    private fun stripComments(content: String): String =
        content.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

    private data class HeaderSection(
        val terminals: Set<Symbol.Terminal>,
        val ignoredTokens: Set<Symbol.Terminal>,
        val precedenceTable: List<PrecedenceLevel>
    )

    private fun parseTokensSection(lines: List<String>): HeaderSection {
        val terminals = mutableSetOf<Symbol.Terminal>()
        val ignored = mutableSetOf<Symbol.Terminal>()
        val precedenceTable = mutableListOf<PrecedenceLevel>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("%token") -> {
                    trimmed.removePrefix("%token").trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .forEach { terminals.add(Symbol.Terminal(it)) }
                }
                trimmed.startsWith("%left") -> {
                    precedenceTable.add(
                        buildPrecedenceLevel(
                            text = trimmed.removePrefix("%left"),
                            associativity = Associativity.LEFT,
                            level = precedenceTable.size
                        )
                    )
                }
                trimmed.startsWith("%right") -> {
                    precedenceTable.add(
                        buildPrecedenceLevel(
                            text = trimmed.removePrefix("%right"),
                            associativity = Associativity.RIGHT,
                            level = precedenceTable.size
                        )
                    )
                }
                trimmed.startsWith("IGNORE") -> {
                    trimmed.removePrefix("IGNORE").trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .forEach {
                            val terminal = Symbol.Terminal(it)
                            terminals.add(terminal)
                            ignored.add(terminal)
                        }
                }
            }
        }

        return HeaderSection(terminals, ignored, precedenceTable)
    }

    private fun buildPrecedenceLevel(
        text: String,
        associativity: Associativity,
        level: Int
    ): PrecedenceLevel {
        val operators = text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { Symbol.Terminal(it) }
            .toSet()
        require(operators.isNotEmpty()) {
            "Precedence declaration ${associativity.name.lowercase()} has no operators"
        }
        return PrecedenceLevel(level, operators, associativity)
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
