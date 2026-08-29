package org.compiler.gui.components

import org.compiler.frontend.ast.models.TreeNodeView

/**
 * Un nodo con su posicion ya calculada, en unidades de columna y nivel.
 *
 * Las unidades no son pixeles a proposito: el layout se puede probar sin Compose, y
 * el canvas decide despues cuanto mide una columna.
 */
internal data class PositionedNode(
    val id: Int,
    val label: String,
    val detail: String?,
    val column: Float,
    val depth: Int
)

internal data class TreeLayout(
    val nodes: List<PositionedNode>,
    val edges: List<Pair<Int, Int>>,
    val columns: Float,
    val levels: Int
)

/**
 * Coloca el arbol: las hojas ocupan columnas consecutivas y cada padre se centra
 * sobre sus hijos.
 *
 * Es la primera pasada del algoritmo de Reingold-Tilford. No corre contornos, asi
 * que dos subarboles vecinos pueden quedar mas separados de lo necesario, pero nunca
 * encimados: para dibujar un arbol sintactico alcanza y se lee en diez lineas.
 */
internal fun layoutTree(root: TreeNodeView): TreeLayout {
    val nodes = mutableListOf<PositionedNode>()
    val edges = mutableListOf<Pair<Int, Int>>()
    var nextLeafColumn = 0f
    var nextId = 0

    fun place(node: TreeNodeView, depth: Int): PositionedNode {
        val id = nextId++

        // Las hojas mandan: son las que consumen columnas.
        if (node.children.isEmpty()) {
            val leaf = PositionedNode(id, node.label, node.detail, nextLeafColumn, depth)
            nextLeafColumn += 1f
            nodes.add(leaf)
            return leaf
        }

        val children = node.children.map { place(it, depth + 1) }
        val column = (children.first().column + children.last().column) / 2f

        val parent = PositionedNode(id, node.label, node.detail, column, depth)
        nodes.add(parent)
        children.forEach { child -> edges.add(id to child.id) }
        return parent
    }

    place(root, depth = 0)

    return TreeLayout(
        nodes = nodes,
        edges = edges,
        columns = nextLeafColumn.coerceAtLeast(1f),
        levels = (nodes.maxOfOrNull { it.depth } ?: 0) + 1
    )
}
