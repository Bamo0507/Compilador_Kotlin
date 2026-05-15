package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.GrammarValidator
import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Associativity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.PrecedenceLevel
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.ValidationError.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarValidatorTest {

    private val javaLexerCategories = setOf(
        "KEYWORD", "ID", "INT", "FLOAT", "OPERATOR", "PUNCTUATION", "WHITESPACE", "COMMENT"
    )

    @Test
    fun `parser yalp passes validation cleanly`() {
        val grammar = YalpReader.parse(File("src/main/resources/parser.yalp").readText())
        val errors = GrammarValidator.validate(grammar, javaLexerCategories)
        val blocking = errors.filter { error -> error.severity == Severity.ERROR }
        assertTrue(blocking.isEmpty(), "Expected no errors but got: $blocking")
    }

    @Test
    fun `undeclared token produces error`() {
        val content = """
            %token ID UNKNOWN_TOKEN
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        val errors = GrammarValidator.validate(grammar, setOf("ID"))
        assertTrue(errors.any { error ->
            error.severity == Severity.ERROR && "UNKNOWN_TOKEN" in error.message
        })
    }

    @Test
    fun `undefined non-terminal produces error`() {
        val content = """
            %token ID
            %%
            program : expr ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        val errors = GrammarValidator.validate(grammar, setOf("ID"))
        assertTrue(errors.any { error ->
            error.severity == Severity.ERROR && "expr" in error.message
        })
    }

    @Test
    fun `cycle in unit productions produces error`() {
        val terminalA = Symbol.Terminal("A")
        val nonTerminalX = Symbol.NonTerminal("x")
        val nonTerminalY = Symbol.NonTerminal("y")
        val productions = listOf(
            Production(1, nonTerminalX, listOf(nonTerminalY)),
            Production(2, nonTerminalY, listOf(nonTerminalX))
        )
        val grammar = Grammar(
            terminals = setOf(terminalA),
            nonTerminals = setOf(nonTerminalX, nonTerminalY),
            productions = productions,
            startSymbol = nonTerminalX,
            ignoredTokens = emptySet()
        )
        val errors = GrammarValidator.validate(grammar, setOf("A"))
        assertTrue(errors.any { error ->
            error.severity == Severity.ERROR && "Cycle" in error.message
        })
    }

    @Test
    fun `unreachable non-terminal produces warning`() {
        val content = """
            %token ID
            %%
            program : ID ;
            orphan : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        val errors = GrammarValidator.validate(grammar, setOf("ID"))
        assertTrue(errors.any { error ->
            error.severity == Severity.WARNING && "orphan" in error.message
        })
    }

    @Test
    fun `duplicate production produces warning`() {
        val nonTerminal = Symbol.NonTerminal("program")
        val terminal = Symbol.Terminal("ID")
        val productions = listOf(
            Production(1, nonTerminal, listOf(terminal)),
            Production(2, nonTerminal, listOf(terminal))
        )
        val grammar = Grammar(
            terminals = setOf(terminal),
            nonTerminals = setOf(nonTerminal),
            productions = productions,
            startSymbol = nonTerminal,
            ignoredTokens = emptySet()
        )
        val errors = GrammarValidator.validate(grammar, setOf("ID"))
        assertTrue(errors.any { error ->
            error.severity == Severity.WARNING && "Duplicate" in error.message
        })
    }

    @Test
    fun `operator in precedence table but not declared as token produces error`() {
        val content = """
            %token ID
            %left OP_GHOST
            %%
            program : ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        val errors = GrammarValidator.validate(grammar, setOf("ID"))
        assertTrue(errors.any { error ->
            error.severity == Severity.ERROR &&
                "OP_GHOST" in error.message &&
                "precedence" in error.message
        })
    }

    @Test
    fun `operator appearing in two precedence levels produces error`() {
        val nonTerminal = Symbol.NonTerminal("program")
        val terminalId = Symbol.Terminal("ID")
        val terminalPlus = Symbol.Terminal("OP_PLUS")
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminal),
            productions = listOf(Production(1, nonTerminal, listOf(terminalId))),
            startSymbol = nonTerminal,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT),
                PrecedenceLevel(1, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val errors = GrammarValidator.validate(grammar, setOf("ID", "OP_PLUS"))
        assertTrue(errors.any { error ->
            error.severity == Severity.ERROR &&
                "OP_PLUS" in error.message &&
                "level 0" in error.message &&
                "level 1" in error.message
        })
    }

    @Test
    fun `operator in many precedence levels is reported only once`() {
        val nonTerminal = Symbol.NonTerminal("program")
        val terminalId = Symbol.Terminal("ID")
        val terminalPlus = Symbol.Terminal("OP_PLUS")
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminal),
            productions = listOf(Production(1, nonTerminal, listOf(terminalId))),
            startSymbol = nonTerminal,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT),
                PrecedenceLevel(1, setOf(terminalPlus), Associativity.LEFT),
                PrecedenceLevel(2, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val errors = GrammarValidator.validate(grammar, setOf("ID", "OP_PLUS"))
        val duplicateLevelErrors = errors.filter { error ->
            error.severity == Severity.ERROR &&
                "OP_PLUS" in error.message &&
                "multiple precedence levels" in error.message
        }
        assertEquals(1, duplicateLevelErrors.size)
    }
}
