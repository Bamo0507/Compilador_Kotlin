package org.compiler.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.compiler.runtime.CompilerPipeline
import org.compiler.runtime.models.CompilationResult

class AppState {

    // El programa que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(DEFAULT_PROGRAM)

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

    private companion object {
        // Ejercita todo lo visible del lenguaje: clases, herencia, `this`,
        // constructor, recursion, arreglos, foreach, continue, y una expresion que
        // el plegado de la Fase 4 resuelve. Compila y ejecuta sin un solo error.
        private val DEFAULT_PROGRAM = """
            // Programa de demostración de Compiscript

            class Animal {
              let nombre: string;

              function constructor(nombre: string) {
                this.nombre = nombre;
              }

              function hablar(): string {
                return this.nombre + " hace ruido.";
              }
            }

            class Perro : Animal {
              function hablar(): string {
                return this.nombre + " ladra.";
              }
            }

            function factorial(n: integer): integer {
              if (n <= 1) { return 1; }
              return n * factorial(n - 1);
            }

            let perro: Perro = new Perro("Toby");
            print(perro.hablar());

            let notas: integer[] = [90, 85, 100];
            foreach (nota in notas) {
              if (nota < 60) { continue; }
              print(nota);
            }

            print(factorial(5));
            print(3 + 5 * 2);
        """.trimIndent()
    }
}
