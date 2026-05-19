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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree

// Layout constants -- tuned for monospace text at 13sp.
private const val LEVEL_HEIGHT = 88f
private const val HORIZONTAL_GAP = 28f
private const val CHAR_WIDTH = 7.8f
private const val LINE_HEIGHT = 18f
private const val NODE_PADDING_X = 14f
private const val NODE_PADDING_Y = 10f
private const val MIN_NODE_WIDTH = 44f
private const val MIN_ZOOM = 0.2f
private const val MAX_ZOOM = 4f
private const val WHEEL_ZOOM_STEP = 1.12f
private const val TOP_MARGIN = 36f
private const val EDGE_KEEPALIVE = 48f  // pixels of the tree that must stay visible after panning

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParseTreeCanvas(
    tree: ParseTree,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val palette = NodePalette(
        internalFill = colors.primaryContainer,
        internalText = colors.onPrimaryContainer,
        leafFill = colors.secondaryContainer,
        leafText = colors.onSecondaryContainer,
        epsilonText = colors.onSurfaceVariant,
        edgeColor = colors.outline,
        border = colors.outlineVariant
    )
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = palette.internalText
    )
    val textMeasurer = rememberTextMeasurer()

    val laidOut = remember(tree) {
        val root = buildLayout(tree)
        computeWidths(root)
        assignPositions(root, leftX = 0f, y = 0f)
        root
    }
    val treeBounds = remember(laidOut) { computeBounds(laidOut) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember(tree) { mutableStateOf(Offset.Zero) }
    var zoom by remember(tree) { mutableStateOf(1f) }

    fun clampPan(candidate: Offset, currentZoom: Float): Offset {
        val canvasWidth = canvasSize.width.toFloat()
        val canvasHeight = canvasSize.height.toFloat()
        if (canvasWidth == 0f || canvasHeight == 0f) return candidate

        val scaledTreeWidth = treeBounds.size.width * currentZoom
        val scaledTreeHeight = treeBounds.size.height * currentZoom
        val canvasCenterX = canvasWidth / 2f

        val maxAbsPanX = (canvasCenterX + scaledTreeWidth / 2f - EDGE_KEEPALIVE).coerceAtLeast(0f)
        val minPanY = (EDGE_KEEPALIVE - TOP_MARGIN - scaledTreeHeight).coerceAtMost(0f)
        val maxPanY = (canvasHeight - EDGE_KEEPALIVE - TOP_MARGIN).coerceAtLeast(0f)

        return Offset(
            candidate.x.coerceIn(-maxAbsPanX, maxAbsPanX),
            candidate.y.coerceIn(minPanY, maxPanY)
        )
    }

    Box(
        modifier = modifier
            .background(colors.surfaceContainerLow)
            .border(1.dp, palette.border)
            .clipToBounds()
            .onSizeChanged { newSize ->
                canvasSize = newSize
                pan = clampPan(pan, zoom)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tree) {
                    detectDragGestures { _, dragAmount ->
                        pan = clampPan(pan + dragAmount, zoom)
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val scrollY = event.changes.first().scrollDelta.y
                    val factor = if (scrollY < 0f) WHEEL_ZOOM_STEP else 1f / WHEEL_ZOOM_STEP
                    val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    zoom = newZoom
                    pan = clampPan(pan, newZoom)
                }
        ) {
            val canvasCenterX = size.width / 2f
            val transform = TreeTransform(
                offsetX = canvasCenterX + pan.x - treeBounds.center.x * zoom,
                offsetY = TOP_MARGIN + pan.y,
                scale = zoom
            )

            drawEdges(laidOut, transform, palette)
            drawNodes(laidOut, transform, palette, textMeasurer, textStyle)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%.0f%%".format(zoom * 100f),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    pan = Offset.Zero
                    zoom = 1f
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.CenterFocusStrong,
                    contentDescription = "Reset view",
                    tint = colors.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Drag to pan -- scroll to zoom",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

private data class NodePalette(
    val internalFill: Color,
    val internalText: Color,
    val leafFill: Color,
    val leafText: Color,
    val epsilonText: Color,
    val edgeColor: Color,
    val border: Color
)

private data class TreeTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float
) {
    fun screenX(treeX: Float): Float = offsetX + treeX * scale
    fun screenY(treeY: Float): Float = offsetY + treeY * scale
    fun screenSize(treeSize: Float): Float = treeSize * scale
}

private enum class NodeKind { INTERNAL, LEAF, EPSILON }

private class LayoutNode(
    val kind: NodeKind,
    val label: String,
    val children: List<LayoutNode>
) {
    val intrinsicWidth: Float = estimateNodeWidth(label)
    val intrinsicHeight: Float = estimateNodeHeight(label)
    var subtreeWidth: Float = 0f
    var centerX: Float = 0f
    var topY: Float = 0f
}

private fun estimateNodeWidth(label: String): Float {
    val widestLine = label.lines().maxOf { it.length }
    val raw = widestLine * CHAR_WIDTH + NODE_PADDING_X * 2f
    return raw.coerceAtLeast(MIN_NODE_WIDTH)
}

private fun estimateNodeHeight(label: String): Float =
    label.lines().size * LINE_HEIGHT + NODE_PADDING_Y * 2f

private fun buildLayout(tree: ParseTree): LayoutNode = when (tree) {
    is ParseTree.InternalNode -> LayoutNode(
        kind = NodeKind.INTERNAL,
        label = tree.symbol.name,
        children = tree.children.map { buildLayout(it) }
    )
    is ParseTree.LeafNode -> LayoutNode(
        kind = NodeKind.LEAF,
        label = "${tree.symbol.name}\n\"${tree.entry.token.lexeme}\"",
        children = emptyList()
    )
    ParseTree.EpsilonNode -> LayoutNode(
        kind = NodeKind.EPSILON,
        label = "ε",
        children = emptyList()
    )
}

private fun computeWidths(node: LayoutNode) {
    if (node.children.isEmpty()) {
        node.subtreeWidth = node.intrinsicWidth
        return
    }
    node.children.forEach { computeWidths(it) }
    val childrenWidth = node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat() +
        HORIZONTAL_GAP * (node.children.size - 1)
    node.subtreeWidth = maxOf(node.intrinsicWidth, childrenWidth)
}

private fun assignPositions(node: LayoutNode, leftX: Float, y: Float) {
    node.topY = y
    if (node.children.isEmpty()) {
        node.centerX = leftX + node.subtreeWidth / 2f
        return
    }

    val childrenWidth = node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat() +
        HORIZONTAL_GAP * (node.children.size - 1)
    var cursor = leftX + (node.subtreeWidth - childrenWidth) / 2f
    for (child in node.children) {
        assignPositions(child, cursor, y + LEVEL_HEIGHT)
        cursor += child.subtreeWidth + HORIZONTAL_GAP
    }
    val firstChild = node.children.first()
    val lastChild = node.children.last()
    node.centerX = (firstChild.centerX + lastChild.centerX) / 2f
}

private data class TreeBounds(val center: Offset, val size: Size)

private fun computeBounds(root: LayoutNode): TreeBounds {
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    fun walk(node: LayoutNode) {
        val halfWidth = node.intrinsicWidth / 2f
        minX = minOf(minX, node.centerX - halfWidth)
        maxX = maxOf(maxX, node.centerX + halfWidth)
        minY = minOf(minY, node.topY)
        maxY = maxOf(maxY, node.topY + node.intrinsicHeight)
        node.children.forEach { walk(it) }
    }
    walk(root)

    val width = maxX - minX
    val height = maxY - minY
    return TreeBounds(
        center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f),
        size = Size(width, height)
    )
}

private fun DrawScope.drawEdges(
    node: LayoutNode,
    transform: TreeTransform,
    palette: NodePalette
) {
    val parentBottom = Offset(
        transform.screenX(node.centerX),
        transform.screenY(node.topY + node.intrinsicHeight)
    )
    for (child in node.children) {
        val childTop = Offset(
            transform.screenX(child.centerX),
            transform.screenY(child.topY)
        )
        drawLine(
            color = palette.edgeColor,
            start = parentBottom,
            end = childTop,
            strokeWidth = transform.screenSize(1.4f)
        )
        drawEdges(child, transform, palette)
    }
}

private fun DrawScope.drawNodes(
    node: LayoutNode,
    transform: TreeTransform,
    palette: NodePalette,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
) {
    drawSingleNode(node, transform, palette, textMeasurer, textStyle)
    node.children.forEach { drawNodes(it, transform, palette, textMeasurer, textStyle) }
}

private fun DrawScope.drawSingleNode(
    node: LayoutNode,
    transform: TreeTransform,
    palette: NodePalette,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
) {
    val width = transform.screenSize(node.intrinsicWidth)
    val height = transform.screenSize(node.intrinsicHeight)
    val centerScreen = Offset(
        transform.screenX(node.centerX),
        transform.screenY(node.topY + node.intrinsicHeight / 2f)
    )
    val topLeft = Offset(centerScreen.x - width / 2f, centerScreen.y - height / 2f)

    when (node.kind) {
        NodeKind.INTERNAL -> {
            drawRoundRect(
                color = palette.internalFill,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(transform.screenSize(10f))
            )
            drawRoundRect(
                color = palette.border,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(transform.screenSize(10f)),
                style = Stroke(width = transform.screenSize(1f))
            )
            drawCenteredText(node.label, centerScreen, textMeasurer, textStyle.copy(color = palette.internalText), transform.scale)
        }
        NodeKind.LEAF -> {
            drawRoundRect(
                color = palette.leafFill,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(transform.screenSize(4f))
            )
            drawRoundRect(
                color = palette.border,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(transform.screenSize(4f)),
                style = Stroke(width = transform.screenSize(1f))
            )
            drawCenteredText(node.label, centerScreen, textMeasurer, textStyle.copy(color = palette.leafText), transform.scale)
        }
        NodeKind.EPSILON -> {
            drawCenteredText(node.label, centerScreen, textMeasurer, textStyle.copy(color = palette.epsilonText), transform.scale)
        }
    }
}

private fun DrawScope.drawCenteredText(
    text: String,
    center: Offset,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    scale: Float
) {
    val scaledStyle = style.copy(fontSize = style.fontSize * scale)
    val layout = textMeasurer.measure(text, scaledStyle)
    val topLeft = Offset(
        center.x - layout.size.width / 2f,
        center.y - layout.size.height / 2f
    )
    drawText(layout, topLeft = topLeft)
}
