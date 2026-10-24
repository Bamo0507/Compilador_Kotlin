// El control de flujo: las etiquetas, los saltos incondicionales, los condicionales de
// cada tipo, y el momento exacto en que se vacian los registros.
package org.compiler

import org.compiler.backend.ArmGenerator
import org.compiler.backend.ArmPrinter
import org.compiler.backend.models.AssemblyProgram
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmGeneratorFlowTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun linesOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")
        return ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }
    }

    // Las instrucciones que siguen al comentario de una linea del TAC, hasta el
    // comentario siguiente, sin los comentarios al final de cada una.
    private fun translationOf(lines: List<String>, tacLine: String): List<String> {
        val start = lines.indexOf("@ $tacLine")
        assertTrue(start >= 0, "No esta '$tacLine' en:\n${lines.joinToString("\n")}")
        return lines.drop(start + 1).takeWhile { !it.startsWith("@") }
            .map { it.substringBefore("  @") }
    }

    private fun translationOf(source: String, tacLine: String): List<String> =
        translationOf(linesOf(source), tacLine)

    // Un main vacio con estas instrucciones adentro. Sirve para probar una forma del TAC
    // que ningun programa de la bateria produce todavia sin su parte que no se traduce.
    private fun linesOf(vararg quadruples: Quadruple): List<String> {
        val result = CompilerPipeline.compile("", execute = false)
        val tac = assertNotNull(result.tac)
        val (begin, end) = tac.instructions
        val program = tac.copy(instructions = listOf(begin) + quadruples + listOf(end))
        val assembly: AssemblyProgram =
            ArmGenerator(program, assertNotNull(result.storageLayout)).generate()
        return ArmPrinter.print(assembly, includeRuntime = false).lines().map { it.trim() }
    }

    // El programa de referencia de casi todas las pruebas. `m` sale de un ternario, el
    // ciclo compara contra una constante, y `t` es un booleano guardado en una variable.
    private val program = """
        function f(a: integer, b: integer) {
          let m: integer = a > b ? a : b;
          print(m);
          var i: integer = 0;
          while (i < 5) { i = i + 1; }
          let t: boolean = a < b;
          if (t) { print(1); }
        }
    """.trimIndent()

    // ── Etiquetas ──────────────────────────────────────────────────────────

    // El numero de la etiqueta ya es unico en todo el programa; el nombre de la funcion
    // esta para poder leer el .s y saber de quien es el salto.
    @Test
    fun `una etiqueta lleva el nombre de su funcion`() {
        val lines = linesOf(program)

        assertTrue(".Lf_1:" in lines, "falta la etiqueta de f en:\n${lines.joinToString("\n")}")
        assertTrue(lines.none { it.startsWith(".L1:") }, "una etiqueta sin funcion")
    }

    // Dos funciones pueden tener etiquetas con el mismo numero, y el .s es uno solo.
    @Test
    fun `las etiquetas de dos funciones no chocan`() {
        val lines = linesOf(
            """
            function uno(a: integer) { if (a > 0) { print(1); } }
            function dos(a: integer) { if (a > 0) { print(2); } }
            """.trimIndent()
        )

        val labels = lines.filter { it.startsWith(".L") && it.endsWith(":") }
        assertEquals(labels.distinct(), labels, "hay etiquetas repetidas: $labels")
        assertTrue(labels.any { it.startsWith(".Luno_") } && labels.any { it.startsWith(".Ldos_") })
    }

    // ── El salto incondicional ─────────────────────────────────────────────

    @Test
    fun `goto vacia el bloque antes de saltar`() {
        assertEquals(
            listOf("str r4, [fp, #-28]", "b .Lf_3"),
            translationOf(program, "goto L3")
        )
    }

    // ── El salto condicional ───────────────────────────────────────────────

    /**
     * La prueba que pide la aceptacion del ticket.
     *
     * `m` solo esta en un registro cuando llega el `if`, y se lee despues de la etiqueta
     * de salida. El `str` que lo guarda va DESPUES del `cmp` y ANTES del salto: ahi
     * todavia se sabe de que bloque se viene, y ni `ldr` ni `str` tocan las banderas, asi
     * que la comparacion sigue valiendo cuando se llega al `b`.
     */
    @Test
    fun `el vaciado va entre la comparacion y el salto`() {
        val source = """
            function f(a: integer) {
              let x: integer = a + 1;
              if (a > 0) { print(0); }
              print(x);
            }
        """.trimIndent()

        assertEquals(
            listOf("cmp r4, #0", "str r5, [fp, #-20]", "ble .Lf_1"),
            translationOf(source, "if a <= 0 goto L1")
        )
    }

    /**
     * El otro lado de lo mismo, en el ternario.
     *
     * La rama verdadera termina en `goto`, y lo que esta escrito despues de ese goto es
     * la rama falsa, que vuelve a escribir el mismo temporal. Si la vivacidad se leyera
     * en orden de archivo, t1 pareceria muerto aqui, no se guardaria, y esta rama
     * imprimiria basura. Se guarda porque el camino real va a L2, donde se lee.
     */
    @Test
    fun `la rama que salta sobre la otra guarda igual su temporal`() {
        assertEquals(
            listOf("str r4, [fp, #-40]", "b .Lf_2"),
            translationOf(program, "goto L2")
        )
    }

    @Test
    fun `una relacion de enteros compara y salta con el sufijo de su operador`() {
        assertEquals(
            listOf("ldr r4, [fp, #-4]", "ldr r5, [fp, #-8]", "cmp r4, r5", "ble .Lf_1"),
            translationOf(program, "if a <= b goto L1")
        )
    }

    // Igual que en add y sub: una constante que cabe va dentro de la instruccion y no
    // ocupa registro.
    @Test
    fun `una constante que cabe va dentro del cmp`() {
        assertEquals(
            listOf("ldr r4, [fp, #-28]", "cmp r4, #5", "bge .Lf_4"),
            translationOf(program, "if i >= 5 goto L4")
        )
    }

    // ── Booleanos ──────────────────────────────────────────────────────────

    // Un booleano del TAC es 1 o 0, asi que "es verdadero" es "no es cero".
    @Test
    fun `ifFalse compara contra cero y salta cuando es igual`() {
        assertEquals(
            listOf("cmp r4, #0", "strb r4, [fp, #-29]", "beq .Lf_5"),
            translationOf(program, "ifFalse t goto L5")
        )
    }

    // El `if x goto L` del TAC no sale de un `if` del lenguaje, que se compila con la
    // relacion invertida: sale de un `||`, donde el primer operando verdadero ya decide
    // y se salta sin mirar el segundo.
    @Test
    fun `if compara contra cero y salta cuando es distinto`() {
        val code = translationOf(
            "let a: boolean = true; let b: boolean = false; let c: boolean = a || b; print(c);",
            "if a goto L3"
        )

        assertEquals("cmp r4, #0", code.first())
        assertEquals("bne .Lmain_3", code.last())
    }

    // null es la direccion 0, asi que la comparacion es la misma de los enteros. Ningun
    // programa de la bateria llega todavia aqui: los chequeos contra null salen de los
    // objetos y las listas, que piden `alloc`.
    @Test
    fun `null es la direccion cero`() {
        val lines = linesOf(
            IfRelationalGoto(
                Constant(null), RelationalOperator.NOT_EQUAL, Constant(null),
                OperandKind.REFERENCE, Label(1)
            ),
            LabelDefinition(Label(1))
        )

        assertEquals(
            listOf("mov r2, #0", "cmp r2, #0", "bne .Lmain_1"),
            translationOf(lines, "if null != null goto L1")
        )
    }

    // ── Textos y flotantes ─────────────────────────────────────────────────

    // La rutina ya existe desde el ticket 3.4: llama a strcmp y deja en r0 un numero
    // negativo, cero o positivo, que se compara con 0 usando la misma relacion del TAC.
    @Test
    fun `comparar textos llama a la rutina y mira r0`() {
        assertEquals(
            listOf(
                "ldr r4, [fp, #-4]", "ldr r3, =.Lstr_1", "mov r0, r4", "mov r1, r3",
                "bl cps_comparar_strings", "cmp r0, #0", "bne .Lf_1"
            ),
            translationOf(
                """function f(s: string) { if (s == "hola") { print(1); } }""",
                """if s !=s "hola" goto L1"""
            )
        )
    }

    // vcmp compara en la unidad de punto flotante y vmrs copia el resultado a las
    // banderas principales, que son las que leen los sufijos.
    @Test
    fun `comparar flotantes pasa por vcmp y vmrs`() {
        assertEquals(
            listOf(
                "vldr d1, [fp, #-8]", "vldr d2, [fp, #-16]",
                "vcmp.f64 d1, d2", "vmrs APSR_nzcv, fpscr", "bmi .Lf_1"
            ),
            translationOf(
                "function f(x: float, y: float) { if (x >= y) { print(1); } }",
                "if x <f y goto L1"
            )
        )
    }

    /**
     * Menor y menor o igual no usan `lt` ni `le` cuando los operandos son flotantes.
     *
     * Esos dos sufijos tambien se cumplen cuando la comparacion quedo "sin orden", que es
     * lo que pasa si alguno de los dos operandos no es un numero. `mi` y `ls` no.
     */
    @Test
    fun `los sufijos de flotante no son los de entero`() {
        val source = """
            function f(x: float, y: float) {
              if (x >= y) { print(1); }
              if (x > y) { print(2); }
            }
        """.trimIndent()

        assertEquals("bmi .Lf_1", translationOf(source, "if x <f y goto L1").last())
        assertEquals("bls .Lf_2", translationOf(source, "if x <=f y goto L2").last())
    }

    // ── La bateria ─────────────────────────────────────────────────────────

    // Lo que el ticket agrega al alcance: estos ya no se quedan sin ensamblador. Los
    // demas programas de flujo necesitan llamadas o excepciones, que son de las fases 5
    // y 7.
    @Test
    fun `los programas de flujo sin llamadas ya se traducen enteros`() {
        listOf("flujo_condiciones", "flujo_ternario", "tipos_logicos").forEach { name ->
            val sample = SamplePrograms.all.first { it.id == "validos/$name" }
            val result = CompilerPipeline.compile(sample.source, execute = false)

            assertNotNull(result.assembly, "$name se quedo sin ensamblador")
        }
    }
}
