package org.compiler

import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.ast.toTreeView
import org.compiler.gui.components.layoutTree
import org.compiler.runtime.CompilerPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests del ticket 7.3.
 *
 * Cubren las dos partes que no son dibujo: la conversion del AST a la vista neutral,
 * y el calculo de posiciones. Lo que el canvas pinta con eso no se prueba aqui.
 */
class TreeViewTest {

    private fun astView(source: String): TreeNodeView {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "errores: ${result.errors.map { it.message }}")
        return result.ast?.toTreeView() ?: fail("sin AST")
    }

    private fun parseView(source: String): TreeNodeView =
        CompilerPipeline.compile(source, execute = false).parseTreeView ?: fail("sin árbol")

    private fun TreeNodeView.count(): Int = 1 + children.sumOf { it.count() }

    private fun TreeNodeView.find(prefix: String): TreeNodeView? =
        if (label.startsWith(prefix)) this
        else children.firstNotNullOfOrNull { it.find(prefix) }

    // ── El AST lleva la decoracion de la Fase 4 ────────────────────────────

    // Es el ejemplo del ticket: la pantalla como evidencia visible del plegado.
    @Test
    fun `una suma constante muestra su tipo y su valor`() {
        val suma = astView("print(3 + 5);").find("BinaryOperation +")

        assertNotNull(suma)
        assertEquals("integer = 8", suma.detail)
    }

    @Test
    fun `una expresion no constante muestra solo el tipo`() {
        val suma = astView("let x: integer = 1;\nprint(x + 1);").find("BinaryOperation +")

        assertNotNull(suma)
        assertEquals("integer", suma.detail)
    }

    @Test
    fun `una cadena constante se muestra entre comillas`() {
        val literal = astView("print(\"hola\");").find("Literal")

        assertNotNull(literal)
        assertEquals("string = \"hola\"", literal.detail)
    }

    @Test
    fun `una declaracion muestra el tipo que se escribio`() {
        val declaracion = astView("let notas: integer[] = [1, 2];").find("let notas")

        assertNotNull(declaracion)
        assertEquals("integer[]", declaracion.detail)
    }

    // Sin tipo de retorno la funcion es void (decision 15), y eso se ve.
    @Test
    fun `una funcion sin tipo de retorno se marca void`() {
        val funcion = astView("function saludar() { print(1); }").find("function saludar")

        assertNotNull(funcion)
        assertEquals("void", funcion.detail)
    }

    @Test
    fun `una clase muestra su superclase`() {
        val vista = astView("class Animal { }\nclass Perro : Animal { }")

        assertNotNull(vista.find("class Perro : Animal"))
    }

    // ── El punto didactico de la pantalla ──────────────────────────────────

    // La torre de precedencia de la gramatica contra el nodo unico del AST.
    @Test
    fun `el AST de un identificador es un solo nodo y el de ANTLR es una torre`() {
        val fuente = "let x: integer = 1;\nprint(x);"

        val identificador = astView(fuente).find("Identifier x")
        assertNotNull(identificador)
        assertTrue(identificador.children.isEmpty())

        assertTrue(
            parseView(fuente).count() > astView(fuente).count() * 2,
            "el árbol de ANTLR debería ser mucho más grande"
        )
    }

    @Test
    fun `la precedencia ya esta en la forma del AST`() {
        // 3 + 5 * 2 se pliega entero, asi que se usan variables para conservar la forma.
        val raiz = astView(
            "let a: integer = 1;\nlet b: integer = 2;\nlet c: integer = 3;\nprint(a + b * c);"
        ).find("BinaryOperation +")

        assertNotNull(raiz)

        // El * cuelga del +, no al reves: la multiplicacion amarra mas fuerte.
        assertEquals("Identifier a", raiz.children[0].label)
        assertEquals("BinaryOperation *", raiz.children[1].label)
    }

    // ── El layout ──────────────────────────────────────────────────────────

    private fun hoja(label: String) = TreeNodeView(label, null, emptyList())

    @Test
    fun `una sola hoja ocupa una columna`() {
        val layout = layoutTree(hoja("raiz"))

        assertEquals(1, layout.nodes.size)
        assertEquals(1f, layout.columns)
        assertEquals(1, layout.levels)
        assertEquals(0f, layout.nodes.single().column)
    }

    @Test
    fun `las hojas ocupan columnas consecutivas`() {
        val layout = layoutTree(
            TreeNodeView("raiz", null, listOf(hoja("a"), hoja("b"), hoja("c")))
        )

        val columnas = layout.nodes.filter { it.depth == 1 }.map { it.column }
        assertEquals(listOf(0f, 1f, 2f), columnas.sorted())
        assertEquals(3f, layout.columns)
    }

    // La regla del algoritmo: el padre se centra sobre el primero y el ultimo hijo.
    @Test
    fun `un padre queda centrado sobre sus hijos`() {
        val layout = layoutTree(
            TreeNodeView("raiz", null, listOf(hoja("a"), hoja("b"), hoja("c")))
        )

        assertEquals(1f, layout.nodes.single { it.depth == 0 }.column)
    }

    @Test
    fun `cada nodo salvo la raiz tiene exactamente una arista que lo apunta`() {
        val layout = layoutTree(astView("print(3 + 5 * 2);"))

        assertEquals(layout.nodes.size - 1, layout.edges.size)
        assertEquals(layout.edges.map { it.second }.toSet().size, layout.edges.size)
    }

    // Un arbol de ANTLR de verdad: hondo, con muchos nodos de un solo hijo.
    @Test
    fun `el arbol de ANTLR se posiciona completo`() {
        val vista = parseView("print(3 + 5);")
        val layout = layoutTree(vista)

        assertEquals(vista.count(), layout.nodes.size)
        assertTrue(layout.levels > 8, "niveles: ${layout.levels}")
    }
}
