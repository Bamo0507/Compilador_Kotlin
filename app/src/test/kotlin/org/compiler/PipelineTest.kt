package org.compiler

import org.compiler.frontend.syntaxAnalyzer.runtime.Pipeline
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineTest {

    // A small program with two var_decls: enough to exercise the parser without
    // pulling in cadenas.txt (which has an intentional error in it).
    private val simpleInput = """
        let x = 1;
        let y = 2 + 3;
    """.trimIndent()

    private fun readYalex(): String =
        File("src/main/resources/parser_test.yal").readText()

    private fun readYalp(): String =
        File("src/main/resources/parser.yalp").readText()

    @Test
    fun `runFull with SLR1 accepts a small valid program and populates only SLR fields`() {
        val result = Pipeline.runFull(readYalex(), readYalp(), simpleInput, ParserMethod.SLR1)

        assertEquals(ParserMethod.SLR1, result.method)
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "Expected Accepted, got ${result.parseResult}"
        )

        assertNotNull(result.slr1Automaton)
        assertNotNull(result.slr1Table)
        assertNull(result.leftRecursionRewrittenGrammar)
        assertNull(result.lalr1Automaton)
        assertNull(result.lalr1Table)
        assertNull(result.ll1Table)
    }

    @Test
    fun `runFull with LALR1 accepts a small valid program and populates both automata`() {
        val result = Pipeline.runFull(readYalex(), readYalp(), simpleInput, ParserMethod.LALR1)

        assertEquals(ParserMethod.LALR1, result.method)
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "Expected Accepted, got ${result.parseResult}"
        )

        // LALR derives its automaton from SLR's, so both should be populated.
        assertNotNull(result.slr1Automaton)
        assertNotNull(result.lalr1Automaton)
        assertNotNull(result.lalr1Table)
        assertNull(result.leftRecursionRewrittenGrammar)
        assertNull(result.slr1Table)
        assertNull(result.ll1Table)
    }

    @Test
    fun `runFull with LL1 on parser_dot_yalp builds a conflict-laden table and still returns a result`() {
        val result = Pipeline.runFull(readYalex(), readYalp(), simpleInput, ParserMethod.LL1)

        assertEquals(ParserMethod.LL1, result.method)

        // parser.yalp is not LL(1): the table must report conflicts but the
        // pipeline must not throw.
        assertNotNull(result.ll1Table)
        assertFalse(result.ll1Table!!.isLL1, "parser.yalp must produce LL(1) conflicts")
        assertTrue(result.ll1Table!!.conflicts.isNotEmpty())

        assertNotNull(result.leftRecursionRewrittenGrammar)
        assertNull(result.slr1Automaton)
        assertNull(result.slr1Table)
        assertNull(result.lalr1Automaton)
        assertNull(result.lalr1Table)
    }

    @Test
    fun `runFull throws when grammar declares a token the lexer does not produce`() {
        val miniYalex = """
            let digit = ['0'-'9']
            let id = digit+

            rule tokens =
              | id   { ID }
              | eof  { EOF }
        """.trimIndent()

        // The grammar declares UNDECLARED_TOKEN, which the lexer never produces.
        // GrammarValidator must flag this and the pipeline must surface the error.
        val miniYalp = """
            %token ID UNDECLARED_TOKEN
            %%
            program : ID
                    | UNDECLARED_TOKEN
                    ;
        """.trimIndent()

        val thrown = assertFailsWith<IllegalStateException> {
            Pipeline.runFull(miniYalex, miniYalp, "1", ParserMethod.SLR1)
        }
        assertTrue(
            thrown.message!!.contains("UNDECLARED_TOKEN"),
            "Error message should name the offending token: ${thrown.message}"
        )
    }
}
