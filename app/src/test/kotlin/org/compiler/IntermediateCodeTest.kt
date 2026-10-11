
package org.compiler

import java.io.File
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SampleProgram
import org.compiler.samples.SamplePrograms
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class IntermediateCodeTest {

    // ── Los casos exitosos ─────────────────────────────────────────────────

    @TestFactory
    fun `cada programa valido genera el TAC de su archivo dorado`(): List<DynamicTest> =
        validPrograms().map { sample ->
            DynamicTest.dynamicTest(sample.name) {
                val actual = tacOf(sample)
                val golden = goldenFileOf(sample)

                if (updateGolden) {
                    golden.writeText(actual)
                    return@dynamicTest
                }

                if (!golden.exists()) {
                    fail(
                        "${sample.id} no tiene su archivo ${golden.name}. El TAC generado " +
                            "es este; si es correcto, guardalo con " +
                            "`./gradlew test -DupdateGolden=true`:\n\n$actual"
                    )
                }

                assertSameLines(sample, normalized(golden.readText()), actual)
            }
        }

    // un .tac sin su .cps es un caso que se borro a medias.
    @TestFactory
    fun `ningun archivo dorado queda sin su programa`(): List<DynamicTest> =
        (goldenFolder.listFiles { file -> file.name.endsWith(".tac") } ?: emptyArray())
            .sortedBy { it.name }
            .map { golden ->
                DynamicTest.dynamicTest(golden.name) {
                    val program = File(goldenFolder, golden.name.removeSuffix(".tac") + ".cps")
                    assertTrue(program.exists(), "${golden.name} no tiene su ${program.name}")
                }
            }

    private fun validPrograms(): List<SampleProgram> {
        val programs = SamplePrograms.all.filter { it.id.startsWith(VALID_PREFIX) }
        assertTrue(programs.isNotEmpty(), "No hay programas validos")
        return programs
    }

    private fun tacOf(sample: SampleProgram): String {
        val result = CompilerPipeline.compile(sample.source, execute = false)
        assertTrue(
            result.errors.isEmpty(),
            "${sample.id} deberia compilar sin errores: ${result.errors.map { it.message }}"
        )
        val tac = assertNotNull(result.tac, "${sample.id} no genero TAC")
        return TacPrinter.print(tac.instructions) + "\n"
    }

    private fun goldenFileOf(sample: SampleProgram): File =
        File(goldenFolder, sample.id.removePrefix(VALID_PREFIX) + ".tac")

    // \r\n; el TAC se compara sin ellos.
    private fun normalized(text: String): String = text.replace("\r\n", "\n")

    private fun assertSameLines(sample: SampleProgram, expected: String, actual: String) {
        if (expected == actual) return

        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val index = expectedLines.indices.firstOrNull { it >= actualLines.size || expectedLines[it] != actualLines[it] }
            ?: expectedLines.size

        fail(
            "El TAC de ${sample.id} no coincide con ${goldenFileOf(sample).name}, " +
                "desde la linea ${index + 1}:\n" +
                "  esperado: ${expectedLines.getOrNull(index) ?: "<fin del archivo>"}\n" +
                "  generado: ${actualLines.getOrNull(index) ?: "<fin del TAC>"}\n" +
                "Si el cambio es deliberado: ./gradlew test -DupdateGolden=true"
        )
    }

    private companion object {
        const val VALID_PREFIX = "validos/"

        // Gradle corre los tests con el modulo app/ como directorio de trabajo.
        val goldenFolder = File("src/main/resources/programas/validos")

        val updateGolden: Boolean = System.getProperty("updateGolden") == "true"
    }
}
