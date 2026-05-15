package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.GrammarRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarRewriterTest {

    @Test
    fun `grammar without left recursion is not modified`() {
        val content = """
            %token ID PLUS
            %%
            program : ID ;
            program : ID PLUS ID ;
        """.trimIndent()
        val grammar = YalpReader.parse(content)
        val rewritten = GrammarRewriter.eliminateLeftRecursion(grammar)

        assertTrue(rewritten.nonTerminals.none { nonTerminal -> nonTerminal.name.endsWith("_prime") })
        assertEquals(grammar.productions.size, rewritten.productions.size)
    }

    @Test
    fun `immediate left recursion is eliminated`() {
        val expr = Symbol.NonTerminal("expr")
        val term = Symbol.NonTerminal("term")
        val PLUS = Symbol.Terminal("PLUS")
        val TERM = Symbol.Terminal("TERM")
        val grammar = Grammar(
            terminals = setOf(PLUS, TERM),
            nonTerminals = setOf(expr, term),
            productions = listOf(
                Production(1, expr, listOf(expr, PLUS, term)),
                Production(2, expr, listOf(term)),
                Production(3, term, listOf(TERM))
            ),
            startSymbol = expr,
            ignoredTokens = emptySet()
        )

        val rewritten = GrammarRewriter.eliminateLeftRecursion(grammar)

        assertTrue(rewritten.nonTerminals.any { nonTerminal -> nonTerminal.name == "expr_prime" })
        assertTrue(rewritten.productions.none { production ->
            production.head == expr && production.body.firstOrNull() == expr
        })
        assertTrue(rewritten.productions.any { production ->
            production.head.name == "expr_prime" && production.body == listOf(Symbol.Epsilon)
        })
    }

    @Test
    fun `multiple recursive productions produce one base and multiple prime productions`() {
        val expr = Symbol.NonTerminal("expr")
        val term = Symbol.NonTerminal("term")
        val PLUS = Symbol.Terminal("PLUS")
        val MINUS = Symbol.Terminal("MINUS")
        val TERM = Symbol.Terminal("TERM")
        val grammar = Grammar(
            terminals = setOf(PLUS, MINUS, TERM),
            nonTerminals = setOf(expr, term),
            productions = listOf(
                Production(1, expr, listOf(expr, PLUS, term)),
                Production(2, expr, listOf(expr, MINUS, term)),
                Production(3, expr, listOf(term)),
                Production(4, term, listOf(TERM))
            ),
            startSymbol = expr,
            ignoredTokens = emptySet()
        )

        val rewritten = GrammarRewriter.eliminateLeftRecursion(grammar)

        // one base: expr -> term expr_prime
        assertEquals(1, rewritten.productions.count { production -> production.head == expr })
        // two recursive (PLUS, MINUS) + one epsilon = three productions for expr_prime
        assertEquals(3, rewritten.productions.count { production -> production.head.name == "expr_prime" })
    }

    @Test
    fun `indirect left recursion through two non-terminals is eliminated`() {
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val terminalX = Symbol.Terminal("x")
        val terminalA = Symbol.Terminal("a")
        val terminalY = Symbol.Terminal("y")
        val terminalB = Symbol.Terminal("b")
        val grammar = Grammar(
            terminals = setOf(terminalX, terminalA, terminalY, terminalB),
            nonTerminals = setOf(nonTerminalA, nonTerminalB),
            productions = listOf(
                Production(1, nonTerminalA, listOf(nonTerminalB, terminalX)),
                Production(2, nonTerminalA, listOf(terminalA)),
                Production(3, nonTerminalB, listOf(nonTerminalA, terminalY)),
                Production(4, nonTerminalB, listOf(terminalB))
            ),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )

        val rewritten = GrammarRewriter.eliminateLeftRecursion(grammar)

        assertTrue(rewritten.nonTerminals.any { nonTerminal -> nonTerminal.name == "B_prime" })
        assertTrue(rewritten.productions.none { production ->
            production.head == nonTerminalB && production.body.firstOrNull() == nonTerminalB
        })
        assertTrue(rewritten.productions.any { production ->
            production.head.name == "B_prime" && production.body == listOf(Symbol.Epsilon)
        })
    }

    @Test
    fun `indirect left recursion through three non-terminals is eliminated`() {
        val nonTerminalA = Symbol.NonTerminal("A")
        val nonTerminalB = Symbol.NonTerminal("B")
        val nonTerminalC = Symbol.NonTerminal("C")
        val terminalX = Symbol.Terminal("x")
        val terminalA = Symbol.Terminal("a")
        val terminalY = Symbol.Terminal("y")
        val terminalB = Symbol.Terminal("b")
        val terminalZ = Symbol.Terminal("z")
        val terminalC = Symbol.Terminal("c")
        val grammar = Grammar(
            terminals = setOf(terminalX, terminalA, terminalY, terminalB, terminalZ, terminalC),
            nonTerminals = setOf(nonTerminalA, nonTerminalB, nonTerminalC),
            productions = listOf(
                Production(1, nonTerminalA, listOf(nonTerminalB, terminalX)),
                Production(2, nonTerminalA, listOf(terminalA)),
                Production(3, nonTerminalB, listOf(nonTerminalC, terminalY)),
                Production(4, nonTerminalB, listOf(terminalB)),
                Production(5, nonTerminalC, listOf(nonTerminalA, terminalZ)),
                Production(6, nonTerminalC, listOf(terminalC))
            ),
            startSymbol = nonTerminalA,
            ignoredTokens = emptySet()
        )

        val rewritten = GrammarRewriter.eliminateLeftRecursion(grammar)

        assertTrue(rewritten.nonTerminals.any { nonTerminal -> nonTerminal.name == "C_prime" })
        assertTrue(rewritten.productions.none { production ->
            production.head == nonTerminalC && production.body.firstOrNull() == nonTerminalC
        })
        assertTrue(rewritten.productions.any { production ->
            production.head.name == "C_prime" && production.body == listOf(Symbol.Epsilon)
        })
    }
}
