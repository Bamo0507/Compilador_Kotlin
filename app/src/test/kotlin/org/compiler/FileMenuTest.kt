package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.backend.ArmPrinter
import org.compiler.gui.components.saveAssembly
import org.compiler.gui.components.saveIntermediateCode
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileMenuTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `exportar guarda el mismo TAC de la pantalla con extension tac`() {
        val tac = assertNotNull(CompilerPipeline.compile("print(1);", execute = false).tac)

        val exported = saveIntermediateCode(directory.resolve("programa").toFile(), tac)

        assertEquals("programa.tac", exported.name)
        assertEquals(TacPrinter.print(tac.instructions) + "\n", exported.readText())
    }

    @Test
    fun `exportar un programa cps cambia su extension por tac`() {
        val tac = assertNotNull(CompilerPipeline.compile("print(1);", execute = false).tac)

        val exported = saveIntermediateCode(directory.resolve("programa.cps").toFile(), tac)

        assertEquals("programa.tac", exported.name)
    }

    // ── El codigo ARM ──────────────────────────────────────────────────────

    private val factorial = """
        function factorial(n: integer): integer {
          if (n <= 1) { return 1; }
          return n * factorial(n - 1);
        }
        print(factorial(5));
    """.trimIndent()

    // El .s exportado lleva el runtime: es un programa completo, no solo lo que se ve en
    // la pantalla por defecto.
    @Test
    fun `exportar el codigo ARM guarda un programa completo con extension s`() {
        val assembly = assertNotNull(CompilerPipeline.compile(factorial, execute = false).assembly)

        val exported = saveAssembly(directory.resolve("factorial.cps").toFile(), assembly)

        assertEquals("factorial.s", exported.name)
        assertEquals(ArmPrinter.print(assembly), exported.readText())
        assertTrue("cps_imprimir_entero:" in exported.readText())
    }

    // El archivo exportado es lo que scripts/arm.sh ensambla y corre.
    @Test
    fun `el codigo ARM exportado corre en el contenedor`() {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")
        val assembly = assertNotNull(CompilerPipeline.compile(factorial, execute = false).assembly)
        val exported = saveAssembly(directory.resolve("factorial.cps").toFile(), assembly)

        val execution = ArmContainer.run("factorial", exported.readText())

        assertEquals(0, execution.exitCode, execution.output)
        assertEquals("120\n", execution.output)
    }
}
