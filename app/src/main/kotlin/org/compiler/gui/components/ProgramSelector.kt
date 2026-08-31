package org.compiler.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.compiler.samples.SampleGroup
import org.compiler.samples.SampleProgram
import org.compiler.samples.SamplePrograms

// Alto de una fila del menu. El maximo del menu son cinco filas, de tal forma que
// siempre se ve que hay mas abajo y la lista no tapa el editor completo.
private val ITEM_HEIGHT = 44.dp
private const val VISIBLE_ITEMS = 5

/**
 * Selector de los programas de ejemplo, encima del editor.
 *
 * Los ejemplos son los mismos .cps de la bateria de pruebas, asi que cargar uno y
 * darle a compilar reproduce exactamente lo que verifica esa prueba.
 */
@Composable
fun ProgramSelector(
    selected: SampleProgram?,
    onSelect: (SampleProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Sin ejemplo seleccionado el texto fue editado a mano; decirlo evita que el
    // selector afirme que se esta viendo algo que ya no es cierto.
    val label = selected?.name ?: "Programa personalizado"

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = "Programa de ejemplo: $label" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Programa",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = ITEM_HEIGHT * VISIBLE_ITEMS)
        ) {
            SamplePrograms.grouped().forEach { (group, programs) ->
                GroupHeader(group)
                programs.forEach { program ->
                    ProgramItem(
                        program = program,
                        isSelected = program.id == selected?.id,
                        onClick = {
                            expanded = false
                            onSelect(program)
                        }
                    )
                }
            }
        }
    }
}

// El encabezado ordena una lista de casi cuarenta entradas: sin el, con solo cinco
// visibles a la vez, no hay forma de saber en que parte de la lista se va.
@Composable
private fun GroupHeader(group: SampleGroup) {
    val colors = MaterialTheme.colorScheme

    HorizontalDivider(color = colors.outlineVariant)
    Text(
        text = group.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProgramItem(
    program: SampleProgram,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    DropdownMenuItem(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = ITEM_HEIGHT)
            .background(if (isSelected) colors.secondaryContainer else Color.Transparent),
        text = {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        // El check marca lo que esta cargado; el hueco del mismo ancho cuando no lo
        // esta mantiene alineados todos los nombres.
        leadingIcon = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }
        }
    )
}
