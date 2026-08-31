package org.compiler.gui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.compiler.gui.state.AppState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun FileMenu(
    state: AppState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = modifier
    ) {
        Text("Archivo")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Abrir...") },
            onClick = {
                expanded = false
                open(state)
            }
        )
        DropdownMenuItem(
            text = { Text("Guardar") },
            onClick = {
                expanded = false
                save(state)
            }
        )
        DropdownMenuItem(
            text = { Text("Guardar como...") },
            onClick = {
                expanded = false
                saveAs(state)
            }
        )
    }
}

private fun open(state: AppState) {
    runCatching {
        val file = chooseFile("Abrir programa Compiscript", FileDialog.LOAD) ?: return

        // Un solo llamado porque abrir un archivo cambia varias cosas a la vez: el
        // texto, la ruta, y deja de haber un ejemplo seleccionado.
        state.loadFromFile(file.readText(), file.absolutePath)
    }.onFailure {
        state.errorMessage = "No se pudo abrir el archivo: ${it.message}"
    }
}

// Guarda sobre la ruta conocida; si no hay ninguna, se comporta como "Guardar como".
private fun save(state: AppState) {
    val path = state.sourceFilePath
    if (path == null) {
        saveAs(state)
        return
    }

    runCatching {
        File(path).writeText(state.sourceContent)
        state.errorMessage = null
    }.onFailure {
        state.errorMessage = "No se pudo guardar el archivo: ${it.message}"
    }
}

// Aqui solo cambia la RUTA: el contenido ya esta en el editor. Es el caso que hacia
// incomodo tener una funcion `updateSource(content, path)` en AppState.
private fun saveAs(state: AppState) {
    runCatching {
        val file = chooseFile("Guardar programa Compiscript", FileDialog.SAVE) ?: return

        val target = withCompiscriptExtension(file)
        target.writeText(state.sourceContent)
        state.sourceFilePath = target.absolutePath
        state.errorMessage = null
    }.onFailure {
        state.errorMessage = "No se pudo guardar el archivo: ${it.message}"
    }
}

// El dialogo de AWT no agrega la extension: un nombre sin punto se guarda sin ella.
private fun withCompiscriptExtension(file: File): File =
    if (file.name.contains('.')) file else File(file.parentFile, "${file.name}$EXTENSION")

private fun chooseFile(title: String, mode: Int): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    dialog.file = "*$EXTENSION"
    dialog.isVisible = true

    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name)
}

private const val EXTENSION = ".cps"
