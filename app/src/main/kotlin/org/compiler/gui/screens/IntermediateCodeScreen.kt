package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.gui.components.QuadrupleTable
import org.compiler.gui.state.AppState

private enum class IntermediateView(val label: String) {
    TAC("TAC"),
    QUADRUPLES("Cuádruplos")
}

@Composable
fun IntermediateCodeScreen(state: AppState, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(IntermediateView.TAC) }
    val result = state.result
    val tac = result?.tac

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntermediateView.entries.forEach { option ->
                    FilterChip(
                        selected = view == option,
                        onClick = { view = option },
                        label = { Text(option.label) }
                    )
                }
            }

            tac?.let { Text("Temporales usados: ${it.temporaryCount}") }
        }

        when {
            result == null -> Text("Presiona compilar para ver el código intermedio.")
            result.hasErrors -> Text("No disponible: el programa tiene errores. Revísalos en el Editor.")
            tac == null -> Text("No disponible: no se generó código intermedio.")
            view == IntermediateView.TAC -> TacText(tac.instructions)
            else -> QuadrupleTable(tac.instructions)
        }
    }
}

@Composable
private fun TacText(instructions: List<Quadruple>) {
    val lineNumbers = instructions.indices.joinToString("\n") { (it + 1).toString() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = lineNumbers,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false
        )
        Text(
            text = TacPrinter.print(instructions),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false
        )
    }
}
