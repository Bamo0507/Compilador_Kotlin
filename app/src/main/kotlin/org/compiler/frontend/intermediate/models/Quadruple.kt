package org.compiler.frontend.intermediate.models

/**
 * Una instruccion de tres direcciones.
 *
 * Una clase por familia y no un registro generico (op, arg1, arg2, resultado): asi cada
 * instruccion trae exactamente sus campos, y un `when` sin `else` no compila si se
 * olvida una familia. Ninguna tiene mas de cuatro campos con contenido, asi que todas
 * siguen siendo cuadruplos.
 *
 * Las que mueven un valor llevan ademas su tipo (`kind`). El TAC impreso no lo muestra,
 * pero la maquina lo necesita: un flotante mide 8 bytes y se mueve con otras
 * instrucciones que un entero, y un booleano mide 1.
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
    val source: Address,
    val kind: OperandKind
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
data class Param(
    val value: Address,
    val kind: OperandKind
) : Quadruple

// call f, n    /    x = call f, n. Sin resultado si la funcion es void.
//
// accessHops: cuantos enlaces sube quien llama para encontrar el registro de la funcion
// que contiene a la llamada, que es el enlace de acceso que recibe. Null para las
// funciones del nivel superior, que no lo usan.
data class Call(
    val result: Address?,
    val function: FunctionLabel,
    val argumentCount: Int,
    val resultKind: OperandKind?,
    val accessHops: Int? = null
) : Quadruple {
    init {
        require((result == null) == (resultKind == null)) { "Un resultado sin tipo, o al reves" }
    }
}

// return    /    return x
data class Return(
    val value: Address?,
    val kind: OperandKind?
) : Quadruple {
    init {
        require((value == null) == (kind == null)) { "Un valor sin tipo, o al reves" }
    }
}

// x = y[i]. El indice es un desplazamiento en bytes, no el numero del elemento.
data class IndexedLoad(
    val result: Address,
    val base: Address,
    val offset: Address,
    val kind: OperandKind
) : Quadruple

// x[i] = y
data class IndexedStore(
    val base: Address,
    val offset: Address,
    val value: Address,
    val kind: OperandKind
) : Quadruple

// push_<tipo> lista, x: agrega x al final de la lista. Si el contenido esta lleno, lo
// muda a uno del doble de capacidad. Es una instruccion aparte porque en la maquina es
// una rutina, igual que concat.
data class ListPush(
    val list: Address,
    val value: Address,
    val kind: OperandKind
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

data class Allocate(
    val result: Address,
    val size: Address
) : Quadruple

// call t, n    /    x = call t, n: llama a la direccion que esta en t.
//
// method: el metodo que resolvio el verificador, aunque en ejecucion corra el de una
// subclase. Todas las versiones de un metodo tienen la misma firma, asi que sus
// registros de activacion empiezan igual, y la maquina necesita saber cuanto mide esa
// parte para armar la llamada.
data class IndirectCall(
    val result: Address?,
    val target: Address,
    val argumentCount: Int,
    val method: FunctionLabel,
    val resultKind: OperandKind?
) : Quadruple {
    init {
        require((result == null) == (resultKind == null)) { "Un resultado sin tipo, o al reves" }
    }
}

data class VirtualTableDefinition(
    val className: String,
    val methods: List<FunctionLabel>
) : Quadruple
