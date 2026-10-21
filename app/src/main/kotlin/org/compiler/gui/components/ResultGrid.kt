package org.compiler.gui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.compiler.engine.StatementOutcome

@Composable
fun ResultGrid(
    outcomes: List<StatementOutcome>,
    modifier: Modifier = Modifier,
    hasRun: Boolean = true,
    hasErrors: Boolean = false
) {
    if (outcomes.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            GridMessage(
                when {
                    !hasRun -> "Presiona correr para ejecutar el script."
                    hasErrors -> "El script no se ejecutó porque tiene errores."
                    else -> "El script no produjo resultados."
                }
            )
        }
        return
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    val prepared = remember(outcomes, cellStyle) {
        outcomes.map { outcome ->
            val grid = (outcome as? StatementOutcome.Rows)?.let { gridOf(it.result) }
            PreparedGrid(
                grid = grid,
                widths = grid?.widest.orEmpty().map { widthOf(it, cellStyle, measurer, density) },
                scroll = ScrollState(0)
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        outcomes.forEachIndexed { position, outcome ->
            val ready = prepared[position]

            item(key = "head-${outcome.number}") {
                StatementHeader(outcome, ready.grid?.rows?.size)
            }

            val grid = ready.grid ?: return@forEachIndexed
            if (grid.isEmpty) {
                item(key = "empty-${outcome.number}") { GridMessage("sin resultados") }
                return@forEachIndexed
            }

            item(key = "cols-${outcome.number}") {
                Column(modifier = Modifier.horizontalScroll(ready.scroll)) {
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        grid.columns.forEachIndexed { index, column ->
                            HeaderCell(column, ready.widths[index])
                        }
                    }
                    HorizontalDivider()
                }
            }

            items(count = grid.rows.size, key = { "row-${outcome.number}-$it" }) { index ->
                Row(
                    modifier = Modifier
                        .horizontalScroll(ready.scroll)
                        .padding(vertical = 2.dp)
                ) {
                    grid.rows[index].forEachIndexed { column, cell ->
                        BodyCell(cell, grid.columns[column].numeric, ready.widths[column])
                    }
                }
            }
        }
    }
}

private class PreparedGrid(
    // Null en una sentencia que no es consulta: esa solo tiene su linea de resumen.
    val grid: Grid?,
    val widths: List<Dp>,
    val scroll: ScrollState
)

// ── Las celdas ─────────────────────────────────────────────────────────────

@Composable
private fun HeaderCell(column: GridColumn, width: Dp) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(width)
            .padding(horizontal = CELL_PADDING)
    ) {
        Text(
            text = column.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = column.type,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BodyCell(cell: GridCell, numeric: Boolean, width: Dp) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = cell.text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontStyle = if (cell.isNull) FontStyle.Italic else FontStyle.Normal,
        color = if (cell.isNull) colors.onSurfaceVariant else colors.onSurface,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = CELL_PADDING)
    )
}

// ── El encabezado de cada sentencia ────────────────────────────────────────

@Composable
private fun StatementHeader(outcome: StatementOutcome, rows: Int?) {
    val colors = MaterialTheme.colorScheme
    val detail = when (outcome) {
        is StatementOutcome.Rows -> rowCountOf(rows ?: 0)
        is StatementOutcome.Written -> outcome.detail
    }
    val description = "sentencia ${outcome.number}, ${outcome.label}, $detail"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${outcome.number}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .background(colors.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            text = outcome.label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun GridMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = message }
    )
}

private val CELL_PADDING = 6.dp


private fun widthOf(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density
): Dp {
    val measured = measurer.measure(measuredText(text), style).size.width
    return with(density) { measured.toDp() } + CELL_PADDING * 2
}
