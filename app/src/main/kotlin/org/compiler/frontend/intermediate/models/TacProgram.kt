package org.compiler.frontend.intermediate.models

/**
 * Lo que produce el generador de codigo intermedio para un programa.
 */
data class TacProgram(
    val instructions: List<Quadruple>,

    // Cuantos temporales distintos hicieron falta, sumando los de cada funcion. Es la
    // medida que el pool con conteo de usos minimiza.
    val temporaryCount: Int,

    // El registro de activacion final de cada funcion, ya con sus temporales: el del
    // main primero y despues los demas en el orden en que se emitieron.
    val activationRecords: List<ActivationRecordLayout> = emptyList()
)
