package org.compiler

import org.compiler.gui.state.AppState
import org.compiler.samples.SqlScript
import org.compiler.samples.SqlScripts
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El estado de la ventana.
 *
 * Ninguna prueba usa `DataDirectory.DEFAULT`: todas le pasan un @TempDir, que es lo
 * que evita que la bateria escriba sobre el `data/` de verdad. Esa es la razon de
 * que el directorio sea un parametro del constructor.
 */
class AppStateTest {

    private fun state(temp: File) = AppState(DataDirectory(temp))

    // ── Arranque ───────────────────────────────────────────────────────────

    @Test
    fun `arranca con un ejemplo cargado y sin archivo`(@TempDir temp: File) {
        val state = state(temp)

        // El texto es el del ejemplo, cualquiera que la carpeta ofrezca primero: lo
        // que se afirma es que el editor y el selector no se contradicen.
        val script = assertNotNull(state.selectedScript)
        assertEquals(script.source, state.sourceContent)

        assertNull(state.sourceFilePath)
        assertNull(state.errorMessage)
        assertNull(state.result)
        assertFalse(state.isRunning)
    }

    @Test
    fun `dos instancias no comparten estado`(@TempDir temp: File) {
        val first = state(temp)
        val second = state(temp)

        first.onSourceChanged("SELECT * FROM users;")

        assertEquals(second.selectedScript?.source, second.sourceContent)
    }

    // ── Edicion ────────────────────────────────────────────────────────────

    @Test
    fun `el editor escribe el contenido sin tocar la ruta`(@TempDir temp: File) {
        val state = state(temp)

        state.onSourceChanged("SELECT * FROM users;")

        assertEquals("SELECT * FROM users;", state.sourceContent)
        assertNull(state.sourceFilePath)
    }

    /**
     * Editar suelta el ejemplo.
     *
     * Sin esto el selector seguiria mostrando el nombre de un script que ya no es
     * lo que esta en el editor, que es la peor clase de mentira en una interfaz:
     * una que parece informacion.
     */
    @Test
    fun `editar el texto suelta el ejemplo seleccionado`(@TempDir temp: File) {
        val state = state(temp)

        state.onSourceChanged("SELECT 1 FROM users;")

        assertNull(state.selectedScript)
    }

    // Volver a escribir EXACTAMENTE el texto del ejemplo no es un caso que haya que
    // detectar; lo que importa es que un cambio real lo suelte y no al reves.
    @Test
    fun `escribir el mismo texto del ejemplo no lo suelta`(@TempDir temp: File) {
        val state = state(temp)
        val script = assertNotNull(state.selectedScript)

        state.onSourceChanged(script.source)

        assertEquals(script, state.selectedScript)
    }

    @Test
    fun `cargar un ejemplo lo pone en el editor`(@TempDir temp: File) {
        val state = state(temp)
        val script = SqlScript("demo", "Demo", "SELECT * FROM users;")

        state.loadScript(script)

        assertEquals("SELECT * FROM users;", state.sourceContent)
        assertEquals(script, state.selectedScript)
    }

    // Los errores y las filas de la corrida anterior son de OTRO script: dejarlos a
    // la vista al lado de un texto nuevo hace creer que corresponden.
    @Test
    fun `cargar un ejemplo descarta el resultado anterior`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged("SELECT * FROM users;")
        state.onCompile()
        assertNotNull(state.result)

        state.loadScript(assertNotNull(SqlScripts.byId(SqlScripts.BLANK_ID)))

        assertNull(state.result)
        assertNull(state.errorMessage)
        assertNull(state.highlightedLine)
    }

    @Test
    fun `abrir un archivo no corresponde a ningun ejemplo`(@TempDir temp: File) {
        val state = state(temp)

        state.loadFromFile("SELECT * FROM users;", "/tmp/consulta.sql")

        assertNull(state.selectedScript)
        assertEquals("/tmp/consulta.sql", state.sourceFilePath)
        assertEquals("SELECT * FROM users;", state.sourceContent)
    }

    // ── Compilacion ────────────────────────────────────────────────────────

    // markRunning existe para deshabilitar el boton en el hilo de UI ANTES de que
    // arranque el hilo de fondo. Sin el, un doble clic rapido arranca dos corridas.
    @Test
    fun `markRunning deshabilita el boton de inmediato`(@TempDir temp: File) {
        val state = state(temp)

        state.markRunning()

        assertTrue(state.isRunning)
    }

    @Test
    fun `onCompile siempre deja isRunning en false`(@TempDir temp: File) {
        val state = state(temp)
        state.markRunning()

        state.onCompile()

        assertFalse(state.isRunning)
        assertNull(state.errorMessage)
    }

    @Test
    fun `un script que escribe deja los archivos en el directorio que se le dio`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged(
            """
            CREATE TABLE socios (id INT PRIMARY KEY, nombre VARCHAR(20));
            INSERT INTO socios (id, nombre) VALUES (1, 'ana');
            """.trimIndent()
        )

        state.onCompile()

        val result = assertNotNull(state.result)
        assertTrue(result.errors.isEmpty(), "errores: ${result.errors.map { it.message }}")
        assertTrue(result.written.contains("socios.csv"), "escritos: ${result.written}")
        assertTrue(File(temp, "socios.csv").exists())
    }

    @Test
    fun `una consulta deja sus filas en el resultado`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged(
            """
            CREATE TABLE socios (id INT PRIMARY KEY, nombre VARCHAR(20));
            INSERT INTO socios (id, nombre) VALUES (1, 'ana');
            SELECT nombre FROM socios;
            """.trimIndent()
        )

        state.onCompile()

        val rows = assertNotNull(state.result).results.single().rows
        assertEquals(1, rows.size)
    }

    // Un error del PROGRAMA no es un error de la aplicacion: va a la lista de
    // errores y el banner se queda vacio.
    @Test
    fun `onCompile guarda los errores del script del usuario`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged("SELECT * FROM no_existe;")

        state.onCompile()

        val result = assertNotNull(state.result)
        assertTrue(result.semanticErrors.isNotEmpty())
        assertNull(state.errorMessage)
    }

    // Todo o nada: una sentencia que falla deja el directorio intacto, y el estado
    // tiene que poder decirlo.
    @Test
    fun `un script con errores no escribe nada`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged(
            """
            CREATE TABLE socios (id INT PRIMARY KEY, nombre VARCHAR(20));
            INSERT INTO socios (id, nombre) VALUES (1, 'ana');
            SELECT * FROM no_existe;
            """.trimIndent()
        )

        state.onCompile()

        assertTrue(assertNotNull(state.result).written.isEmpty())
        assertFalse(File(temp, "socios.csv").exists())
    }

    @Test
    fun `un script que no pasa el semantico no deja ejecucion`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged("SELECT * FROM no_existe;")

        state.onCompile()

        assertNull(state.result?.execution)
    }

    @Test
    fun `un script sin sintaxis valida no tumba la aplicacion`(@TempDir temp: File) {
        val state = state(temp)
        state.onSourceChanged("SELECT FROM;")

        state.onCompile()

        assertNull(state.errorMessage)
        assertTrue(assertNotNull(state.result).errors.isNotEmpty())
    }

    @Test
    fun `compilar de nuevo limpia la linea resaltada`(@TempDir temp: File) {
        val state = state(temp)
        state.highlightedLine = 7

        state.onCompile()

        assertNull(state.highlightedLine)
    }
}
