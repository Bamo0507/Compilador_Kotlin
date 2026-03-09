package org.compiler.lexicalAnalyzer.manageGrammar.utils

import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.parse.Parser
import org.compiler.lexicalAnalyzer.manageGrammar.models.*
import java.io.File

fun visualizeTree(root: TreeNode, category: String) {
    val dot = buildDotString(root, category)
    val outputDir = File("generatedTrees")
    outputDir.mkdirs()
    Graphviz.fromGraph(Parser().read(dot))
        .render(Format.PNG)
        .toFile(File(outputDir, "$category.png"))
}

private fun buildDotString(root: TreeNode, graphName: String): String {
    val sb = StringBuilder()
    sb.appendLine("digraph \"$graphName\" {")
    sb.appendLine("  node [fontname=\"Courier\", shape=circle];")

    var counter = 0
    fun visit(node: TreeNode): Int {
        val id = counter++
        val label = when (node) {
            is Leaf -> if (node.symbol == EPSILON) "ε" else "${node.symbol}\\n(${node.position})"
            is UnaryNode -> "${node.operator}"
            is BinaryNode -> "${node.operator}"
        }
        sb.appendLine("  n$id [label=\"$label\"];")
        when (node) {
            is Leaf -> {}
            is UnaryNode -> {
                val childId = visit(node.child)
                sb.appendLine("  n$id -> n$childId;")
            }
            is BinaryNode -> {
                val leftId = visit(node.left)
                val rightId = visit(node.right)
                sb.appendLine("  n$id -> n$leftId;")
                sb.appendLine("  n$id -> n$rightId;")
            }
        }
        return id
    }

    visit(root)
    sb.appendLine("}")
    return sb.toString()
}
