package org.compiler.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod

@Composable
fun MethodDropdown(
    selectedMethod: ParserMethod,
    onMethodSelected: (ParserMethod) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .widthIn(min = 144.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedMethod.displayName())
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ParserMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = { Text(method.displayName()) },
                    leadingIcon = {
                        if (method == selectedMethod) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onMethodSelected(method)
                    },
                    enabled = enabled
                )
            }
        }
    }
}

fun ParserMethod.displayName(): String = when (this) {
    ParserMethod.LL1 -> "LL(1)"
    ParserMethod.SLR1 -> "SLR(1)"
    ParserMethod.LALR1 -> "LALR(1)"
}

@Composable
fun MethodDropdownDemo() {
    MethodDropdown(
        selectedMethod = ParserMethod.SLR1,
        onMethodSelected = {}
    )
}
