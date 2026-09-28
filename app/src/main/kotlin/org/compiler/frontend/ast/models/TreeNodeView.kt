package org.compiler.frontend.ast.models

data class TreeNodeView(
    val label: String,
    val detail: String? = null,
    val children: List<TreeNodeView> = emptyList()
)
