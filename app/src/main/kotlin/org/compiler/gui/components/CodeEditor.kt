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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
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
        lineHeight = 20.sp
    )

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
                Text(
                    text = (1..lines).joinToString("\n"),
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
