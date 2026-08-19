package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

/**
 * Una sentencia: algo que se ejecuta, no produce un valor.
 *
 * Las implementaciones estan repartidas en Declarations.kt, SimpleStatements.kt y
 * ControlFlow.kt.
 */
sealed interface Statement : Node

data class Program(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Node
