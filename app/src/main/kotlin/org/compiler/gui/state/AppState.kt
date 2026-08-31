package org.compiler.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.compiler.runtime.CompilerPipeline
import org.compiler.runtime.models.CompilationResult
import org.compiler.samples.SampleProgram
import org.compiler.samples.SamplePrograms

class AppState {

    // El programa que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(SamplePrograms.default.source)
        private set

    // El ejemplo cargado desde el selector, o null si el texto ya no coincide con
    // ninguno porque el usuario lo edito.
    var selectedSample by mutableStateOf<SampleProgram?>(SamplePrograms.default)
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
        if (selectedSample?.source != newSource) {
            selectedSample = null
        }
    }

    // Carga un ejemplo en el editor. Descarta el resultado anterior a proposito: los
    // errores y la salida son de OTRO programa, y dejarlos a la vista confunde.
    fun loadSample(sample: SampleProgram) {
        sourceContent = sample.source
        selectedSample = sample
        result = null
        errorMessage = null
        highlightedLine = null
    }

    // La usa el FileMenu al abrir un archivo: el contenido viene de disco, asi que
    // no corresponde a ningun ejemplo.
    fun loadFromFile(content: String, path: String) {
        sourceContent = content
        selectedSample = null
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
            result = CompilerPipeline.compile(sourceContent)
        } catch (throwable: Throwable) {
            // Throwable y no Exception: un StackOverflowError de un programa muy
            // anidado debe salir como banner, no matar la ventana.
            //
            // El pipeline nunca lanza por errores del usuario: esos van a la lista
            // de errores. Si algo llega aqui, es un bug del compilador.
            result = null
            errorMessage = throwable.message
                ?: throwable::class.simpleName
                ?: "Error desconocido del compilador"
        } finally {
            isRunning = false
        }
    }
}
