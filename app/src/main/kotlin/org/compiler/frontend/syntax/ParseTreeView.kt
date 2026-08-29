package org.compiler.frontend.syntax

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.parser.CompiscriptParser

/**
 * Convierte el arbol de ANTLR a la vista neutral.
 *
 * Junto con el AstBuilder, es el ultimo archivo que importa org.compiler.parser: lo
 * llama el pipeline para que el ProgramContext no salga del backend.
 */
fun CompiscriptParser.ProgramContext.toTreeView(): TreeNodeView = viewOf(this)

private fun viewOf(tree: ParseTree): TreeNodeView = when (tree) {
    // Una hoja: el texto del token tal cual aparece en el fuente.
    is TerminalNode -> TreeNodeView(tree.text, detail = null, children = emptyList())

    // Un nodo interno: el nombre de la regla que lo produjo.
    is ParserRuleContext -> TreeNodeView(
        label = CompiscriptParser.ruleNames[tree.ruleIndex],
        detail = null,
        children = (0 until tree.childCount).map { viewOf(tree.getChild(it)) }
    )

    else -> TreeNodeView(tree.text, detail = null, children = emptyList())
}
