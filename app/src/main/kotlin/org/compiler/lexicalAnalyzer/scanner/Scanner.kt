package org.compiler.lexicalAnalyzer.scanner

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

import org.compiler.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import org.compiler.lexicalAnalyzer.manageGrammar.models.MinimizedDFA
import org.compiler.lexicalAnalyzer.lexer.models.SymbolTable
import org.compiler.lexicalAnalyzer.lexer.models.SymbolTableEntry
import org.compiler.lexicalAnalyzer.scanner.models.ErrorEntry
import org.compiler.lexicalAnalyzer.scanner.models.Token
import org.compiler.lexicalAnalyzer.scanner.models.TokenEntrys
import java.nio.file.Files
import java.nio.file.Paths

const val BUFFER_SIZE = 10
const val EOF = '\u0000'

data class BufferCursor(
    val bufferIndex: Int, 
    val posInsideBuffer: Int
)

private val mapper = ObjectMapper(YAMLFactory()).apply {
    findAndRegisterModules()
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

// Resolves the string value of a YAML input field to a single Char.
// Handles the escape notations written by charToYamlInput: \t, \n.
// For plain single-char strings, returns that char directly.
private fun resolveInputChar(value: String): Char = when (value) {
    "\\t" -> '\t'
    "\\n" -> '\n'
    "\\q" -> '\''
    else  -> value.single()
}

fun YamlLoader(path: String): CategoryAutomataIndex {
    val paths = loadYamlsFromPath(path)
    for (yamlPath in paths) {
        loadYamlDfas(yamlPath)
    }
    return CategoryAutomataIndex
}

fun loadYamlsFromPath(path: String): List<String> {
    val yamlDir = Paths.get(path)
    require(Files.isDirectory(yamlDir)) { "El path $yamlDir no es un directorio válido" }

    return Files.list(yamlDir)
        .filter { it.toString().endsWith(".yaml") }
        .map { it.toString() }
        .toList()
}

fun loadYamlDfas(path: String): CategoryAutomataIndex {
    val yamlPath = Paths.get(path)
    require(Files.exists(yamlPath)) { "El archivo $yamlPath no existe" }

    val yamlText = Files.readString(yamlPath)
    val documents = yamlText
        .split(Regex("(?m)^---\\s*$"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    documents.forEachIndexed { docIndex, docText ->
        val document: Map<String, Any?> = mapper.readValue(
            docText,
            object : TypeReference<Map<String, Any?>>() {}
        )

        val qStates = document["q_states"] as? Map<*, *>
            ?: error("El documento YAML #${docIndex + 1} no contiene q_states")

        val initialState = qStates["initial"]?.toString()?.toIntOrNull()
            ?: error("initial inválido en el documento YAML #${docIndex + 1}")

        val finalRaw = qStates["final"]
        val acceptingStates = when (finalRaw) {
            is List<*> -> finalRaw.mapNotNull { it?.toString()?.toIntOrNull() }.toSet()
            null -> emptySet()
            else -> setOf(finalRaw.toString().toIntOrNull()
                ?: error("final inválido en el documento YAML #${docIndex + 1}"))
        }

        val deltaEntries = document["delta"] as? List<*>
            ?: error("El documento YAML #${docIndex + 1} no contiene delta")

        val transitions = mutableMapOf<Int, MutableMap<Char, Int>>()

        for (entry in deltaEntries) {
            val transition = entry as Map<*, *>
            val params = transition["params"] as Map<*, *>
            val output = transition["output"] as Map<*, *>

            val fromState = params["initial_state"].toString().toInt()
            val inputChar = resolveInputChar(params["input"].toString())
            val toState = output["final_state"].toString().toInt()

            transitions.getOrPut(fromState) { mutableMapOf() }[inputChar] = toState
        }

        val categoryName = document["name"]?.toString()
            ?: yamlPath.fileName.toString().removeSuffix(".yaml")

        val index = CategoryAutomataIndex.put(
            categoryName,
            MinimizedDFA(
                initialState = initialState,
                acceptingStates = acceptingStates,
                transitions = transitions.mapValues { (_, value) -> value.toMap() }
            )
        )
    }

    return CategoryAutomataIndex
}

// Splits the source string into segments of 10
// The last segment gets an EOF marker appended to signal
fun segmentInput(source: String): List<CharArray> {
    val segments = mutableListOf<CharArray>()
    var segmentStart = 0

    while (segmentStart < source.length) {
        val segmentEnd = minOf(segmentStart + BUFFER_SIZE, source.length)
        val isLastSegment = segmentEnd == source.length

        val segmentSize = if (isLastSegment) segmentEnd - segmentStart + 1 else segmentEnd - segmentStart
        val segment = CharArray(segmentSize)

        for (sourceIndex in segmentStart until segmentEnd) {
            segment[sourceIndex - segmentStart] = source[sourceIndex]
        }
        if (isLastSegment) segment[segmentEnd - segmentStart] = EOF

        segments.add(segment)
        segmentStart = segmentEnd
    }

    return segments
}

// Returns the character at the cursor and the next position
// advances to the next buffer when the current one is completed
fun readChar(segments: List<CharArray>, cursor: BufferCursor): Pair<Char, BufferCursor>? {
    if (cursor.bufferIndex >= segments.size) return null

    val currentSegment = segments[cursor.bufferIndex]
    if (cursor.posInsideBuffer >= currentSegment.size) return null
    val char = currentSegment[cursor.posInsideBuffer]

    val nextCursor = if (cursor.posInsideBuffer + 1 < currentSegment.size) {
        BufferCursor(cursor.bufferIndex, cursor.posInsideBuffer + 1)
    } else {
        BufferCursor(cursor.bufferIndex + 1, 0)
    }

    return Pair(char, nextCursor)
}

// Simulates a single DFA on the buffer list starting at lexembegin
// forward moves one character at a time through the buffers
// Returns the number of characters consumed up to the last accepting state, retract
// Returns 0 if no accepting state was reached
fun simulateDFA(dfa: MinimizedDFA, segments: List<CharArray>, lexembegin: BufferCursor): Int {
    var currentState = dfa.initialState
    var lastAcceptSteps = 0
    var stepsFromLexembegin = 0
    var forward = lexembegin

    while (true) {
        val (currentChar, nextForward) = readChar(segments, forward) ?: break
        if (currentChar == EOF) break

        val nextState = dfa.transitions[currentState]?.get(currentChar) ?: break  // fail

        currentState = nextState
        forward = nextForward
        stepsFromLexembegin++

        if (currentState in dfa.acceptingStates) {
            lastAcceptSteps = stepsFromLexembegin  // retract point
        }
    }

    return lastAcceptSteps
}

// Advances a cursor by the given number of steps through the buffer list.
fun advanceCursor(segments: List<CharArray>, cursor: BufferCursor, forwardSteps: Int): BufferCursor {
    var current = cursor
    repeat(forwardSteps) {
        val (_, next) = readChar(segments, current) ?: return current
        current = next
    }
    return current
}

// Panic mode: consumes characters from cursor until the next position where any DFA matches.
// Returns the entire bad sequence as a single string and the new cursor position.
// This avoids flooding the error list when multiple consecutive chars are unrecognized.
fun panicMode(
    segments: List<CharArray>,
    cursor: BufferCursor,
    automata: Map<String, MinimizedDFA>
): Pair<String, BufferCursor> {
    val bad = StringBuilder()
    var current = cursor

    while (true) {
        val (char, _) = readChar(segments, current) ?: break
        if (char == EOF) break
        val anyMatch = automata.values.any { dfa -> simulateDFA(dfa, segments, current) > 0 }
        if (anyMatch) break
        bad.append(char)
        current = advanceCursor(segments, current, 1)
    }

    return bad.toString() to current
}

// Extracts the lexeme string by reading forwardSteps characters from lexembegin.
// Handles buffer boundaries transparently — the result is always a flat String.
fun extractLexeme(segments: List<CharArray>, lexembegin: BufferCursor, forwardSteps: Int): String {
    val lexeme = StringBuilder()
    var forward = lexembegin

    repeat(forwardSteps) {
        val (char, nextForward) = readChar(segments, forward) ?: return lexeme.toString()
        lexeme.append(char)
        forward = nextForward
    }

    return lexeme.toString()
}

// Main scan loop — reads the source through buffers, runs all DFAs on each position,
// applies longest match, and fills TokenEntrys with tokens and errors.
fun scan(source: String) {
    TokenEntrys.clear()
    SymbolTable.clear()

    val segments = segmentInput(source)
    val automata = CategoryAutomataIndex.getAll()
    var lexembegin = BufferCursor(0, 0)
    var currentLine = 1

    while (true) {
        val (currentChar, _) = readChar(segments, lexembegin) ?: break
        if (currentChar == EOF) break

        // Run every DFA from lexembegin and collect (category, forwardSteps)
        val results = mutableListOf<Pair<String, Int>>()
        for ((category, dfa) in automata) {
            val forwardSteps = simulateDFA(dfa, segments, lexembegin)
            results.add(category to forwardSteps)
        }

        // Pick the category that consumed the most characters (longest match)
        val best = results.maxByOrNull { it.second }

        when {
            best == null || best.second == 0 -> {
                // Panic mode: consume all consecutive unrecognized chars as one error
                val (badSeq, newCursor) = panicMode(segments, lexembegin, automata)
                TokenEntrys.addError(ErrorEntry(consumed = badSeq, line = currentLine))
                currentLine += badSeq.count { it == '\n' }
                lexembegin = newCursor
            }
            best.first == "WHITESPACE" || best.first == "COMMENT" -> {
                // skip whitespace and comments — neither produces a token
                val lexeme = extractLexeme(segments, lexembegin, best.second)
                currentLine += lexeme.count { it == '\n' }
                lexembegin = advanceCursor(segments, lexembegin, best.second)
            }
            else -> {
                val lexeme = extractLexeme(segments, lexembegin, best.second)
                val tokenValue = if (best.first != "KEYWORD") {
                    SymbolTable.addOrGet(lexeme).toString()
                } else {
                    lexeme
                }
                TokenEntrys.addToken(Token(attribute = best.first, value = tokenValue))
                lexembegin = advanceCursor(segments, lexembegin, best.second)
            }
        }
    }
}
