package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@Composable
fun CatalogTree(
    entries: List<CatalogEntry>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "El directorio de datos está vacío.",
    onReferenceClick: (String) -> Unit = {}
) {
    if (entries.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { contentDescription = emptyMessage }
        )
        return
    }

    // AUSENTE = expandido. El arbol arranca abierto: el punto de la pantalla es que
    // se vean las columnas, no que haya que ir descubriendolas.
    val collapsed = remember(entries) { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "data/",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        entries.forEach { entry ->
            val isCollapsed = collapsed[entry.table] == true

            TableRow(entry, isCollapsed) { collapsed[entry.table] = !isCollapsed }
            if (!isCollapsed) {
                entry.columns.forEach { column -> ColumnRow(column, onReferenceClick) }
            }
        }
    }
}

@Composable
private fun TableRow(entry: CatalogEntry, isCollapsed: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = "tabla ${entry.table}, ${entry.problem ?: entry.summary}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.columns.isNotEmpty(), onClick = onToggle)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.columns.isEmpty()) {
            Spacer(modifier = Modifier.width(ARROW_SIZE))
        } else {
            Icon(
                imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(ARROW_SIZE)
            )
        }

        if (entry.isBroken) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(ARROW_SIZE)
            )
        }

        Text(
            text = entry.table,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.isBroken) colors.error else colors.onSurface
        )
        Text(
            text = entry.problem ?: entry.summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.isBroken) colors.error else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColumnRow(column: CatalogColumn, onReferenceClick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reference = column.referenceTo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, top = 1.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = column.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
            modifier = Modifier.width(NAME_WIDTH),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = column.type,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(TYPE_WIDTH),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // La flecha de una clave foranea es CLICABLE: lleva a la columna a la que
        // apunta, que es la pregunta que uno se hace al verla.
        if (reference != null) {
            Text(
                text = column.constraints,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier.clickable { onReferenceClick(reference.first) }
            )
        } else if (column.constraints.isNotEmpty()) {
            Text(
                text = column.constraints,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        column.indexedBy?.let { index ->
            Text(
                text = "[$index]",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private val ARROW_SIZE = 16.dp
private val NAME_WIDTH = 120.dp
private val TYPE_WIDTH = 110.dp
