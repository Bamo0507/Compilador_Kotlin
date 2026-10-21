package org.compiler.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.compiler.runtime.DbmsPipeline
import org.compiler.runtime.models.CompilationResult
import org.compiler.samples.SqlScript
import org.compiler.samples.SqlScripts
import org.compiler.storage.DataDirectory

/**
 * El directorio es un parametro y no `DataDirectory.DEFAULT` escrito adentro por la
 * misma razon que en el resto del DBMS: los tests tienen que poder correr sobre un
 * temporal. Si estuviera fijo, probar `onCompile` escribiria sobre la base real.
 */
class AppState(private val directory: DataDirectory = DataDirectory.DEFAULT) {

    // El script que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(initialScript.source)
        private set

    // El ejemplo cargado desde el selector, o null si el texto ya no coincide con
    // ninguno porque el usuario lo edito.
    var selectedScript by mutableStateOf<SqlScript?>(initialScript)
        private set

    // Ruta del archivo abierto, o null si nunca se abrio ni se guardo uno.
    // La escribe el FileMenu al abrir y al guardar; la lee la barra de titulo.
    var sourceFilePath by mutableStateOf<String?>(null)

    // Banner para lo que NO es error del programa del usuario: no se pudo leer el
    // archivo, o el compilador tuvo un bug. Los errores del programa van a la lista
    // de errores.
    var errorMessage by mutableStateOf<String?>(null)

    // Todo lo que produjo la ultima compilacion. `private set` porque solo lo
    // escribe onCompile(), y siempre en par con isRunning.
    var result by mutableStateOf<CompilationResult?>(null)
        private set

    // La linea a la que salta el editor. La escribe la lista de errores al hacer
    // clic; la lee el CodeEditor para resaltarla y hacer scroll.
    var highlightedLine by mutableStateOf<Int?>(null)

    // Este SI queda con `private set`, porque no es un dato: es el estado de una
    // operacion en curso. Lo prende markRunning() y lo apaga el `finally` de
    // onCompile(), y ese par no se puede romper desde afuera.
    var isRunning by mutableStateOf(false)
        private set

    // ── Edicion ────────────────────────────────────────────────────────────

    // La llama el editor en cada tecla. En cuanto el texto se aparta del ejemplo, el
    // selector deja de afirmar que ese ejemplo es lo que se esta viendo.
    fun onSourceChanged(newSource: String) {
        sourceContent = newSource
        if (selectedScript?.source != newSource) {
            selectedScript = null
        }
    }

    // Carga un ejemplo en el editor. Descarta el resultado anterior a proposito: los
    // errores, las filas y el resumen de cambios son de OTRO script.
    fun loadScript(script: SqlScript) {
        sourceContent = script.source
        selectedScript = script
        result = null
        errorMessage = null
        highlightedLine = null
    }

    // La usa el FileMenu al abrir un archivo: el contenido viene de disco, asi que
    // no corresponde a ningun ejemplo.
    fun loadFromFile(content: String, path: String) {
        sourceContent = content
        selectedScript = null
        sourceFilePath = path
        result = null
        errorMessage = null
        highlightedLine = null
    }

    // ── Compilacion ────────────────────────────────────────────────────────

    // Deshabilita el boton de inmediato. La GUI llama a esto en el hilo de UI ANTES
    // de despachar la compilacion a un hilo de fondo, asi un doble clic no puede
    // arrancar dos corridas.
    fun markRunning() {
        isRunning = true
    }

    fun onCompile() {
        isRunning = true
        errorMessage = null
        highlightedLine = null

        try {
            result = DbmsPipeline.run(sourceContent, directory)
        } catch (throwable: Throwable) {
            // Throwable y no Exception: un StackOverflowError de un script muy
            // anidado debe salir como banner, no matar la ventana.
            //
            // El pipeline nunca lanza por errores del usuario: esos van a la lista
            // de errores. Si algo llega aqui, es un bug del DBMS.
            result = null
            errorMessage = throwable.message
                ?: throwable::class.simpleName
                ?: "Error desconocido del compilador"
        } finally {
            isRunning = false
        }
    }

    private companion object {

        // Con que arranca la ventana: la demo, que se arma su propia base. Abrir el
        // IDE en un editor vacio no ensena nada el dia de la presentacion.
        val initialScript: SqlScript get() = SqlScripts.default
    }
}
