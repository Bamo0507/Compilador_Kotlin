package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.compiler.interpreter.ExecutionResult

/**
 * Lo que imprimio print(), y el error de ejecucion si lo hubo.
 *
 * Los tres estados dicen cosas distintas y por eso llevan mensajes distintos: no
 * ejecutar por tener errores no es lo mismo que no haber compilado todavia.
 */
@Composable
fun OutputConsole(
    execution: ExecutionResult?,
    modifier: Modifier = Modifier,
    hasErrors: Boolean = false
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            execution != null -> ExecutionOutput(execution)

            hasErrors -> ConsoleMessage("El programa no se ejecutó porque tiene errores.")

            else -> ConsoleMessage("Presiona compilar para ejecutar.")
        }
    }
}

@Composable
private fun ExecutionOutput(execution: ExecutionResult) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (execution.output.isEmpty() && execution.runtimeError == null) {
            ConsoleMessage("El programa terminó sin imprimir nada.")
        }

        execution.output.forEach { line -> ConsoleLine(line) }

        // Va al final y no arriba: el usuario necesita ver hasta donde llego su
        // programa antes de leer por que se detuvo.
        execution.runtimeError?.let { error -> RuntimeErrorLine(error.location.line, error.message) }
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
private fun RuntimeErrorLine(line: Int, message: String) {
    val colors = MaterialTheme.colorScheme
    val text = "Error de ejecución en la línea $line: $message"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(colors.errorContainer.copy(alpha = 0.42f), MaterialTheme.shapes.extraSmall)
            .padding(8.dp)
            .semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.error
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onErrorContainer
        )
    }
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
