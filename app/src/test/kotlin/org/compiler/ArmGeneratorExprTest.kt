// La traduccion de las expresiones: aritmetica de enteros, division, comparaciones como
// valor, operaciones de un operando, flotantes y comparacion de textos. Al final, tres
// programas corren en el contenedor y se comparan con el interprete.
package org.compiler

import org.compiler.backend.ArmPrinter
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmGeneratorExprTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    // La traduccion de una linea del TAC dentro de f, sin comentarios al final.
    private fun translationOf(source: String, tacLine: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")
        val lines = ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }

        val start = lines.indexOf("@ $tacLine")
        assertTrue(start >= 0, "No esta '$tacLine' en:\n${lines.joinToString("\n")}")
        return lines.drop(start + 1).takeWhile { !it.startsWith("@") }
            .map { it.substringBefore("  @") }
    }

    private fun function(body: String) =
        "function f(a: integer, b: integer, x: float, y: float, s: string) { $body }"

    // ── Enteros ────────────────────────────────────────────────────────────

    // Una constante chica va dentro de la instruccion.
    @Test
    fun `sumar una constante la pone dentro de la instruccion`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "add r4, r4, #5"),
            translationOf(function("print(a + 5);"), "t1 = a + 5")
        )
    }

    // mul no acepta constantes: el 3 pasa antes por r3, que getReg no reparte. El
    // resultado reusa el registro de a, que esta en memoria y no se vuelve a leer.
    @Test
    fun `multiplicar por una constante la carga antes en un registro de paso`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "mov r3, #3", "mul r4, r4, r3"),
            translationOf(function("print(a * 3);"), "t1 = a * 3")
        )
    }

    // La division la hace libgcc: dividendo en r0, divisor en r1, cociente en r0.
    @Test
    fun `dividir llama a la rutina de libgcc`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "mov r3, #4", "mov r0, r4", "mov r1, r3",
                "bl __aeabi_idiv", "mov r4, r0"),
            translationOf(function("print(a / 4);"), "t1 = a / 4")
        )
    }

    // El modulo usa la otra rutina, que deja el residuo en r1.
    @Test
    fun `el modulo toma el residuo de r1`() {
        val code = translationOf(function("print(a % 4);"), "t1 = a % 4")

        assertTrue("bl __aeabi_idivmod" in code)
        assertTrue(code.last().endsWith(", r1"))
    }

    // El slt de MIPS: cmp, y despues solo una de las dos escrituras cuenta.
    @Test
    fun `una comparacion como valor deja 1 o 0`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "ldr r5, [fp, #-8]", "cmp r4, r5", "mov r4, #0",
                "movlt r4, #1"),
            translationOf(function("let m: boolean = a < b;"), "t1 = a < b")
        )
    }

    @Test
    fun `el menos y la negacion logica`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "rsb r4, r4, #0"),
            translationOf(function("print(-a);"), "t1 = - a")
        )
        assertEquals(
            "eor r6, r4, #1",
            translationOf(function("let m: boolean = a < b; print(!m);"), "t1 = ! m").last()
        )
    }

    // ── Flotantes ──────────────────────────────────────────────────────────

    // Sin getReg: se cargan los dos operandos, se opera en d0 y se guarda en la casilla
    // del temporal, la primera despues de las de f: [fp, #-48].
    @Test
    fun `sumar flotantes carga opera y guarda`() {
        assertEquals(
            listOf("vldr d1, [fp, #-16]", "vldr d2, [fp, #-24]", "vadd.f64 d0, d1, d2",
                "vstr d0, [fp, #-48]"),
            translationOf(function("print(x + y);"), "t1 = x +f y")
        )
    }

    // El entero pasa por s0 y vcvt lo convierte a doble precision en d0.
    @Test
    fun `convertir un entero a flotante`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "vmov s0, r4", "vcvt.f64.s32 d0, s0",
                "vstr d0, [fp, #-48]"),
            translationOf(function("print(x + a);"), "t1 = inttofloat a")
        )
    }

    // vmrs copia el resultado de vcmp a las banderas; menor que usa mi y no lt.
    @Test
    fun `comparar flotantes usa vcmp y el sufijo mi`() {
        assertEquals(
            listOf("vldr d1, [fp, #-16]", "vldr d2, [fp, #-24]", "vcmp.f64 d1, d2",
                "vmrs APSR_nzcv, fpscr", "mov r4, #0", "movmi r4, #1"),
            translationOf(function("let m: boolean = x < y;"), "t1 = x <f y")
        )
    }

    // ── Textos ─────────────────────────────────────────────────────────────

    // strcmp devuelve negativo, cero o positivo, y se compara con 0.
    @Test
    fun `comparar textos llama a la rutina del runtime`() {
        val code = translationOf(function("let m: boolean = s == \"hola\";"), "t1 = s ==s \"hola\"")

        assertTrue("bl cps_comparar_strings" in code, code.joinToString("\n"))
        assertEquals(listOf("cmp r0, #0", "mov r4, #0", "moveq r4, #1"), code.takeLast(3))
    }

    // ── En el contenedor ───────────────────────────────────────────────────

    private fun assertSameOutputAsInterpreter(name: String, source: String) {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")
        val result = CompilerPipeline.compile(source)
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")

        val execution = ArmContainer.run(name, ArmPrinter.print(assembly))

        assertEquals(0, execution.exitCode, execution.output)
        assertEquals(result.execution?.output, execution.output.lines().dropLast(1))
    }

    @Test
    fun `los flotantes imprimen lo mismo que el interprete`() {
        assertSameOutputAsInterpreter(
            "flotantes",
            """
            let a: float = 1.5; let b: float = 2.25; let n: integer = 3;
            let c: float = a * b + n;
            print(c); print(a - b); print(c / 2.0); print(-a);
            let mayor: boolean = a > b; print(mayor); print(a <= b); print(a == 1.5);
            let x: float = n; print(x); print(n / 2); print(n % 2); print(-n);
            """.trimIndent()
        )
    }

    // Ocho variables vivas y seis registros: getReg tiene que derramar.
    @Test
    fun `con mas valores que registros el resultado sigue siendo correcto`() {
        assertSameOutputAsInterpreter(
            "presion",
            """
            let a: integer = 1; let b: integer = 2; let c: integer = 3; let d: integer = 4;
            let e: integer = 5; let f: integer = 6; let g: integer = 7; let h: integer = 8;
            let s: integer = (a + b) * (c + d) + (e + f) * (g + h) - (a * h) + (b * g)
                - (c * f) + (d * e);
            print(s); print(a); print(h);
            let p: boolean = !(a < b); print(p); let q: boolean = p == false; print(q);
            let k: integer = 100000; print(k * 3); print(k / 7); print(-k);
            """.trimIndent()
        )
    }

    @Test
    fun `las copias y los textos imprimen lo mismo que el interprete`() {
        assertSameOutputAsInterpreter(
            "copias",
            """
            let x: integer = 5; let y: integer = x; let z: integer = y; x = 9;
            print(y); print(z); print(x);
            let t: string = "hola"; let u: string = t; print(u); print(t == u);
            print(t != "chao"); print(t < "zeta");
            """.trimIndent()
        )
    }
}
