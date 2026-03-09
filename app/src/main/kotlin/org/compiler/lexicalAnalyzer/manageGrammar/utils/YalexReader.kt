package org.compiler.lexicalAnalyzer.manageGrammar.utils

import org.compiler.lexicalAnalyzer.manageGrammar.models.RegexMapping
import java.io.File

object YalexReader {
    fun read(filePath: String): List<RegexMapping> {
        val content = File(filePath).readText()
        val cleaned = stripComments(content)
        val lines = cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val rawDefinitions = linkedMapOf<String, String>()
        val rawRules = mutableListOf<Pair<String, String>>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("let ") -> {
                    val eqIdx = line.indexOf('=')
                    val name = line.substring(4, eqIdx).trim()
                    var value = line.substring(eqIdx + 1).trim()
                    i++
                    // Accumulate continuation lines until next block
                    while (i < lines.size
                        && !lines[i].startsWith("let ")
                        && !lines[i].startsWith("rule ")
                    ) {
                        value += " ${lines[i]}"
                        i++
                    }
                    rawDefinitions[name] = value
                }

                line.startsWith("rule ") -> {
                    i++
                    while (i < lines.size
                        && !lines[i].startsWith("rule ")
                        && !lines[i].startsWith("let ")
                    ) {
                        val rule = lines[i]
                        if (rule.startsWith("|")) {
                            val body = rule.removePrefix("|").trim()
                            val braceOpen = body.lastIndexOf('{')
                            val braceClose = body.lastIndexOf('}')
                            if (braceOpen >= 0 && braceClose > braceOpen) {
                                val pattern = body.substring(0, braceOpen).trim()
                                val category = body.substring(braceOpen + 1, braceClose).trim()
                                rawRules.add(pattern to category)
                            }
                        }
                        i++
                    }
                }

                else -> i++
            }
        }

        // Expand each definition transitively using previously expanded ones
        val definitions = expandDefinitions(rawDefinitions)

        return rawRules.map { (pattern, category) ->
            RegexMapping(
                pattern = substituteNames(pattern, definitions),
                category = category
            )
        }
    }

    // Removes (* ... *) block comments, including multi-line ones
    private fun stripComments(content: String): String =
        content.replace(Regex("""\(\*.*?\*\)""", RegexOption.DOT_MATCHES_ALL), "")

    // Expands each definition using all previously expanded definitions
    // Order matters: definitions are processed in declaration order (LinkedHashMap)
    private fun expandDefinitions(raw: LinkedHashMap<String, String>): Map<String, String> {
        val expanded = linkedMapOf<String, String>()
        for ((name, pattern) in raw) {
            expanded[name] = substituteNames(pattern, expanded)
        }
        return expanded
    }

    // Replaces let-names in a pattern with their expanded values
    // Sorted by name length descending to avoid partial substitutions
    // "float_basic" must be replaced before "float"
    private fun substituteNames(pattern: String, definitions: Map<String, String>): String {
        var result = pattern
        val sortedByLength = definitions.entries.sortedByDescending { it.key.length }
        for ((name, value) in sortedByLength) {
            result = substituteOutsideQuotes(result, Regex("""\b$name\b"""), "($value)")
        }
        return result
    }

    // Replaces nameRegex only in the unquoted segments of text.
    // Quoted segments ("..." and '...') are passed through unchanged.
    private fun substituteOutsideQuotes(text: String, nameRegex: Regex, replacement: String): String {
        val escapedReplacement = Regex.escapeReplacement(replacement)
        return buildString {
            var pos = 0
            for (match in QUOTED_SEGMENT.findAll(text)) {
                val unquoted = text.substring(pos, match.range.first)
                append(nameRegex.replace(unquoted, escapedReplacement))
                append(match.value)
                pos = match.range.last + 1
            }
            append(nameRegex.replace(text.substring(pos), escapedReplacement))
        }
    }

    // Matches double-quoted strings ("...") and single-quoted char literals ('...')
    private val QUOTED_SEGMENT = Regex(""""[^"]*"|'[^']*'""")
}
