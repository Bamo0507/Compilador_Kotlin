package org.compiler.frontend.intermediate.models

/**
 * Una instruccion de tres direcciones.
 *
 * Una clase por familia y no un registro generico (op, arg1, arg2, resultado): asi cada
 * instruccion trae exactamente sus campos, y un `when` sin `else` no compila si se
 * olvida una familia. Ninguna tiene mas de cuatro campos con contenido, asi que todas
 * siguen siendo cuadruplos.
 */
sealed interface Quadruple

// x = y op z
data class Arithmetic(
    val result: Address,
    val left: Address,
    val operator: ArithmeticOperator,
    val right: Address,
    val kind: OperandKind
) : Quadruple

// x = y relop z: un booleano como valor, no como salto.
data class Relational(
    val result: Address,
    val left: Address,
    val operator: RelationalOperator,
    val right: Address,
    val kind: OperandKind
) : Quadruple

// x = y concat z. Aparte de Arithmetic porque en la maquina no es una suma, sino una
// rutina que reserva memoria para el string nuevo.
data class Concat(
    val result: Address,
    val left: Address,
    val right: Address
) : Quadruple

// x = op y
data class Unary(
    val result: Address,
    val operator: TacUnaryOperator,
    val operand: Address,
    val kind: OperandKind
) : Quadruple

// x = y
data class Copy(
    val result: Address,
    val source: Address
) : Quadruple

// L:
data class LabelDefinition(val label: Label) : Quadruple

// goto L
data class Goto(val label: Label) : Quadruple

// if x goto L
data class IfGoto(
    val condition: Address,
    val label: Label
) : Quadruple

// ifFalse x goto L
data class IfFalseGoto(
    val condition: Address,
    val label: Label
) : Quadruple

// if x relop y goto L
data class IfRelationalGoto(
    val left: Address,
    val operator: RelationalOperator,
    val right: Address,
    val kind: OperandKind,
    val label: Label
) : Quadruple

// begin_func f, 24: aqui empieza f, y su registro de activacion mide 24 bytes.
data class FunctionBegin(
    val function: FunctionLabel,
    val frameSize: Int
) : Quadruple

// end_func f: llegar aqui es retornar sin valor.
data class FunctionEnd(val function: FunctionLabel) : Quadruple

// param x
data class Param(val value: Address) : Quadruple

// call f, n    /    x = call f, n. Sin resultado si la funcion es void.
data class Call(
    val result: Address?,
    val function: FunctionLabel,
    val argumentCount: Int
) : Quadruple

// return    /    return x
data class Return(val value: Address?) : Quadruple

// x = y[i]. El indice es un desplazamiento en bytes, no el numero del elemento.
data class IndexedLoad(
    val result: Address,
    val base: Address,
    val offset: Address
) : Quadruple

// x[i] = y
data class IndexedStore(
    val base: Address,
    val offset: Address,
    val value: Address
) : Quadruple

// print x. El tipo decide como se imprime: un entero y un string son llamadas al
// sistema distintas.
data class Print(
    val value: Address,
    val kind: OperandKind
) : Quadruple

// try L, e: si algo falla, el mensaje va a e y se salta a L.
data class TryBegin(
    val handler: Label,
    val exceptionVariable: Address
) : Quadruple

// endtry: el bloque protegido termino sin errores y se quita su manejador.
data object TryEnd : Quadruple

// throw x
data class Throw(val message: Address) : Quadruple
