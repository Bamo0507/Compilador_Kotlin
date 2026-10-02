// Los programas .cps que DEBEN compilar.
//
// A diferencia de los tests unitarios —que prueban una funcion—,
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

class ValidProgramsTest {

    @TestFactory
    fun `cada programa valido compila sin errores`(): List<DynamicTest> =
        programsOf(SampleGroup.VALID).map { sample ->
            DynamicTest.dynamicTest(sample.name) {
                val result = CompilerPipeline.compile(sample.source)

                assertTrue(
                    result.errors.isEmpty(),
                    "${sample.id} debería compilar sin errores, pero produjo:\n" +
                        result.errors.joinToString("\n") {
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
        programsOf(SampleGroup.VALID)
            .filter { expectedOutputOf(it).isNotEmpty() }
            .map { sample ->
                DynamicTest.dynamicTest(sample.name) {
                    val expected = expectedOutputOf(sample)
                    val result = CompilerPipeline.compile(sample.source)

                    val execution = result.execution
                    assertNotNull(
                        execution,
                        "${sample.id} no se ejecutó. Errores:\n" +
                            result.errors.joinToString("\n") {
                                "  línea ${it.location.line}: ${it.message}"
                            }
                    )

                    assertNull(
                        execution.runtimeError,
                        "${sample.id} falló en ejecución: ${execution.runtimeError?.message}"
                    )

                    assertEquals(
                        expected, execution.output,
                        "La salida de ${sample.id} no coincide con sus anotaciones // SALIDA:"
                    )
                }
            }

    // demo_completa.cps ES el programa por
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
        fun programsOf(group: SampleGroup): List<SampleProgram> {
            val programs = SamplePrograms.all.filter { it.group == group }

            // Sin esto, borrar la carpeta por accidente dejaria la bateria en cero
            // tests y en verde, que es la peor forma de fallar.
            assertTrue(programs.isNotEmpty(), "No hay programas .cps del grupo $group")

            return programs
        }

        fun expectedOutputOf(sample: SampleProgram): List<String> =
            sample.source.lineSequence()
                .filter { it.startsWith("// SALIDA:") }
                .map { it.removePrefix("// SALIDA:").trim() }
                .toList()
    }
}
