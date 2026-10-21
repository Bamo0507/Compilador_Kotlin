package org.compiler.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * La salida de una corrida, en texto plano.
 *
 * Los errores de ejecucion NO salen aqui: son una variante de CompilerError y los
 * muestra ErrorList, junto a los de las demas etapas.
 */
@Composable
fun OutputConsole(
    output: List<String>,
    modifier: Modifier = Modifier,
    hasErrors: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            output.isNotEmpty() -> output.forEach { line -> ConsoleLine(line) }

            hasErrors -> ConsoleMessage("El script no se ejecutó porque tiene errores.")

            else -> ConsoleMessage("Presiona correr para ejecutar.")
        }
    }
}

@Composable
private fun ConsoleLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ConsoleMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = message }
    )
}
