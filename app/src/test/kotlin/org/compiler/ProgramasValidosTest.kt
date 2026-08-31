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
import org.compiler.samples.SampleGroup
import org.compiler.samples.SampleProgram
import org.compiler.samples.SamplePrograms
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramasValidosTest {

    @TestFactory
    fun `cada programa valido compila sin errores`(): List<DynamicTest> =
        programasDe(SampleGroup.VALID).map { programa ->
            DynamicTest.dynamicTest(programa.name) {
                val resultado = CompilerPipeline.compile(programa.source)

                assertTrue(
                    resultado.errors.isEmpty(),
                    "${programa.id} debería compilar sin errores, pero produjo:\n" +
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
        programasDe(SampleGroup.VALID)
            .filter { salidaEsperadaDe(it).isNotEmpty() }
            .map { programa ->
                DynamicTest.dynamicTest(programa.name) {
                    val esperada = salidaEsperadaDe(programa)
                    val resultado = CompilerPipeline.compile(programa.source)

                    val ejecucion = resultado.execution
                    assertNotNull(
                        ejecucion,
                        "${programa.id} no se ejecutó. Errores:\n" +
                            resultado.errors.joinToString("\n") {
                                "  línea ${it.location.line}: ${it.message}"
                            }
                    )

                    assertNull(
                        ejecucion.runtimeError,
                        "${programa.id} falló en ejecución: ${ejecucion.runtimeError?.message}"
                    )

                    assertEquals(
                        esperada, ejecucion.output,
                        "La salida de ${programa.id} no coincide con sus anotaciones // SALIDA:"
                    )
                }
            }

    // Criterio de aceptacion del ticket: demo_completa.cps ES el programa por
    // defecto del IDE. Si alguien cambia uno de los dos y no el otro, la demo de la
    // presentacion deja de estar cubierta por la bateria y nadie se entera.
    @Test
    fun `el programa por defecto del IDE sale de la bateria`() {
        assertEquals(
            SamplePrograms.default.source, AppState().sourceContent,
            "el editor debería arrancar con el programa de demostración de la batería"
        )
    }

    companion object {

        // Los programas los enumera el mismo cargador que usa el selector del IDE,
        // asi que la bateria y el menu nunca se pueden desincronizar.
        fun programasDe(grupo: SampleGroup): List<SampleProgram> {
            val programas = SamplePrograms.all.filter { it.group == grupo }

            // Sin esto, borrar la carpeta por accidente dejaria la bateria en cero
            // tests y en verde, que es la peor forma de fallar.
            assertTrue(programas.isNotEmpty(), "No hay programas .cps del grupo $grupo")

            return programas
        }

        fun salidaEsperadaDe(programa: SampleProgram): List<String> =
            programa.source.lineSequence()
                .filter { it.startsWith("// SALIDA:") }
                .map { it.removePrefix("// SALIDA:").trim() }
                .toList()
    }
}
