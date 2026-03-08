package org.compiler.lexicalAnalyzer.manageGrammar.models

import org.compiler.lexicalAnalyzer.manageGrammar.utils.EPSILON

sealed class TreeNode {
    abstract var firstPos: List<Int>
    abstract var lastPos: List<Int>
    abstract var isNullable: Boolean
}

data class BinaryNode(
    val operator: Char,
    val left: TreeNode,
    val right: TreeNode,
    override var firstPos: List<Int> = emptyList(),
    override var lastPos: List<Int> = emptyList(),
    override var isNullable: Boolean = false
) : TreeNode()

data class UnaryNode(
    val operator: Char,
    val child: TreeNode,
    override var firstPos: List<Int> = emptyList(),
    override var lastPos: List<Int> = emptyList(),
    override var isNullable: Boolean = false
) : TreeNode()

// symbol: the character this leaf represents. EPSILON for ε leaves.
// position: nullable — assigned during tree labeling, null until then.
data class Leaf(
    val symbol: Char,
    var position: Int? = null,
    override var firstPos: List<Int> = emptyList(),
    override var lastPos: List<Int> = emptyList(),
    override var isNullable: Boolean = symbol == EPSILON
) : TreeNode()
