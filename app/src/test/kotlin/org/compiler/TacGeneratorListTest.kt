// Tickets 5.4 y 5.5: listas, acceso por indice, foreach, y los chequeos de null y de
// rango.
//
// Las listas se declaran en la linea 1 con `lista`, y el codigo probado empieza en la
// linea 2. Solo se compara lo que genera el codigo probado.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TacGeneratorListTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun mainOf(source: String): List<String> {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val tac = assertNotNull(result.tac, "El TAC salio null para:\n$source")

        val all = TacPrinter.print(tac.instructions).lines().map { it.trim() }
        return all.subList(all.indexOf("begin_func \$main, ${tac.activationRecords.first().size}") + 1,
            all.indexOf("end_func \$main"))
    }

    // El main sin las lineas del preludio: `prelude` las declara y no son lo que se prueba.
    private fun tac(code: String, prelude: String = "let lista: integer[] = [10, 20, 30]; let i: integer = 1;"): List<String> {
        val skipped = mainOf(prelude).size
        return mainOf("$prelude\n$code").drop(skipped)
    }

    // Los chequeos de null y de rango de un acceso `lista[i]` en la linea 2, con sus
    // etiquetas a partir de L1.
    private val checksOfListI = listOf(
        "if lista != null goto L1",
        "throw \"Acceso a null (línea 2)\"",
        "L1:",
        "t1 = lista[0]",
        "if i < 0 goto L3",
        "if i < t1 goto L2",
        "L3:",
        "throw \"Índice fuera de rango (línea 2)\"",
        "L2:"
    )

    // ── Creacion ───────────────────────────────────────────────────────────

    // El largo en la casilla 0 y los enteros desde 4: 4 + 3 × 4 = 16 bytes.
    @Test
    fun `una lista de enteros pide 16 bytes y escribe largo y elementos`() {
        assertEquals(
            listOf("t1 = alloc 16", "t1[0] = 3", "t1[4] = 10", "t1[8] = 20", "t1[12] = 30", "l = t1"),
            tac("let l: integer[] = [10, 20, 30];", prelude = "")
        )
    }

    // Un float mide 8 y se alinea a 8: los elementos empiezan en 8, no en 4.
    @Test
    fun `los elementos float empiezan en 8`() {
        assertEquals(
            listOf("t1 = alloc 24", "t1[0] = 2", "t1[8] = 1.5", "t1[16] = 2.5", "f = t1"),
            tac("let f: float[] = [1.5, 2.5];", prelude = "")
        )
    }

    // Un entero en una lista float se convierte al escribirse.
    @Test
    fun `un entero en una lista float se convierte`() {
        assertTrue("t1[8] = 1.0" in tac("let f: float[] = [1, 2.5];", prelude = ""))
    }

    @Test
    fun `una lista vacia solo pide la casilla del largo`() {
        assertEquals(listOf("t1 = alloc 4", "t1[0] = 0", "v = t1"), tac("let v: integer[] = [];", prelude = ""))
    }

    // ── Acceso por indice ──────────────────────────────────────────────────

    // inicio + i × tamaño, despues de los dos chequeos.
    @Test
    fun `leer lista de i calcula el desplazamiento despues de los chequeos`() {
        assertEquals(
            checksOfListI + listOf("t1 = i * 4", "t1 = t1 + 4", "t1 = lista[t1]", "print_i t1"),
            tac("print(lista[i]);")
        )
    }

    @Test
    fun `escribir lista de i usa la misma formula`() {
        assertEquals(
            checksOfListI + listOf("t1 = i * 4", "t1 = t1 + 4", "lista[t1] = 5"),
            tac("lista[i] = 5;")
        )
    }

    // Con un indice constante el desplazamiento se calcula al compilar: 4 + 2 × 4 = 12.
    // Solo se compara con el largo: el negativo constante ya lo rechazo el TypeChecker.
    @Test
    fun `con un indice constante solo se compara con el largo`() {
        assertEquals(
            listOf(
                "if lista != null goto L1",
                "throw \"Acceso a null (línea 2)\"",
                "L1:",
                "t1 = lista[0]",
                "if 2 < t1 goto L2",
                "throw \"Índice fuera de rango (línea 2)\"",
                "L2:",
                "t1 = lista[12]",
                "print_i t1"
            ),
            tac("print(lista[2]);")
        )
    }

    // Un booleano mide 1: no hace falta multiplicar.
    @Test
    fun `los elementos de un byte no se multiplican`() {
        val code = tac("print(bs[i]);", prelude = "let bs: boolean[] = [true, false]; let i: integer = 0;")

        assertTrue("t1 = i + 4" in code)
        assertTrue(code.none { it.contains("*") })
    }

    // El primer acceso devuelve la referencia a la fila, y el segundo la chequea.
    @Test
    fun `una matriz son dos accesos encadenados`() {
        val code = tac(
            "print(m[i][j]);",
            prelude = "let m: integer[][] = [[1, 2], [3, 4]]; let i: integer = 0; let j: integer = 1;"
        )

        assertEquals(2, code.count { it.startsWith("throw \"Acceso a null") })
        assertEquals(2, code.count { it.startsWith("throw \"Índice fuera de rango") })
        assertTrue("t1 = m[t1]" in code)
        assertTrue("if t1 != null goto L4" in code, "la fila tambien se chequea:\n${code.joinToString("\n")}")
        assertTrue("t1 = t1[t2]" in code)
    }

    // ── foreach ────────────────────────────────────────────────────────────

    // $lista se evalua una vez; $i recorre con el largo; continue va a la etiqueta que
    // incrementa.
    @Test
    fun `foreach con continue salta al incremento`() {
        assertEquals(
            listOf(
                "\$lista = lista",
                "if \$lista != null goto L1",
                "throw \"Acceso a null (línea 2)\"",
                "L1:",
                "\$i = 0",
                "L2:",
                "t1 = \$lista[0]",
                "if \$i >= t1 goto L4",
                "t1 = \$i * 4",
                "t1 = t1 + 4",
                "n = \$lista[t1]",
                "if n != 20 goto L5",
                "goto L3",
                "L5:",
                "print_i n",
                "L3:",
                "\$i = \$i + 1",
                "goto L2",
                "L4:"
            ),
            tac("foreach (n in lista) { if (n == 20) { continue; } print(n); }")
        )
    }

    @Test
    fun `break en un foreach sale al final`() {
        val code = tac("foreach (n in lista) { break; }")

        assertTrue("goto L4" in code.subList(code.indexOf("n = \$lista[t1]"), code.size))
        assertEquals("L4:", code.last())
    }

    // ── Chequeos dentro de un try ──────────────────────────────────────────

    // El throw del chequeo queda entre try y endtry: el catch lo atrapa.
    @Test
    fun `el throw de rango queda dentro del bloque protegido`() {
        val code = tac("try { print(lista[10]); } catch (e) { print(e); }")

        val tryAt = code.indexOf("try L1, e")
        val throwAt = code.indexOfFirst { it.startsWith("throw \"Índice fuera de rango") }
        val endTryAt = code.indexOf("endtry")
        assertTrue(tryAt in 0 until throwAt && throwAt < endTryAt, code.joinToString("\n"))
    }

    // ── La bateria ─────────────────────────────────────────────────────────

    @Test
    fun `tipos_listas genera TAC`() {
        val sample = assertNotNull(SamplePrograms.byId("validos/tipos_listas"))
        assertTrue(mainOf(sample.source).isNotEmpty())
    }
}
