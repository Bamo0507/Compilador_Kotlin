// La prueba de punta a punta: cada programa valido de la bateria, ensamblado y corrido en
// el contenedor ARM, imprime lo mismo que el interprete. Solo corre con
// `./gradlew test -DrunArm=true`, porque tarda y necesita Docker.
package org.compiler

import org.compiler.backend.ArmPrinter
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArmExecutionTest {

    private val enabled = System.getProperty("runArm") == "true"

    // Un programa que usa algo que el backend todavia no traduce se salta: no tiene
    // ensamblador, y su TAC se sigue mostrando en el IDE.
    @TestFactory
    fun `cada programa valido imprime en ARM lo mismo que el interprete`(): List<DynamicTest> =
        SamplePrograms.all.filter { it.id.startsWith("validos/") }.map { sample ->
            DynamicTest.dynamicTest(sample.name) {
                assumeTrue(enabled, "Solo con -DrunArm=true")
                assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")

                val result = CompilerPipeline.compile(sample.source)
                val assembly = result.assembly
                assumeTrue(assembly != null, "El backend todavia no traduce ${sample.id}")
                val expected = assertNotNull(result.execution)

                val execution = ArmContainer.run(
                    sample.id.removePrefix("validos/"), ArmPrinter.print(checkNotNull(assembly))
                )

                assertEquals(expected.output, execution.output.lines().dropLast(1), sample.id)
                assertEquals(if (expected.runtimeError == null) 0 else 1, execution.exitCode)
            }
        }
}
