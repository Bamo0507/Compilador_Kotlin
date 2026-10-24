// Los bloques basicos y la informacion de proximo uso del generador simple.
package org.compiler

import org.compiler.backend.BlockLiveness
import org.compiler.backend.NextUseAnalysis
import org.compiler.backend.Usage
import org.compiler.backend.basicBlocksOf
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasicBlocksTest {

    private fun variable(name: String) = Name(
        Symbol(name, DeclarationKind.VARIABLE, IntegerType, LexemeLocation(1, 1), "global")
    )

    private val a = variable("a")
    private val b = variable("b")
    private val c = variable("c")
    private val d = variable("d")
    private val t1 = Temporary(1)
    private val t2 = Temporary(2)
    private val t3 = Temporary(3)
    private val integer = OperandKind.INTEGER

    private fun subtract(result: Name, left: Name, right: Name) =
        Arithmetic(result, left, ArithmeticOperator.SUBTRACT, right, integer)

    private fun operation(result: Temporary, left: Name, right: Name) =
        Arithmetic(result, left, ArithmeticOperator.SUBTRACT, right, integer)

    // ── Los bloques ────────────────────────────────────────────────────────

    @Test
    fun `sin saltos todo es un solo bloque`() {
        val body = listOf(
            Copy(a, Constant(1L), integer),
            subtract(b, a, a),
            Print(b, integer)
        )

        val blocks = basicBlocksOf(body)

        assertEquals(1, blocks.size)
        assertEquals(body, blocks.single().instructions)
    }

    // Una etiqueta empieza un bloque: alguien puede saltar ahi.
    @Test
    fun `una etiqueta empieza un bloque nuevo`() {
        val body = listOf(
            Copy(a, Constant(1L), integer),
            LabelDefinition(Label(1)),
            Print(a, integer)
        )

        val blocks = basicBlocksOf(body)

        assertEquals(listOf(0, 1), blocks.map { it.start })
        assertEquals(LabelDefinition(Label(1)), blocks[1].instructions.first())
    }

    // Despues de un salto, condicional o no, y despues de una llamada, empieza otro.
    @Test
    fun `un salto y una llamada terminan su bloque`() {
        val body = listOf(
            IfRelationalGoto(a, RelationalOperator.LESS, b, integer, Label(1)),
            Print(a, integer),
            Goto(Label(2)),
            Call(null, FunctionLabel("f"), 0, null),
            Print(b, integer)
        )

        val blocks = basicBlocksOf(body)

        assertEquals(listOf(0, 1, 3, 4), blocks.map { it.start })
        assertEquals(listOf(1, 2, 1, 1), blocks.map { it.instructions.size })
    }

    @Test
    fun `un cuerpo vacio no tiene bloques`() {
        assertTrue(basicBlocksOf(emptyList()).isEmpty())
    }

    // ── El proximo uso ─────────────────────────────────────────────────────

    // El ejemplo del libro: t = a - b, u = a - c, v = t + u, a = d, d = v + u.
    private val bookExample: List<Quadruple> = listOf(
        operation(t1, a, b),
        operation(t2, a, c),
        Arithmetic(t3, t1, ArithmeticOperator.ADD, t2, integer),
        Copy(a, d, integer),
        Arithmetic(d, t3, ArithmeticOperator.ADD, t2, integer)
    )

    private fun analysisOfBookExample(): NextUseAnalysis {
        val blocks = basicBlocksOf(bookExample)
        return NextUseAnalysis(blocks.single(), BlockLiveness(blocks).liveAtExitOf(0))
    }

    @Test
    fun `cada operando sabe donde se vuelve a leer`() {
        val analysis = analysisOfBookExample()

        // Despues de t = a - b, a se lee otra vez en u = a - c.
        assertEquals(Usage(live = true, nextUse = 1), analysis.usageAfter(0, a))
        // b no se vuelve a leer en el bloque, pero es del programa: sigue viva.
        assertEquals(Usage(live = true, nextUse = null), analysis.usageAfter(0, b))
        // u se lee en v = t + u y en d = v + u.
        assertEquals(Usage(live = true, nextUse = 2), analysis.usageAfter(1, t2))
        assertEquals(Usage(live = true, nextUse = 4), analysis.usageAfter(2, t2))
    }

    // `a = d` escribe a: el valor de a de antes ya no le sirve a nadie despues de u = a - c.
    @Test
    fun `una variable que se va a sobrescribir no esta viva`() {
        val analysis = analysisOfBookExample()

        assertFalse(analysis.usageAfter(1, a).live)
    }

    // Un temporal muere en su ultima lectura: nadie lo lee despues del bloque.
    @Test
    fun `un temporal muere en su ultima lectura`() {
        val analysis = analysisOfBookExample()

        assertFalse(analysis.usageAfter(2, t1).live)
        assertFalse(analysis.usageAfter(4, t3).live)
        assertFalse(analysis.usageAfter(4, t2).live)
    }

    // En t1 = t1 * t2 la lectura va antes que la escritura: t1 se usa aqui mismo.
    @Test
    fun `leer y escribir la misma variable cuenta como lectura`() {
        val body = listOf(
            Arithmetic(t1, t1, ArithmeticOperator.MULTIPLY, t2, integer),
            Print(t1, integer)
        )
        val analysis = NextUseAnalysis(basicBlocksOf(body).single()) { false }

        assertEquals(Usage(live = true, nextUse = 1), analysis.usageAfter(0, t1))
    }

    // ── La vivacidad entre bloques ─────────────────────────────────────────

    // El resultado de un ternario se escribe en una rama y se lee despues de la etiqueta:
    // sigue vivo al salir del bloque de la rama.
    @Test
    fun `un temporal que se lee en otro bloque sigue vivo al salir`() {
        val blocks = basicBlocksOf(
            listOf(
                Copy(t1, Constant(1L), integer),
                Goto(Label(1)),
                LabelDefinition(Label(1)),
                Print(t1, integer)
            )
        )
        val liveness = BlockLiveness(blocks)

        assertTrue(liveness.liveAtExitOf(0)(t1))
        assertTrue(liveness.liveAtExitOf(0)(a), "una variable del programa siempre")
        assertFalse(liveness.liveAtExitOf(1)(t1), "nadie lo lee despues del ultimo bloque")
    }

    /**
     * El caso que obliga a mirar el grafo y no el archivo.
     *
     * Es el ternario tal como lo escribe el TacGenerator: la rama verdadera va primero y
     * termina en `goto`, y lo que esta escrito DESPUES de ese goto es la rama falsa, que
     * vuelve a escribir el mismo temporal. Leido en orden de archivo, t1 parece muerto al
     * salir de la rama verdadera, no se guarda, y esa rama termina imprimiendo basura.
     */
    @Test
    fun `un goto que salta sobre otra escritura del mismo temporal`() {
        val blocks = basicBlocksOf(
            listOf(
                IfRelationalGoto(a, RelationalOperator.LESS, b, integer, Label(1)),
                Copy(t1, Constant(1L), integer),
                Goto(Label(2)),
                LabelDefinition(Label(1)),
                Copy(t1, Constant(0L), integer),
                LabelDefinition(Label(2)),
                Print(t1, integer)
            )
        )
        val liveness = BlockLiveness(blocks)

        // Bloque 1: la rama verdadera, la que termina en `goto L2`.
        assertTrue(liveness.liveAtExitOf(1)(t1), "el camino real va a L2, donde se lee")
        // Bloque 2: la rama falsa, que cae en L2.
        assertTrue(liveness.liveAtExitOf(2)(t1))
    }

    // Un ciclo salta hacia atras, asi que la vivacidad tiene que dar varias vueltas: lo
    // que el cuerpo lee en la proxima iteracion sigue vivo al terminar esta.
    @Test
    fun `un temporal que vive de una vuelta del ciclo a la siguiente`() {
        val blocks = basicBlocksOf(
            listOf(
                Copy(t1, Constant(0L), integer),
                LabelDefinition(Label(1)),
                Print(t1, integer),
                Arithmetic(t1, t1, ArithmeticOperator.ADD, t2, integer),
                Goto(Label(1))
            )
        )
        val liveness = BlockLiveness(blocks)

        // El bloque 0 es `t1 = 0`; el 1 es el cuerpo del ciclo, de L1 hasta el goto.
        assertTrue(liveness.liveAtExitOf(0)(t1), "lo lee la primera vuelta")
        assertTrue(liveness.liveAtExitOf(1)(t1), "y lo vuelve a leer la siguiente")
    }

    // Despues de un return el registro de activacion se muere, y con el sus temporales.
    @Test
    fun `un temporal no sobrevive a un return`() {
        val blocks = basicBlocksOf(
            listOf(
                Copy(t1, Constant(1L), integer),
                Return(null, null),
                LabelDefinition(Label(1)),
                Print(t1, integer)
            )
        )

        assertFalse(BlockLiveness(blocks).liveAtExitOf(0)(t1))
    }
}
