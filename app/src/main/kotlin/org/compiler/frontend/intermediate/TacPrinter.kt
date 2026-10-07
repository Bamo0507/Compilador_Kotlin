package org.compiler.frontend.intermediate

import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.IndexedLoad
import org.compiler.frontend.intermediate.models.IndexedStore
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Param
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.QuadrupleRow
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Unary

/**
 * La sintaxis textual del TAC. Esta aparte del modelo porque como se escribe una
 * instruccion es una vista, no un dato: la misma regla que separa Type de TypeRules.
 */
object TacPrinter {

    private const val INDENT = "    "

    fun print(quadruples: List<Quadruple>): String =
        quadruples.joinToString("\n") { line(it) }

    // Las etiquetas van pegadas al margen y el resto con sangria, para que los
    // destinos de los saltos se encuentren de un vistazo.
    fun line(quadruple: Quadruple): String = when (quadruple) {
        is LabelDefinition -> "${label(quadruple.label)}:"
        is FunctionBegin, is FunctionEnd -> instruction(quadruple)
        else -> INDENT + instruction(quadruple)
    }

    private fun instruction(quadruple: Quadruple): String = when (quadruple) {
        is Arithmetic -> "${address(quadruple.result)} = ${address(quadruple.left)} " +
            "${quadruple.operator.symbol}${quadruple.kind.suffix} ${address(quadruple.right)}"
        is Relational -> "${address(quadruple.result)} = ${address(quadruple.left)} " +
            "${quadruple.operator.symbol}${quadruple.kind.suffix} ${address(quadruple.right)}"
        is Concat -> "${address(quadruple.result)} = ${address(quadruple.left)} concat " +
            address(quadruple.right)
        is Unary -> "${address(quadruple.result)} = ${unarySymbol(quadruple)} " +
            address(quadruple.operand)
        is Copy -> "${address(quadruple.result)} = ${address(quadruple.source)}"
        is LabelDefinition -> "${label(quadruple.label)}:"
        is Goto -> "goto ${label(quadruple.label)}"
        is IfGoto -> "if ${address(quadruple.condition)} goto ${label(quadruple.label)}"
        is IfFalseGoto -> "ifFalse ${address(quadruple.condition)} goto ${label(quadruple.label)}"
        is IfRelationalGoto -> "if ${address(quadruple.left)} " +
            "${quadruple.operator.symbol}${quadruple.kind.suffix} ${address(quadruple.right)} " +
            "goto ${label(quadruple.label)}"
        is FunctionBegin -> "begin_func ${quadruple.function.name}, ${quadruple.frameSize}"
        is FunctionEnd -> "end_func ${quadruple.function.name}"
        is Param -> "param ${address(quadruple.value)}"
        is Call -> callText(quadruple)
        is Return -> quadruple.value?.let { "return ${address(it)}" } ?: "return"
        is IndexedLoad -> "${address(quadruple.result)} = " +
            "${address(quadruple.base)}[${address(quadruple.offset)}]"
        is IndexedStore -> "${address(quadruple.base)}[${address(quadruple.offset)}] = " +
            address(quadruple.value)
        is Print -> "${printOperator(quadruple.kind)} ${address(quadruple.value)}"
        is TryBegin -> "try ${label(quadruple.handler)}, ${address(quadruple.exceptionVariable)}"
        TryEnd -> "endtry"
        is Throw -> "throw ${address(quadruple.message)}"
    }

    private fun callText(call: Call): String {
        val text = "call ${call.function.name}, ${call.argumentCount}"
        return call.result?.let { "${address(it)} = $text" } ?: text
    }

    fun address(address: Address): String = when (address) {
        // Con sufijo solo si otra variable con el mismo nombre se confundiria con esta.
        is Name -> address.symbol.tacName ?: address.symbol.name
        is Temporary -> "t${address.index}"
        is Constant -> constant(address.value)
    }

    fun label(label: Label): String = "L${label.index}"

    private fun constant(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"$value\""
        else -> value.toString()
    }

    // El negativo lleva sufijo porque negar un entero y un flotante son instrucciones
    // distintas; el not y la conversion ya dicen su tipo.
    private fun unarySymbol(unary: Unary): String = when (unary.operator) {
        TacUnaryOperator.NEGATE -> unary.operator.symbol + unary.kind.suffix
        TacUnaryOperator.NOT, TacUnaryOperator.INT_TO_FLOAT -> unary.operator.symbol
    }

    private fun printOperator(kind: OperandKind): String = when (kind) {
        OperandKind.INTEGER -> "print_i"
        OperandKind.FLOAT -> "print_f"
        OperandKind.STRING -> "print_s"
        OperandKind.BOOLEAN -> "print_b"
        OperandKind.REFERENCE -> error("print de una referencia: el TypeChecker lo rechaza")
    }

    // La vista de cuatro columnas. En las instrucciones que escriben algo, la columna
    // de resultado es lo que se escribe; en los saltos, la etiqueta de destino.
    fun row(quadruple: Quadruple): QuadrupleRow = when (quadruple) {
        is Arithmetic -> QuadrupleRow(
            quadruple.operator.symbol + quadruple.kind.suffix,
            address(quadruple.left), address(quadruple.right), address(quadruple.result)
        )
        is Relational -> QuadrupleRow(
            quadruple.operator.symbol + quadruple.kind.suffix,
            address(quadruple.left), address(quadruple.right), address(quadruple.result)
        )
        is Concat -> QuadrupleRow(
            "concat", address(quadruple.left), address(quadruple.right), address(quadruple.result)
        )
        is Unary -> QuadrupleRow(
            unaryRowOperator(quadruple), address(quadruple.operand), "", address(quadruple.result)
        )
        is Copy -> QuadrupleRow("=", address(quadruple.source), "", address(quadruple.result))
        is LabelDefinition -> QuadrupleRow("label", "", "", label(quadruple.label))
        is Goto -> QuadrupleRow("goto", "", "", label(quadruple.label))
        is IfGoto -> QuadrupleRow("if", address(quadruple.condition), "", label(quadruple.label))
        is IfFalseGoto -> QuadrupleRow(
            "ifFalse", address(quadruple.condition), "", label(quadruple.label)
        )
        is IfRelationalGoto -> QuadrupleRow(
            "if" + quadruple.operator.symbol + quadruple.kind.suffix,
            address(quadruple.left), address(quadruple.right), label(quadruple.label)
        )
        is FunctionBegin -> QuadrupleRow(
            "begin_func", quadruple.function.name, quadruple.frameSize.toString(), ""
        )
        is FunctionEnd -> QuadrupleRow("end_func", quadruple.function.name, "", "")
        is Param -> QuadrupleRow("param", address(quadruple.value), "", "")
        is Call -> QuadrupleRow(
            "call", quadruple.function.name, quadruple.argumentCount.toString(),
            quadruple.result?.let { address(it) } ?: ""
        )
        is Return -> QuadrupleRow("return", quadruple.value?.let { address(it) } ?: "", "", "")
        is IndexedLoad -> QuadrupleRow(
            "=[]", address(quadruple.base), address(quadruple.offset), address(quadruple.result)
        )
        is IndexedStore -> QuadrupleRow(
            "[]=", address(quadruple.offset), address(quadruple.value), address(quadruple.base)
        )
        is Print -> QuadrupleRow(printOperator(quadruple.kind), address(quadruple.value), "", "")
        is TryBegin -> QuadrupleRow(
            "try", address(quadruple.exceptionVariable), "", label(quadruple.handler)
        )
        TryEnd -> QuadrupleRow("endtry", "", "", "")
        is Throw -> QuadrupleRow("throw", address(quadruple.message), "", "")
    }

    // En la tabla el menos unario se escribe `minus`, como en el Dragon Book: en la
    // columna de operador no se distinguiria del binario.
    private fun unaryRowOperator(unary: Unary): String = when (unary.operator) {
        TacUnaryOperator.NEGATE -> "minus" + unary.kind.suffix
        TacUnaryOperator.NOT, TacUnaryOperator.INT_TO_FLOAT -> unary.operator.symbol
    }
}

// La vista de cuatro columnas de un cuadruplo, como en la diapositiva 25.
fun Quadruple.toRow(): QuadrupleRow = TacPrinter.row(this)
