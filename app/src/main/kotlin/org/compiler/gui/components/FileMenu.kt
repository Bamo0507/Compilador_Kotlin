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
        Text("File")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Open .yalex / .yal") },
            onClick = {
                expanded = false
                openTextFile("Open lexical specification") { content, path ->
                    state.updateYalexContent(content, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not open lexer file: ${it.message}") }
            }
        )
        DropdownMenuItem(
            text = { Text("Open .yalp") },
            onClick = {
                expanded = false
                openTextFile("Open parser grammar") { content, path ->
                    state.updateYalpContent(content, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not open grammar file: ${it.message}") }
            }
        )
        DropdownMenuItem(
            text = { Text("Open Input") },
            onClick = {
                expanded = false
                openTextFile("Open input program") { content, path ->
                    state.updateInputContent(content, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not open input file: ${it.message}") }
            }
        )
        DropdownMenuItem(
            text = { Text("Save All") },
            onClick = {
                expanded = false
                saveAll(state)
            }
        )
        DropdownMenuItem(
            text = { Text("Save Lexer As...") },
            onClick = {
                expanded = false
                saveTextFileAs("Save lexical specification", state.yalexContent) { path ->
                    state.updateYalexContent(state.yalexContent, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not save lexer file: ${it.message}") }
            }
        )
        DropdownMenuItem(
            text = { Text("Save Grammar As...") },
            onClick = {
                expanded = false
                saveTextFileAs("Save parser grammar", state.yalpContent) { path ->
                    state.updateYalpContent(state.yalpContent, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not save grammar file: ${it.message}") }
            }
        )
        DropdownMenuItem(
            text = { Text("Save Input As...") },
            onClick = {
                expanded = false
                saveTextFileAs("Save input program", state.inputContent) { path ->
                    state.updateInputContent(state.inputContent, path)
                    state.clearError()
                }.onFailure { state.reportFileError("Could not save input file: ${it.message}") }
            }
        )
    }
}

private fun openTextFile(
    title: String,
    onLoaded: (content: String, path: String) -> Unit
): Result<Unit> = runCatching {
    val file = chooseFile(title, FileDialog.LOAD) ?: return@runCatching
    onLoaded(file.readText(), file.absolutePath)
}

private fun saveTextFileAs(
    title: String,
    content: String,
    onSaved: (path: String) -> Unit
): Result<Unit> = runCatching {
    val file = chooseFile(title, FileDialog.SAVE) ?: return@runCatching
    file.writeText(content)
    onSaved(file.absolutePath)
}

private fun saveAll(state: AppState) {
    val results = listOf(
        saveKnownOrAs(state.yalexFilePath, "Save lexical specification", state.yalexContent) {
            state.updateYalexContent(state.yalexContent, it)
        },
        saveKnownOrAs(state.yalpFilePath, "Save parser grammar", state.yalpContent) {
            state.updateYalpContent(state.yalpContent, it)
        },
        saveKnownOrAs(state.inputFilePath, "Save input program", state.inputContent) {
            state.updateInputContent(state.inputContent, it)
        }
    )
    val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
    if (failure != null) {
        state.reportFileError("Could not save files: ${failure.message}")
    } else {
        state.clearError()
    }
}

private fun saveKnownOrAs(
    path: String?,
    title: String,
    content: String,
    onSaved: (path: String) -> Unit
): Result<Unit> = runCatching {
    if (path != null) {
        File(path).writeText(content)
        onSaved(path)
    } else {
        val file = chooseFile(title, FileDialog.SAVE) ?: return@runCatching
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

private fun chooseFile(
    title: String,
    mode: Int
): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}
