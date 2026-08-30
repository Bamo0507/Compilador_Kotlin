// Ticket 8.1: los programas .cps que DEBEN compilar.
//
// A diferencia de los tests unitarios de cada ticket —que prueban una funcion—,
// estos corren el compilador COMPLETO sobre un programa real, desde el texto hasta
// la ejecucion.
//
// Agregar un caso es agregar un archivo a la carpeta: no hay que tocar este codigo.
package org.compiler

import org.compiler.gui.state.AppState
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramasValidosTest {

    @TestFactory
    fun `cada programa valido compila sin errores`(): List<DynamicTest> =
        programasEn("programas/validos").map { archivo ->
            DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                val resultado = CompilerPipeline.compile(archivo.readText())

                assertTrue(
                    resultado.errors.isEmpty(),
                    "${archivo.name} debería compilar sin errores, pero produjo:\n" +
                        resultado.errors.joinToString("\n") {
                            "  línea ${it.location.line}: ${it.message}"
                        }
                )
            }
        }

    // La salida esperada va en el archivo, en lineas `// SALIDA:`. Un programa sin
    // esas lineas solo tiene que compilar; uno con ellas ademas tiene que imprimir
    // exactamente eso, en ese orden.
    @TestFactory
    fun `cada programa valido imprime su salida anotada`(): List<DynamicTest> =
        programasEn("programas/validos")
            .filter { salidaEsperadaDe(it).isNotEmpty() }
            .map { archivo ->
                DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                    val esperada = salidaEsperadaDe(archivo)
                    val resultado = CompilerPipeline.compile(archivo.readText())

                    val ejecucion = resultado.execution
                    assertNotNull(
                        ejecucion,
                        "${archivo.name} no se ejecutó. Errores:\n" +
                            resultado.errors.joinToString("\n") {
                                "  línea ${it.location.line}: ${it.message}"
                            }
                    )

                    assertNull(
                        ejecucion.runtimeError,
                        "${archivo.name} falló en ejecución: ${ejecucion.runtimeError?.message}"
                    )

                    assertEquals(
                        esperada, ejecucion.output,
                        "La salida de ${archivo.name} no coincide con sus anotaciones // SALIDA:"
                    )
                }
            }

    // Criterio de aceptacion del ticket: demo_completa.cps ES el programa por
    // defecto del IDE. Si alguien cambia uno de los dos y no el otro, la demo de la
    // presentacion deja de estar cubierta por la bateria y nadie se entera.
    @Test
    fun `demo_completa es el programa por defecto del IDE`() {
        val archivo = programasEn("programas/validos").single { it.name == "demo_completa.cps" }

        val programaDelArchivo = archivo.readText()
            .lines()
            .dropWhile { it.startsWith("// SALIDA:") || it.isBlank() }
            .joinToString("\n")
            .trim()

        assertEquals(
            AppState().sourceContent.trim(), programaDelArchivo,
            "demo_completa.cps y el programa por defecto del IDE se desincronizaron"
        )
    }

    companion object {

        // Los .cps viven en src/test/resources, asi que el classpath los encuentra
        // sin rutas absolutas ni suposiciones sobre el directorio de trabajo.
        fun programasEn(carpeta: String): List<File> {
            val url = ProgramasValidosTest::class.java.classLoader.getResource(carpeta)
                ?: error("No se encontró la carpeta de programas '$carpeta'")

            val archivos = File(url.toURI())
                .listFiles { archivo -> archivo.extension == "cps" }
                ?.sortedBy { it.name }
                ?: emptyList()

            // Sin esto, borrar la carpeta por accidente dejaria la bateria en cero
            // tests y en verde, que es la peor forma de fallar.
            assertTrue(archivos.isNotEmpty(), "La carpeta '$carpeta' no tiene programas .cps")

            return archivos
        }

        fun salidaEsperadaDe(archivo: File): List<String> =
            archivo.readLines()
                .filter { it.startsWith("// SALIDA:") }
                .map { it.removePrefix("// SALIDA:").trim() }
    }
}
