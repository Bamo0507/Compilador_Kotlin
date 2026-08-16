package org.compiler

import org.compiler.gui.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    @Test
    fun `arranca con el programa por defecto y sin archivo`() {
        val state = AppState()

        assertTrue(state.sourceContent.isNotBlank())
        assertNull(state.sourceFilePath)
        assertNull(state.errorMessage)
        assertFalse(state.isRunning)
    }

    @Test
    fun `el editor escribe el contenido sin tocar la ruta`() {
        val state = AppState()

        state.sourceContent = "print(1);"

        assertEquals("print(1);", state.sourceContent)
        assertNull(state.sourceFilePath)
    }

    // markRunning existe para deshabilitar el boton en el hilo de UI ANTES de que
    // arranque el hilo de fondo. Sin el, un doble clic rapido arranca dos corridas.
    @Test
    fun `markRunning deshabilita el boton de inmediato`() {
        val state = AppState()

        state.markRunning()

        assertTrue(state.isRunning)
    }

    @Test
    fun `onCompile siempre deja isRunning en false`() {
        val state = AppState()
        state.markRunning()

        state.onCompile()

        assertFalse(state.isRunning)
        assertNull(state.errorMessage)
    }

    @Test
    fun `dos instancias no comparten estado`() {
        val primera = AppState()
        val segunda = AppState()

        primera.sourceContent = "print(1);"

        assertTrue(segunda.sourceContent.contains("Hola Compiscript"))
    }
}
