package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.compiler.frontend.syntax.TokenView
import org.compiler.frontend.syntax.tokensOf
import org.compiler.gui.state.AppState


@Composable
fun TokensScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    val source = state.sourceContent
    val tokens = remember(source) { tokensOf(source) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TokensPanel(
            title = "Tokens · ${tokens.size}",
            modifier = Modifier.fillMaxSize()
        ) {
            if (tokens.isEmpty()) {
                EmptyMessage(
                    if (source.isBlank()) "Escribe un script para ver sus tokens."
                    else "El script no tiene tokens: solo comentarios o espacios."
                )
                return@TokensPanel
            }

            Column(modifier = Modifier.fillMaxSize()) {
                HeaderRow()
                HorizontalDivider()

                // Un script grande son miles de tokens: solo se componen los que
                // caben en pantalla.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = tokens.size) { index -> TokenRow(index + 1, tokens[index]) }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("#", NUMBER_WIDTH, TextAlign.End)
        HeaderCell("Tipo", TYPE_WIDTH)
        HeaderCell("Lexema", LEXEME_WIDTH)
        HeaderCell("Línea", NUMBER_WIDTH, TextAlign.End)
        HeaderCell("Columna", NUMBER_WIDTH, TextAlign.End)
    }
}


@Composable
private fun TokenRow(number: Int, token: TokenView) {
    val colors = MaterialTheme.colorScheme
    val description = "${token.type} ${token.lexeme} línea ${token.line} columna ${token.column}"

    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell("$number", NUMBER_WIDTH, colors.onSurfaceVariant, TextAlign.End)
        BodyCell(token.type, TYPE_WIDTH, colors.onSurfaceVariant)
        BodyCell(token.lexeme, LEXEME_WIDTH, colors.onSurface, monospace = true)
        BodyCell("${token.line}", NUMBER_WIDTH, colors.onSurfaceVariant, TextAlign.End)
        BodyCell("${token.column}", NUMBER_WIDTH, colors.onSurfaceVariant, TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    align: TextAlign = TextAlign.Start,
    monospace: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = color,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = message }
    )
}

@Composable
private fun TokensPanel(
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

private val NUMBER_WIDTH = 60.dp
private val TYPE_WIDTH = 140.dp
private val LEXEME_WIDTH = 260.dp
