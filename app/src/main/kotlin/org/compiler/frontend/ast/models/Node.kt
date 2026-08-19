package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

/**
 * Todo nodo del AST sabe donde estaba en el archivo fuente. Sin esto no se puede
 * reportar un error con linea y columna.
 */
sealed interface Node {
    val location: LexemeLocation
}
