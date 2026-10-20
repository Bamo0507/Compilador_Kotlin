// Listas, acceso por indice, foreach, y los chequeos de null y de rango.
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

        val lines = TacPrinter.print(tac.instructions).lines().map { it.trim() }
        val mainBegin = "begin_func \$main, ${tac.activationRecords.first().size}"
        return lines.subList(lines.indexOf(mainBegin) + 1, lines.indexOf("end_func \$main"))
    }

    private val defaultPrelude = "let lista: integer[] = [10, 20, 30]; let i: integer = 1;"

    // El main sin las lineas del preludio: `prelude` las declara y no son lo que se prueba.
    private fun tac(code: String, prelude: String = defaultPrelude): List<String> {
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
        "L2:",
        "t1 = lista[8]"
    )

    // ── Creacion ───────────────────────────────────────────────────────────

    // La ficha mide 12: longitud, capacidad y contenido. El contenido tiene la
    // capacidad minima, 4, y los elementos desde su casilla 0: 4 × 4 = 16 bytes.
    @Test
    fun `una lista de enteros pide la ficha y el contenido`() {
        assertEquals(
            listOf(
                "t1 = alloc 12", "t1[0] = 3", "t1[4] = 4", "t2 = alloc 16", "t1[8] = t2",
                "t2[0] = 10", "t2[4] = 20", "t2[8] = 30", "l = t1"
            ),
            tac("let l: integer[] = [10, 20, 30];", prelude = "")
        )
    }

    // El contenido es un bloque propio, ya alineado a 8: un float no necesita relleno.
    @Test
    fun `los elementos float empiezan en la casilla 0 del contenido`() {
        assertEquals(
            listOf(
                "t1 = alloc 12", "t1[0] = 2", "t1[4] = 4", "t2 = alloc 32", "t1[8] = t2",
                "t2[0] = 1.5", "t2[8] = 2.5", "f = t1"
            ),
            tac("let f: float[] = [1.5, 2.5];", prelude = "")
        )
    }

    // Un entero en una lista float se convierte al escribirse.
    @Test
    fun `un entero en una lista float se convierte`() {
        assertTrue("t2[0] = 1.0" in tac("let f: float[] = [1, 2.5];", prelude = ""))
    }

    // Sin elementos no se sabe cuanto mide uno: el contenido lo pide el primer push.
    @Test
    fun `una lista vacia solo pide la ficha`() {
        assertEquals(
            listOf("t1 = alloc 12", "t1[0] = 0", "t1[4] = 0", "t1[8] = null", "v = t1"),
            tac("let v: integer[] = [];", prelude = "")
        )
    }

    // ── Acceso por indice ──────────────────────────────────────────────────

    // Despues de los dos chequeos, el contenido y el desplazamiento i × tamaño.
    @Test
    fun `leer lista de i calcula el desplazamiento despues de los chequeos`() {
        assertEquals(
            checksOfListI + listOf("t2 = i * 4", "t1 = t1[t2]", "print_i t1"),
            tac("print(lista[i]);")
        )
    }

    @Test
    fun `escribir lista de i usa la misma formula`() {
        assertEquals(
            checksOfListI + listOf("t2 = i * 4", "t1[t2] = 5"),
            tac("lista[i] = 5;")
        )
    }

    // Con un indice constante el desplazamiento se calcula al compilar: 2 × 4 = 8.
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
                "t1 = lista[8]",
                "t1 = t1[8]",
                "print_i t1"
            ),
            tac("print(lista[2]);")
        )
    }

    // Un booleano mide 1: no hace falta multiplicar.
    @Test
    fun `los elementos de un byte no se multiplican`() {
        val code = tac(
            "print(banderas[i]);",
            prelude = "let banderas: boolean[] = [true, false]; let i: integer = 0;"
        )

        assertTrue("t1 = t1[i]" in code)
        assertTrue(code.none { it.contains("*") })
    }

    // El primer acceso devuelve la referencia a la fila, y el segundo la chequea.
    @Test
    fun `una matriz son dos accesos encadenados`() {
        val code = tac(
            "print(m[i][j]);",
            prelude = "let m: integer[][] = [[1, 2], [3, 4]]; " +
                "let i: integer = 0; let j: integer = 1;"
        )

        assertEquals(2, code.count { it.startsWith("throw \"Acceso a null") })
        assertEquals(2, code.count { it.startsWith("throw \"Índice fuera de rango") })
        assertTrue("t1 = t1[t2]" in code)
        assertTrue(
            "if t1 != null goto L4" in code,
            "la fila tambien se chequea:\n${code.joinToString("\n")}"
        )
        assertTrue("t1 = t2[t3]" in code)
    }

    // ── push y length ──────────────────────────────────────────────────────

    // El valor primero, y el chequeo de null justo antes de agregar.
    @Test
    fun `push evalua el valor y chequea la lista antes de agregar`() {
        assertEquals(
            listOf(
                "t1 = i + 1",
                "if lista != null goto L1",
                "throw \"Acceso a null (línea 2)\"",
                "L1:",
                "push_i lista, t1"
            ),
            tac("lista.push(i + 1);")
        )
    }

    @Test
    fun `un entero que entra a una lista float se convierte`() {
        assertTrue(
            "push_f precios, 2.0" in tac("precios.push(2);", prelude = "let precios: float[] = [];")
        )
    }

    // Si el valor llama a una funcion, la lista se copia antes: la funcion podria
    // reasignarla, y se agrega a la lista que habia antes de la llamada.
    @Test
    fun `push copia la lista antes de una llamada`() {
        val code = tac(
            "lista.push(f());",
            prelude = "let lista: integer[] = [1]; function f(): integer { return 5; }"
        )

        assertEquals("t1 = lista", code.first())
        assertEquals("push_i t1, t2", code.last())
    }

    @Test
    fun `length lee la casilla 0 de la ficha`() {
        assertEquals(
            listOf(
                "if lista != null goto L1",
                "throw \"Acceso a null (línea 2)\"",
                "L1:",
                "t1 = lista[0]",
                "print_i t1"
            ),
            tac("print(lista.length);")
        )
    }

    // ── foreach ────────────────────────────────────────────────────────────

    // $lista se evalua una vez; $i recorre con el largo; el contenido se relee en cada
    // vuelta, porque el cuerpo puede mudarlo con un push; continue va a la etiqueta que
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
                "t1 = \$lista[8]",
                "t2 = \$i * 4",
                "n = t1[t2]",
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

        assertTrue("goto L4" in code.subList(code.indexOf("n = t1[t2]"), code.size))
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
