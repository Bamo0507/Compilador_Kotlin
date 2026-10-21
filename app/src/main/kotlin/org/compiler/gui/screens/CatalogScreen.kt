package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.compiler.gui.components.CatalogTree
import org.compiler.gui.components.catalogEntriesOf
import org.compiler.gui.state.AppState
import org.compiler.storage.DataDirectory


@Composable
fun CatalogScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    val result = state.result
    val catalog = result?.catalog

    val entries = remember(result) {
        if (catalog == null) emptyList()
        else catalogEntriesOf(catalog, DataDirectory.DEFAULT, result.catalogProblems)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CatalogPanel(
            title = if (catalog == null) "Catálogo" else "Catálogo · ${entries.size} tablas",
            modifier = Modifier.fillMaxSize()
        ) {
            CatalogTree(
                entries = entries,
                emptyMessage = if (result == null) {
                    "Presiona correr para leer el catálogo."
                } else {
                    "El directorio de datos está vacío."
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CatalogPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .background(colors.surfaceContainerLowest, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        content()
    }
}
