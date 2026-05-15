package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.PrecedenceRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Associativity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.PrecedenceLevel
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrecedenceRewriterTest {

    private val nonTerminalExpr = Symbol.NonTerminal("expr")
    private val terminalId = Symbol.Terminal("ID")
    private val terminalLParen = Symbol.Terminal("LPAREN")
    private val terminalRParen = Symbol.Terminal("RPAREN")
    private val terminalPlus = Symbol.Terminal("OP_PLUS")
    private val terminalMinus = Symbol.Terminal("OP_MINUS")
    private val terminalTimes = Symbol.Terminal("OP_TIMES")
    private val terminalNot = Symbol.Terminal("OP_NOT")
    private val terminalAssign = Symbol.Terminal("OP_ASSIGN")

    @Test
    fun `empty precedence table returns grammar unchanged`() {
        val grammar = Grammar(
            terminals = setOf(terminalId),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(Production(1, nonTerminalExpr, listOf(terminalId))),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet()
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        assertEquals(grammar, rewritten)
    }

    @Test
    fun `classic cascade with PLUS and TIMES produces Dragon Book canonical form`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus, terminalTimes),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(nonTerminalExpr, terminalTimes, nonTerminalExpr)),
                Production(3, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT),
                PrecedenceLevel(1, setOf(terminalTimes), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead
        val exprLvl0 = Symbol.NonTerminal("expr_lvl0")
        val exprLvl1 = Symbol.NonTerminal("expr_lvl1")
        val exprAtom = Symbol.NonTerminal("expr_atom")

        assertTrue(exprLvl0 in rewritten.nonTerminals)
        assertTrue(exprLvl1 in rewritten.nonTerminals)
        assertTrue(exprAtom in rewritten.nonTerminals)

        assertEquals(listOf(listOf<Symbol>(exprLvl0)), byHead[nonTerminalExpr]?.map { it.body })
        assertEquals(
            listOf(
                listOf<Symbol>(exprLvl0, terminalPlus, exprLvl1),
                listOf<Symbol>(exprLvl1)
            ),
            byHead[exprLvl0]?.map { it.body }
        )
        assertEquals(
            listOf(
                listOf<Symbol>(exprLvl1, terminalTimes, exprAtom),
                listOf<Symbol>(exprAtom)
            ),
            byHead[exprLvl1]?.map { it.body }
        )
        assertEquals(listOf(listOf<Symbol>(terminalId)), byHead[exprAtom]?.map { it.body })
    }

    @Test
    fun `NOT unary prefix produces right-recursive level regardless of associativity`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalNot),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(terminalNot, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalNot), Associativity.RIGHT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead
        val exprLvl0 = Symbol.NonTerminal("expr_lvl0")
        val exprAtom = Symbol.NonTerminal("expr_atom")

        assertEquals(
            listOf(
                listOf<Symbol>(terminalNot, exprLvl0),
                listOf<Symbol>(exprAtom)
            ),
            byHead[exprLvl0]?.map { it.body }
        )
    }

    @Test
    fun `ASSIGN right-associative binary produces inverted body order`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalAssign),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalAssign, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalAssign), Associativity.RIGHT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead
        val exprLvl0 = Symbol.NonTerminal("expr_lvl0")
        val exprAtom = Symbol.NonTerminal("expr_atom")

        assertEquals(
            listOf(
                listOf<Symbol>(exprAtom, terminalAssign, exprLvl0),
                listOf<Symbol>(exprAtom)
            ),
            byHead[exprLvl0]?.map { it.body }
        )
    }

    @Test
    fun `parenthesized production falls to atom level and references original head`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus, terminalLParen, terminalRParen),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalLParen, nonTerminalExpr, terminalRParen)),
                Production(3, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead
        val exprAtom = Symbol.NonTerminal("expr_atom")
        val atomBodies = byHead[exprAtom]?.map { it.body } ?: emptyList()

        assertTrue(atomBodies.contains(listOf<Symbol>(terminalLParen, nonTerminalExpr, terminalRParen)))
        assertTrue(atomBodies.contains(listOf<Symbol>(terminalId)))
    }

    @Test
    fun `non-terminal without operator productions is passed through unchanged`() {
        val nonTerminalStmt = Symbol.NonTerminal("stmt")
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminalExpr, nonTerminalStmt),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId)),
                Production(3, nonTerminalStmt, listOf(nonTerminalExpr))
            ),
            startSymbol = nonTerminalStmt,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead

        assertEquals(listOf(listOf<Symbol>(nonTerminalExpr)), byHead[nonTerminalStmt]?.map { it.body })
        assertEquals(
            listOf(listOf<Symbol>(Symbol.NonTerminal("expr_lvl0"))),
            byHead[nonTerminalExpr]?.map { it.body }
        )
    }

    @Test
    fun `header maps original head to first level and keeps start symbol`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)

        assertEquals(nonTerminalExpr, rewritten.startSymbol)
        assertEquals(
            listOf(listOf<Symbol>(Symbol.NonTerminal("expr_lvl0"))),
            rewritten.productionsByHead[nonTerminalExpr]?.map { it.body }
        )
    }

    @Test
    fun `synthetic non-terminal name collision uses numeric suffix`() {
        val exprLvl0Existing = Symbol.NonTerminal("expr_lvl0")
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminalExpr, exprLvl0Existing),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId)),
                Production(3, exprLvl0Existing, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)

        assertTrue(Symbol.NonTerminal("expr_lvl0_2") in rewritten.nonTerminals)
        assertTrue(exprLvl0Existing in rewritten.nonTerminals)
    }

    @Test
    fun `multiple operators in same level produce one cascade each plus fallthrough`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus, terminalMinus),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(nonTerminalExpr, terminalMinus, nonTerminalExpr)),
                Production(3, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus, terminalMinus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)
        val byHead = rewritten.productionsByHead
        val exprLvl0 = Symbol.NonTerminal("expr_lvl0")
        val exprAtom = Symbol.NonTerminal("expr_atom")
        val lvl0Bodies = byHead[exprLvl0]?.map { it.body } ?: emptyList()

        assertTrue(lvl0Bodies.contains(listOf<Symbol>(exprLvl0, terminalPlus, exprAtom)))
        assertTrue(lvl0Bodies.contains(listOf<Symbol>(exprLvl0, terminalMinus, exprAtom)))
        assertTrue(lvl0Bodies.contains(listOf<Symbol>(exprAtom)))
        assertEquals(3, lvl0Bodies.size)
    }

    @Test
    fun `production ids are sequential starting at one`() {
        val grammar = Grammar(
            terminals = setOf(terminalId, terminalPlus),
            nonTerminals = setOf(nonTerminalExpr),
            productions = listOf(
                Production(1, nonTerminalExpr, listOf(nonTerminalExpr, terminalPlus, nonTerminalExpr)),
                Production(2, nonTerminalExpr, listOf(terminalId))
            ),
            startSymbol = nonTerminalExpr,
            ignoredTokens = emptySet(),
            precedenceTable = listOf(
                PrecedenceLevel(0, setOf(terminalPlus), Associativity.LEFT)
            )
        )
        val rewritten = PrecedenceRewriter.rewrite(grammar)

        rewritten.productions.forEachIndexed { index, production ->
            assertEquals(index + 1, production.id)
        }
    }
}
