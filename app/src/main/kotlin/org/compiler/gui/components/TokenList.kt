package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.models.TokenEntry

@Composable
fun TokenList(
    entries: List<TokenEntry>,
    modifier: Modifier = Modifier
) {
    ResultPanel(
        title = "Tokens",
        empty = entries.isEmpty(),
        emptyMessage = "Run the analyzer to inspect recognized tokens.",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TokenRow(
                index = "#",
                category = "Category",
                lexeme = "Lexeme",
                symbol = "Symbol",
                location = "Line:Col",
                isHeader = true
            )
            entries.forEachIndexed { index, entry ->
                TokenRow(
                    index = (index + 1).toString(),
                    category = entry.token.category,
                    lexeme = entry.token.lexeme.ifEmpty { " " },
                    symbol = entry.token.symbolIndex?.toString() ?: "-",
                    location = "${entry.location.line}:${entry.location.position}",
                    isHeader = false
                )
            }
        }
    }
}

@Composable
private fun TokenRow(
    index: String,
    category: String,
    lexeme: String,
    symbol: String,
    location: String,
    isHeader: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val rowBackground = if (isHeader) colors.surfaceContainerHighest else colors.surfaceContainerLow
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isHeader) colors.onSurface else colors.onSurfaceVariant
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TableCell(index, 42, textStyle)
        TableCell(category, 132, textStyle)
        TableCell(lexeme, 156, textStyle)
        TableCell(symbol, 64, textStyle)
        TableCell(location, 78, textStyle)
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Int,
    style: androidx.compose.ui.text.TextStyle
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width.dp)
    )
}

@Composable
internal fun ResultPanel(
    title: String,
    empty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                .padding(10.dp)
        ) {
            if (empty) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                content()
            }
        }
    }
}
