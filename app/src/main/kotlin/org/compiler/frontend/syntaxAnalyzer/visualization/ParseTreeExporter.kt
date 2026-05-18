package org.compiler.frontend.syntaxAnalyzer.visualization

import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree

object ParseTreeExporter {

    fun toIndentedText(tree: ParseTree): String {
        val lines = mutableListOf<String>()
        renderText(tree, lines, prefix = "", isRoot = true, isLast = true)
        return lines.joinToString("\n")
    }

    private fun renderText(
        node: ParseTree,
        lines: MutableList<String>,
        prefix: String,
        isRoot: Boolean,
        isLast: Boolean
    ) {
        val connector = if (isRoot) "" else "+-- "
        val nodeText = when (node) {
            is ParseTree.InternalNode -> {
                val bodyStr = node.production.body.joinToString(" ") { it.name }
                "${node.symbol.name} [${node.production.head.name} -> $bodyStr]"
            }
            is ParseTree.LeafNode  -> "${node.symbol.name} \"${node.entry.token.lexeme}\""
            is ParseTree.EpsilonNode -> "ε"
        }
        lines.add("$prefix$connector$nodeText")

        if (node is ParseTree.InternalNode) {
            val childPrefix = if (isRoot) "" else prefix + if (isLast) "    " else "|   "
            node.children.forEachIndexed { i, child ->
                renderText(child, lines, childPrefix, isRoot = false, isLast = i == node.children.size - 1)
            }
        }
    }

    fun toDot(tree: ParseTree): String {
        val counter = intArrayOf(0)
        val nodes = StringBuilder()
        val edges = StringBuilder()
        buildDot(tree, counter, nodes, edges)

        return buildString {
            appendLine("digraph ParseTree {")
            appendLine("  rankdir=TB;")
            appendLine("  ordering=out;")
            appendLine()
            append(nodes)
            appendLine()
            append(edges)
            append("}")
        }
    }

    private fun buildDot(
        node: ParseTree,
        counter: IntArray,
        nodes: StringBuilder,
        edges: StringBuilder
    ) {
        val myId = counter[0]++
        when (node) {
            is ParseTree.InternalNode -> {
                nodes.appendLine("  n$myId [label=\"${node.symbol.name}\", shape=ellipse];")
                node.children.forEach { child ->
                    val childId = counter[0]
                    edges.appendLine("  n$myId -> n${childId};")
                    buildDot(child, counter, nodes, edges)
                }
            }
            is ParseTree.LeafNode -> {
                val label = "${node.symbol.name}\\n\\\"${node.entry.token.lexeme}\\\""
                nodes.appendLine("  n$myId [label=\"$label\", shape=box];")
            }
            is ParseTree.EpsilonNode -> {
                nodes.appendLine("  n$myId [label=\"ε\", shape=plaintext];")
            }
        }
    }
}