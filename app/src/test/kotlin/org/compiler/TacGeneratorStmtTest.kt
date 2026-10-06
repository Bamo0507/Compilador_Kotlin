// Las sentencias de control: if, while, do-while, for, switch y try/catch, con break y
// continue.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TacGeneratorStmtTest {

    private val prelude = "let a: integer = 1; let b: integer = 2; let x: integer = 3; " +
        "let y: integer = 4; let c: boolean = true;"

    // El TAC de `code`, sin las instrucciones del preludio y sin sangria.
    private fun tac(code: String): List<String> {
        val preludeSize = instructionsOf(prelude).size
        return instructionsOf("$prelude\n$code").drop(preludeSize)
    }

    private fun instructionsOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")

        val program = assertNotNull(result.tac, "El TAC salio null para:\n$source")
        return TacPrinter.print(program.instructions).lines().map { it.trim() }
    }

    // ── if ─────────────────────────────────────────────────────────────────

    // La condicion cae al cuerpo y solo salta cuando es falsa.
    @Test
    fun `un if sin else salta al final si la condicion es falsa`() {
        assertEquals(
            listOf("if a >= b goto L1", "print_i 1", "L1:"),
            tac("if (a < b) { print(1); }")
        )
    }

    @Test
    fun `un if con else salta al else si es falsa y al final despues del then`() {
        assertEquals(
            listOf("if a >= b goto L1", "print_i 1", "goto L2", "L1:", "print_i 2", "L2:"),
            tac("if (a < b) { print(1); } else { print(2); }")
        )
    }

    // Si a < b es falso, x > y ni se evalua: los dos saltos van al final.
    @Test
    fun `un if con and no evalua la segunda condicion si la primera es falsa`() {
        assertEquals(
            listOf("if a >= b goto L1", "if x <= y goto L1", "print_i 1", "L1:"),
            tac("if (a < b && x > y) { print(1); }")
        )
    }

    // Si a < b es verdadero salta directo al cuerpo, que necesita su propia etiqueta.
    @Test
    fun `un if con or entra al cuerpo si la primera condicion es verdadera`() {
        assertEquals(
            listOf("if a < b goto L2", "if x <= y goto L1", "L2:", "print_i 1", "L1:"),
            tac("if (a < b || x > y) { print(1); }")
        )
    }

    @Test
    fun `un if con negacion solo intercambia las etiquetas`() {
        assertEquals(
            listOf("if a < b goto L1", "print_i 1", "L1:"),
            tac("if (!(a < b)) { print(1); }")
        )
    }

    @Test
    fun `un if sobre una variable booleana usa ifFalse`() {
        assertEquals(
            listOf("ifFalse c goto L1", "print_i 1", "L1:"),
            tac("if (c) { print(1); }")
        )
    }

    // ── Bucles ─────────────────────────────────────────────────────────────

    @Test
    fun `un while compara al inicio y vuelve con goto`() {
        assertEquals(
            listOf("L1:", "if a >= 3 goto L2", "t1 = a + 1", "a = t1", "goto L1", "L2:"),
            tac("while (a < 3) { a = a + 1; }")
        )
    }

    // La condicion verdadera es la vuelta del bucle: no hay goto hacia atras propio.
    @Test
    fun `un do while salta al cuerpo desde la condicion`() {
        assertEquals(
            listOf("L1:", "t1 = a + 1", "a = t1", "L2:", "if a < 3 goto L1", "L3:"),
            tac("do { a = a + 1; } while (a < 3);")
        )
    }

    // continue va a la actualizacion: si fuera a la condicion, el for no avanzaria.
    @Test
    fun `continue en un for salta a la actualizacion`() {
        val lines = tac("for (let i: integer = 0; i < 3; i = i + 1) { continue; }")

        assertEquals(
            listOf("i = 0", "L1:", "if i >= 3 goto L3", "goto L2", "L2:", "t1 = i + 1", "i = t1",
                "goto L1", "L3:"),
            lines
        )
    }

    // continue va a reevaluar la condicion, no a repetir el cuerpo sin mirarla.
    @Test
    fun `continue en un do while salta a la condicion`() {
        assertEquals(
            listOf("L1:", "goto L2", "L2:", "if c goto L1", "L3:"),
            tac("do { continue; } while (c);")
        )
    }

    // La condicion se plego a true: no se compara nada, y solo se sale con break.
    @Test
    fun `while true no compara y break salta al final`() {
        assertEquals(
            listOf("L1:", "goto L2", "goto L1", "L2:"),
            tac("while (true) { break; }")
        )
    }

    @Test
    fun `break en un bucle anidado sale del interno`() {
        val lines = tac("while (a < 3) { while (b < 3) { break; } a = a + 1; }")

        // El while externo usa L1 y L2; el interno, L3 y L4. El break va a L4.
        assertEquals(listOf("L3:", "if b >= 3 goto L4", "goto L4", "goto L3", "L4:"),
            lines.subList(2, 7))
    }

    // ── switch ─────────────────────────────────────────────────────────────

    // Sin fall-through: cada case termina saltando al final.
    @Test
    fun `un switch es una cadena de comparaciones con default al final`() {
        assertEquals(
            listOf(
                "if a != 1 goto L2", "print_i 10", "goto L1",
                "L2:", "if a != 2 goto L3", "print_i 20", "goto L1",
                "L3:", "print_i 0",
                "L1:"
            ),
            tac("switch (a) { case 1: print(10); case 2: print(20); default: print(0); }")
        )
    }

    @Test
    fun `sin default un valor que no coincide sale del switch`() {
        assertEquals(
            listOf("if a != 1 goto L2", "print_i 10", "goto L1", "L2:", "L1:"),
            tac("switch (a) { case 1: print(10); }")
        )
    }

    // El sujeto se calcula una vez, y su temporal sigue vivo durante el primer case:
    // el cuerpo tiene que usar otro.
    @Test
    fun `el sujeto se evalua una vez y vive hasta la ultima comparacion`() {
        val lines = tac("switch (a + b) { case 3: print(a * b); case 4: print(1); }")

        assertEquals(1, lines.count { it == "t1 = a + b" })
        assertEquals(listOf("t1 = a + b", "if t1 != 3 goto L2", "t2 = a * b"), lines.take(3))
        assertTrue("if t1 != 4 goto L3" in lines)
    }

    @Test
    fun `un switch de strings compara con el sufijo de string`() {
        val lines = tac("let s: string = \"b\"; switch (s) { case \"a\": print(1); }")

        assertTrue("if s !=s \"a\" goto L2" in lines)
    }

    // El switch no es un bucle: el break sale del while que lo contiene.
    @Test
    fun `break dentro de un switch sale del bucle que lo contiene`() {
        val lines = tac("while (a < 3) { switch (a) { case 1: break; } a = a + 1; }")

        assertEquals(listOf("if a != 1 goto L4", "goto L2"), lines.subList(2, 4))
    }

    // ── try/catch ──────────────────────────────────────────────────────────

    @Test
    fun `try registra el manejador y endtry lo quita al terminar sin errores`() {
        assertEquals(
            listOf(
                "try L1, e",
                "print_i 1",
                "endtry",
                "goto L2",
                "L1:",
                "print_s e",
                "L2:"
            ),
            tac("try { print(1); } catch (e) { print(e); }")
        )
    }

    // El throw del chequeo queda dentro del bloque protegido: si b vale 0, salta al
    // catch con el mensaje.
    @Test
    fun `el chequeo de division dentro de un try queda protegido`() {
        val lines = tac("try { let q: integer = a / b; } catch (e) { print(e); }")

        val tryIndex = lines.indexOf("try L1, e")
        val throwIndex = lines.indexOfFirst { it.startsWith("throw") }
        val endTryIndex = lines.indexOf("endtry")
        assertTrue(tryIndex < throwIndex && throwIndex < endTryIndex)
    }

    // Sin el endtry, un error posterior, ya fuera del bucle, saltaria a este catch.
    @Test
    fun `break dentro de un try quita el manejador antes de salir`() {
        val lines = tac("while (a < 3) { try { break; } catch (e) { } }")

        assertEquals(listOf("try L3, e", "endtry", "goto L2"), lines.subList(2, 5))
    }

    // El throw que llevo al catch ya quito el manejador: no hay nada que quitar.
    @Test
    fun `break dentro de un catch no emite endtry`() {
        val lines = tac("while (a < 3) { try { } catch (e) { break; } }")

        val handlerIndex = lines.indexOf("L3:")
        assertEquals("goto L2", lines[handlerIndex + 1])
    }

    @Test
    fun `continue que abandona dos try emite dos endtry`() {
        val lines = tac("while (a < 3) { try { try { continue; } catch (e) { } } catch (f) { } }")

        assertEquals(
            listOf("try L3, f", "try L5, e", "endtry", "endtry", "goto L1"),
            lines.subList(2, 7)
        )
    }

    // ── Programas de la bateria ────────────────────────────────────────────

    @Test
    fun `el programa de condiciones y bucles de la bateria genera TAC`() {
        val program = assertNotNull(SamplePrograms.byId("validos/flujo_condiciones"))
        val result = CompilerPipeline.compile(program.source, execute = false)

        assertNotNull(result.tac)
    }
}
