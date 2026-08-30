package org.compiler.gui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Las vistas del IDE. Agregar una es agregar una entrada al enum y una rama en App.
enum class AppView {
    WORKSPACE,
    TREES,
    SYMBOLS
}

@Composable
fun ViewMenu(
    selectedView: AppView,
    onViewSelected: (AppView) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = modifier
    ) {
        Text("Vista")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        AppView.entries.forEach { view ->
            DropdownMenuItem(
                text = { Text(view.displayName()) },
                leadingIcon = {
                    if (view == selectedView) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onViewSelected(view)
                }
            )
        }
    }
}

fun AppView.displayName(): String = when (this) {
    AppView.WORKSPACE -> "Editor"
    AppView.TREES -> "Árboles"
    AppView.SYMBOLS -> "Tabla de símbolos"
}
