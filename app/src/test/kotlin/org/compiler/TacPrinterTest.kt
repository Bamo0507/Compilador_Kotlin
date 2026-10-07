package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
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
import org.compiler.frontend.intermediate.models.QuadrupleRow
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.intermediate.toRow
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TacPrinterTest {

    private fun name(name: String) = Name(
        Symbol(
            name = name,
            kind = DeclarationKind.VARIABLE,
            type = IntegerType,
            location = LexemeLocation(1, 1),
            scopeName = "global"
        )
    )

    private val a = name("a")
    private val b = name("b")
    private val c = name("c")
    private val t1 = Temporary(1)
    private val t2 = Temporary(2)
    private val l1 = Label(1)

    private fun assertLine(expected: String, quadruple: Quadruple) =
        assertEquals(expected, TacPrinter.line(quadruple))

    // ── Una linea por familia ──────────────────────────────────────────────

    @Test
    fun `las asignaciones llevan el sufijo de su tipo`() {
        assertLine(
            "    t1 = a + b",
            Arithmetic(t1, a, ArithmeticOperator.ADD, b, OperandKind.INTEGER)
        )
        assertLine(
            "    t1 = a *f 2.5",
            Arithmetic(t1, a, ArithmeticOperator.MULTIPLY, Constant(2.5), OperandKind.FLOAT)
        )
        assertLine(
            "    t1 = a < b",
            Relational(t1, a, RelationalOperator.LESS, b, OperandKind.INTEGER)
        )
        assertLine(
            "    t1 = a ==s \"hola\"",
            Relational(t1, a, RelationalOperator.EQUAL, Constant("hola"), OperandKind.STRING)
        )
        assertLine("    t1 = \"Hola \" concat a", Concat(t1, Constant("Hola "), a))
        assertLine("    t1 = - c", Unary(t1, TacUnaryOperator.NEGATE, c, OperandKind.INTEGER))
        assertLine("    t1 = -f c", Unary(t1, TacUnaryOperator.NEGATE, c, OperandKind.FLOAT))
        assertLine("    t1 = ! b", Unary(t1, TacUnaryOperator.NOT, b, OperandKind.BOOLEAN))
        assertLine(
            "    t1 = inttofloat a",
            Unary(t1, TacUnaryOperator.INT_TO_FLOAT, a, OperandKind.INTEGER)
        )
        assertLine("    a = t1", Copy(a, t1))
    }

    // La etiqueta va pegada al margen: es lo que hace que los destinos se encuentren
    // de un vistazo.
    @Test
    fun `las etiquetas van al margen y los saltos con sangria`() {
        assertLine("L1:", LabelDefinition(l1))
        assertLine("    goto L1", Goto(l1))
        assertLine("    if t1 goto L1", IfGoto(t1, l1))
        assertLine("    ifFalse t1 goto L1", IfFalseGoto(t1, l1))
        assertLine(
            "    if a >=f b goto L1",
            IfRelationalGoto(a, RelationalOperator.GREATER_EQUAL, b, OperandKind.FLOAT, l1)
        )
    }

    @Test
    fun `las llamadas con y sin resultado`() {
        assertLine("    param t1", Param(t1))
        assertLine("    t2 = call factorial, 1", Call(t2, FunctionLabel("factorial"), 1))
        assertLine("    call saludar, 0", Call(null, FunctionLabel("saludar"), 0))
        assertLine("    return t1", Return(t1))
        assertLine("    return", Return(null))
    }

    @Test
    fun `la copia indexada`() {
        assertLine("    t2 = a[t1]", IndexedLoad(t2, a, t1))
        assertLine("    a[t1] = 5", IndexedStore(a, t1, Constant(5L)))
    }

    @Test
    fun `print lleva el tipo en su nombre`() {
        assertLine("    print_i t1", Print(t1, OperandKind.INTEGER))
        assertLine("    print_f t1", Print(t1, OperandKind.FLOAT))
        assertLine("    print_s \"fin\"", Print(Constant("fin"), OperandKind.STRING))
        assertLine("    print_b true", Print(Constant(true), OperandKind.BOOLEAN))
    }

    @Test
    fun `try endtry y throw`() {
        assertLine("    try L1, a", TryBegin(l1, a))
        assertLine("    endtry", TryEnd)
        assertLine("    throw \"División entre cero (línea 7)\"", Throw(Constant("División entre cero (línea 7)")))
    }

    @Test
    fun `las constantes se escriben como en el codigo fuente`() {
        assertEquals("null", TacPrinter.address(Constant(null)))
        assertEquals("\"texto\"", TacPrinter.address(Constant("texto")))
        assertEquals("false", TacPrinter.address(Constant(false)))
        assertEquals("3.5", TacPrinter.address(Constant(3.5)))
        assertEquals("42", TacPrinter.address(Constant(42L)))
    }

    // El TypeChecker ya rechaza imprimir un objeto o una lista: si llega aqui, es un
    // bug del compilador.
    @Test
    fun `print de una referencia es un error interno`() {
        assertFailsWith<IllegalStateException> {
            TacPrinter.line(Print(a, OperandKind.REFERENCE))
        }
    }

    // ── El ejemplo de las diapositivas 24 y 25 ─────────────────────────────

    // a = b * - c + b * - c, armado a mano como lo genera la asignacion ingenua.
    private val slideExample = listOf(
        Unary(t1, TacUnaryOperator.NEGATE, c, OperandKind.INTEGER),
        Arithmetic(t2, b, ArithmeticOperator.MULTIPLY, t1, OperandKind.INTEGER),
        Unary(Temporary(3), TacUnaryOperator.NEGATE, c, OperandKind.INTEGER),
        Arithmetic(Temporary(4), b, ArithmeticOperator.MULTIPLY, Temporary(3), OperandKind.INTEGER),
        Arithmetic(Temporary(5), t2, ArithmeticOperator.ADD, Temporary(4), OperandKind.INTEGER),
        Copy(a, Temporary(5))
    )

    @Test
    fun `el ejemplo de la diapositiva 24 imprime su TAC`() {
        val expected = listOf(
            "    t1 = - c",
            "    t2 = b * t1",
            "    t3 = - c",
            "    t4 = b * t3",
            "    t5 = t2 + t4",
            "    a = t5"
        ).joinToString("\n")

        assertEquals(expected, TacPrinter.print(slideExample))
    }

    @Test
    fun `el ejemplo reproduce la tabla de cuadruplos de la diapositiva 25`() {
        val expected = listOf(
            QuadrupleRow("minus", "c", "", "t1"),
            QuadrupleRow("*", "b", "t1", "t2"),
            QuadrupleRow("minus", "c", "", "t3"),
            QuadrupleRow("*", "b", "t3", "t4"),
            QuadrupleRow("+", "t2", "t4", "t5"),
            QuadrupleRow("=", "t5", "", "a")
        )

        assertEquals(expected, slideExample.map { it.toRow() })
    }
}
