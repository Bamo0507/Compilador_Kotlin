// La secuencia de llamadas: el registro de activacion visto desde la pila, el prologo y
// el epilogo, los argumentos, el enlace de acceso, el resultado y el retorno.
package org.compiler

import org.compiler.backend.ArmPrinter
import org.compiler.backend.Frame
import org.compiler.frontend.intermediate.StorageAllocator
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallingSequenceTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun linesOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")
        return ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }
    }

    private fun translationOf(lines: List<String>, tacLine: String): List<String> {
        val start = lines.indexOf("@ $tacLine")
        assertTrue(start >= 0, "No esta '$tacLine' en:\n${lines.joinToString("\n")}")
        return lines.drop(start + 1).takeWhile { !it.startsWith("@") }
            .map { it.substringBefore("  @") }
    }

    private fun translationOf(source: String, tacLine: String): List<String> =
        translationOf(linesOf(source), tacLine)

    private fun sourceOf(name: String): String =
        SamplePrograms.all.first { it.id == "validos/$name" }.source

    private fun frameOf(source: String, function: String): Frame {
        val tac = assertNotNull(CompilerPipeline.compile(source, execute = false).tac)
        return Frame(tac.activationRecords.first { it.function.name == function })
    }

    // El programa de referencia: factorial, con recursion y un caso base que retorna.
    private val recursion = sourceOf("funciones_recursion")

    // ── La parte fija ──────────────────────────────────────────────────────

    /**
     * La parte fija va de la casilla 0 hasta el final de la direccion de retorno, y se
     * redondea a 8. En factorial eso es 20 redondeado a 24.
     *
     * El numero no esta escrito a mano en ningun lado: sale de la casilla de la direccion
     * de retorno, que es la ultima que quien llama tiene que dejar reservada.
     */
    @Test
    fun `la parte fija sale del registro de activacion`() {
        val record = assertNotNull(CompilerPipeline.compile(recursion, execute = false).tac)
            .activationRecords.first { it.function.name == "factorial" }
        val returnAddress = record.fields.first { it.name == StorageAllocator.RETURN_ADDRESS_SLOT }

        assertEquals(20, returnAddress.offset + returnAddress.size, "hasta la direccion de retorno")
        assertEquals(24, Frame(record).fixedPartSize, "redondeada a 8")
        assertEquals(40, Frame(record).size)
    }

    /**
     * Si la parte fija no fuera multiplo de 8, quien llama dejaria sp en un multiplo de 4
     * durante toda la funcion llamada, y libc exige 8 en cada llamada.
     *
     * `media(x: float, y: float): float` es el caso: dos flotantes, el valor devuelto y
     * los tres campos de enlace suman 36.
     */
    @Test
    fun `la pila siempre se mueve en multiplos de 8`() {
        val lines = linesOf(
            """
            function media(x: float, y: float): float { return (x + y) / 2.0; }
            print(media(1.5, 2.5));
            """.trimIndent()
        )

        val moves = lines.filter { it.startsWith("sub sp, sp, #") || it.startsWith("add sp, sp, #") }
        assertTrue(moves.isNotEmpty())
        moves.forEach { line ->
            val bytes = line.substringAfter("#").toInt()
            assertEquals(0, bytes % 8, "'$line' desalinea la pila")
        }
    }

    // ── El prologo y el epilogo ────────────────────────────────────────────

    // Quien llama ya dejo reservada la parte fija, asi que la funcion solo termina de
    // armar el registro y reserva lo suyo. fp se escribe de ultimo porque hasta ahi hace
    // falta el de quien llama.
    @Test
    fun `el prologo arma el registro sobre lo que reservo quien llama`() {
        assertEquals(
            listOf(
                "cps_factorial:",
                "add ip, sp, #24",
                "str fp, [ip, #-12]",
                "str lr, [ip, #-20]",
                "mov fp, ip",
                "sub sp, fp, #40"
            ),
            translationOf(recursion, "begin_func factorial, 40")
        )
    }

    // El epilogo deja la pila como la recibio y vuelve. Va detras de la etiqueta de
    // salida, que es a donde salta cada return.
    @Test
    fun `el epilogo esta escrito una sola vez detras de la etiqueta de salida`() {
        val lines = linesOf(recursion)

        assertEquals(
            listOf(
                ".Lfactorial_fin:",
                "ldr lr, [fp, #-20]",
                "sub sp, fp, #24",
                "ldr fp, [fp, #-12]",
                "bx lr"
            ),
            translationOf(lines, "end_func factorial").takeWhile { !it.startsWith(".section") }
        )
        assertEquals(1, lines.count { it == ".Lfactorial_fin:" })
        assertEquals(2, lines.count { it == "b .Lfactorial_fin" }, "los dos return saltan ahi")
    }

    // ── return ─────────────────────────────────────────────────────────────

    // El valor vuelve en r0. La casilla "valor devuelto" del registro no se usa: ese
    // registro ya se libero cuando quien llama lee el resultado.
    @Test
    fun `return deja el valor en r0 y salta a la salida`() {
        assertEquals(
            listOf("mov r0, #1", "b .Lfactorial_fin"),
            translationOf(recursion, "return 1")
        )
        assertEquals(
            listOf("mov r0, r4", "b .Lfactorial_fin"),
            translationOf(recursion, "return t1")
        )
    }

    // Una funcion sin valor de retorno cae en su etiqueta de salida sin saltar.
    @Test
    fun `una funcion sin valor no tiene ningun salto a la salida`() {
        val lines = linesOf("function f(n: integer) { print(n); } f(1);")

        assertTrue(".Lf_fin:" in lines)
        assertTrue(lines.none { it == "b .Lf_fin" })
    }

    // ── Los argumentos ─────────────────────────────────────────────────────

    /**
     * El primer param reserva la parte fija del registro nuevo, y cada argumento va a su
     * casilla contada desde sp: el tope del registro va a quedar en sp + parte fija, asi
     * que la casilla de desplazamiento d y tamaño s cae en sp + F - d - s.
     *
     * `n` esta en el desplazamiento 0 y mide 4, y la parte fija es 24: `[sp, #20]`.
     */
    @Test
    fun `el primer param reserva el registro y cada argumento va a su casilla`() {
        assertEquals(
            listOf("sub sp, sp, #24", "str r5, [sp, #20]"),
            translationOf(recursion, "param t2")
        )
    }

    // Los argumentos se escriben de arriba hacia abajo, porque el primero es el que esta
    // mas cerca del tope del registro.
    @Test
    fun `seis argumentos van en sus seis casillas`() {
        val source = """
            function suma(a: integer, b: integer, c: integer, d: integer,
                          e: integer, f: integer): integer {
              return a + b + c + d + e + f;
            }
            print(suma(1, 2, 3, 4, 5, 6));
        """.trimIndent()
        val lines = linesOf(source)

        assertEquals(40, frameOf(source, "suma").fixedPartSize)
        assertEquals(listOf("sub sp, sp, #40", "mov r2, #1", "str r2, [sp, #36]"),
            translationOf(lines, "param 1"))
        assertEquals(listOf("mov r2, #6", "str r2, [sp, #16]"), translationOf(lines, "param 6"))
    }

    // Un booleano ocupa un byte. Escribirle cuatro pisaria la casilla de al lado, asi que
    // la instruccion la decide el tamaño de la casilla, no el tipo del registro.
    @Test
    fun `un argumento booleano se guarda con strb`() {
        assertEquals(
            listOf("sub sp, sp, #24", "mov r2, #1", "strb r2, [sp, #23]"),
            translationOf(
                "function f(b: boolean, n: integer) { if (b) { print(n); } } f(true, 7);",
                "param true"
            )
        )
    }

    // Un flotante no pasa por getReg: se carga en d0 y se guarda con vstr.
    @Test
    fun `un argumento flotante va por d0`() {
        assertEquals(
            listOf("sub sp, sp, #24", "ldr ip, =.Lfloat_1", "vldr d0, [ip]", "vstr d0, [sp, #16]"),
            translationOf(
                "function f(x: float) { print(x); } f(1.5);",
                "param 1.5"
            )
        )
    }

    // Sin argumentos no hay ningun param que reserve el registro, asi que lo reserva la
    // propia llamada.
    @Test
    fun `una funcion sin argumentos reserva igual su parte fija`() {
        assertEquals(
            listOf("sub sp, sp, #16", "bl cps_f", "add sp, sp, #16"),
            translationOf("function f() { } f();", "call f, 0")
        )
    }

    // ── El enlace de acceso ────────────────────────────────────────────────

    // ^0: la funcion que contiene a la llamada es la que esta llamando, asi que el enlace
    // es su propio fp.
    @Test
    fun `con cero saltos el enlace de acceso es el propio fp`() {
        val code = translationOf(sourceOf("funciones_acceso_anidado"), "t1 = call externa.hermana, 0, ^0")

        assertEquals(listOf("sub sp, sp, #16", "str fp, [sp, #4]"), code.take(2))
    }

    // ^1: hay que subir un enlace desde el registro propio antes de pasarlo.
    @Test
    fun `con un salto el enlace de acceso se lee del registro propio`() {
        val code = translationOf(sourceOf("funciones_acceso_anidado"), "t1 = call externa.media, 0, ^1")

        assertEquals(
            listOf("sub sp, sp, #16", "ldr ip, [fp, #-12]", "str ip, [sp, #4]"),
            code.take(3)
        )
    }

    // ── La llamada ─────────────────────────────────────────────────────────

    /**
     * Lo ultimo antes del `bl` es el vaciado.
     *
     * Una funcion de Compiscript usa los mismos registros que reparte getReg y no los
     * devuelve intactos, asi que lo que quede en uno se pierde en la llamada. Aqui `t1`
     * estaba solo en un registro y se guarda antes de saltar. `t2` tambien, pero no se
     * guarda: la llamada lo sobrescribe con su resultado.
     */
    @Test
    fun `una llamada vacia los registros antes del bl`() {
        assertEquals(
            listOf(
                "str r4, [fp, #-32]",
                "bl cps_factorial",
                "add sp, sp, #24",
                "mov r4, r0",
                "str r4, [fp, #-40]"
            ),
            translationOf(recursion, "t2 = call factorial, 1")
        )
    }

    // El resultado llega en r0, pasa al registro del temporal y de ahi a su casilla: la
    // llamada cierra el bloque, y el siguiente va a buscarlo en memoria.
    @Test
    fun `el resultado pasa de r0 al temporal y queda en memoria`() {
        val code = translationOf(recursion, "t1 = call factorial, 1")

        assertEquals(listOf("bl cps_factorial", "add sp, sp, #24", "mov r4, r0"), code.take(3))
        assertEquals("str r4, [fp, #-24]", code.last())
    }

    // Un resultado flotante vuelve en d0 y se guarda directo en la casilla del temporal.
    @Test
    fun `un resultado flotante vuelve en d0`() {
        val code = translationOf(
            """
            function media(x: float, y: float): float { return (x + y) / 2.0; }
            print(media(1.5, 2.5));
            """.trimIndent(),
            "t1 = call media, 2"
        )

        assertEquals(listOf("bl cps_media", "add sp, sp, #40", "vstr d0, [fp, #-24]"), code)
    }

    // Una llamada sin resultado no toca r0.
    @Test
    fun `una llamada void no lee ningun resultado`() {
        assertEquals(
            listOf("sub sp, sp, #16", "mov r2, #1", "str r2, [sp, #12]"),
            translationOf("function f(n: integer) { print(n); } f(1);", "param 1")
        )
        assertEquals(
            listOf("bl cps_f", "add sp, sp, #16"),
            translationOf("function f(n: integer) { print(n); } f(1);", "call f, 1")
        )
    }

    // ── La bateria ─────────────────────────────────────────────────────────

    // Lo que la fase agrega al alcance. funciones_argumentos queda fuera porque concatena
    // textos, que es de la fase 6.
    @Test
    fun `los programas de funciones ya se traducen enteros`() {
        listOf(
            "funciones_recursion", "funciones_closures", "funciones_acceso_anidado",
            "flujo_switch", "tac_subexpresiones", "ambito_global_posterior"
        ).forEach { name ->
            val result = CompilerPipeline.compile(sourceOf(name), execute = false)

            assertNotNull(result.assembly, "$name se quedo sin ensamblador")
        }
    }
}
