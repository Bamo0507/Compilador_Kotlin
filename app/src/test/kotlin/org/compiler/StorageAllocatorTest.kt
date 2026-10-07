// La memoria de cada simbolo y la forma de cada registro de activacion.
package org.compiler

import org.compiler.frontend.intermediate.StorageLayout
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.StorageLocation
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.runtime.CompilerPipeline
import org.compiler.runtime.models.CompilationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageAllocatorTest {

    private fun compile(source: String): CompilationResult {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        return result
    }

    private fun layoutOf(result: CompilationResult): StorageLayout = assertNotNull(result.storageLayout)

    private fun Scope.child(name: String): Scope = children.single { it.name == name }

    private fun Scope.symbol(name: String): Symbol = assertNotNull(lookupLocal(name))

    // ── Zonas y alineacion ─────────────────────────────────────────────────

    // b es float: empieza en un multiplo de 8, no en 4.
    @Test
    fun `las globales viven en datos estaticos y se alinean`() {
        val result = compile("let a: integer = 1; let b: float = 2.5;")
        val global = assertNotNull(result.globalScope)

        assertEquals(StorageLocation.Static(0), global.symbol("a").storage)
        assertEquals(StorageLocation.Static(8), global.symbol("b").storage)
        assertEquals(16, layoutOf(result).staticSize)
    }

    // El orden de la diapositiva 21, con el relleno de la alineacion.
    @Test
    fun `el registro de una funcion sigue el orden de la teoria`() {
        val result = compile(
            "function suma(a: integer, b: float): float { let r: float = a + b; return r; }"
        )
        val record = layoutOf(result).functions.values.single()

        assertEquals(
            listOf(
                ActivationRecordField("a", 0, 4),
                ActivationRecordField("b", 8, 8),
                ActivationRecordField("valor devuelto", 16, 8),
                ActivationRecordField("enlace de control", 24, 4),
                ActivationRecordField("enlace de acceso", 28, 4),
                ActivationRecordField("dirección de retorno", 32, 4),
                ActivationRecordField("r", 40, 8)
            ),
            record.fields
        )
        assertEquals(48, record.size)
    }

    // Un if abre un ambito pero no un registro.
    @Test
    fun `una variable de un if vive en el registro de su funcion`() {
        val result = compile("function f(c: boolean) { if (c) { let x: integer = 1; } }")
        val function = assertNotNull(result.globalScope).child("f")

        val x = function.children.single().symbol("x")
        assertTrue(x.storage is StorageLocation.Frame)
        assertTrue(layoutOf(result).functions.getValue(function).fields.any { it.name == "x" })
    }

    // Muere al cerrar el bloque, asi que no es global: va al registro del main.
    @Test
    fun `una variable de un bloque del nivel superior vive en el registro del main`() {
        val result = compile("{ let x: integer = 5; print(x); }")
        val block = assertNotNull(result.globalScope).children.single()

        assertEquals(StorageLocation.Frame(12), block.symbol("x").storage)
        assertEquals("\$main", layoutOf(result).main.function.name)
    }

    @Test
    fun `todo registro mide un multiplo de 8`() {
        val layout = layoutOf(
            compile("function f(b: boolean): boolean { return b; } function g() { }")
        )

        (layout.functions.values + layout.main).forEach { assertEquals(0, it.size % 8) }
    }

    @Test
    fun `una funcion anidada lleva el camino de las que la contienen`() {
        val layout = layoutOf(
            compile("function externa() { function sumar(): integer { return 1; } print(sumar()); }")
        )

        val names = layout.functions.values.map { it.function.name }
        assertEquals(listOf("externa", "externa.sumar"), names)
    }

    // ── Los nombres en el TAC ──────────────────────────────────────────────

    // La global conserva el nombre; la local lleva la linea de su declaracion.
    @Test
    fun `una local que se llama igual que una global lleva sufijo`() {
        val result = compile("let x: integer = 1;\n{ let x: integer = 5; print(x); }\nprint(x);")
        val global = assertNotNull(result.globalScope)

        assertNull(global.symbol("x").tacName)
        assertEquals("x@2", global.children.single().symbol("x").tacName)

        val tac = TacPrinter.print(assertNotNull(result.tac).instructions).lines().map { it.trim() }
        assertEquals(listOf("x = 1", "x@2 = 5", "print_i x@2", "print_i x"), tac)
    }

    // Viven en registros distintos: no se pueden confundir.
    @Test
    fun `dos parametros con el mismo nombre en funciones distintas no llevan sufijo`() {
        val result = compile(
            "function factorial(n: integer): integer { return n; }\n" +
                "function fibonacci(n: integer): integer { return n; }"
        )
        val global = assertNotNull(result.globalScope)

        assertNull(global.child("factorial").symbol("n").tacName)
        assertNull(global.child("fibonacci").symbol("n").tacName)
    }
}
