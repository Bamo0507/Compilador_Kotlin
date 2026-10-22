package org.compiler.backend.models

// Las formas que puede tomar un operando de una instruccion. El generador arma cada una
// con estas funciones y nunca escribe corchetes ni numerales a mano.

// `#5`: una constante que va dentro de la misma instruccion, como en `add r4, r5, #5`.
fun immediate(value: Long): String = "#$value"

// `[fp, #-8]`: la memoria en la direccion que esta en el registro base, mas un
// desplazamiento en bytes. Es como se lee una local: `ldr r4, [fp, #-8]`. Con
// desplazamiento 0 queda `[r4]`: la memoria a la que apunta r4.
fun memory(base: Register, offset: Int = 0): String =
    if (offset == 0) "[${base.text}]" else "[${base.text}, ${immediate(offset.toLong())}]"

// `[r4, r5]`: la memoria en la direccion r4 + r5. Es como se lee un elemento de una
// lista, con el contenido en r4 y el desplazamiento del elemento en r5.
fun memory(base: Register, offset: Register): String = "[${base.text}, ${offset.text}]"

// `=cps_estatico`: la direccion de una etiqueta. Una direccion no cabe dentro de una
// instruccion de 32 bits, asi que `ldr ip, =cps_estatico` le pide al ensamblador que la
// guarde en una tablita junto al codigo, el literal pool, y la cargue desde ahi.
fun literal(label: String): String = "=$label"

// `{fp, lr}`: los registros que push guarda en la pila, o que pop recupera de ella.
fun registerList(vararg registers: Register): String =
    registers.joinToString(", ", "{", "}") { it.text }
