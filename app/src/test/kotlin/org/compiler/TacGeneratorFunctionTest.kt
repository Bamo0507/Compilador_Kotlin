// Las funciones: begin_func y end_func, param, call, return y recursion.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TacGeneratorFunctionTest {

    private fun program(source: String): TacProgram {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        return assertNotNull(result.tac, "El TAC salio null para:\n$source")
    }

    private fun tac(source: String): List<String> =
        TacPrinter.print(program(source).instructions).lines().map { it.trim() }

    // n se copia antes de la llamada recursiva: esta a su izquierda, y la llamada podria
    // modificarla. El registro mide 24 mas dos temporales de 8.
    @Test
    fun `factorial genera su funcion recursiva y el main que la llama`() {
        assertEquals(
            listOf(
                "begin_func \$main, 24",
                "param 5",
                "t1 = call factorial, 1",
                "print_i t1",
                "end_func \$main",
                "begin_func factorial, 40",
                "if n > 1 goto L1",
                "return 1",
                "L1:",
                "t1 = n",
                "t2 = n - 1",
                "param t2",
                "t2 = call factorial, 1",
                "t1 = t1 * t2",
                "return t1",
                "end_func factorial"
            ),
            tac(
                "function factorial(n: integer): integer {\n" +
                    "  if (n <= 1) { return 1; }\n" +
                    "  return n * factorial(n - 1);\n" +
                    "}\n" +
                    "print(factorial(5));"
            )
        )
    }

    // Primero se calculan todos los argumentos y despues van los param: los de g no se
    // intercalan con los de f.
    @Test
    fun `una llamada como argumento de otra sale en el orden de la teoria`() {
        val lines = tac(
            "function g(a: integer): integer { return a; }\n" +
                "function f(b: integer): integer { return b; }\n" +
                "let x: integer = 1;\n" +
                "let r: integer = f(g(x));"
        )

        assertEquals(
            listOf("param x", "t1 = call g, 1", "param t1", "t1 = call f, 1", "r = t1"),
            lines.subList(2, 7)
        )
    }

    @Test
    fun `una llamada como sentencia no guarda resultado`() {
        val lines = tac("function saluda() { print(\"hola\"); }\nsaluda();")

        assertTrue("call saluda, 0" in lines)
    }

    // Cada funcion tiene su propio pool: los dos usan t1 sin pisarse, porque viven en
    // registros distintos.
    @Test
    fun `cada funcion tiene sus propios temporales`() {
        val lines = tac(
            "function doble(a: integer): integer { return a * 2; }\n" +
                "let r: integer = doble(3) + 1;"
        )

        assertTrue("t1 = call doble, 1" in lines)
        assertTrue("t1 = a * 2" in lines)
    }

    // El return sale del try: primero quita el manejador. El del catch no, porque el
    // error ya lo quito.
    @Test
    fun `un return dentro de un try quita el manejador antes de salir`() {
        val lines = tac(
            "function seguro(a: integer, b: integer): integer {\n" +
                "  try { return a / b; } catch (e) { return -1; }\n" +
                "}\n" +
                "print(seguro(4, 2));"
        )

        val returnIndex = lines.indexOf("return t1")
        assertEquals("endtry", lines[returnIndex - 1])
        assertEquals("L1:", lines[lines.indexOf("return -1") - 1])
    }

    @Test
    fun `un entero se convierte al pasarlo o devolverlo como float`() {
        val lines = tac(
            "function mitad(x: float): float { return x / 2.0; }\n" +
                "function uno(): float { return 1; }\n" +
                "print(mitad(3));"
        )

        assertTrue("param 3.0" in lines)
        assertTrue("return 1.0" in lines)
    }

    // El tamano de begin_func es el registro del StorageAllocator mas los temporales que
    // uso la funcion, y el registro final los lista como campos.
    @Test
    fun `begin_func lleva el tamano del registro final con sus temporales`() {
        val tacProgram = program(
            "function f(a: integer, b: integer): integer { return a * b + a; }\nprint(f(1, 2));"
        )

        val begins = tacProgram.instructions.filterIsInstance<FunctionBegin>()
        assertEquals(begins.map { it.frameSize }, tacProgram.activationRecords.map { it.size })

        val record = tacProgram.activationRecords.single { it.function.name == "f" }
        assertEquals("t1", record.fields.last().name)
    }
}
