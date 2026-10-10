package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.gui.components.saveIntermediateCode
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileMenuTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `exportar guarda el mismo TAC de la pantalla con extension tac`() {
        val tac = assertNotNull(CompilerPipeline.compile("print(1);", execute = false).tac)

        val exported = saveIntermediateCode(directory.resolve("programa").toFile(), tac)

        assertEquals("programa.tac", exported.name)
        assertEquals(TacPrinter.print(tac.instructions), exported.readText())
    }
}
