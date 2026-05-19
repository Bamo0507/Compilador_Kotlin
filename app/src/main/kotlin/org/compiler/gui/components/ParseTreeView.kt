package org.compiler.gui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.visualization.ParseTreeExporter

private enum class ParseTreeViewMode { GRAPH, RENDERED, TEXT }

private fun ParseTreeViewMode.displayName(): String = when (this) {
    ParseTreeViewMode.GRAPH -> "Graph"
    ParseTreeViewMode.RENDERED -> "Rendered"
    ParseTreeViewMode.TEXT -> "Text"
}

@Composable
fun ParseTreeView(
    parseResult: ParseResult?,
    modifier: Modifier = Modifier
) {
    val tree = parseResult.treeOrPartial()
    var viewMode by remember { mutableStateOf(ParseTreeViewMode.GRAPH) }

    ResultPanel(
        title = "Parse Tree",
        empty = tree == null,
        emptyMessage = "Run the parser to inspect the generated syntax tree.",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 8.dp)) {
                val options = ParseTreeViewMode.entries
                options.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(mode.displayName()) }
                    )
                }
            }

            when (viewMode) {
                ParseTreeViewMode.GRAPH -> ParseTreeCanvas(
                    tree = tree!!,
                    modifier = Modifier.fillMaxSize()
                )
                ParseTreeViewMode.RENDERED -> ParseTreeImage(
                    tree = tree!!,
                    modifier = Modifier.fillMaxSize()
                )
                ParseTreeViewMode.TEXT -> Text(
                    text = ParseTreeExporter.toIndentedText(tree!!),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun ParseResult?.treeOrPartial(): ParseTree? = when (this) {
    is ParseResult.Accepted -> parseTree
    is ParseResult.Rejected -> partialTree
    null -> null
}
