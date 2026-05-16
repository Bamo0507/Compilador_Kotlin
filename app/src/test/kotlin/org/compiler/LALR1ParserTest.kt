package org.compiler

import org.compiler.frontend.models.Token
import org.compiler.frontend.models.TokenEntry
import org.compiler.models.LexemeLocation
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1AutomatonMerger
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1Parser
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LALR1ParserTest {

    // Dragon Book §4.1 expression grammar:
    //   E -> E + T | T
    //   T -> T * F | F
    //   F -> ( E ) | id
    private val nonTerminalE = Symbol.NonTerminal("E")
    private val nonTerminalT = Symbol.NonTerminal("T")
    private val nonTerminalF = Symbol.NonTerminal("F")
    private val terminalPlus = Symbol.Terminal("+")
    private val terminalTimes = Symbol.Terminal("*")
    private val terminalLParen = Symbol.Terminal("(")
    private val terminalRParen = Symbol.Terminal(")")
    private val terminalId = Symbol.Terminal("id")

    private val productionEpT = Production(1, nonTerminalE, listOf(nonTerminalE, terminalPlus, nonTerminalT))
    private val productionET = Production(2, nonTerminalE, listOf(nonTerminalT))
    private val productionTtF = Production(3, nonTerminalT, listOf(nonTerminalT, terminalTimes, nonTerminalF))
    private val productionTF = Production(4, nonTerminalT, listOf(nonTerminalF))
    private val productionFParen = Production(5, nonTerminalF, listOf(terminalLParen, nonTerminalE, terminalRParen))
    private val productionFid = Production(6, nonTerminalF, listOf(terminalId))

    private val expressionGrammar = Grammar(
        terminals = setOf(terminalPlus, terminalTimes, terminalLParen, terminalRParen, terminalId),
        nonTerminals = setOf(nonTerminalE, nonTerminalT, nonTerminalF),
        productions = listOf(productionEpT, productionET, productionTtF, productionTF, productionFParen, productionFid),
        startSymbol = nonTerminalE,
        ignoredTokens = emptySet()
    )

    private fun buildLALR1Table(): org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table {
        val firstSets = FirstSetComputer.compute(expressionGrammar)
        val slr1Automata = SLR1AutomataBuilder.build(expressionGrammar, firstSets)
        val merged = LALR1AutomatonMerger.mergeFromSLR1(slr1Automata)
        return LALR1TableBuilder.build(merged)
    }

    private fun entry(category: String, lexeme: String, line: Int = 1, position: Int = 1): TokenEntry =
        TokenEntry(
            token = Token(category = category, lexeme = lexeme, symbolIndex = null),
            location = LexemeLocation(line = line, position = position)
        )

    @Test
    fun `LALR1 parses a single id and accepts`() {
        val table = buildLALR1Table()
        val entries = listOf(entry("id", "x"))
        val result = LALR1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted)
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(nonTerminalE, root.symbol)
    }

    @Test
    fun `LALR1 parses id plus id times id with precedence respected`() {
        val table = buildLALR1Table()
        val entries = listOf(
            entry("id", "a"),
            entry("+", "+"),
            entry("id", "b"),
            entry("*", "*"),
            entry("id", "c")
        )
        val result = LALR1Parser.parse(entries, emptySet(), table)

        assertTrue(result is ParseResult.Accepted)
        val root = result.parseTree as ParseTree.InternalNode
        assertEquals(productionEpT, root.production)
        val rightT = root.children[2] as ParseTree.InternalNode
        assertEquals(productionTtF, rightT.production)
    }

    @Test
    fun `LALR1 recovers from id plus plus and ends Accepted with one error`() {
        val table = buildLALR1Table()
        val entries = listOf(
            entry("id", "a"),
            entry("+", "+"),
            entry("+", "+")
        )
        val result = LALR1Parser.parse(entries, emptySet(), table)

        // Same recovery behavior as SLR1Parser (LALR1Parser delegates).
        assertTrue(result is ParseResult.Accepted)
        assertEquals(1, result.errors.size)
        assertEquals("+", result.errors.first().foundToken?.lexeme)
    }
}
