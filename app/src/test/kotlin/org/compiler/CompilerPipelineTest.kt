package org.compiler

import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del ticket 7.1.
 *
 * El pipeline no tiene logica propia: lo que se prueba es el ORDEN de las etapas y
 * que cada fallo deje el resultado en la forma que la GUI espera.
 */
class CompilerPipelineTest {

    private fun TreeNodeView.count(): Int = 1 + children.sumOf { it.count() }

    // ── El camino feliz ────────────────────────────────────────────────────

    @Test
    fun `un programa valido puebla todos los campos y no reporta errores`() {
        val result = CompilerPipeline.compile("let x: integer = 2;\nprint(x * 3);")

        assertNotNull(result.parseTreeView)
        assertNotNull(result.ast)
        assertNotNull(result.globalScope)
        assertNotNull(result.garbageCollectorReport)
        assertNotNull(result.execution)

        assertTrue(result.errors.isEmpty(), "errores inesperados: ${result.errors}")
        assertEquals(listOf("6"), result.execution.output)
    }

    @Test
    fun `la raiz del arbol de ANTLR es la regla program`() {
        val vista = CompilerPipeline.compile("print(1);").parseTreeView

        assertNotNull(vista)
        assertEquals("program", vista.label)
    }

    // La torre de precedencia de la gramatica: llegar a un identificador cuesta once
    // reglas encadenadas, y el AstBuilder las colapsa. Es lo que el 7.3 pone lado a
    // lado en pantalla.
    @Test
    fun `el arbol de ANTLR es mucho mas grande que el fuente`() {
        val vista = CompilerPipeline.compile("print(x);").parseTreeView

        assertNotNull(vista)
        assertTrue(vista.count() > 15, "nodos: ${vista.count()}")
    }

    // ── Cada fallo deja el resultado en una forma distinta ─────────────────

    @Test
    fun `un error sintactico corta el pipeline y no deja arbol`() {
        val result = CompilerPipeline.compile("let x: integer = ;")

        assertNull(result.ast)
        assertNull(result.parseTreeView)
        assertNull(result.globalScope)
        assertNull(result.execution)
        assertTrue(result.syntaxErrors.isNotEmpty())
    }

    // Un error de tipos NO corta: la GUI sigue pudiendo dibujar los dos arboles y la
    // tabla de simbolos. Lo unico que no corre es la ejecucion.
    @Test
    fun `un error de tipos conserva el arbol pero no ejecuta`() {
        val result = CompilerPipeline.compile("let x: integer = \"texto\";")

        assertNotNull(result.ast)
        assertNotNull(result.globalScope)
        assertNull(result.execution)
        assertTrue(result.semanticErrors.isNotEmpty())
    }

    // El test que justifica correr la etapa D aunque la C haya fallado: sin eso, el
    // usuario arreglaria un error, recompilaria, y veria el siguiente.
    @Test
    fun `un error de declaracion no oculta los de tipos`() {
        val result = CompilerPipeline.compile(
            """
            let x: integer = 1;
            let x: integer = 2;
            let a: integer = "texto";
            let b: boolean = 5;
            let c: string = true;
            """.trimIndent()
        )

        assertEquals(4, result.errors.size, "errores: ${result.errors.map { it.message }}")
        assertTrue(result.errors.first().message.contains("ya fue declarado"))
    }

    @Test
    fun `los errores llegan ordenados por linea`() {
        val result = CompilerPipeline.compile(
            "let a: integer = true;\nlet b: integer = true;\nlet c: integer = true;"
        )

        assertEquals(listOf(1, 2, 3), result.errors.map { it.location.line })
    }

    // ── Ejecucion ──────────────────────────────────────────────────────────

    @Test
    fun `execute en false no ejecuta aunque el programa sea valido`() {
        val result = CompilerPipeline.compile("print(1);", execute = false)

        assertNull(result.execution)
        assertTrue(result.errors.isEmpty())
        assertNotNull(result.ast)
    }

    // Un error de ejecucion NO es un CompilerError: viaja en execution, porque no
    // es un problema del codigo fuente sino de esta corrida.
    @Test
    fun `un error de ejecucion viaja en execution y no en la lista de errores`() {
        val result = CompilerPipeline.compile("let xs: integer[] = [1];\nprint(xs[5]);")

        assertTrue(result.errors.isEmpty())
        assertNotNull(result.execution)
        assertNotNull(result.execution.runtimeError)
        assertTrue(result.execution.runtimeError.message.contains("fuera de rango"))
    }

    // ── El pipeline no revienta ────────────────────────────────────────────

    @Test
    fun `un fuente vacio compila y no ejecuta nada`() {
        val result = CompilerPipeline.compile("")

        assertTrue(result.errors.isEmpty())
        assertNotNull(result.execution)
        assertTrue(result.execution.output.isEmpty())
    }

    @Test
    fun `un fuente que no es codigo devuelve errores en vez de lanzar`() {
        val result = CompilerPipeline.compile("### esto no es Compiscript ###")

        assertTrue(result.hasErrors)
        assertNull(result.ast)
    }
}
