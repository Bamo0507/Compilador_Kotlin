// Las condiciones como codigo de saltos: caida, cortocircuito, el ternario y los
// booleanos como valor.
//
// Mientras el `if` no se traduzca, las condiciones se prueban a traves del ternario y
// de `&&` y `||` usados como valor, que pasan por la misma funcion.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TacGeneratorConditionTest {

    private val prelude = "let a: integer = 1; let b: integer = 2; let x: integer = 3; " +
        "let y: integer = 4; let c: boolean = true; let z: boolean = false;"

    // El TAC de `code`, sin las instrucciones que produce el preludio. Las lineas van
    // sin sangria: lo que se prueba es la secuencia, no el formato.
    private fun tac(code: String): List<String> {
        val preludeSize = instructionsOf(prelude).size
        return instructionsOf("$prelude\n$code").drop(preludeSize)
    }

    private fun instructionsOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")

        val program = assertNotNull(result.tac, "El TAC salio null para:\n$source")
        // Sin el begin_func y el end_func del main: lo que se prueba es su cuerpo.
        return TacPrinter.print(program.instructions).lines()
            .filterNot { it.startsWith("begin_func \$main") || it.startsWith("end_func \$main") }
            .map { it.trim() }
    }

    // ── La relacion invertida ──────────────────────────────────────────────

    @Test
    fun `cada relacion tiene su inversa y invertir dos veces la devuelve`() {
        assertEquals(RelationalOperator.GREATER_EQUAL, RelationalOperator.LESS.inverted)
        assertEquals(RelationalOperator.GREATER, RelationalOperator.LESS_EQUAL.inverted)
        assertEquals(RelationalOperator.NOT_EQUAL, RelationalOperator.EQUAL.inverted)

        RelationalOperator.entries.forEach { assertEquals(it, it.inverted.inverted) }
    }

    // ── El ternario ────────────────────────────────────────────────────────

    // Con caida, la condicion solo salta al lado falso, con la relacion invertida.
    @Test
    fun `el ternario copia cada rama al mismo temporal`() {
        assertEquals(
            listOf(
                "if a <= b goto L1",
                "t1 = a",
                "goto L2",
                "L1:",
                "t1 = b",
                "L2:",
                "m = t1"
            ),
            tac("let m: integer = a > b ? a : b;")
        )
    }

    @Test
    fun `una rama entera de un ternario float se convierte`() {
        assertEquals(
            listOf(
                "ifFalse c goto L1",
                "t2 = inttofloat x",
                "t1 = t2",
                "goto L2",
                "L1:",
                "t1 = 2.5",
                "L2:",
                "f = t1"
            ),
            tac("let f: float = c ? x : 2.5;")
        )
    }

    // ── Los booleanos como valor y el cortocircuito ────────────────────────

    // Si x < y es falso, z ni se mira: los dos saltos van al mismo lado falso.
    @Test
    fun `and como valor no evalua el segundo operando si el primero es falso`() {
        assertEquals(
            listOf(
                "if x >= y goto L1",
                "ifFalse z goto L1",
                "t1 = true",
                "goto L2",
                "L1:",
                "t1 = false",
                "L2:",
                "r = t1"
            ),
            tac("let r: boolean = x < y && z;")
        )
    }

    // Si a < b es verdadero salta directo al lado verdadero, que necesita su propia
    // etiqueta porque cae.
    @Test
    fun `or como valor no evalua el segundo operando si el primero es verdadero`() {
        assertEquals(
            listOf(
                "if a < b goto L3",
                "if x <= y goto L1",
                "L3:",
                "t1 = true",
                "goto L2",
                "L1:",
                "t1 = false",
                "L2:",
                "r = t1"
            ),
            tac("let r: boolean = a < b || x > y;")
        )
    }

    // !B no genera nada propio: las dos negaciones solo intercambian etiquetas.
    @Test
    fun `la negacion intercambia las etiquetas`() {
        val lines = tac("let r: boolean = !(a < b) && !(x > y);")

        assertEquals(listOf("if a < b goto L1", "if x > y goto L1"), lines.take(2))
    }

    @Test
    fun `una comparacion entre entero y flotante convierte el entero`() {
        val lines = tac("let r: boolean = x < 2.5 && c;")

        assertEquals(listOf("t2 = inttofloat x", "if t2 >=f 2.5 goto L1"), lines.take(2))
    }

    // Plegadas por el TypeChecker: `true` no salta nada y el ternario constante no
    // genera saltos.
    @Test
    fun `las condiciones constantes no comparan`() {
        assertEquals(listOf("ifFalse c goto L1"), tac("let r: boolean = true && c;").take(1))
        assertEquals(listOf("u = 1"), tac("let u: integer = true ? 1 : 2;"))
    }

    // ── El GDA y el orden de lectura ───────────────────────────────────────

    // La segunda multiplicacion esta en una rama que puede no ejecutarse: no se
    // reutiliza la primera.
    @Test
    fun `el GDA no comparte calculos a traves de un ternario`() {
        val lines = tac("let r: integer = a * b + (c ? a * b : 0);")

        assertEquals(2, lines.count { it.endsWith("= a * b") })
    }

    // La `a` de la izquierda se lee antes del ternario, que podria modificarla.
    @Test
    fun `una variable se copia antes de un ternario a su derecha`() {
        assertEquals("t1 = a", tac("let r: integer = a + (c ? 1 : 2);").first())
    }
}
