package org.compiler.frontend.models

import org.compiler.models.LexemeLocation

data class TokenEntry(
    val token: Token,
    val location: LexemeLocation
)
