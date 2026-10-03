package org.compiler.frontend.intermediate.models

// Un cuadruplo visto como la fila de una tabla de cuatro columnas. Una columna que la
// familia no usa queda vacia.
data class QuadrupleRow(
    val operator: String,
    val argument1: String,
    val argument2: String,
    val result: String
)
