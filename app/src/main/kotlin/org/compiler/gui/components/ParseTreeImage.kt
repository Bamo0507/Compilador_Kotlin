package org.compiler.gui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.visualization.DotExporter
import org.compiler.frontend.syntaxAnalyzer.visualization.ParseTreeExporter
import org.jetbrains.skia.Image
import java.io.File

// Renders the parse tree by piping the DOT representation through the system
// `dot` binary and loading the resulting PNG. If Graphviz is not installed the
// result is null and the composable shows a fallback message with install hints.
private data class RenderResult(
    val bitmap: ImageBitmap?,
    val errorMessage: String?
)

@Composable
fun ParseTreeImage(
    tree: ParseTree,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val rendered = remember(tree) { renderTreeAsPng(tree) }

    Box(
        modifier = modifier
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant)
            .clipToBounds()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            rendered.bitmap != null -> Image(
                bitmap = rendered.bitmap,
                contentDescription = "Parse tree rendered with Graphviz",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            else -> GraphvizMissingNotice(
                detail = rendered.errorMessage,
                colors = colors
            )
        }
    }
}

@Composable
private fun GraphvizMissingNotice(
    detail: String?,
    colors: androidx.compose.material3.ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Graphviz `dot` binary not found.",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
        Text(
            text = "Install Graphviz to enable the rendered view:",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        val monospace = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        Text(text = "macOS    -- brew install graphviz", style = monospace, color = colors.onSurfaceVariant)
        Text(text = "Linux    -- sudo apt install graphviz", style = monospace, color = colors.onSurfaceVariant)
        Text(text = "Windows  -- choco install graphviz", style = monospace, color = colors.onSurfaceVariant)
        if (detail != null) {
            Text(
                text = "Detail: $detail",
                style = MaterialTheme.typography.labelSmall,
                color = colors.error
            )
        }
    }
}

private fun renderTreeAsPng(tree: ParseTree): RenderResult {
    val dotSource = ParseTreeExporter.toDot(tree)
    val outputFile = try {
        File.createTempFile("parse_tree_", ".png").also { it.deleteOnExit() }
    } catch (exception: Exception) {
        return RenderResult(bitmap = null, errorMessage = "Could not create temp file: ${exception.message}")
    }

    val success = DotExporter.renderToImage(dotSource, outputFile.absolutePath)
    if (!success || outputFile.length() == 0L) {
        return RenderResult(bitmap = null, errorMessage = null)
    }

    return try {
        val bytes = outputFile.readBytes()
        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
        RenderResult(bitmap = bitmap, errorMessage = null)
    } catch (exception: Exception) {
        RenderResult(bitmap = null, errorMessage = "Could not load PNG: ${exception.message}")
    }
}
