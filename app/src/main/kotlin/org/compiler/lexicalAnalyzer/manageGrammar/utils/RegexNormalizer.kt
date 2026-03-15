package org.compiler.lexicalAnalyzer.manageGrammar.utils

const val EPSILON = 'ε'

val ALL_OPERATORS = setOf('|', '.', '?', '*', '+')
val REGEX_METACHARACTERS = setOf('|', '.', '*', '+', '?', '(', ')', '[', ']', '^')
val UNARY_OPERATORS = setOf('*', '+', '?')
val BINARY_OPERATORS = setOf('|', '.')

// 32..126 covers all printable ASCII chars
val ALPHABET: Set<Char> = buildSet {
    for (code in 32..126) add(code.toChar())
    add('\t')
    add('\n')
}

// Resolves the raw content between single quotes to the actual character.
private fun resolveCharLiteral(content: String): Char = when (content) {
    "\\t"  -> '\t'
    "\\n" -> '\n'
    "\\q" -> '\''
    else  -> content[0]
}

// Converts a single character to its atom representation in the normalized regex.
// Characters that would be misinterpreted by splitIntoAtoms must be quoted.
private fun charToAtom(c: Char): String = when {
    c == '\t' -> "'\\t'"
    c == '\n' -> "'\\n'"
    c == '\'' -> "'\\q'"  // single-quote must be quoted — splitIntoAtoms uses ' as atom delimiter
    c == '"'  -> "'\"'"  // double-quote must be quoted — splitIntoAtoms treats raw " as string delimiter
    c == ' '  -> "' '"   // space must be quoted — splitIntoAtoms skips raw spaces
    c in REGEX_METACHARACTERS -> "'$c'"
    else -> c.toString()
}

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
    val tokens = splitIntoAtoms(regex)
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
// Each atom is the minimum unit that ShuntingYard can process.
private fun splitIntoAtoms(regex: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < regex.length) {
        when {
            // Space is formatting only — skip it
            // "a | b"  →  ["a", "|", "b"]
            regex[i] == ' ' -> i++

            // Single-quoted literal — kept whole as one atom
            // '\n'  →  ["'\n'"]
            regex[i] == '\'' -> {
                val sb = StringBuilder()
                sb.append('\''); i++
                while (i < regex.length && regex[i] != '\'') { sb.append(regex[i]); i++ }
                if (i < regex.length) { sb.append('\''); i++ }
                tokens.add(sb.toString())
            }

            // Double-quoted string — exploded into individual char atoms
            // metacharacters inside get single-quoted so they aren't misread as operators
            // "int"  →  ["i", "n", "t"]
            // "a.b"  →  ["a", "'.'", "b"]
            regex[i] == '"' -> {
                i++ // skip opening quote
                while (i < regex.length && regex[i] != '"') {
                    val c = regex[i]
                    tokens.add(if (c in REGEX_METACHARACTERS) "'$c'" else c.toString())
                    i++
                }
                if (i < regex.length) i++ // skip closing quote
            }

            // Any other character — becomes its own atom (operators, letters, digits, parens)
            // a|b*  →  ["a", "|", "b", "*"]
            else -> { tokens.add(regex[i].toString()); i++ }
        }
    }
    return tokens
}

// Insert '.' between left and right when:
// left  closes a term: anything except  (  |  .
// right opens  a term: anything except  )  |  .  *
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

// Parses the content inside [...] and returns an alternation of the matched characters.
// Supports ranges like 'a'-'z', individual chars like '\n', and negation with ^ prefix.
// [^'*'] expands to every ALPHABET character except '*'.
private fun expandCharClass(content: String): String {
    val negate = content.startsWith("^")
    val parseFrom = if (negate) content.substring(1) else content

    val chars = mutableListOf<Char>()
    var i = 0
    while (i < parseFrom.length) {
        if (parseFrom[i] == '\'') {
            // Read everything between quotes to support multi-char escapes like '\n', '\q'
            val start = i + 1
            val end = parseFrom.indexOf('\'', start)
            if (end == -1) { i++; continue }
            val c = resolveCharLiteral(parseFrom.substring(start, end))
            i = end + 1

            if (i < parseFrom.length && parseFrom[i] == '-') {
                i++ // skip '-'
                val rStart = i + 1
                val rEnd = parseFrom.indexOf('\'', rStart)
                if (i < parseFrom.length && parseFrom[i] == '\'' && rEnd != -1) {
                    val endChar = resolveCharLiteral(parseFrom.substring(rStart, rEnd))
                    i = rEnd + 1
                    for (ch in c..endChar) chars.add(ch)
                }
            } else {
                chars.add(c)
            }
        } else {
            i++
        }
    }

    val finalChars = if (negate) {
        (ALPHABET - chars.toSet()).sortedBy { it.code }
    } else {
        chars
    }

    return when {
        finalChars.isEmpty() -> ""
        finalChars.size == 1 -> charToAtom(finalChars[0])
        else -> "(${finalChars.joinToString("|") { charToAtom(it) }})"
    }
}
