package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.gui.components.CodeEditor
import org.compiler.gui.components.ErrorList
import org.compiler.gui.components.MethodDropdown
import org.compiler.gui.components.ParseTreeView
import org.compiler.gui.components.PlayButton
import org.compiler.gui.components.TokenList
import org.compiler.gui.state.AppState

@Composable
fun WorkspaceScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    var editorTab by remember { mutableIntStateOf(0) }
    var resultTab by remember { mutableIntStateOf(0) }
    val result = state.pipelineResult
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WorkspaceToolbar(state = state)
        state.errorMessage?.let { message -> ErrorBanner(message) }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkspacePanel(
                title = "Source Workspace",
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
            ) {
                CompilerTabs(
                    selectedIndex = editorTab,
                    labels = listOf("Lexer (.yal)", "Grammar (.yalp)", "Input"),
                    onSelected = { editorTab = it }
                )
                Box(modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                    when (editorTab) {
                        0 -> CodeEditor(
                            value = state.yalexContent,
                            onValueChange = { state.yalexContent = it },
                            label = "Lexical specification",
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> CodeEditor(
                            value = state.yalpContent,
                            onValueChange = { state.yalpContent = it },
                            label = "Parser grammar",
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> CodeEditor(
                            value = state.inputContent,
                            onValueChange = { state.inputContent = it },
                            label = "Input program",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            WorkspacePanel(
                title = "Results",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CompilerTabs(
                    selectedIndex = resultTab,
                    labels = listOf("Tokens", "Parse Tree", "Errors"),
                    onSelected = { resultTab = it }
                )
                Box(modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                    when (resultTab) {
                        0 -> TokenList(
                            entries = result?.lexerResult?.entries ?: emptyList(),
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> ParseTreeView(
                            parseResult = result?.parseResult,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> ErrorList(
                            parseResult = result?.parseResult,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceToolbar(
    state: AppState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Compiler Kotlin",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Lexical and syntactic analyzer workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MethodDropdown(
            selectedMethod = state.selectedMethod,
            onMethodSelected = state::changeMethod,
            enabled = !state.isRunning
        )
        PlayButton(
            isRunning = state.isRunning,
            onClick = state::onPlay
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.error.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .background(colors.errorContainer.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer
        )
    }
}

@Composable
private fun WorkspacePanel(
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
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun CompilerTabs(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                text = {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace
                    )
                }
            )
        }
    }
}

@Composable
fun WorkspaceScreenDemo() {
    WorkspaceScreen(
        state = AppState(),
        modifier = Modifier.fillMaxSize()
    )
}
