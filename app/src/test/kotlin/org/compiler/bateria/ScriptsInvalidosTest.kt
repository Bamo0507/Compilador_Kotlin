package org.compiler.bateria

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Severity
import org.compiler.runtime.DbmsPipeline
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Los scripts que tienen que fallar, y los que solo avisan.
 *
 * Se verifica la linea y un FRAGMENTO del mensaje, no el texto exacto: mejorar
 * la redaccion de un error no deberia romper la bateria.
 *
 * El que ninguno deje el directorio tocado es la decision 5 a escala de toda la
 * bateria: nada se escribe hasta el final, y el final nunca llega.
 */
class ScriptsInvalidosTest {

    @TestFactory
    fun `los scripts invalidos reportan su error`(@TempDir temp: File): List<DynamicTest> =
        Bateria.of(Expectation.ERROR).map { case ->
            DynamicTest.dynamicTest(case.toString()) { runInvalid(case, File(temp, case.id)) }
        }

    @TestFactory
    fun `los scripts con avisos se ejecutan igual`(@TempDir temp: File): List<DynamicTest> =
        Bateria.of(Expectation.WARNING).map { case ->
            DynamicTest.dynamicTest(case.toString()) { runWarning(case, File(temp, case.id)) }
        }

    private fun runInvalid(case: ScriptCase, directory: File) {
        directory.mkdirs()
        val before = snapshot(directory)

        val result = DbmsPipeline.run(case.script.source, DataDirectory(directory))
        val errors = result.errors.filter { it.severity == Severity.ERROR }

        assertTrue(errors.isNotEmpty(), "${case.id} no reporto ningun error")

        val fragment = assertNotNull(case.message, "${case.id} no declara MENSAJE")
        val matching = errors.filter { it.message.contains(fragment) }
        assertTrue(
            matching.isNotEmpty(),
            "${case.id} no reporto '$fragment'. Reporto:\n${describe(result)}"
        )

        case.line?.let { expected ->
            assertTrue(
                matching.any { it.location.line == expected },
                "${case.id} esperaba el error en la linea $expected, salio en " +
                    "${matching.map { it.location.line }}"
            )
        }

        // Un error corta antes del volcado, asi que el directorio queda intacto.
        assertEquals(before, snapshot(directory), "${case.id} escribio pese a fallar")
    }

    private fun runWarning(case: ScriptCase, directory: File) {
        directory.mkdirs()

        val result = DbmsPipeline.run(case.script.source, DataDirectory(directory))

        assertTrue(result.errors.none { it is CompilerError.LexerError }, "${case.id}: error lexico")
        assertTrue(!result.hasErrors, "${case.id} reporto errores:\n${describe(result)}")

        val fragment = assertNotNull(case.message, "${case.id} no declara MENSAJE")
        assertTrue(
            result.warnings.any { it.message.contains(fragment) },
            "${case.id} no aviso '$fragment'. Reporto:\n${describe(result)}"
        )

        // Lo que estos casos prueban es justamente que el script SI corre.
        assertNotNull(result.execution, "${case.id} no se ejecuto")
    }

    @Test
    fun `cada script invalido declara el mensaje que espera`() {
        val sinMensaje = (Bateria.of(Expectation.ERROR) + Bateria.of(Expectation.WARNING))
            .filter { it.message == null }

        assertTrue(sinMensaje.isEmpty(), "sin anotacion MENSAJE: ${sinMensaje.map { it.id }}")
    }
}
