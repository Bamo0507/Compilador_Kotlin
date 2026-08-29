package org.compiler.interpreter

import org.compiler.models.LexemeLocation

// Un error que solo se puede detectar ejecutando.
class RuntimeError(
    val location: LexemeLocation,
    override val message: String
) : RuntimeException(message)
