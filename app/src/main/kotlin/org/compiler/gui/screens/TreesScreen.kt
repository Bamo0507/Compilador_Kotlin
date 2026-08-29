package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.compiler.frontend.ast.toTreeView
import org.compiler.gui.components.TreeCanvas
import org.compiler.gui.state.AppState

// Los dos arboles se pueden ver a la vez o de uno en uno. Lado a lado es lo que
// muestra el punto de la fase; uno solo sirve cuando el de ANTLR es enorme.
private enum class TreeMode(val label: String) {
    SIDE_BY_SIDE("Lado a lado"),
    PARSE_TREE("Árbol sintáctico"),
    AST("AST")
}

@Composable
fun TreesScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(TreeMode.SIDE_BY_SIDE) }

    val result = state.result
    val parseTree = result?.parseTreeView

    // La conversion del AST se hace aqui y no en el pipeline: solo esta pantalla la
    // necesita, y recalcularla por compilacion es barato.
    val ast = remember(result) { result?.ast?.toTreeView() }

    val emptyMessage = when {
        result == null -> "Presiona compilar para ver los árboles."
        else -> "No disponible: el programa no parsea."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TreeMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { mode = option },
                    label = { Text(option.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode != TreeMode.AST) {
                TreePanel(
                    title = "Árbol sintáctico (ANTLR)",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TreeCanvas(root = parseTree, emptyMessage = emptyMessage)
                }
            }

            if (mode != TreeMode.PARSE_TREE) {
                TreePanel(
                    title = "AST propio, con los tipos de la Fase 4",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TreeCanvas(root = ast, emptyMessage = emptyMessage)
                }
            }
        }
    }
}

@Composable
private fun TreePanel(
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
