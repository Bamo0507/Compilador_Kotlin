package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind

/**
 * El arbol de ambitos completo, navegable.
 *
 * Que este arbol se pueda recorrer ENTERO —incluidos los bloques que ya se
 * cerraron— es la demostracion visual de la decision 7: los ambitos no se descartan
 * al salir de ellos. Si se hubieran descartado, aqui solo se veria `global`.
 */
@Composable
fun ScopeTreeView(
    root: Scope?,
    selectedScope: Scope?,
    onScopeSelected: (Scope) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No disponible."
) {
    if (root == null) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { contentDescription = emptyMessage }
        )
        return
    }

    // AUSENTE = expandido. El arbol arranca abierto a proposito: el punto de esta
    // pantalla es que estan todos los ambitos, no que haya que ir descubriendolos.
    //
    // La clave es el Scope mismo, y funciona porque Scope no sobrescribe equals: un
    // ambito tiene IDENTIDAD, asi que dos `if@20` distintos son claves distintas.
    val collapsed = remember(root) { mutableStateMapOf<Scope, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScopeRows(
            scope = root,
            depth = 0,
            collapsed = collapsed,
            selectedScope = selectedScope,
            onScopeSelected = onScopeSelected
        )
    }
}

// Una fila por ambito, y luego las de sus hijos. Recursiva, igual que el arbol.
@Composable
private fun ScopeRows(
    scope: Scope,
    depth: Int,
    collapsed: SnapshotStateMap<Scope, Boolean>,
    selectedScope: Scope?,
    onScopeSelected: (Scope) -> Unit
) {
    val isCollapsed = collapsed[scope] == true

    ScopeRow(
        scope = scope,
        depth = depth,
        isCollapsed = isCollapsed,
        isSelected = scope === selectedScope,
        onToggle = { collapsed[scope] = !isCollapsed },
        onSelect = { onScopeSelected(scope) }
    )

    if (!isCollapsed) {
        scope.children.forEach { child ->
            ScopeRows(child, depth + 1, collapsed, selectedScope, onScopeSelected)
        }
    }
}

@Composable
private fun ScopeRow(
    scope: Scope,
    depth: Int,
    isCollapsed: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val symbolCount = scope.localSymbols().size
    val description =
        "Ámbito ${scope.name}, ${kindLabelOf(scope.kind)}, $symbolCount símbolos"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) colors.secondaryContainer else colors.surfaceContainerLowest,
                MaterialTheme.shapes.extraSmall
            )
            .clickable { onSelect() }
            .padding(start = (depth * 14).dp, top = 4.dp, bottom = 4.dp, end = 6.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // El chevron pliega; el resto de la fila selecciona. Un ambito sin hijos no
        // lleva chevron, pero si el mismo hueco, para que los nombres alineen.
        if (scope.children.isEmpty()) {
            Spacer(modifier = Modifier.size(18.dp))
        } else {
            Icon(
                imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowRight
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onToggle() }
            )
        }

        Text(
            text = scope.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.onSecondaryContainer else colors.onSurface
        )

        Text(
            text = kindLabelOf(scope.kind),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )

        if (symbolCount > 0) {
            Text(
                text = "($symbolCount)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// El enunciado nombra los cuatro entornos en espanol; LOOP es un bucle, que es un
// bloque con nombre propio.
fun kindLabelOf(kind: ScopeKind): String = when (kind) {
    ScopeKind.GLOBAL -> "global"
    ScopeKind.CLASS -> "clase"
    ScopeKind.FUNCTION -> "función"
    ScopeKind.BLOCK -> "bloque"
    ScopeKind.LOOP -> "bucle"
}
