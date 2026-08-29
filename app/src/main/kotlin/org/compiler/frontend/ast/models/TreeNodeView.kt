package org.compiler.frontend.ast.models

/**
 * Lo minimo que el dibujante necesita saber de un nodo.
 *
 * El arbol de ANTLR y el AST propio son tipos distintos; los dos se convierten a
 * esto, asi un solo componente dibuja los dos sin conocer ninguno.
 *
 * Vive aqui y no en gui/ para que el pipeline pueda convertir el arbol de ANTLR
 * antes de devolverlo, y la GUI nunca importe org.compiler.parser.
 */
data class TreeNodeView(
    val label: String,

    // El tipo decorado, para los nodos del AST. Null en el arbol de ANTLR, que no
    // lleva decoracion.
    val detail: String?,

    val children: List<TreeNodeView>
)
