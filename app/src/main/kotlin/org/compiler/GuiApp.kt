package org.compiler

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.compiler.gui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compiler Kotlin",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        App()
    }
}
