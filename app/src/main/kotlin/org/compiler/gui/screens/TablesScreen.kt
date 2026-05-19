package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.PipelineResult
import org.compiler.frontend.syntaxAnalyzer.visualization.TableFormatter

@Composable
fun TablesScreen(
    result: PipelineResult?,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val labels = listOf("FIRST/FOLLOW", "LL(1)", "SLR(1)", "LALR(1)")
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tables",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surfaceContainerLow,
            contentColor = colors.primary
        ) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
            }
        }

        if (result == null) {
            EmptyScreenMessage("Run the parser to inspect FIRST/FOLLOW sets and parsing tables.")
            return@Column
        }

        val tableText = when (selectedTab) {
            0 -> buildString {
                appendLine("FIRST")
                appendLine(TableFormatter.formatFirstSets(result.firstSets))
                appendLine()
                appendLine("FOLLOW")
                appendLine(TableFormatter.formatFollowSets(result.followSets))
            }
            1 -> TableFormatter.formatLL1Table(result.ll1Table)
            2 -> buildString {
                appendLine("ACTION")
                appendLine(TableFormatter.formatSLR1Action(result.slr1Table))
                appendLine()
                appendLine("GOTO")
                appendLine(TableFormatter.formatSLR1Goto(result.slr1Table))
            }
            else -> buildString {
                appendLine("ACTION")
                appendLine(TableFormatter.formatLALR1Action(result.lalr1Table))
                appendLine()
                appendLine("GOTO")
                appendLine(TableFormatter.formatLALR1Goto(result.lalr1Table))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ReadOnlyMonospacePanel(
                text = tableText,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TablesScreenDemo() {
    TablesScreen(result = null, modifier = Modifier.fillMaxSize())
}
