package org.compiler.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.gui.components.GarbageCollectorReportView
import org.compiler.gui.components.ScopeTreeView
import org.compiler.gui.components.kindLabelOf
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.gui.state.AppState

private enum class SymbolView(val label: String) {
    SYMBOLS("Tabla de símbolos"),
    LIVENESS("Reporte de vivacidad")
}

@Composable
fun SymbolTableScreen(
    state: AppState,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf(SymbolView.SYMBOLS) }

    val result = state.result
    val globalScope = result?.globalScope

    var selectedScope by remember(globalScope) { mutableStateOf(globalScope) }

    val emptyMessage = when {
        result == null -> "Presiona compilar para ver la tabla de símbolos."
        else -> "No disponible: el programa no compiló."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymbolView.entries.forEach { option ->
                FilterChip(
                    selected = view == option,
                    onClick = { view = option },
                    label = { Text(option.label) }
                )
            }
        }

        when (view) {
            SymbolView.SYMBOLS -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Panel(
                    title = "Ámbitos",
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                ) {
                    ScopeTreeView(
                        root = globalScope,
                        selectedScope = selectedScope,
                        onScopeSelected = { selectedScope = it },
                        emptyMessage = emptyMessage
                    )
                }

                Panel(
                    title = selectedScope?.let {
                        "Símbolos de: ${it.name} (${kindLabelOf(it.kind)})"
                    } ?: "Símbolos",
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                ) {
                    SymbolTable(scope = selectedScope, emptyMessage = emptyMessage)
                }
            }

            SymbolView.LIVENESS -> Panel(
                title = "Vivacidad: hasta cuándo importa cada símbolo",
                modifier = Modifier.fillMaxSize()
            ) {
                GarbageCollectorReportView(
                    report = result?.garbageCollectorReport,
                    emptyMessage = emptyMessage
                )
            }
        }
    }
}

// ── La tabla de la derecha ─────────────────────────────────────────────────

@Composable
private fun SymbolTable(
    scope: Scope?,
    emptyMessage: String
) {
    if (scope == null) {
        Message(emptyMessage)
        return
    }

    val own = scope.localSymbols()
    val inherited = inheritedSymbolsOf(scope)

    if (own.isEmpty() && inherited.isEmpty()) {
        Message("Este ámbito no declara símbolos.")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        SymbolHeaderRow()
        HorizontalDivider()

        own.forEach { symbol -> SymbolRow(symbol) }

        if (inherited.isNotEmpty()) {
            SectionLabel("Heredados")
            inherited.forEach { entry -> SymbolRow(entry.symbol, inheritedFrom = entry.className) }
        }
    }
}

@Composable
private fun SymbolHeaderRow() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Nombre", NAME_WIDTH)
        HeaderCell("Categoría", CATEGORY_WIDTH)
        HeaderCell("Tipo", TYPE_WIDTH)
        HeaderCell("Offset", NUMBER_WIDTH)
        HeaderCell("Línea", NUMBER_WIDTH)
    }
}

@Composable
private fun SymbolRow(symbol: Symbol, inheritedFrom: String? = null) {
    val category = categoryLabel(symbol) + (inheritedFrom?.let { " (de $it)" } ?: "")
    val description = "${symbol.name}, $category, tipo ${symbol.type.name}, " +
        "offset ${symbol.offset}, línea ${symbol.location.line}"

    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(symbol.name, NAME_WIDTH, monospace = true)
        BodyCell(category, CATEGORY_WIDTH)

        BodyCell(symbol.type.name, TYPE_WIDTH, monospace = true)

        BodyCell(symbol.offset.toString(), NUMBER_WIDTH)
        BodyCell(symbol.location.line.toString(), NUMBER_WIDTH)
    }
}

private fun categoryLabel(symbol: Symbol): String = when {
    symbol.kind == DeclarationKind.FUNCTION && symbol.isMember -> "Método"
    symbol.kind == DeclarationKind.VARIABLE && symbol.isMember -> "Campo"
    symbol.kind == DeclarationKind.CONSTANT && symbol.isMember -> "Constante de clase"
    symbol.kind == DeclarationKind.VARIABLE -> "Variable"
    symbol.kind == DeclarationKind.CONSTANT -> "Constante"
    symbol.kind == DeclarationKind.PARAMETER -> "Parámetro"
    symbol.kind == DeclarationKind.FUNCTION -> "Función"
    else -> "Clase"
}

private data class InheritedSymbol(val className: String, val symbol: Symbol)

private fun inheritedSymbolsOf(scope: Scope): List<InheritedSymbol> {
    val result = mutableListOf<InheritedSymbol>()
    val visited = mutableSetOf<Scope>()
    var current: Scope? = scope.superclass

    while (true) {
        val ancestor = current ?: break
        if (!visited.add(ancestor)) break

        ancestor.localSymbols().forEach { symbol ->
            result.add(InheritedSymbol(ancestor.name, symbol))
        }
        current = ancestor.superclass
    }

    return result
}

// ── Piezas compartidas ─────────────────────────────────────────────────────

@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .background(colors.surfaceContainerLowest, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = text }
    )
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun BodyCell(text: String, width: Dp, monospace: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(width)
    )
}

private val NAME_WIDTH = 150.dp
private val CATEGORY_WIDTH = 150.dp
private val TYPE_WIDTH = 200.dp
private val NUMBER_WIDTH = 70.dp
