// Los renglones que muestra la vista del registro de activacion.
package org.compiler

import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.ClassLayout
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.gui.components.RecordRow
import org.compiler.gui.components.methodTableOf
import org.compiler.gui.components.objectRecordOf
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

    // ── La clase seleccionada en la tabla de simbolos (Fase 5) ─────────────

    private val perro = ClassLayout(
        className = "Perro",
        size = 16,
        methods = listOf(FunctionLabel("Perro.hablar"), FunctionLabel("Animal.comer")),
        fields = listOf(
            ActivationRecordField("tabla de métodos", 0, 4),
            ActivationRecordField("nombre", 4, 4),
            ActivationRecordField("peso", 8, 8)
        )
    )

    @Test
    fun `el objeto se muestra con la casilla de la tabla primero`() {
        assertEquals(
            listOf(
                RecordRow(0, "tabla de métodos", 4, false),
                RecordRow(4, "nombre", 4, false),
                RecordRow(8, "peso", 8, false)
            ),
            rowsOf(objectRecordOf(perro))
        )
    }

    // Cada entrada de la tabla mide 4: la posicion n esta en el desplazamiento 4n.
    @Test
    fun `la tabla de metodos se muestra entrada por entrada`() {
        assertEquals(
            listOf(RecordRow(0, "Perro.hablar", 4, false), RecordRow(4, "Animal.comer", 4, false)),
            rowsOf(methodTableOf(perro))
        )
    }
}
