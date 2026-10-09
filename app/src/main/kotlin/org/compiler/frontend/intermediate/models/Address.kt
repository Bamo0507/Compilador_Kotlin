package org.compiler.frontend.intermediate.models

import org.compiler.frontend.semantic.symbols.Symbol

// Lo que puede ir en un operando o en el resultado de un cuadruplo.
sealed interface Address

// Una variable del programa fuente. Guarda el Symbol y no el nombre: dos variables
// llamadas `x` en ambitos distintos son direcciones distintas.
//
// hops: cuantos enlaces de acceso hay que subir para llegar al registro donde vive.
// 0 es el registro propio; una funcion anidada que lee una local de su padre usa 1.
data class Name(val symbol: Symbol, val hops: Int = 0) : Address

// Long, Double, String o Boolean; null para el literal `null`.
data class Constant(val value: Any?) : Address

// Inventado por el compilador para guardar un resultado intermedio.
data class Temporary(val index: Int) : Address

// La direccion de la tabla de metodos de una clase, que vive en datos estaticos:
// `vtable.Perro`. Es lo que `new` escribe en la casilla 0 del objeto.
data class VirtualTableAddress(val className: String) : Address

// El destino de un salto. No es un Address: nunca se lee ni se escribe como valor.
data class Label(val index: Int)

// El punto de entrada de una funcion. Lleva nombre y no numero porque la llamada lo
// nombra, y es un tipo aparte de Label porque a una funcion no se salta con goto.
data class FunctionLabel(val name: String)
