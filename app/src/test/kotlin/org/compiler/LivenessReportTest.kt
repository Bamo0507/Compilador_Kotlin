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
 * Tests del reporte de vivacidad.
 *
 * Corren el pipeline completo porque los contadores los llena el TypeChecker: el
 * LivenessReportBuilder solo los lee.
 */
class LivenessReportTest {

    private fun report(source: String): GarbageCollectorReport {
        val diagnostics = Diagnostics()
        val tree = SyntaxAnalyzer.parse(source, diagnostics)
            ?: fail("el fuente no parsea: ${diagnostics.all().map { it.message }}")

        val ast = AstBuilder().visit(tree) as Program
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        return LivenessReportBuilder().build(collector.globalScope)
    }

    private fun GarbageCollectorReport.symbolNamed(name: String): SymbolLiveness =
        entriesByScope.values.flatten().single { it.symbol.name == name }

    // ── Contadores de uso ──────────────────────────────────────────────────

    @Test
    fun `un uso cuenta uno`() {
        val x = report("let x: integer = 1;\nprint(x);").symbolNamed("x")

        assertEquals(1, x.useCount)
        assertEquals(2, x.lastUseLine)
        assertFalse(x.neverUsed)
    }

    // El TypeChecker cuenta y el LivenessReportBuilder solo formatea. Si las dos contaran, saldria 2.
    @Test
    fun `no hay doble conteo`() {
        assertEquals(1, report("let x: integer = 1; print(x);").symbolNamed("x").useCount)
    }

    @Test
    fun `una variable nunca usada`() {
        val r = report("let x: integer = 1;")

        assertEquals(0, r.symbolNamed("x").useCount)
        assertNull(r.symbolNamed("x").lastUseLine)
        assertTrue(r.neverUsed.any { it.symbol.name == "x" })
    }

    @Test
    fun `lastUseLine es la del ultimo uso`() {
        val x = report("let x: integer = 1;\nprint(x);\nprint(x);").symbolNamed("x")

        assertEquals(2, x.useCount)
        assertEquals(3, x.lastUseLine)
    }

    @Test
    fun `los campos de una clase tambien se cuentan`() {
        val r = report(
            """
            class Animal {
              let nombre: string;
              function hablar(): string { return this.nombre; }
            }
            """.trimIndent()
        )

        assertEquals(1, r.symbolNamed("nombre").useCount)
    }

    // ── Captura ────────────────────────────────────────────────────────────

    @Test
    fun `una local usada por una funcion anidada se marca`() {
        val r = report(
            """
            function crearContador(): integer {
              let cuenta: integer = 0;
              function siguiente(): integer { return cuenta; }
              return siguiente();
            }
            """.trimIndent()
        )

        assertTrue(r.symbolNamed("cuenta").usedInNestedFunction)
        assertTrue(r.usedInNestedFunctions.any { it.symbol.name == "cuenta" })
    }

    // Los globales viven todo el programa: no hay nada que capturar.
    @Test
    fun `una global usada por una funcion anidada no se marca`() {
        val r = report(
            """
            let total: integer = 0;
            function externa(): integer {
              function interna(): integer { return total; }
              return interna();
            }
            """.trimIndent()
        )

        assertFalse(r.symbolNamed("total").usedInNestedFunction)
    }

    // Los bloques no cuentan en functionDepth: un if dentro de la misma funcion no
    // dispara nada.
    @Test
    fun `un uso dentro de un if de la misma funcion no es captura`() {
        val r = report(
            """
            let bandera: boolean = true;
            function f(): integer {
              let cuenta: integer = 0;
              if (bandera) { cuenta = cuenta + 1; }
              return cuenta;
            }
            """.trimIndent()
        )

        assertFalse(r.symbolNamed("cuenta").usedInNestedFunction)
    }

    @Test
    fun `una funcion anidada que solo usa sus locales no captura nada`() {
        val r = report(
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
        val r = report(
            """
            class Animal { let nombre: string; }
            function procesar(): integer {
              for (let i: integer = 0; i < 3; i = i + 1) { }
              return 0;
            }
            """.trimIndent()
        )

        val scopes = r.entriesByScope.keys
        assertTrue(scopes.contains("global"))
        assertTrue(scopes.contains("Animal"))
        assertTrue(scopes.contains("procesar"))
        assertTrue(scopes.any { it.startsWith("for@") })
    }

    @Test
    fun `cada simbolo sabe en que ambito vive y en que linea se declaro`() {
        val r = report("function f(): integer {\n  let local: integer = 1;\n  return local;\n}")

        val local = r.symbolNamed("local")
        assertEquals("f", local.scopeName)
        assertEquals(2, local.declaredAtLine)
    }

    // Un ambito ya cerrado sigue en el reporte: el arbol no se descarta al salir.
    @Test
    fun `un bloque cerrado sigue apareciendo`() {
        val r = report("{ let temporal: integer = 1; }")

        assertTrue(r.entriesByScope.keys.any { it.startsWith("block@") })
        assertNotNull(r.symbolNamed("temporal"))
    }

    @Test
    fun `un programa vacio da un reporte con solo el ambito global`() {
        val r = report("")

        assertEquals(setOf("global"), r.entriesByScope.keys)
        assertTrue(r.neverUsed.isEmpty())
    }


    // ── Escrituras ─────────────────────────────────────────────────────────

    @Test
    fun `una escritura no cuenta como uso`() {
        val x = report("let x: integer = 0;\nx = 5;\nx = 10;").symbolNamed("x")

        assertEquals(0, x.useCount)
        assertTrue(x.neverUsed)
    }

    @Test
    fun `escribir un campo no cuenta como uso del campo`() {
        val r = report("class C { let cuenta: integer; function reiniciar() { this.cuenta = 0; } }")

        assertEquals(0, r.symbolNamed("cuenta").useCount)
    }

    // El cuerpo de mostrar se revisa al final aunque este arriba: el ultimo uso es
    // la linea mayor, no la ultima revisada.
    @Test
    fun `el ultimo uso es la linea mayor`() {
        val r = report("function mostrar() { print(g); }\nlet g: integer = 1;\nprint(g);")

        assertEquals(3, r.symbolNamed("g").lastUseLine)
    }
}
