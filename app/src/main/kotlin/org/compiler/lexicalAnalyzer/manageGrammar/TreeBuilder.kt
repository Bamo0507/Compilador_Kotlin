package org.compiler.lexicalAnalyzer.manageGrammar

import org.compiler.lexicalAnalyzer.manageGrammar.models.*
import org.compiler.lexicalAnalyzer.manageGrammar.utils.BINARY_OPERATORS
import org.compiler.lexicalAnalyzer.manageGrammar.utils.EPSILON
import org.compiler.lexicalAnalyzer.manageGrammar.utils.UNARY_OPERATORS

// Builds a syntax tree from an augmented postfix regex string
// Operates first operations for building dfa
fun buildSyntaxTree(augmentedPostfix: String): TreeNode {
    val stack = ArrayDeque<TreeNode>()
    var positionCounter = 0
    var index = 0

    while (index < augmentedPostfix.length) {
        val character = augmentedPostfix[index]
        when {
            // Single-quoted char literal 'c' or '\x'
            // Read until closing quote to handle escape sequences like '\t', '\n'
            character == '\'' -> {
                index++ // skip opening quote
                val content = StringBuilder()
                while (index < augmentedPostfix.length && augmentedPostfix[index] != '\'') {
                    content.append(augmentedPostfix[index]); index++
                }
                if (index < augmentedPostfix.length) index++ // skip closing quote
                val symbol = resolveSymbol(content.toString())
                val leaf = Leaf(symbol = symbol, position = ++positionCounter)
                leaf.firstPos = listOf(positionCounter)
                leaf.lastPos = listOf(positionCounter)
                stack.addLast(leaf)
            }

            // Epsilon leaf — nullable, no position
            character == EPSILON -> {
                index++
                stack.addLast(Leaf(symbol = EPSILON))
            }

            // Unary operator
            character in UNARY_OPERATORS -> {
                index++
                val child = stack.removeLast()
                val node = UnaryNode(operator = character, child = child)
                computeUnary(node)
                stack.addLast(node)
            }

            // Binary operators: '.' and '|'
            character in BINARY_OPERATORS -> {
                index++
                val right = stack.removeLast()
                val left = stack.removeLast()
                val node = BinaryNode(operator = character, left = left, right = right)
                computeBinary(node)
                stack.addLast(node)
            }

            // Plain single-char operand
            else -> {
                index++
                val leaf = Leaf(symbol = character, position = ++positionCounter)
                leaf.firstPos = listOf(positionCounter)
                leaf.lastPos = listOf(positionCounter)
                stack.addLast(leaf)
            }
        }
    }

    return stack.removeLast()
}

// Converts the raw content between single quotes to the actual character
private fun resolveSymbol(content: String): Char = when (content) {
    "\\t" -> '\t'
    "\\n" -> '\n'
    "\\r" -> '\r'
    "\\\\" -> '\\'
    else -> content[0]
}

// Builds the followPos table from an already-constructed syntax tree
fun computeFollowPos(root: TreeNode): FollowingPositionTable {
    val symbolMap = mutableMapOf<Int, Char>()
    val followPosMap = mutableMapOf<Int, MutableSet<Int>>()

    collectLeaves(root, symbolMap, followPosMap)
    applyFollowPosRules(root, followPosMap)

    val table = FollowingPositionTable()
    for (pos in symbolMap.keys.sorted()) {
        table.add(FollowingPositionEntry(
            entry = symbolMap[pos]!!,
            transitions = followPosMap[pos]!!.sorted().toMutableList()
        ))
    }
    return table
}

// Collects every labeled leaf: stores its symbol and initializes an empty followPos set
private fun collectLeaves(
    node: TreeNode,
    symbolMap: MutableMap<Int, Char>,
    followPosMap: MutableMap<Int, MutableSet<Int>>
) {
    when (node) {
        is Leaf -> node.position?.let { pos ->
            symbolMap[pos] = node.symbol
            followPosMap[pos] = mutableSetOf()
        }
        is UnaryNode  -> collectLeaves(node.child, symbolMap, followPosMap)
        is BinaryNode -> {
            collectLeaves(node.left,  symbolMap, followPosMap)
            collectLeaves(node.right, symbolMap, followPosMap)
        }
    }
}

// Applies followPos rules via post-order traversal
private fun applyFollowPosRules(node: TreeNode, followPosMap: MutableMap<Int, MutableSet<Int>>) {
    when (node) {
        is Leaf -> {}
        is UnaryNode -> {
            applyFollowPosRules(node.child, followPosMap)
            if (node.operator == '*') {
                for (pos in node.lastPos) {
                    followPosMap[pos]?.addAll(node.firstPos)
                }
            }
        }
        is BinaryNode -> {
            applyFollowPosRules(node.left,  followPosMap)
            applyFollowPosRules(node.right, followPosMap)
            if (node.operator == '.') {
                for (pos in node.left.lastPos) {
                    followPosMap[pos]?.addAll(node.right.firstPos)
                }
            }
        }
    }
}

private fun computeUnary(node: UnaryNode) {
    node.isNullable = true
    node.firstPos = node.child.firstPos
    node.lastPos = node.child.lastPos
}

private fun computeBinary(node: BinaryNode) {
    when (node.operator) {
        '.' -> {
            node.isNullable = node.left.isNullable && node.right.isNullable
            node.firstPos = if (node.left.isNullable)
                (node.left.firstPos + node.right.firstPos).distinct()
            else
                node.left.firstPos
            node.lastPos = if (node.right.isNullable)
                (node.left.lastPos + node.right.lastPos).distinct()
            else
                node.right.lastPos
        }
        '|' -> {
            node.isNullable = node.left.isNullable || node.right.isNullable
            node.firstPos = (node.left.firstPos + node.right.firstPos).distinct()
            node.lastPos = (node.left.lastPos + node.right.lastPos).distinct()
        }
    }
}
