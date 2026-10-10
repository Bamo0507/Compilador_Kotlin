package org.compiler.gui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.toRow

@Composable
fun QuadrupleTable(instructions: List<Quadruple>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TableCell("#", LINE_WIDTH, header = true)
            TableCell("Operador", OPERATOR_WIDTH, header = true)
            TableCell("Arg1", ARGUMENT_WIDTH, header = true)
            TableCell("Arg2", ARGUMENT_WIDTH, header = true)
            TableCell("Resultado", RESULT_WIDTH, header = true)
        }
        HorizontalDivider()

        instructions.forEachIndexed { index, instruction ->
            val row = instruction.toRow()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TableCell((index + 1).toString(), LINE_WIDTH)
                TableCell(row.operator, OPERATOR_WIDTH)
                TableCell(row.argument1, ARGUMENT_WIDTH)
                TableCell(row.argument2, ARGUMENT_WIDTH)
                TableCell(row.result, RESULT_WIDTH)
            }
        }
    }
}

@Composable
private fun TableCell(value: String, width: Dp, header: Boolean = false) {
    Text(
        text = value,
        modifier = Modifier.width(width).padding(vertical = 5.dp),
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        fontFamily = if (header) null else FontFamily.Monospace,
        fontWeight = if (header) FontWeight.SemiBold else null,
        softWrap = false
    )
}

private val LINE_WIDTH = 48.dp
private val OPERATOR_WIDTH = 120.dp
private val ARGUMENT_WIDTH = 200.dp
private val RESULT_WIDTH = 200.dp
