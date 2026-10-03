package org.compiler.frontend.intermediate.models

import org.compiler.frontend.semantic.symbols.Symbol

// Lo que puede ir en un operando o en el resultado de un cuadruplo.
sealed interface Address

// Una variable del programa fuente. Guarda el Symbol y no el nombre: dos variables
// llamadas `x` en ambitos distintos son direcciones distintas.
data class Name(val symbol: Symbol) : Address

// Long, Double, String o Boolean; null para el literal `null`.
data class Constant(val value: Any?) : Address

// Inventado por el compilador para guardar un resultado intermedio.
data class Temporary(val index: Int) : Address

// El destino de un salto. No es un Address: nunca se lee ni se escribe como valor.
data class Label(val index: Int)

// El punto de entrada de una funcion. Lleva nombre y no numero porque la llamada lo
// nombra, y es un tipo aparte de Label porque a una funcion no se salta con goto.
data class FunctionLabel(val name: String)
