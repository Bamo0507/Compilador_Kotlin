package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.LivenessReportBuilder
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.semantic.models.GarbageCollectorReport
import org.compiler.frontend.semantic.models.SymbolLiveness
import org.compiler.frontend.syntax.SyntaxAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests del ticket 5.2.
 *
 * Corren el pipeline completo porque los contadores los llena la Fase 4: este ticket
 * solo los lee.
 */
class LivenessReportTest {

    private fun reporte(fuente: String): GarbageCollectorReport {
        val diagnostics = Diagnostics()
        val tree = SyntaxAnalyzer.parse(fuente, diagnostics)
            ?: fail("el fuente no parsea: ${diagnostics.all().map { it.message }}")

        val ast = AstBuilder().visit(tree) as Program
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        return LivenessReportBuilder().build(collector.globalScope)
    }

    private fun GarbageCollectorReport.simbolo(nombre: String): SymbolLiveness =
        entriesByScope.values.flatten().single { it.symbol.name == nombre }

    // ── Contadores de uso ──────────────────────────────────────────────────

    @Test
    fun `un uso cuenta uno`() {
        val x = reporte("let x: integer = 1;\nprint(x);").simbolo("x")

        assertEquals(1, x.useCount)
        assertEquals(2, x.lastUseLine)
        assertFalse(x.neverUsed)
    }

    // La Fase 4 cuenta y la Fase 5 solo formatea. Si las dos contaran, saldria 2.
    @Test
    fun `no hay doble conteo`() {
        assertEquals(1, reporte("let x: integer = 1; print(x);").simbolo("x").useCount)
    }

    @Test
    fun `una variable nunca usada`() {
        val r = reporte("let x: integer = 1;")

        assertEquals(0, r.simbolo("x").useCount)
        assertNull(r.simbolo("x").lastUseLine)
        assertTrue(r.neverUsed.any { it.symbol.name == "x" })
    }

    @Test
    fun `lastUseLine es la del ultimo uso`() {
        val x = reporte("let x: integer = 1;\nprint(x);\nprint(x);").simbolo("x")

        assertEquals(2, x.useCount)
        assertEquals(3, x.lastUseLine)
    }

    @Test
    fun `los campos de una clase tambien se cuentan`() {
        val r = reporte(
            """
            class Animal {
              let nombre: string;
              function hablar(): string { return this.nombre; }
            }
            """.trimIndent()
        )

        assertEquals(1, r.simbolo("nombre").useCount)
    }

    // ── Captura ────────────────────────────────────────────────────────────

    @Test
    fun `una local usada por una funcion anidada se marca`() {
        val r = reporte(
            """
            function crearContador(): integer {
              let cuenta: integer = 0;
              function siguiente(): integer { return cuenta; }
              return siguiente();
            }
            """.trimIndent()
        )

        assertTrue(r.simbolo("cuenta").usedInNestedFunction)
        assertTrue(r.usedInNestedFunctions.any { it.symbol.name == "cuenta" })
    }

    // Los globales viven todo el programa: no hay nada que capturar.
    @Test
    fun `una global usada por una funcion anidada no se marca`() {
        val r = reporte(
            """
            let total: integer = 0;
            function externa(): integer {
              function interna(): integer { return total; }
              return interna();
            }
            """.trimIndent()
        )

        assertFalse(r.simbolo("total").usedInNestedFunction)
    }

    // Los bloques no cuentan en functionDepth: un if dentro de la misma funcion no
    // dispara nada.
    @Test
    fun `un uso dentro de un if de la misma funcion no es captura`() {
        val r = reporte(
            """
            let bandera: boolean = true;
            function f(): integer {
              let cuenta: integer = 0;
              if (bandera) { cuenta = cuenta + 1; }
              return cuenta;
            }
            """.trimIndent()
        )

        assertFalse(r.simbolo("cuenta").usedInNestedFunction)
    }

    @Test
    fun `una funcion anidada que solo usa sus locales no captura nada`() {
        val r = reporte(
            """
            function externa(): integer {
              function interna(): integer { let propia: integer = 1; return propia; }
              return interna();
            }
            """.trimIndent()
        )

        assertTrue(r.usedInNestedFunctions.isEmpty())
    }

    // ── La forma del reporte ───────────────────────────────────────────────

    @Test
    fun `hay una entrada por cada ambito del programa`() {
        val r = reporte(
            """
            class Animal { let nombre: string; }
            function procesar(): integer {
              for (let i: integer = 0; i < 3; i = i + 1) { }
              return 0;
            }
            """.trimIndent()
        )

        val ambitos = r.entriesByScope.keys
        assertTrue(ambitos.contains("global"))
        assertTrue(ambitos.contains("Animal"))
        assertTrue(ambitos.contains("procesar"))
        assertTrue(ambitos.any { it.startsWith("for@") })
    }

    @Test
    fun `cada simbolo sabe en que ambito vive y en que linea se declaro`() {
        val r = reporte("function f(): integer {\n  let local: integer = 1;\n  return local;\n}")

        val local = r.simbolo("local")
        assertEquals("f", local.scopeName)
        assertEquals(2, local.declaredAtLine)
    }

    // Un ambito ya cerrado sigue en el reporte: el arbol no se descarta al salir.
    @Test
    fun `un bloque cerrado sigue apareciendo`() {
        val r = reporte("{ let temporal: integer = 1; }")

        assertTrue(r.entriesByScope.keys.any { it.startsWith("block@") })
        assertNotNull(r.simbolo("temporal"))
    }

    @Test
    fun `un programa vacio da un reporte con solo el ambito global`() {
        val r = reporte("")

        assertEquals(setOf("global"), r.entriesByScope.keys)
        assertTrue(r.neverUsed.isEmpty())
    }
}
