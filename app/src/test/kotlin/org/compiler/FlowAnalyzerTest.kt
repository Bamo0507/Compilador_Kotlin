package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.FlowAnalyzer
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.syntax.SyntaxAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests del FlowAnalyzer.
 *
 * Corren el pipeline completo porque alwaysReturns depende del plegado de constantes
 * que hace el TypeChecker: sin el, `while (true)` no se reconoce como bucle infinito.
 */
class FlowAnalyzerTest {

    // Solo los errores del FlowAnalyzer: los de las fases previas se descartan para
    // que un programa con un error de tipo no ensucie la expectativa.
    private fun errorMessages(source: String): List<String> {
        val previousDiagnostics = Diagnostics()
        val tree = SyntaxAnalyzer.parse(source, previousDiagnostics)
            ?: fail("el fuente no parsea: ${previousDiagnostics.all().map { it.message }}")

        val ast = AstBuilder().visit(tree) as Program
        val collector = DeclarationCollector(previousDiagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, previousDiagnostics).check(ast)

        val flowDiagnostics = Diagnostics()
        FlowAnalyzer(flowDiagnostics).analyze(ast)
        return flowDiagnostics.all().map { it.message }
    }

    private fun assertValid(source: String) {
        val e = errorMessages(source)
        assertTrue(e.isEmpty(), "no deberia haber errores de flujo: $e")
    }

    private fun assertError(source: String, fragment: String) {
        val e = errorMessages(source)
        assertTrue(e.any { it.contains(fragment) }, "se esperaba '$fragment', se obtuvo: $e")
    }

    // ── Ubicacion de los saltos ────────────────────────────────────────────

    @Test
    fun `break y continue dentro de un bucle`() {
        assertValid("while (true) { break; }")
        assertValid("foreach (n in [1, 2]) { continue; }")
    }

    @Test
    fun `break fuera de un bucle`() {
        assertError("break;", "solo se puede usar dentro de un bucle")
        assertError("function f() { break; }", "solo se puede usar dentro de un bucle")
    }

    // El caso que se olvida: la funcion anidada NO hereda el bucle de afuera.
    @Test
    fun `break dentro de una funcion anidada en un bucle`() {
        assertError("while (true) { function f() { break; } }", "dentro de un bucle")
    }

    @Test
    fun `return dentro y fuera de una funcion`() {
        assertValid("function f(): integer { return 1; }")
        assertError("return 1;", "solo se puede usar dentro de una función")
    }

    // Decision 5: no hay break de switch, asi que fuera de un bucle es error.
    @Test
    fun `break en un switch fuera de un bucle`() {
        assertError("let x: integer = 1; switch (x) { case 1: break; }", "dentro de un bucle")
    }

    @Test
    fun `break en un switch dentro de un bucle rompe el bucle`() {
        assertValid("let x: integer = 1; while (true) { switch (x) { case 1: break; } }")
    }

    // ── Codigo muerto ──────────────────────────────────────────────────────

    @Test
    fun `sentencia despues de un return en el nivel superior`() {
        assertError("function f() { } return; print(1);", "Código inalcanzable")
    }

    // El caso que el plan no detectaba: checkUnreachable corria solo sobre el
    // programa, no sobre los cuerpos.
    @Test
    fun `sentencia despues de un return dentro de una funcion`() {
        assertError("function f(): integer { return 1; print(2); }", "Código inalcanzable")
    }

    @Test
    fun `sentencia despues de un break`() {
        assertError("while (true) { break; print(1); }", "Código inalcanzable")
    }

    @Test
    fun `el mensaje nombra la linea del corte`() {
        val e = errorMessages("function f(): integer {\n  return 1;\n  print(2);\n}")
        assertTrue(e.single().contains("línea 2"), "mensaje: $e")
    }

    @Test
    fun `diez sentencias muertas producen un solo error`() {
        val body = (1..10).joinToString("\n") { "  print($it);" }
        assertEquals(1, errorMessages("function f(): integer {\n  return 1;\n$body\n}").size)
    }

    @Test
    fun `un return como ultima sentencia no es codigo muerto`() {
        assertValid("function f(): integer { print(1); return 1; }")
    }

    // ── Retorno en todos los caminos ───────────────────────────────────────

    @Test
    fun `if sin else no garantiza retorno`() {
        assertError(
            "let x: boolean = true; function f(): integer { if (x) { return 1; } }",
            "hay caminos que no retornan"
        )
    }

    @Test
    fun `if con else donde ambas ramas retornan`() {
        assertValid("let x: boolean = true; function f(): integer { if (x) { return 1; } else { return 2; } }")
    }

    @Test
    fun `un return despues del if cubre el camino faltante`() {
        assertValid("let x: boolean = true; function f(): integer { if (x) { return 1; } return 2; }")
    }

    @Test
    fun `un while que puede no ejecutarse no garantiza retorno`() {
        assertError(
            "let x: boolean = true; function f(): integer { while (x) { return 1; } }",
            "hay caminos que no retornan"
        )
    }

    @Test
    fun `while con condicion constante si garantiza`() {
        assertValid("function f(): integer { while (true) { return 1; } }")
    }

    // Sale del plegado del TypeChecker: en el proyecto anterior esto era un falso positivo.
    @Test
    fun `while con una condicion que se pliega a true`() {
        assertValid("function f(): integer { while (1 == 1) { return 1; } }")
    }

    @Test
    fun `while sobre una const que vale true`() {
        assertValid("const SIEMPRE: boolean = true; function f(): integer { while (SIEMPRE) { return 1; } }")
    }

    // Limitacion documentada: una variable mutable no propaga su valor.
    @Test
    fun `while sobre una let que vale true da falso positivo`() {
        assertError(
            "let siempre: boolean = true; function f(): integer { while (siempre) { return 1; } }",
            "hay caminos que no retornan"
        )
    }

    @Test
    fun `un break escapa del bucle infinito`() {
        assertError(
            "let x: boolean = true; function f(): integer { while (true) { if (x) { break; } } }",
            "hay caminos que no retornan"
        )
    }

    // El break del bucle INTERNO no cuenta para el externo.
    @Test
    fun `un break en un bucle anidado no escapa del externo`() {
        assertValid(
            """
            let y: boolean = true;
            function f(): integer {
              while (true) {
                while (y) { break; }
                return 1;
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `do while garantiza retorno porque su cuerpo corre al menos una vez`() {
        assertValid("let x: boolean = true; function f(): integer { do { return 1; } while (x); }")
    }

    @Test
    fun `un switch sin default no garantiza retorno`() {
        assertError(
            "let x: integer = 1; function f(): integer { switch (x) { case 1: return 1; } }",
            "hay caminos que no retornan"
        )
    }

    @Test
    fun `un switch con default donde todos los casos retornan`() {
        assertValid(
            "let x: integer = 1; function f(): integer { switch (x) { case 1: return 1; default: return 2; } }"
        )
    }

    @Test
    fun `una funcion void no tiene que retornar`() {
        assertValid("function f() { print(1); }")
    }

    // El mensaje usa TypeReference.name: con baseName solo diria "integer".
    @Test
    fun `el mensaje conserva las dimensiones del arreglo`() {
        val e = errorMessages("let x: boolean = true; function f(): integer[] { if (x) { return [1]; } }")
        assertTrue(e.single().contains("'integer[]'"), "mensaje: $e")
    }


    // ── try/catch ──────────────────────────────────────────────────────────

    @Test
    fun `un try catch donde las dos ramas retornan garantiza retorno`() {
        assertValid("function f(): integer { try { return 1; } catch (e) { return 2; } }")
    }

    @Test
    fun `un try catch donde solo el try retorna no garantiza retorno`() {
        assertError("function f(): integer { try { return 1; } catch (e) { } }", "hay caminos que no retornan")
    }
}
