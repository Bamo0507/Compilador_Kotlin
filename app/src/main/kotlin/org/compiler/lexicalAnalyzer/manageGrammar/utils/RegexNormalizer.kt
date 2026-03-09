package org.compiler.lexicalAnalyzer.manageGrammar.utils

const val EPSILON = 'ε'

val ALL_OPERATORS = setOf('|', '.', '?', '*', '+')
val REGEX_METACHARACTERS = setOf('|', '.', '*', '+', '?', '(', ')', '[', ']', '^')
val UNARY_OPERATORS = setOf('*', '+', '?')
val BINARY_OPERATORS = setOf('|', '.')

// Precedencia de operadores
fun getPrecedence(operatorChar: Char): Int {
    return when (operatorChar) {
        '(' -> 1
        '|' -> 2
        '.' -> 3
        '?', '*', '+' -> 4
        else -> 0
    }
}

// Sustituye r? por (r|ε)
fun normalizeOptional(pattern: String): String {
    val output = StringBuilder()
    var idx = 0
    while (idx < pattern.length) {
        val ch = pattern[idx]
        if (ch == '\'' || ch == '"') {
            output.append(ch); idx++
            while (idx < pattern.length && pattern[idx] != ch) { output.append(pattern[idx]); idx++ }
            if (idx < pattern.length) { output.append(pattern[idx]); idx++ }
        } else if (ch == '?') {
            val built = output.toString()
            var start = built.length - 1
            val operand = when {
                built[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (built[start] == ')') depth++
                        if (built[start] == '(') depth--
                        start--
                    }
                    built.substring(start + 1)
                }
                built[start] == ']' -> {
                    start--
                    while (start >= 0 && built[start] != '[') start--
                    built.substring(start)
                }
                start > 0 && built[start - 1] == '\\' -> built.substring(start - 1)
                else -> built.substring(start)
            }
            output.setLength(output.length - operand.length)
            output.append("($operand|$EPSILON)")
            idx++
        } else {
            output.append(ch)
            idx++
        }
    }
    return output.toString()
}

// Sustituye r+ por r(r)*
fun normalizePlus(pattern: String): String {
    val output = StringBuilder()
    var idx = 0
    while (idx < pattern.length) {
        val ch = pattern[idx]
        if (ch == '\'' || ch == '"') {
            output.append(ch); idx++
            while (idx < pattern.length && pattern[idx] != ch) { output.append(pattern[idx]); idx++ }
            if (idx < pattern.length) { output.append(pattern[idx]); idx++ }
        } else if (ch == '+') {
            val built = output.toString()
            var start = built.length - 1
            val operand = when {
                built[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (built[start] == ')') depth++
                        if (built[start] == '(') depth--
                        start--
                    }
                    built.substring(start + 1)
                }
                built[start] == ']' -> {
                    start--
                    while (start >= 0 && built[start] != '[') start--
                    built.substring(start)
                }
                start > 0 && built[start - 1] == '\\' -> built.substring(start - 1)
                else -> built.substring(start)
            }
            output.append("($operand)*")
            idx++
        } else {
            output.append(ch)
            idx++
        }
    }
    return output.toString()
}

// Applies both operator normalizations in order: first + then ?
fun normalizeOperators(pattern: String): String =
    normalizeOptional(normalizePlus(pattern))

// Applies all normalizations to a regex in the correct order
// eof is a scanner signal, not a regex
fun normalizeRegex(pattern: String): String {
    if (pattern.trim() == "eof") return "eof"
    return addConcatenation(normalizeOperators(normalizeCharClasses(pattern)))
}

// Makes concatenation explicit by inserting '.' between adjacent tokens
fun addConcatenation(regex: String): String {
    val tokens = tokenize(regex)
    val sb = StringBuilder()
    for (i in tokens.indices) {
        sb.append(tokens[i])
        if (i + 1 < tokens.size && needsConcatenation(tokens[i].last(), tokens[i + 1].first())) {
            sb.append('.')
        }
    }
    return sb.toString()
}

// Splits the regex into atomic tokens, skipping formatting spaces.
private fun tokenize(regex: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < regex.length) {
        when {
            regex[i] == ' ' -> i++
            regex[i] == '\'' -> {
                val sb = StringBuilder()
                sb.append('\''); i++
                while (i < regex.length && regex[i] != '\'') { sb.append(regex[i]); i++ }
                if (i < regex.length) { sb.append('\''); i++ }
                tokens.add(sb.toString())
            }
            regex[i] == '"' -> {
                i++ // skip opening quote
                while (i < regex.length && regex[i] != '"') {
                    val c = regex[i]
                    tokens.add(if (c in REGEX_METACHARACTERS) "'$c'" else c.toString())
                    i++
                }
                if (i < regex.length) i++ // skip closing quote
            }
            else -> { tokens.add(regex[i].toString()); i++ }
        }
    }
    return tokens
}

// Insert '.' between left and right when:
//   left  closes a term: anything except  (  |  .
//   right opens  a term: anything except  )  |  .  *
private fun needsConcatenation(left: Char, right: Char): Boolean {
    val leftClosesTerm = left  !in setOf('(', '|', '.')
    val rightOpensTerm = right !in setOf(')', '|', '.', '*')
    return leftClosesTerm && rightOpensTerm
}

// Normalizes character classes into explicit alternations
// ['0'-'9'] -> (0|1|2|3|4|5|6|7|8|9)
// ['a'-'z''A'-'Z'] -> (a|b|...|z|A|B|...|Z)
fun normalizeCharClasses(pattern: String): String {
    val result = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        when {
            pattern[i] == '"' -> {
                result.append(pattern[i]); i++
                while (i < pattern.length && pattern[i] != '"') { result.append(pattern[i]); i++ }
                if (i < pattern.length) { result.append(pattern[i]); i++ }
            }
            pattern[i] == '[' -> {
                val end = pattern.indexOf(']', i + 1)
                val content = pattern.substring(i + 1, end)
                result.append(expandCharClass(content))
                i = end + 1
            }
            else -> { result.append(pattern[i]); i++ }
        }
    }
    return result.toString()
}

// Parses the content inside [...] and returns the alternation
// Each char is always written as 'c' (3 source chars: quote, char, quote)
private fun expandCharClass(content: String): String {
    val chars = mutableListOf<Char>()
    var i = 0
    while (i < content.length) {
        if (content[i] == '\'') {
            val c = content[i + 1]
            i += 3 // skip 'c'
            if (i < content.length && content[i] == '-') {
                i++ // skip '-'
                val end = content[i + 1]
                i += 3 // skip 'c'
                for (ch in c..end) chars.add(ch)
            } else {
                chars.add(c)
            }
        } else {
            i++
        }
    }
    return if (chars.size == 1) chars[0].toString()
    else "(${chars.joinToString("|")})"
}

