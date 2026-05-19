package org.compiler.gui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.visualization.ParseTreeExporter

@Composable
fun ParseTreeView(
    parseResult: ParseResult?,
    modifier: Modifier = Modifier
) {
    val tree = parseResult.treeOrPartial()

    ResultPanel(
        title = "Parse Tree",
        empty = tree == null,
        emptyMessage = "Run the parser to inspect the generated syntax tree.",
        modifier = modifier
    ) {
        Text(
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

private fun ParseResult?.treeOrPartial(): ParseTree? = when (this) {
    is ParseResult.Accepted -> parseTree
    is ParseResult.Rejected -> partialTree
    null -> null
}

@Composable
fun ParseTreeViewDemo() {
    ParseTreeView(parseResult = null, modifier = Modifier.fillMaxSize())
}
