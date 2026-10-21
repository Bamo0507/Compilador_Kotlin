package org.compiler.gui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation


class SqlHighlighter(private val colors: SqlColors) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, colors), OffsetMapping.Identity)
}

class SqlColors(
    val keyword: Color,
    val text: Color,
    val number: Color,
    val comment: Color,
    val identifier: Color,
    val symbol: Color
)

@Composable
fun rememberSqlColors(): SqlColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        SqlColors(
            keyword = scheme.primary,
            text = scheme.tertiary,
            number = scheme.secondary,
            comment = scheme.onSurfaceVariant,
            identifier = scheme.onSurface,
            symbol = scheme.onSurfaceVariant
        )
    }
}

internal fun highlight(source: String, colors: SqlColors): AnnotatedString = buildAnnotatedString {
    append(source)

    sqlSpansOf(source).forEach { span ->
        // Guarda de cordura: un tramo fuera del texto tiraria la ventana entera, y
        // el editor se repinta en cada tecla.
        if (span.start < 0 || span.end > source.length || span.start >= span.end) return@forEach

        addStyle(styleOf(span.kind, colors), span.start, span.end)
    }
}

private fun styleOf(kind: SqlSpanKind, colors: SqlColors): SpanStyle = when (kind) {
    SqlSpanKind.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold)
    SqlSpanKind.TEXT -> SpanStyle(color = colors.text)
    SqlSpanKind.NUMBER -> SpanStyle(color = colors.number)
    SqlSpanKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
    SqlSpanKind.IDENTIFIER -> SpanStyle(color = colors.identifier)
    SqlSpanKind.SYMBOL -> SpanStyle(color = colors.symbol)
}
