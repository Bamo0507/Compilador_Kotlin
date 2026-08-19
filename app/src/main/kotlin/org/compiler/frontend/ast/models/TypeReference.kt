package org.compiler.frontend.ast.models

import org.compiler.models.LexemeLocation

/**
 * El tipo tal como el programador lo ESCRIBIO, sin resolver.
 *
 *   "integer" - TypeReference("integer", 0)
 *   "integer[][]" - TypeReference("integer", 2)
 *   "Perro" - TypeReference("Perro", 0)
 *
 * No guarda un Type porque al construir el AST todavia no se sabe si la clase existe:
 * `let p: Perro` puede aparecer antes de `class Perro`. El TypeResolver lo convierte
 * despues, cuando ya recorrio el programa completo.
 */
data class TypeReference(
    val baseName: String,
    val arrayDimensions: Int,
    override val location: LexemeLocation
) : Node {
    val name: String get() = baseName + "[]".repeat(arrayDimensions)
}
