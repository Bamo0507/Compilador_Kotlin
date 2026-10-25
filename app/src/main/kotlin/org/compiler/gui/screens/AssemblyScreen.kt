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
import org.compiler.backend.ArmPrinter
import org.compiler.backend.models.ArmLine
import org.compiler.backend.models.AssemblyProgram
import org.compiler.gui.state.AppState

// El .s del programa. Cada instruccion del TAC aparece como comentario antes de su
// traduccion, asi que se pueden leer las dos juntas sin otra columna.
@Composable
fun AssemblyScreen(state: AppState, modifier: Modifier = Modifier) {
    // El runtime es igual para todos los programas: por defecto no se muestra, para que
    // se vea solo lo que genero el compilador.
    var includeRuntime by remember { mutableStateOf(false) }
    val result = state.result
    val assembly = result?.assembly

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
            FilterChip(
                selected = includeRuntime,
                onClick = { includeRuntime = !includeRuntime },
                label = { Text("Incluir el runtime") }
            )

            assembly?.let { Text("Instrucciones generadas: ${it.instructionCount()}") }
        }

        when {
            result == null -> Text("Presiona compilar para ver el código ARM.")
            result.hasErrors -> Text(
                "No disponible: el programa tiene errores. Revísalos en el Editor."
            )
            assembly == null -> Text("No disponible: no se generó código ARM.")
            else -> AssemblyText(ArmPrinter.print(assembly, includeRuntime))
        }
    }
}

private fun AssemblyProgram.instructionCount(): Int = lines.count { it is ArmLine.Instruction }

@Composable
private fun AssemblyText(text: String) {
    val lines = text.trimEnd().lines()
    val lineNumbers = lines.indices.joinToString("\n") { (it + 1).toString() }
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
            text = lines.joinToString("\n"),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false
        )
    }
}
