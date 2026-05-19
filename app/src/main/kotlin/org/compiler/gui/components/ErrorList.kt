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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseError
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult

@Composable
fun ErrorList(
    parseResult: ParseResult?,
    modifier: Modifier = Modifier
) {
    ErrorList(
        errors = parseResult.errorsOrEmpty(),
        hasRun = parseResult != null,
        modifier = modifier
    )
}

@Composable
fun ErrorList(
    errors: List<ParseError>,
    modifier: Modifier = Modifier,
    hasRun: Boolean = true
) {
    val isEmpty = errors.isEmpty()
    ResultPanel(
        title = "Errors",
        empty = false,
        emptyMessage = "",
        modifier = modifier
    ) {
        when {
            !hasRun -> EmptyStatus(
                message = "Run the parser to inspect syntax errors.",
                isPositive = false
            )
            isEmpty -> EmptyStatus(
                message = "No syntax errors found.",
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
private fun EmptyStatus(
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
    error: ParseError
) {
    val colors = MaterialTheme.colorScheme
    val location = error.location?.let { "Line ${it.line}, column ${it.position}" } ?: "End of input"
    val found = error.foundToken?.lexeme?.let { "Found: $it" } ?: "Found: EOF"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.error.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .background(colors.errorContainer.copy(alpha = 0.42f), MaterialTheme.shapes.extraSmall)
            .padding(10.dp)
            .semantics { contentDescription = "Syntax error $index at $location" },
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
            Text(
                text = found,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = colors.onErrorContainer.copy(alpha = 0.82f)
            )
        }
    }
}

private fun ParseResult?.errorsOrEmpty(): List<ParseError> = when (this) {
    is ParseResult.Accepted -> errors
    is ParseResult.Rejected -> errors
    null -> emptyList()
}

@Composable
fun ErrorListDemo() {
    ErrorList(errors = emptyList(), modifier = Modifier.fillMaxSize())
}
