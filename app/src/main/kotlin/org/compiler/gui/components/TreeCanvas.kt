package org.compiler.gui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.ast.models.TreeNodeView

// Los margenes internos y las separaciones son constantes; el ALTO del nodo no, y
// tampoco el ancho: los dos salen de medir el texto. Fijarlos hacia que el label y el
// tipo se encimaran en pantallas de densidad alta, donde 12.sp no son 12 pixeles.
private const val NODE_PADDING = 10f
private const val TEXT_GAP = 4f
private const val COLUMN_GAP = 28f
private const val ROW_GAP = 52f
private const val MIN_SCALE = 0.02f
private const val MAX_SCALE = 3f

// Debajo de esta escala el texto es una mancha: se dibujan solo las cajas. Es lo que
// hace usable el arbol de ANTLR completo, que son cientos de nodos.
private const val TEXT_MIN_SCALE = 0.4f

private val LABEL_STYLE = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
private val DETAIL_STYLE = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)

private class MeasuredNode(
    val node: PositionedNode,
    val label: TextLayoutResult,
    val detail: TextLayoutResult?
) {
    val width: Float = maxOf(label.size.width, detail?.size?.width ?: 0) + 2 * NODE_PADDING
}

/**
 * El arbol ya medido y posicionado: todo lo que no cambia mientras se navega.
 *
 * Se calcula una vez por arbol. El dibujo solo lee de aqui, asi que arrastrar y hacer
 * zoom no vuelve a medir texto ni a resolver aristas.
 */
private class PreparedTree(root: TreeNodeView, textMeasurer: TextMeasurer) {
    val layout = layoutTree(root)

    val nodes = layout.nodes.map { node ->
        MeasuredNode(
            node = node,
            label = textMeasurer.measure(node.label, LABEL_STYLE),
            detail = node.detail?.let { textMeasurer.measure(it, DETAIL_STYLE) }
        )
    }

    // El ancho de columna sale del nodo mas ancho: asi ninguno se sale de la suya y
    // dos vecinos no se pueden encimar. Con el alto es lo mismo, en vertical.
    val columnWidth = nodes.maxOf { it.width } + COLUMN_GAP

    private val labelHeight = nodes.maxOf { it.label.size.height }.toFloat()
    private val detailHeight = nodes.maxOf { it.detail?.size?.height ?: 0 }.toFloat()

    // Uniforme para todos: los nodos con tipo y los sin tipo tienen que quedar en la
    // misma linea de su nivel.
    val nodeHeight =
        labelHeight + (if (detailHeight > 0f) TEXT_GAP + detailHeight else 0f) + 2 * NODE_PADDING

    private val rowHeight = nodeHeight + ROW_GAP

    val width = layout.columns * columnWidth
    val height = layout.levels * rowHeight

    fun centerX(node: PositionedNode) = node.column * columnWidth + columnWidth / 2f

    fun topY(node: PositionedNode) = node.depth * rowHeight

    // Las aristas ya resueltas a puntos. Antes el dibujo armaba un mapa por id en
    // cada frame; ahora dibujar es recorrer una lista.
    val edges: List<Pair<Offset, Offset>> = run {
        val byId = layout.nodes.associateBy { it.id }
        layout.edges.mapNotNull { (parentId, childId) ->
            val parent = byId[parentId] ?: return@mapNotNull null
            val child = byId[childId] ?: return@mapNotNull null
            Offset(centerX(parent), topY(parent) + nodeHeight) to
                Offset(centerX(child), topY(child))
        }
    }
}

/**
 * Dibuja un arbol.
 *
 * No sabe de donde vino: recibe un TreeNodeView, y el mismo componente sirve para el
 * arbol de ANTLR y para el AST.
 */
@Composable
fun TreeCanvas(
    root: TreeNodeView?,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No disponible."
) {
    if (root == null) {
        EmptyCanvas(emptyMessage, modifier)
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val tree = remember(root, textMeasurer) { PreparedTree(root, textMeasurer) }
    val colors = MaterialTheme.colorScheme

    // scale y offset solo se leen DENTRO del lambda de dibujo, asi que arrastrar
    // invalida el dibujo pero no recompone.
    var scale by remember(tree) { mutableStateOf(1f) }
    var offset by remember(tree) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun fit() {
        if (viewport.width == 0 || viewport.height == 0) return

        scale = minOf(viewport.width / tree.width, viewport.height / tree.height)
            .coerceIn(MIN_SCALE, MAX_SCALE)
        offset = Offset(x = (viewport.width - tree.width * scale) / 2f, y = 8f)
    }

    // Ajusta solo la PRIMERA vez que se conoce el tamaño del area: despues manda lo
    // que el usuario haya hecho con el zoom.
    var hasFitted by remember(tree) { mutableStateOf(false) }
    LaunchedEffect(tree, viewport) {
        if (!hasFitted && viewport.width > 0 && viewport.height > 0) {
            fit()
            hasFitted = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TreeToolbar(
            nodeCount = tree.nodes.size,
            levels = tree.layout.levels,
            onZoomIn = { scale = (scale * 1.4f).coerceAtMost(MAX_SCALE) },
            onZoomOut = { scale = (scale / 1.4f).coerceAtLeast(MIN_SCALE) },
            onFit = { fit() }
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerLowest, MaterialTheme.shapes.extraSmall)
                // Sin esto el dibujo se sale del panel y se encima con el de al lado.
                .clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(tree) {
                    detectDragGestures { _, dragAmount -> offset += dragAmount }
                }
        ) {
            // Lo que se ve, en coordenadas del arbol. Todo lo que caiga fuera no se
            // dibuja: con el arbol de ANTLR acercado, eso descarta casi todo.
            val visible = Rect(
                left = -offset.x / scale,
                top = -offset.y / scale,
                right = (size.width - offset.x) / scale,
                bottom = (size.height - offset.y) / scale
            )

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawEdges(tree, visible, colors.outline)
                drawNodes(
                    tree = tree,
                    visible = visible,
                    withText = scale >= TEXT_MIN_SCALE,
                    borderColor = colors.primary,
                    labelColor = colors.onSurface,
                    detailColor = colors.onSurfaceVariant
                )
            }
        }
    }
}

private fun DrawScope.drawEdges(tree: PreparedTree, visible: Rect, color: Color) {
    tree.edges.forEach { (start, end) ->
        // La arista se descarta solo si su caja envolvente no toca lo visible.
        val outside = maxOf(start.x, end.x) < visible.left ||
            minOf(start.x, end.x) > visible.right ||
            maxOf(start.y, end.y) < visible.top ||
            minOf(start.y, end.y) > visible.bottom
        if (outside) return@forEach

        drawLine(color = color, start = start, end = end, strokeWidth = 1.5f)
    }
}

private fun DrawScope.drawNodes(
    tree: PreparedTree,
    visible: Rect,
    withText: Boolean,
    borderColor: Color,
    labelColor: Color,
    detailColor: Color
) {
    tree.nodes.forEach { item ->
        val centerX = tree.centerX(item.node)
        val top = tree.topY(item.node)
        val left = centerX - item.width / 2f

        if (left > visible.right || left + item.width < visible.left) return@forEach
        if (top > visible.bottom || top + tree.nodeHeight < visible.top) return@forEach

        drawRoundRect(
            color = borderColor,
            topLeft = Offset(left, top),
            size = Size(item.width, tree.nodeHeight),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 1.5f)
        )

        if (!withText) return@forEach

        // Sin tipo el label va centrado; con tipo, arriba y el tipo debajo.
        val labelTop =
            if (item.detail == null) top + (tree.nodeHeight - item.label.size.height) / 2f
            else top + NODE_PADDING

        drawText(
            textLayoutResult = item.label,
            color = labelColor,
            topLeft = Offset(centerX - item.label.size.width / 2f, labelTop)
        )

        item.detail?.let { detail ->
            drawText(
                textLayoutResult = detail,
                color = detailColor,
                topLeft = Offset(
                    x = centerX - detail.size.width / 2f,
                    y = labelTop + item.label.size.height + TEXT_GAP
                )
            )
        }
    }
}

@Composable
private fun TreeToolbar(
    nodeCount: Int,
    levels: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$nodeCount nodos · $levels niveles",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onZoomOut) { Text("−") }
        TextButton(onClick = onZoomIn) { Text("+") }
        TextButton(onClick = onFit) { Text("Ajustar") }
    }
}

@Composable
private fun EmptyCanvas(message: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
