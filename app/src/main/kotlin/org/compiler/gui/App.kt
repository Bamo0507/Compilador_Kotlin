package org.compiler.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.compiler.gui.components.AppView
import org.compiler.gui.components.FileMenu
import org.compiler.gui.components.ViewMenu
import org.compiler.gui.screens.AutomatonScreen
import org.compiler.gui.screens.TablesScreen
import org.compiler.gui.screens.WorkspaceScreen
import org.compiler.gui.state.AppState

@Composable
fun App() {
    val appState = remember { AppState() }
    var selectedView by remember { mutableStateOf(AppView.WORKSPACE) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    FileMenu(state = appState)
                    ViewMenu(
                        selectedView = selectedView,
                        onViewSelected = { selectedView = it }
                    )
                }
                HorizontalDivider()
                when (selectedView) {
                    AppView.WORKSPACE -> WorkspaceScreen(
                        state = appState,
                        modifier = Modifier.fillMaxSize()
                    )
                    AppView.AUTOMATA -> AutomatonScreen(
                        result = appState.pipelineResult,
                        modifier = Modifier.fillMaxSize()
                    )
                    AppView.TABLES -> TablesScreen(
                        result = appState.pipelineResult,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
