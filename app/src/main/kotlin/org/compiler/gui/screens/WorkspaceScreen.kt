package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.compiler.gui.components.CodeEditor
import org.compiler.gui.components.ErrorList
import org.compiler.gui.components.OutputConsole
import org.compiler.gui.components.PlayButton
import org.compiler.gui.state.AppState

@Composable
fun WorkspaceScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WorkspaceToolbar(state = state)
        state.errorMessage?.let { message -> ErrorBanner(message) }

        val result = state.result

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkspacePanel(
                title = state.sourceFilePath ?: "Programa sin guardar",
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
            ) {
                CodeEditor(
                    value = state.sourceContent,
                    onValueChange = { state.sourceContent = it },
                    highlightedLine = state.highlightedLine,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorkspacePanel(
                    title = errorPanelTitle(result?.errors?.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ErrorList(
                        errors = result?.errors ?: emptyList(),
                        hasRun = result != null,
                        onErrorClick = { line -> state.highlightedLine = line },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                WorkspacePanel(
                    title = "Salida",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    OutputConsole(
                        execution = result?.execution,
                        hasErrors = result?.hasErrors == true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun errorPanelTitle(count: Int?): String = when {
    count == null || count == 0 -> "Errores"
    count == 1 -> "Errores (1)"
    else -> "Errores ($count)"
}

@Composable
private fun WorkspaceToolbar(
    state: AppState,
    modifier: Modifier = Modifier
) {
    // La compilacion corre en Dispatchers.Default para que la ventana no se congele.
    // Los campos de AppState son estado de snapshot, asi que escribirlos desde un
    // hilo de fondo es seguro.
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Compiscript",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Analizador semántico",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PlayButton(
            isRunning = state.isRunning,
            onClick = {
                // markRunning() corre en el hilo de UI y deshabilita el boton YA.
                // Sin esto, entre el clic y el arranque del hilo de fondo hay una
                // ventana donde un doble clic arranca dos compilaciones.
                state.markRunning()
                scope.launch(Dispatchers.Default) { state.onCompile() }
            }
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.error.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .background(colors.errorContainer.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer
        )
    }
}

@Composable
private fun WorkspacePanel(
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
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}
