package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Compartida por el texto y por el margen de numeros, para que las dos columnas
// queden linea con linea. De aqui sale tambien el calculo del scroll.
private val LINE_HEIGHT = 20.sp

// Los numeros del margen, con la linea marcada resaltada.
private fun lineNumbers(lines: Int, highlighted: Int?, highlightColor: Color) =
    buildAnnotatedString {
        (1..lines).forEach { number ->
            if (number > 1) append("\n")

            if (number == highlighted) {
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(number.toString())
                }
            } else {
                append(number.toString())
            }
        }
    }

@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    highlightedLine: Int? = null
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val lines = value.lineSequence().count().coerceAtLeast(1)
    val lineNumberWidth = ((lines.toString().length + 3) * 10).dp
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.48f)
    val borderColor = when {
        !enabled -> colors.outlineVariant.copy(alpha = 0.55f)
        isFocused -> colors.primary
        else -> colors.outlineVariant
    }
    val editorTextStyle = TextStyle(
        color = contentColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = LINE_HEIGHT
    )

    // El salto a una linea: se calcula por altura de linea porque el editor es
    // monoespaciado y no envuelve, asi que la linea N esta en (N - 1) alturas.
    val lineHeightPx = with(LocalDensity.current) { LINE_HEIGHT.toPx() }
    LaunchedEffect(highlightedLine) {
        val line = highlightedLine ?: return@LaunchedEffect
        verticalScroll.animateScrollTo(((line - 1) * lineHeightPx).toInt().coerceAtLeast(0))
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(if (isFocused) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                .padding(10.dp)
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .semantics {
                    contentDescription = label ?: "Code editor"
                    if (!enabled) disabled()
                }
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Un solo bloque de texto, no una Text por linea: solo asi el margen
                // usa el mismo interlineado que el campo y las dos columnas quedan
                // linea con linea. La marca va como span.
                Text(
                    text = lineNumbers(lines, highlightedLine, colors.error),
                    style = editorTextStyle,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(min = lineNumberWidth)
                        .background(colors.surfaceContainerHighest.copy(alpha = 0.42f))
                        .padding(start = 4.dp, end = 12.dp)
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    textStyle = editorTextStyle,
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .widthIn(min = 520.dp)
                )
            }
        }
    }
}
