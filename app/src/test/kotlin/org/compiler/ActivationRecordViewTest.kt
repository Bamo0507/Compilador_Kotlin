// Los renglones que muestra la vista del registro de activacion.
package org.compiler

import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.gui.components.RecordRow
import org.compiler.gui.components.rowsOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivationRecordViewTest {

    // a ocupa 4 y b, float, empieza en 8: el hueco de en medio y el del final son relleno.
    @Test
    fun `los huecos de la alineacion salen como relleno`() {
        val record = ActivationRecordLayout(
            FunctionLabel("f"),
            listOf(
                ActivationRecordField("a", 0, 4),
                ActivationRecordField("b", 8, 8),
                ActivationRecordField("c", 16, 1)
            ),
            24
        )

        assertEquals(
            listOf(
                RecordRow(0, "a", 4, false),
                RecordRow(4, "relleno", 4, true),
                RecordRow(8, "b", 8, false),
                RecordRow(16, "c", 1, false),
                RecordRow(17, "relleno", 7, true)
            ),
            rowsOf(record)
        )
    }
}
