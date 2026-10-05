package org.compiler.frontend.intermediate.models

/**
 * Lo que produce el generador de codigo intermedio para un programa.
 */
data class TacProgram(
    val instructions: List<Quadruple>,

    // Cuantos temporales distintos hicieron falta. Es la medida que el pool con
    // conteo de usos minimiza.
    val temporaryCount: Int
)
