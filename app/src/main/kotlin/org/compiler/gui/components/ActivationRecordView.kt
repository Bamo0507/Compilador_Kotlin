package org.compiler.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.ClassLayout
import org.compiler.frontend.intermediate.models.FunctionLabel

// Un renglon de la vista: un campo del registro, o el relleno que deja la alineacion
// entre dos campos o al final.
internal data class RecordRow(
    val offset: Int,
    val name: String,
    val size: Int,
    val isPadding: Boolean
)

// Los campos en orden, con el relleno intercalado donde la alineacion deja un hueco.
internal fun rowsOf(record: ActivationRecordLayout): List<RecordRow> {
    val rows = mutableListOf<RecordRow>()
    var end = 0

    record.fields.forEach { field ->
        if (field.offset > end) rows += RecordRow(end, "relleno", field.offset - end, true)
        rows += RecordRow(field.offset, field.name, field.size, false)
        end = field.offset + field.size
    }

    if (record.size > end) rows += RecordRow(end, "relleno", record.size - end, true)
    return rows
}

// Un objeto se muestra con la misma vista que un registro: casillas con desplazamiento
// y tamaño, y el relleno de la alineacion.
internal fun objectRecordOf(layout: ClassLayout): ActivationRecordLayout =
    ActivationRecordLayout(FunctionLabel(layout.className), layout.fields, layout.size)

// La tabla de metodos tambien: una entrada de 4 bytes por metodo, en su posicion.
internal fun methodTableOf(layout: ClassLayout): ActivationRecordLayout =
    ActivationRecordLayout(
        FunctionLabel(layout.className),
        layout.methods.mapIndexed { slot, method -> ActivationRecordField(method.name, slot * 4, 4) },
        layout.methods.size * 4
    )

/**
 * La forma de un registro de activacion: que campo va en que desplazamiento y cuanto
 * mide la hoja completa. Es la diapositiva 21 con numeros.
 */
@Composable
fun ActivationRecordView(
    record: ActivationRecordLayout,
    title: String = "Registro de activación de ${record.function.name}: ${record.size} bytes"
) {
    val colors = MaterialTheme.colorScheme

    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordCell("Desplazamiento", OFFSET_WIDTH, header = true)
            RecordCell("Campo", FIELD_WIDTH, header = true)
            RecordCell("Tamaño", SIZE_WIDTH, header = true)
        }
        HorizontalDivider()

        rowsOf(record).forEach { row ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecordCell(row.offset.toString(), OFFSET_WIDTH, muted = row.isPadding)
                RecordCell(row.name, FIELD_WIDTH, muted = row.isPadding)
                RecordCell(row.size.toString(), SIZE_WIDTH, muted = row.isPadding)
            }
        }
    }
}

@Composable
private fun RecordCell(text: String, width: Dp, header: Boolean = false, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Text(
        text = text,
        style = if (header) typography.labelMedium else typography.bodySmall,
        fontWeight = if (header) FontWeight.SemiBold else null,
        fontStyle = if (muted) FontStyle.Italic else null,
        fontFamily = if (header) null else FontFamily.Monospace,
        color = if (muted || header) colors.onSurfaceVariant else colors.onSurface,
        modifier = Modifier.width(width)
    )
}

private val OFFSET_WIDTH = 110.dp
private val FIELD_WIDTH = 180.dp
private val SIZE_WIDTH = 70.dp
