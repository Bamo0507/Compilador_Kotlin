package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.PipelineResult
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.visualization.DotExporter

private enum class AutomatonView {
    SLR1,
    LALR1
}

private fun AutomatonView.displayName(): String = when (this) {
    AutomatonView.SLR1 -> "SLR(1) without merge"
    AutomatonView.LALR1 -> "LALR(1) merged"
}

@Composable
fun AutomatonScreen(
    result: PipelineResult?,
    modifier: Modifier = Modifier
) {
    var selectedView by remember { mutableStateOf(AutomatonView.SLR1) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Automata",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        SingleChoiceSegmentedButtonRow {
            val options = AutomatonView.entries
            options.forEachIndexed { index, view ->
                SegmentedButton(
                    selected = selectedView == view,
                    onClick = { selectedView = view },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(view.displayName()) }
                )
            }
        }

        if (result == null) {
            EmptyScreenMessage("Run the parser to inspect generated automata.")
            return@Column
        }

        val automaton = when (selectedView) {
            AutomatonView.SLR1 -> result.slr1Automaton
            AutomatonView.LALR1 -> result.lalr1Automaton
        }
        val dot = when (selectedView) {
            AutomatonView.SLR1 -> DotExporter.slr1ToDot(automaton)
            AutomatonView.LALR1 -> DotExporter.lalr1ToDot(automaton)
        }

        AutomatonSummary(automaton)
        ReadOnlyMonospacePanel(
            text = dot,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AutomatonSummary(automaton: SLR1Automata) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryPill(label = "States", value = automaton.states.size.toString())
        SummaryPill(label = "Transitions", value = automaton.transitions.size.toString())
        SummaryPill(label = "Start", value = "S${automaton.initialState.id}")
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
            .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun EmptyScreenMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun ReadOnlyMonospacePanel(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.onSurface
        ),
        modifier = modifier
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
            .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    )
}
