package org.compiler.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppState {

    // El programa que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(DEFAULT_PROGRAM)

    // Ruta del archivo abierto, o null si nunca se abrio ni se guardo uno.
    // La escribe el FileMenu al abrir y al guardar; la lee la barra de titulo.
    var sourceFilePath by mutableStateOf<String?>(null)

    // Banner para lo que NO es error del programa del usuario: no se pudo leer el
    // archivo, o el compilador tuvo un bug. Los errores del programa van a la lista
    // de errores, que llega en la Fase 7.
    var errorMessage by mutableStateOf<String?>(null)

    // Este SI queda con `private set`, porque no es un dato: es el estado de una
    // operacion en curso. Lo prende markRunning() y lo apaga el `finally` de
    // onCompile(), y ese par no se puede romper desde afuera.
    var isRunning by mutableStateOf(false)
        private set

    // Deshabilita el boton de inmediato. La GUI llama a esto en el hilo de UI ANTES
    // de despachar la compilacion a un hilo de fondo, asi un doble clic no puede
    // arrancar dos corridas.
    fun markRunning() {
        isRunning = true
    }

    fun onCompile() {
        // Placeholder hasta la Fase 7, cuando exista CompilerPipeline.
        // El try/finally ya esta puesto para que el boton se rehabilite igual.
        try {
            errorMessage = null
        } finally {
            isRunning = false
        }
    }

    private companion object {
        private val DEFAULT_PROGRAM = """
            let mensaje: string = "Hola Compiscript";
            print(mensaje);
        """.trimIndent()
    }
}
