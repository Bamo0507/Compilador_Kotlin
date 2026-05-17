package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.LeftRecursionRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.PrecedenceRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.FollowSetComputer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// Integration tests for the LL(1) builder against the actual project grammar
// (parser.yalp). Goal: document, with hard numbers, why the grammar that the
// project ships is not LL(1) and how far each rewriting stage takes us.
class LL1ProjectGrammarTest {

    private fun readGrammar() =
        YalpReader.parse(File("src/main/resources/parser.yalp").readText())

    @Test
    fun `raw parser dot yalp is not LL(1) -- ambiguous expr plus left recursion`() {
        val grammar = readGrammar()
        val firstSets = FirstSetComputer.compute(grammar)
        val followSets = FollowSetComputer.compute(grammar, firstSets)
        val table = LL1TableBuilder.build(grammar, firstSets, followSets)

        println("[LL1ProjectGrammarTest] raw grammar conflicts = ${table.conflicts.size}, isLL1 = ${table.isLL1}")
        for (conflict in table.conflicts.take(5)) {
            println("  - ${conflict.nonTerminal.name} on '${conflict.terminal.name}': ${conflict.productions.size} productions")
        }

        assertTrue(
            table.conflicts.isNotEmpty(),
            "Raw parser.yalp is expected to have LL(1) conflicts -- it is intentionally ambiguous"
        )
    }

    @Test
    fun `after precedence rewriting expr is disambiguated but left recursion remains`() {
        val grammar = readGrammar()
        val rewritten = PrecedenceRewriter.rewrite(grammar)

        val firstSets = FirstSetComputer.compute(rewritten)
        val followSets = FollowSetComputer.compute(rewritten, firstSets)
        val table = LL1TableBuilder.build(rewritten, firstSets, followSets)

        println("[LL1ProjectGrammarTest] precedence-rewritten conflicts = ${table.conflicts.size}, isLL1 = ${table.isLL1}")

        // Precedence rewriting fixes expr ambiguity but does not touch left recursion in
        // top_list, member_list, stmt_list, id_list, expr_list. So conflicts should remain.
        assertTrue(
            table.conflicts.isNotEmpty(),
            "Precedence-rewritten grammar still has left recursion -- LL(1) conflicts expected"
        )
    }

    @Test
    fun `after precedence rewriting plus left recursion elimination -- record conflict count`() {
        val grammar = readGrammar()
        val precedenceRewritten = PrecedenceRewriter.rewrite(grammar)
        val leftRecursionRewritten = LeftRecursionRewriter.eliminateLeftRecursion(precedenceRewritten)

        val firstSets = FirstSetComputer.compute(leftRecursionRewritten)
        val followSets = FollowSetComputer.compute(leftRecursionRewritten, firstSets)
        val table = LL1TableBuilder.build(leftRecursionRewritten, firstSets, followSets)

        println("[LL1ProjectGrammarTest] fully rewritten conflicts = ${table.conflicts.size}, isLL1 = ${table.isLL1}")
        for (conflict in table.conflicts.take(10)) {
            println("  - ${conflict.nonTerminal.name} on '${conflict.terminal.name}': ${conflict.productions.size} productions")
        }

        // No hard assertion on count: this test exists to surface the gap. Whatever the
        // number is, it is the answer to "how far is parser.yalp from being LL(1)?"
        // If the number drops to zero in the future, the assertion below should be
        // updated to reflect that the grammar became LL(1).
    }
}
