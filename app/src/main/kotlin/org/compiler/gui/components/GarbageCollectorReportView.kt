package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.frontend.semantic.models.GarbageCollectorReport
import org.compiler.frontend.semantic.models.SymbolLiveness

/**
 * El reporte de vivacidad: hasta cuando importa cada simbolo.
 *
 * Es la respuesta a lo que pidio el catedratico —"dejar un tipo de meta que me diga
 * cuando algo ya no se va a utilizar"—: cada fila dice donde se declaro un nombre,
 * donde se uso por ultima vez y cuantas veces.
 *
 * NO hay columna "liberable" a proposito: en Compiscript la respuesta seria siempre
 * "si", porque una funcion anidada no puede sobrevivir a la de afuera, y una columna
 * con un solo valor no informa nada.
 */
@Composable
fun GarbageCollectorReportView(
    report: GarbageCollectorReport?,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No disponible."
) {
    if (report == null) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { contentDescription = emptyMessage }
        )
        return
    }

    // Aplanado, con la columna Ámbito: el reporte ya viene agrupado por ambito, pero
    // una tabla corrida se lee mejor que una lista de listas cuando hay pocos
    // simbolos por ambito, que es el caso normal.
    val entries = report.entriesByScope.entries.flatMap { it.value }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LivenessSummary(report, entries.size)

        if (entries.isEmpty()) {
            Text(
                text = "El programa no declara ningún símbolo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            LivenessHeaderRow()
            HorizontalDivider()
            entries.forEach { entry -> LivenessRow(entry) }
        }
    }
}

// Las dos cifras que se leen de un vistazo en la presentacion.
@Composable
private fun LivenessSummary(report: GarbageCollectorReport, total: Int) {
    val colors = MaterialTheme.colorScheme
    val neverUsed = report.neverUsed.size
    val captured = report.usedInNestedFunctions.size
    val text = "$total símbolos · $neverUsed nunca usados · $captured usados en función anidada"

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = text }
    )
}

@Composable
private fun LivenessHeaderRow() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Símbolo", SYMBOL_WIDTH)
        HeaderCell("Ámbito", SCOPE_WIDTH)
        HeaderCell("Declarado", LINE_WIDTH)
        HeaderCell("Último uso", LINE_WIDTH)
        HeaderCell("Usos", COUNT_WIDTH)
        HeaderCell("Usada en función anidada", NESTED_WIDTH)
    }
}

@Composable
private fun LivenessRow(entry: SymbolLiveness) {
    val colors = MaterialTheme.colorScheme

    // Un simbolo que nunca se uso se resalta: es el hallazgo accionable del reporte,
    // porque su memoria nunca hizo falta.
    val background =
        if (entry.neverUsed) colors.errorContainer.copy(alpha = 0.30f) else colors.surface

    val description = if (entry.neverUsed) {
        "${entry.symbol.name} en ${entry.scopeName}, declarado en la línea " +
            "${entry.declaredAtLine}, nunca usado"
    } else {
        "${entry.symbol.name} en ${entry.scopeName}, declarado en la línea " +
            "${entry.declaredAtLine}, último uso en la línea ${entry.lastUseLine}, " +
            "${entry.useCount} usos"
    }

    Row(
        modifier = Modifier
            .background(background, MaterialTheme.shapes.extraSmall)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(entry.symbol.name, SYMBOL_WIDTH, monospace = true)
        BodyCell(entry.scopeName, SCOPE_WIDTH)
        BodyCell(entry.declaredAtLine.toString(), LINE_WIDTH)

        // El guion largo dice "nunca", que no es lo mismo que la linea 0.
        BodyCell(entry.lastUseLine?.toString() ?: "—", LINE_WIDTH)

        BodyCell(entry.useCount.toString(), COUNT_WIDTH)
        BodyCell(if (entry.usedInNestedFunction) "sí" else "no", NESTED_WIDTH)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    monospace: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(width)
    )
}

private val SYMBOL_WIDTH = 150.dp
private val SCOPE_WIDTH = 150.dp
private val LINE_WIDTH = 90.dp
private val COUNT_WIDTH = 60.dp
private val NESTED_WIDTH = 190.dp
