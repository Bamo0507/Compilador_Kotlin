package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.diagnostics.CompilerError

/**
 * La lista de errores del programa del usuario.
 *
 * Llegan ya ordenados por linea y columna desde Diagnostics.all(): la lista se lee
 * en el mismo orden que el codigo, no agrupada por fase.
 */
@Composable
fun ErrorList(
    errors: List<CompilerError>,
    modifier: Modifier = Modifier,
    hasRun: Boolean = true,
    onErrorClick: (Int) -> Unit = {}
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
                    ErrorItem(index + 1, error, onErrorClick)
                }
            }
        }
    }
}

// El enunciado exige distinguir los tres niveles. La etiqueta de texto es lo que
// informa; el color solo acompaña.
private fun levelLabelOf(error: CompilerError): String = when (error) {
    is CompilerError.LexerError -> "léxico"
    is CompilerError.ParserError -> "sintáctico"
    is CompilerError.SemanticError -> "semántico"
}

@Composable
private fun levelColorOf(error: CompilerError): Color {
    val colors = MaterialTheme.colorScheme
    return when (error) {
        is CompilerError.LexerError -> colors.tertiary
        is CompilerError.ParserError -> colors.secondary
        is CompilerError.SemanticError -> colors.error
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
    error: CompilerError,
    onErrorClick: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val level = levelLabelOf(error)
    val levelColor = levelColorOf(error)
    val location = "Línea ${error.location.line}, columna ${error.location.position}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, levelColor.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .background(colors.errorContainer.copy(alpha = 0.42f), MaterialTheme.shapes.extraSmall)
            .clickable { onErrorClick(error.location.line) }
            .padding(10.dp)
            .semantics { contentDescription = "Error $index [$level] en $location" },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = levelColor
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$level]",
                    style = MaterialTheme.typography.labelMedium,
                    color = levelColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$index. $location",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer
            )
        }
    }
}
