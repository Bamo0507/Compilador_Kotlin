package org.compiler.lexicalAnalyzer.scanner

import org.compiler.lexicalAnalyzer.manageGrammar.models.CategoryAutomataIndex
import org.compiler.lexicalAnalyzer.manageGrammar.models.MinimizedDFA
import org.compiler.lexicalAnalyzer.lexer.models.SymbolTable
import org.compiler.lexicalAnalyzer.lexer.models.SymbolTableEntry
import org.compiler.lexicalAnalyzer.scanner.models.ErrorEntry
import org.compiler.lexicalAnalyzer.scanner.models.Token
import org.compiler.lexicalAnalyzer.scanner.models.TokenEntrys

const val BUFFER_SIZE = 10
const val EOF = '\u0000'

data class BufferCursor(
    val bufferIndex: Int, 
    val posInsideBuffer: Int
)

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
        // On tie, first entry wins — category order in .yal = priority
        val best = results.maxByOrNull { it.second }

        when {
            best == null || best.second == 0 -> {
                // error no DFA matched anything — consume the bad character and record it
                // TODO: Add error handling
                val badChar = extractLexeme(segments, lexembegin, 1)
                TokenEntrys.addError(ErrorEntry(consumed = badChar, line = currentLine, index = 0))
                lexembegin = advanceCursor(segments, lexembegin, 1)
            }
            best.first == "WHITESPACE" -> {
                // skip whitespace, track newlines for line counting (ERRORS)
                val lexeme = extractLexeme(segments, lexembegin, best.second)
                currentLine += lexeme.count { it == '\n' }
                lexembegin = advanceCursor(segments, lexembegin, best.second)
            }
            else -> {
                val lexeme = extractLexeme(segments, lexembegin, best.second)
                TokenEntrys.addToken(Token(attribute = best.first, value = lexeme))
                if (best.first == "ID") {
                    SymbolTable.add(SymbolTableEntry(id = lexeme, value = lexeme))
                }
                lexembegin = advanceCursor(segments, lexembegin, best.second)
            }
        }
    }
}
