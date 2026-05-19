package org.compiler.gui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.compiler.gui.screens.WorkspaceScreen
import org.compiler.gui.state.AppState

@Composable
fun App() {
    val appState = remember { AppState() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            WorkspaceScreen(state = appState)
        }
    }
}
