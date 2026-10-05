// Ticket 2.2: el GDA de una expresion.
//
// Las expresiones salen de un programa real, ya analizado: el GDA necesita el AST
// DECORADO (tipos, constantes plegadas, simbolos resueltos), igual que en el
// compilador.
package org.compiler

import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.intermediate.DagNode
import org.compiler.frontend.intermediate.DagOperation
import org.compiler.frontend.intermediate.ExpressionDag
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExpressionDagTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private val variables = """
        let a: integer = 1; let b: integer = 2; let c: integer = 3; let d: integer = 4;
        let x: integer = 5; let y: integer = 6; let f: float = 1.5;
        function g(): integer { return 1; }
    """.trimIndent()

    // La expresion del inicializador de `let r = <expression>;`, ya analizada.
    private fun analyzed(expression: String, type: String = "integer"): Expression {
        val result = CompilerPipeline.compile("$variables\nlet r: $type = $expression;", execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")

        val declaration = result.ast!!.statements.last()
        return assertIs<VariableDeclaration>(declaration).initializer!!
    }

    private fun dag(expression: String, type: String = "integer") = ExpressionDag.build(analyzed(expression, type))

    private fun ExpressionDag.leaf(name: String): Int =
        nodes.indexOfFirst { it is DagNode.Leaf && (it.address as? Name)?.symbol?.name == name }

    private fun ExpressionDag.operations(): List<Int> =
        nodes.indices.filter { nodes[it] is DagNode.Operation }

    private fun ExpressionDag.find(operation: DagOperation, vararg operands: Int): Int =
        nodes.indexOf(DagNode.Operation(operation, operands.toList()))

    private val subtract = DagOperation.Arithmetic(ArithmeticOperator.SUBTRACT, OperandKind.INTEGER)
    private val multiply = DagOperation.Arithmetic(ArithmeticOperator.MULTIPLY, OperandKind.INTEGER)
    private val toFloat = DagOperation.Unary(TacUnaryOperator.INT_TO_FLOAT, OperandKind.FLOAT)

    // ── El numero de valor ─────────────────────────────────────────────────

    // LA DIAPOSITIVA 13. Cuatro hojas y cinco operaciones: `b - c` se escribe dos veces
    // pero es UN nodo con dos padres, y `a` tambien se lee desde dos operaciones.
    @Test
    fun `el ejemplo de la diapositiva 13 tiene nueve nodos`() {
        val dag = dag("a + a * (b - c) + (b - c) * d")

        assertEquals(9, dag.nodes.size)

        val difference = dag.find(subtract, dag.leaf("b"), dag.leaf("c"))
        assertEquals(2, dag.parentCount[difference], "b - c")
        assertEquals(2, dag.parentCount[dag.leaf("a")], "a")
    }

    // Las dos aristas salen del MISMO padre, y cuentan como dos usos.
    @Test
    fun `una subexpresion repetida en el mismo padre tiene dos aristas`() {
        val dag = dag("(x + y) * (x + y)")

        val root = assertIs<DagNode.Operation>(dag.nodes[dag.root])
        val sum = root.operands.first()

        assertEquals(listOf(sum, sum), root.operands)
        assertEquals(2, dag.parentCount[sum])
    }

    @Test
    fun `la raiz no tiene padres`() {
        val dag = dag("a + b")

        assertEquals(0, dag.parentCount[dag.root])
    }

    // ── Constantes y conversiones ──────────────────────────────────────────

    // El TypeChecker ya plego el valor: una hoja, no una suma (decision 32).
    @Test
    fun `una expresion constante es una sola hoja`() {
        val dag = dag("3 + 5")

        assertEquals(listOf<DagNode>(DagNode.Leaf(Constant(8L))), dag.nodes)
    }

    // Las conversiones implicitas son nodos: x es entero y la suma es de flotantes.
    @Test
    fun `una conversion implicita es un nodo entre la variable y la operacion`() {
        val dag = dag("x + 2.5", type = "float")

        val conversion = dag.find(toFloat, dag.leaf("x"))
        assertTrue(conversion >= 0, "falta el nodo inttofloat sobre x")

        val sum = assertIs<DagNode.Operation>(dag.nodes[dag.root])
        assertEquals(DagOperation.Arithmetic(ArithmeticOperator.ADD, OperandKind.FLOAT), sum.operation)
        assertEquals(conversion, sum.operands.first())
    }

    // Al ser nodos, las conversiones tambien se comparten: x se convierte UNA vez.
    @Test
    fun `una conversion repetida se hace una sola vez`() {
        val dag = dag("(x + 2.5) * (x + 2.5)", type = "float")

        assertEquals(1, dag.nodes.count { it == DagNode.Operation(toFloat, listOf(dag.leaf("x"))) })
    }

    // Una constante entera no necesita instruccion: se convierte al construir.
    @Test
    fun `una constante entera en una suma de flotantes se convierte sin instruccion`() {
        val dag = dag("f + 1", type = "float")

        assertTrue(DagNode.Leaf(Constant(1.0)) in dag.nodes)
        assertTrue(dag.nodes.none { (it as? DagNode.Operation)?.operation == toFloat })
    }

    // ── Cuando NO se comparte (decision 30) ────────────────────────────────

    // g puede modificar a o b: las dos a * b no son el mismo valor.
    @Test
    fun `con una llamada las subexpresiones iguales son nodos distintos`() {
        val dag = dag("a * b + g() + a * b")

        val products = dag.nodes.indices.filter {
            dag.nodes[it] == DagNode.Operation(multiply, listOf(dag.leaf("a"), dag.leaf("b")))
        }
        assertEquals(2, products.size)
        assertNotEquals(products[0], products[1])
    }

    // La x de la derecha no es la x de antes de la asignacion.
    @Test
    fun `con una asignacion anidada no se comparte ninguna operacion`() {
        val dag = dag("(x = 5) + x")

        assertTrue(dag.nodes.any { it is DagNode.Assign })
        assertTrue(dag.operations().all { dag.parentCount[it] <= 1 })
    }

    // Las hojas SI se comparten en los dos modos: no generan instruccion.
    @Test
    fun `sin compartir operaciones las hojas se siguen compartiendo`() {
        val dag = dag("a * b + g() + a * b")

        val leavesOfA = dag.nodes.count { it is DagNode.Leaf && (it.address as? Name)?.symbol?.name == "a" }
        assertEquals(1, leavesOfA)
        assertEquals(2, dag.parentCount[dag.leaf("a")])
    }

    @Test
    fun `solo las llamadas y las asignaciones apagan la tabla`() {
        assertTrue(ExpressionDag.hasSideEffects(analyzed("a + g()")))
        assertTrue(ExpressionDag.hasSideEffects(analyzed("(x = 1) * 2")))
        assertFalse(ExpressionDag.hasSideEffects(analyzed("a + b * (c - d)")))
    }

    // ── Lo que esta fase no traduce ────────────────────────────────────────

    // && necesita saltos (punto 6): queda entero, como un solo nodo sin abrir.
    @Test
    fun `lo que necesita saltos queda como un nodo sin traducir`() {
        val dag = dag("a < b && c < d", type = "boolean")

        assertIs<DagNode.Untranslated>(dag.nodes[dag.root])
        assertEquals(1, dag.nodes.size)
    }
}
