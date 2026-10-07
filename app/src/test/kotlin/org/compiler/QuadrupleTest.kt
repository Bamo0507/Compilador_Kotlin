package org.compiler

import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.FunctionLabel
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
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QuadrupleTest {

    // Sin `else` a proposito: si se agrega una familia y no su rama, este archivo no
    // compila. Esa es la garantia real; el test de abajo solo la hace visible.
    private fun familyName(quadruple: Quadruple): String = when (quadruple) {
        is Arithmetic -> "Arithmetic"
        is Relational -> "Relational"
        is Concat -> "Concat"
        is Unary -> "Unary"
        is Copy -> "Copy"
        is LabelDefinition -> "LabelDefinition"
        is Goto -> "Goto"
        is IfGoto -> "IfGoto"
        is IfFalseGoto -> "IfFalseGoto"
        is IfRelationalGoto -> "IfRelationalGoto"
        is FunctionBegin -> "FunctionBegin"
        is FunctionEnd -> "FunctionEnd"
        is Param -> "Param"
        is Call -> "Call"
        is Return -> "Return"
        is IndexedLoad -> "IndexedLoad"
        is IndexedStore -> "IndexedStore"
        is Print -> "Print"
        is TryBegin -> "TryBegin"
        TryEnd -> "TryEnd"
        is Throw -> "Throw"
    }

    private fun symbol(name: String, scopeName: String, line: Int) = Symbol(
        name = name,
        kind = DeclarationKind.VARIABLE,
        type = IntegerType,
        location = LexemeLocation(line, 1),
        scopeName = scopeName
    )

    @Test
    fun `existen las 21 familias de cuadruplos`() {
        val t1 = Temporary(1)
        val one = Constant(1L)
        val label = Label(1)

        val oneOfEach = listOf(
            Arithmetic(t1, one, ArithmeticOperator.ADD, one, OperandKind.INTEGER),
            Relational(t1, one, RelationalOperator.LESS, one, OperandKind.INTEGER),
            Concat(t1, Constant("a"), Constant("b")),
            Unary(t1, TacUnaryOperator.NEGATE, one, OperandKind.INTEGER),
            Copy(t1, one),
            LabelDefinition(label),
            Goto(label),
            IfGoto(t1, label),
            IfFalseGoto(t1, label),
            IfRelationalGoto(t1, RelationalOperator.LESS, one, OperandKind.INTEGER, label),
            FunctionBegin(FunctionLabel("f"), 16),
            FunctionEnd(FunctionLabel("f")),
            Param(t1),
            Call(t1, FunctionLabel("f"), 1),
            Return(t1),
            IndexedLoad(t1, t1, Constant(4L)),
            IndexedStore(t1, Constant(4L), one),
            Print(t1, OperandKind.INTEGER),
            TryBegin(label, t1),
            TryEnd,
            Throw(Constant("error"))
        )

        assertEquals(21, oneOfEach.map { familyName(it) }.toSet().size)
    }

    // && y || son saltos en el TAC, no operaciones: no hay valor de enum para ellos.
    @Test
    fun `no existe un operador logico entre los operadores del TAC`() {
        val symbols = ArithmeticOperator.entries.map { it.symbol } +
            RelationalOperator.entries.map { it.symbol }

        assertTrue("&&" !in symbols && "||" !in symbols)
    }

    @Test
    fun `dos variables con el mismo nombre en ambitos distintos son direcciones distintas`() {
        val outer = symbol("x", scopeName = "global", line = 1)
        val inner = symbol("x", scopeName = "block@3", line = 3)

        assertNotEquals(Name(outer), Name(inner))
        assertEquals(Name(outer), Name(outer))
    }
}
