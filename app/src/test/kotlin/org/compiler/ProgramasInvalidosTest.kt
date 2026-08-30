// Ticket 8.1: los programas .cps que DEBEN fallar, cada uno con su error.
//
// Cada archivo declara en su primera linea que error espera y en que linea, asi que
// el archivo es su propia especificacion:
//
//   // ESPERADO: linea 4, "No se puede asignar 'string' a 'integer'"
//
// Agregar un caso es agregar un archivo: no hay que tocar este codigo.
package org.compiler

import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertTrue

class ProgramasInvalidosTest {

    // El fragmento y no el mensaje completo: el test fija la REGLA que se viola, no
    // la redaccion exacta del mensaje. Cambiar una palabra del texto no debe romper
    // veintidos tests.
    private data class ErrorEsperado(val linea: Int, val fragmento: String)

    @TestFactory
    fun `cada programa invalido produce el error esperado`(): List<DynamicTest> =
        ProgramasValidosTest.programasEn("programas/invalidos").map { archivo ->
            DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                val esperado = leerAnotacionEsperada(archivo)
                val resultado = CompilerPipeline.compile(archivo.readText())

                assertTrue(
                    resultado.hasErrors,
                    "Se esperaba al menos un error en ${archivo.name}, y compiló limpio."
                )

                val coincide = resultado.errors.any { error ->
                    error.location.line == esperado.linea &&
                        error.message.contains(esperado.fragmento)
                }

                // El mensaje de fallo lista los errores que SI salieron: cuando este
                // test falla, se ve de inmediato que paso sin correr el programa a mano.
                assertTrue(
                    coincide,
                    "En ${archivo.name} se esperaba en la línea ${esperado.linea} " +
                        "un error con '${esperado.fragmento}'.\n" +
                        "Errores obtenidos:\n" +
                        resultado.errors.joinToString("\n") {
                            "  línea ${it.location.line}: ${it.message}"
                        }
                )
            }
        }

    // Un programa invalido SOLO se ejecuta si no tiene errores, y por definicion
    // aqui todos tienen. Ejecutar codigo mal tipado da basura en vez de un mensaje.
    @TestFactory
    fun `ningun programa invalido llega a ejecutarse`(): List<DynamicTest> =
        ProgramasValidosTest.programasEn("programas/invalidos").map { archivo ->
            DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                val resultado = CompilerPipeline.compile(archivo.readText())

                assertTrue(
                    resultado.execution == null,
                    "${archivo.name} tiene errores y aun asi se ejecutó."
                )
            }
        }

    private fun leerAnotacionEsperada(archivo: File): ErrorEsperado {
        val anotacion = archivo.readLines().firstOrNull { it.startsWith("// ESPERADO:") }
            ?: error(
                "${archivo.name} no tiene su anotación. La primera línea debe ser:\n" +
                    "  // ESPERADO: linea <n>, \"<fragmento del mensaje>\""
            )

        val coincidencia = ANOTACION.find(anotacion)
            ?: error("La anotación de ${archivo.name} no tiene el formato esperado: $anotacion")

        return ErrorEsperado(
            linea = coincidencia.groupValues[1].toInt(),
            fragmento = coincidencia.groupValues[2]
        )
    }

    private companion object {
        private val ANOTACION = Regex("""// ESPERADO: linea (\d+), "(.+)"""")
    }
}
