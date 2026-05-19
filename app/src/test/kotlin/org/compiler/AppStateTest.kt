package org.compiler

import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import org.compiler.gui.state.AppState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStateTest {

    private val simpleInput = """
        let x = 1;
        let y = 2 + 3;
    """.trimIndent()

    private fun appState(): AppState =
        AppState(
            initialYalexContent = File("src/main/resources/parser_test.yal").readText(),
            initialYalpContent = File("src/main/resources/parser.yalp").readText(),
            initialInputContent = simpleInput
        )

    @Test
    fun `onPlay populates pipelineResult`() {
        val state = appState()

        state.onPlay()

        assertFalse(state.isRunning)
        assertNull(state.errorMessage)
        assertNotNull(state.pipelineResult)
        assertEquals(ParserMethod.SLR1, state.pipelineResult!!.method)
        assertTrue(state.pipelineResult!!.parseResult is ParseResult.Accepted)
    }

    @Test
    fun `default state can run onPlay`() {
        val state = AppState()

        state.onPlay()

        assertNull(state.errorMessage)
        assertNotNull(state.pipelineResult)
    }

    @Test
    fun `changeMethod reparses using cached artifacts`() {
        val state = appState()
        state.onPlay()
        val before = state.pipelineResult!!

        state.changeMethod(ParserMethod.LALR1)

        val after = state.pipelineResult!!
        assertEquals(ParserMethod.LALR1, state.selectedMethod)
        assertEquals(ParserMethod.LALR1, after.method)
        assertTrue(after.parseResult is ParseResult.Accepted)
        assertSame(before.lexerResult, after.lexerResult)
        assertSame(before.slr1Automaton, after.slr1Automaton)
        assertSame(before.lalr1Automaton, after.lalr1Automaton)
        assertSame(before.ll1Table, after.ll1Table)
        assertSame(before.slr1Table, after.slr1Table)
        assertSame(before.lalr1Table, after.lalr1Table)
    }
}
