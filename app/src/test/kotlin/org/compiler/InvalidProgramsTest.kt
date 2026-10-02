// Los programas .cps que DEBEN fallar, cada uno con su error.
//
// Cada archivo declara en su primera linea que error espera y en que linea, asi que
// el archivo es su propia especificacion:
//
//   // ESPERADO: linea 4, "No se puede asignar 'string' a 'integer'"
//
// Agregar un caso es agregar un archivo: no hay que tocar este codigo.
package org.compiler

import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SampleGroup
import org.compiler.samples.SampleProgram
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

class InvalidProgramsTest {

    // El fragmento y no el mensaje completo: el test fija la REGLA que se viola, no
    // la redaccion exacta del mensaje. Cambiar una palabra del texto no debe romper
    // veintidos tests.
    private data class ExpectedError(val line: Int, val fragment: String)

    @TestFactory
    fun `cada programa invalido produce el error esperado`(): List<DynamicTest> =
        ValidProgramsTest.programsOf(SampleGroup.INVALID).map { sample ->
            DynamicTest.dynamicTest(sample.name) {
                val expected = readExpectedAnnotation(sample)
                val result = CompilerPipeline.compile(sample.source)

                assertTrue(
                    result.hasErrors,
                    "Se esperaba al menos un error en ${sample.id}, y compiló limpio."
                )

                val matches = result.errors.any { error ->
                    error.location.line == expected.line &&
                        error.message.contains(expected.fragment)
                }

                // El mensaje de fallo lista los errores que SI salieron: cuando este
                // test falla, se ve de inmediato que paso sin correr el programa a mano.
                assertTrue(
                    matches,
                    "En ${sample.id} se esperaba en la línea ${expected.line} " +
                        "un error con '${expected.fragment}'.\n" +
                        "Errores obtenidos:\n" +
                        result.errors.joinToString("\n") {
                            "  línea ${it.location.line}: ${it.message}"
                        }
                )
            }
        }

    // Un programa invalido SOLO se ejecuta si no tiene errores, y por definicion
    // aqui todos tienen. Ejecutar codigo mal tipado da basura en vez de un mensaje.
    @TestFactory
    fun `ningun programa invalido llega a ejecutarse`(): List<DynamicTest> =
        ValidProgramsTest.programsOf(SampleGroup.INVALID).map { sample ->
            DynamicTest.dynamicTest(sample.name) {
                val result = CompilerPipeline.compile(sample.source)

                assertTrue(
                    result.execution == null,
                    "${sample.id} tiene errores y aun asi se ejecutó."
                )
            }
        }

    private fun readExpectedAnnotation(sample: SampleProgram): ExpectedError {
        val annotation = sample.source.lineSequence().firstOrNull { it.startsWith("// ESPERADO:") }
            ?: error(
                "${sample.id} no tiene su anotación. Debe llevar la línea:\n" +
                    "  // ESPERADO: linea <n>, \"<fragmento del mensaje>\""
            )

        val match = ANNOTATION.find(annotation)
            ?: error("La anotación de ${sample.id} no tiene el formato esperado: $annotation")

        return ExpectedError(
            line = match.groupValues[1].toInt(),
            fragment = match.groupValues[2]
        )
    }

    private companion object {
        private val ANNOTATION = Regex("""// ESPERADO: linea (\d+), "(.+)"""")
    }
}
