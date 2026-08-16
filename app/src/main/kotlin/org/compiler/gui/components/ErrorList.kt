package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.diagnostics.CompilerError

/**
 * La lista de errores del programa del usuario.
 *
 * Solo lee `location` y `message`, que son los miembros de la interfaz
 * [CompilerError]: no toca los campos propios de cada variante. Por eso sobrevive sin
 * cambios al ticket 0.6, que reescribe esas variantes.
 *
 * La etiqueta por nivel (lexico / sintactico / semantico) llega en la Fase 7, junto
 * con el salto al editor al hacer clic.
 */
@Composable
fun ErrorList(
    errors: List<CompilerError>,
    modifier: Modifier = Modifier,
    hasRun: Boolean = true
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            !hasRun -> StatusMessage(
                message = "Presiona compilar para revisar el programa.",
                isPositive = false
            )

            errors.isEmpty() -> StatusMessage(
                message = "Sin errores.",
                isPositive = true
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                errors.forEachIndexed { index, error ->
                    ErrorItem(index + 1, error)
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isPositive: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = message }
    ) {
        Icon(
            imageVector = if (isPositive) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (isPositive) colors.primary else colors.onSurfaceVariant
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorItem(
    index: Int,
    error: CompilerError
) {
    val colors = MaterialTheme.colorScheme
    val location = "Línea ${error.location.line}, columna ${error.location.position}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.error.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .background(colors.errorContainer.copy(alpha = 0.42f), MaterialTheme.shapes.extraSmall)
            .padding(10.dp)
            .semantics { contentDescription = "Error $index en $location" },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.error
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$index. $location",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer
            )
        }
    }
}
