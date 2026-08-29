package org.compiler

import org.compiler.gui.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    @Test
    fun `arranca con el programa por defecto y sin archivo`() {
        val state = AppState()

        assertTrue(state.sourceContent.isNotBlank())
        assertNull(state.sourceFilePath)
        assertNull(state.errorMessage)
        assertNull(state.result)
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

        assertTrue(segunda.sourceContent.contains("Compiscript"))
    }

    // ── El resultado de compilar ───────────────────────────────────────────

    // El criterio del ticket: es la demostracion del dia de la presentacion, asi que
    // no puede tener un solo error.
    @Test
    fun `el programa por defecto compila sin errores y ejecuta`() {
        val state = AppState()

        state.onCompile()

        val result = state.result
        assertNotNull(result)
        assertTrue(result.errors.isEmpty(), "errores: ${result.errors.map { it.message }}")

        val execution = result.execution
        assertNotNull(execution)
        assertNull(execution.runtimeError)
        assertEquals(
            listOf("Toby ladra.", "90", "85", "100", "120", "13"),
            execution.output
        )
    }

    @Test
    fun `onCompile guarda los errores del programa del usuario`() {
        val state = AppState()
        state.sourceContent = "let x: integer = \"texto\";"

        state.onCompile()

        val result = state.result
        assertNotNull(result)
        assertTrue(result.semanticErrors.isNotEmpty())

        // Un error del PROGRAMA no es un error de la aplicacion: el banner sigue
        // vacio y el mensaje va a la lista.
        assertNull(state.errorMessage)
    }

    @Test
    fun `un programa que no ejecuta deja execution en null`() {
        val state = AppState()
        state.sourceContent = "let x: integer = true;"

        state.onCompile()

        assertNull(state.result?.execution)
    }

    // Una recursion infinita se vuelve RuntimeError en la Fase 6, asi que llega como
    // salida de ejecucion y no mata la ventana.
    @Test
    fun `una recursion infinita no tumba la aplicacion`() {
        val state = AppState()
        state.sourceContent = "function f(): integer { return f(); }\nprint(f());"

        state.onCompile()

        assertNull(state.errorMessage)
        assertNotNull(state.result?.execution?.runtimeError)
    }

    @Test
    fun `compilar de nuevo limpia la linea resaltada`() {
        val state = AppState()
        state.highlightedLine = 7

        state.onCompile()

        assertNull(state.highlightedLine)
    }
}
