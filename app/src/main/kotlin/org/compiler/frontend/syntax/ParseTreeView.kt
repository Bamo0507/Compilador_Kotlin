package org.compiler.frontend.syntax

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.parser.SqlParser


fun ParseTree.toTreeView(): TreeNodeView = when (this) {
    is TerminalNode -> terminalView(this)

    is ParserRuleContext -> TreeNodeView(
        label = SqlParser.ruleNames[ruleIndex],
        detail = alternativeOf(this),
        children = (0 until childCount).map { getChild(it).toTreeView() }
    )

    else -> TreeNodeView(label = text)
}


private fun terminalView(node: TerminalNode): TreeNodeView {
    val tokenType = node.symbol.type
    val keyword = SqlParser.VOCABULARY.getLiteralName(tokenType)

    return TreeNodeView(
        label = keyword?.removeSurrounding("'") ?: node.text,
        detail = if (keyword == null) SqlParser.VOCABULARY.getSymbolicName(tokenType) else null
    )
}


private fun alternativeOf(context: ParserRuleContext): String? {
    val alternative = context.javaClass.simpleName
        .removeSuffix("Context")
        .replaceFirstChar { it.lowercase() }

    return alternative.takeIf { it != SqlParser.ruleNames[context.ruleIndex] }
}
