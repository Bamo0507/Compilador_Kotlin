// getReg y sus dos libretas, y donde vive cada variable en memoria.
package org.compiler

import org.compiler.backend.ArmGenerator
import org.compiler.backend.ArmPrinter
import org.compiler.backend.models.Register
import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterAllocatorTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    // Las instrucciones de una funcion, sin comentarios, sin el prologo y sin el epilogo.
    // El prologo son la etiqueta y cinco instrucciones.
    private fun bodyOf(lines: List<String>, function: String): List<String> {
        val start = lines.indexOfFirst { it.startsWith("@ begin_func $function,") } + 7
        return lines.subList(start, lines.indexOf("@ end_func $function"))
            .filterNot { it.startsWith("@") }
    }

    private fun linesOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")
        return ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }
    }

    // La funcion f(a, b, c, d) con este cuerpo escrito a mano y sus temporales, traducida
    // con los registros dados.
    private fun translate(registers: List<Register>, body: (List<Name>) -> List<Quadruple>):
        List<String> {
        val result = CompilerPipeline.compile(
            "function f(a: integer, b: integer, c: integer, d: integer) { }", execute = false
        )
        val tac = assertNotNull(result.tac)
        val layout = assertNotNull(result.storageLayout)
        val (functionScope, record) = layout.functions.entries.single()
        val parameters = functionScope.localSymbols().map { Name(it) }

        val temporaries = (1..3).map { index ->
            ActivationRecordField("t$index", record.size + (index - 1) * 8, 8)
        }
        val withTemporaries = record.copy(
            fields = record.fields + temporaries, size = record.size + temporaries.size * 8
        )
        val main = tac.instructions.subList(0, 2)
        val program = tac.copy(
            instructions = main + FunctionBegin(record.function, withTemporaries.size) +
                body(parameters) + FunctionEnd(record.function),
            activationRecords = listOf(tac.activationRecords.first(), withTemporaries)
        )

        val assembly = ArmGenerator(program, layout, registers).generate()
        val lines = ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }
        return bodyOf(lines, "f").map { it.substringBefore("  @") }
    }

    private val integer = OperandKind.INTEGER

    // ── El ejemplo del libro ───────────────────────────────────────────────

    /**
     * t = a - b; u = a - c; v = t + u; a = d; d = v + u, con tres registros, como en el
     * Dragon Book 8.6. Salen las mismas 10 instrucciones que en el libro: cuatro cargas,
     * cuatro operaciones y, al final del bloque, los dos guardados de a y d, que son las
     * unicas variables cuyo valor solo quedo en un registro.
     *
     * Paso a paso:
     *   t = a - b   a y b se cargan; t reusa el registro de b, que esta en memoria y no
     *               se vuelve a leer
     *   u = a - c   c se carga; u reusa el de a, que despues se sobrescribe con a = d
     *   v = t + u   v reusa el de t, que muere aqui
     *   a = d       d se carga en el de c, que esta en memoria; a no genera instruccion:
     *               se anota en el mismo registro
     *   d = v + u   d reusa el de v
     */
    @Test
    fun `el ejemplo del libro da las mismas diez instrucciones`() {
        val code = translate(listOf(Register.R4, Register.R5, Register.R6)) { (a, b, c, d) ->
            val t = Temporary(1)
            val u = Temporary(2)
            val v = Temporary(3)
            listOf(
                Arithmetic(t, a, ArithmeticOperator.SUBTRACT, b, integer),
                Arithmetic(u, a, ArithmeticOperator.SUBTRACT, c, integer),
                Arithmetic(v, t, ArithmeticOperator.ADD, u, integer),
                Copy(a, d, integer),
                Arithmetic(d, v, ArithmeticOperator.ADD, u, integer)
            )
        }

        assertEquals(
            listOf(
                "ldr r4, [fp, #-4]",
                "ldr r5, [fp, #-8]",
                "sub r5, r4, r5",
                "ldr r6, [fp, #-12]",
                "sub r4, r4, r6",
                "add r5, r5, r4",
                "ldr r6, [fp, #-16]",
                "add r5, r5, r4",
                "str r5, [fp, #-16]",
                "str r6, [fp, #-4]"
            ),
            code
        )
    }

    // Con dos registros: d = a - b llena los dos, y d solo vive en el suyo. Para cargar c,
    // que hace falta en las dos instrucciones siguientes, uno de los dos se vacia, y si es
    // el de d hay que guardarlo antes en memoria: es el derrame del paso 4. Al final, d
    // termina en su casilla.
    @Test
    fun `sin registros libres se guarda lo que solo vivia en el registro`() {
        val code = translate(listOf(Register.R4, Register.R5)) { (a, b, c, d) ->
            listOf(
                Arithmetic(d, a, ArithmeticOperator.SUBTRACT, b, integer),
                Arithmetic(a, b, ArithmeticOperator.ADD, c, integer),
                Arithmetic(b, c, ArithmeticOperator.ADD, d, integer)
            )
        }

        val storesD = code.any { it.startsWith("str") && it.endsWith("[fp, #-16]") }
        assertTrue(storesD, code.joinToString("\n"))
    }

    // ── Las copias ─────────────────────────────────────────────────────────

    // x = t1 y y = x no generan instrucciones: x y y se anotan en el registro de la suma,
    // y se guardan una sola vez, al final del bloque. La suma reusa el registro de a, que
    // esta en memoria y no se vuelve a leer.
    @Test
    fun `una copia no genera instrucciones`() {
        val lines = linesOf(
            "function f(a: integer, b: integer) { let x: integer = a + b; let y: integer = x; " +
                "print(y); }"
        )

        assertEquals(
            listOf(
                "ldr r4, [fp, #-4]  @ a",
                "ldr r5, [fp, #-8]  @ b",
                "add r4, r4, r5",
                "mov r0, r4",
                "bl cps_imprimir_entero",
                "str r4, [fp, #-24]  @ x",
                "str r4, [fp, #-28]  @ y"
            ),
            bodyOf(lines, "f")
        )
    }

    // ── Donde vive cada variable ───────────────────────────────────────────

    // Una global se encuentra sumando su desplazamiento a la zona estatica.
    @Test
    fun `una global se lee desde la zona estatica`() {
        val lines = linesOf("let g: integer = 5; print(g + 1);")

        assertTrue("ldr ip, =cps_estatico" in lines)
        assertTrue(lines.any { it.startsWith("str r4, [ip]") && it.endsWith("@ g") })
    }

    // Un booleano ocupa 1 byte: se guarda con strb y se lee con ldrb.
    @Test
    fun `un booleano se guarda con un solo byte`() {
        val lines = linesOf("function f(a: integer) { let b: boolean = a < 3; print(b); }")

        val storesOneByte = lines.any { it.startsWith("strb") && it.endsWith("@ b") }
        assertTrue(storesOneByte, lines.joinToString("\n"))
    }

    // cuenta^1 vive en el registro de externa: se sigue el enlace de acceso de interna.
    @Test
    fun `una variable de la funcion que la contiene se busca por el enlace de acceso`() {
        val lines = linesOf(
            "function externa() { let cuenta: integer = 0; " +
                "function interna() { print(cuenta); } }"
        )

        val body = bodyOf(lines, "externa.interna")
        assertEquals("ldr ip, [fp, #-8]  @ enlace de acceso", body[0])
        assertTrue(body[1].startsWith("ldr r4, [ip, #-") && body[1].endsWith("@ cuenta^1"))
    }
}
