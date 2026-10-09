// La memoria de cada simbolo y la forma de cada registro de activacion.
package org.compiler

import org.compiler.frontend.intermediate.StorageLayout
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ClassLayout
import org.compiler.frontend.intermediate.models.FunctionLabel
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
        assertEquals(
            listOf("begin_func \$main, 16", "x = 1", "x@2 = 5", "print_i x@2", "print_i x",
                "end_func \$main"),
            tac
        )
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

    // ── Las clases (Fase 5) ────────────────────────────────────────────────

    private val animals = """
        class Animal {
          let nombre: string;
          function hablar(): string { return "..."; }
          function comer(): string { return "come"; }
        }
        class Perro : Animal {
          let raza: string;
          function hablar(): string { return "guau"; }
        }
    """.trimIndent()

    private fun classLayout(source: String, className: String): ClassLayout {
        val result = compile(source)
        return assertNotNull(assertNotNull(result.globalScope).child(className).classLayout)
    }

    private fun labels(vararg names: String) = names.map { FunctionLabel(it) }

    // La casilla 0 es la de la tabla; el primer campo va en 4.
    @Test
    fun `Animal mide 8 y su tabla tiene sus dos metodos`() {
        val layout = classLayout(animals, "Animal")

        assertEquals(8, layout.size)
        assertEquals(labels("Animal.hablar", "Animal.comer"), layout.methods)
        assertEquals(
            listOf(ActivationRecordField("tabla de métodos", 0, 4), ActivationRecordField("nombre", 4, 4)),
            layout.fields
        )
    }

    // nombre conserva su desplazamiento, y hablar su posicion en la tabla.
    @Test
    fun `Perro hereda los campos primero y sobrescribe en la misma posicion`() {
        val result = compile(animals)
        val global = assertNotNull(result.globalScope)
        val layout = assertNotNull(global.child("Perro").classLayout)

        assertEquals(12, layout.size)
        assertEquals(labels("Perro.hablar", "Animal.comer"), layout.methods)
        assertEquals(StorageLocation.Field(4), global.child("Animal").symbol("nombre").storage)
        assertEquals(StorageLocation.Field(8), global.child("Perro").symbol("raza").storage)
        assertEquals(listOf("tabla de métodos", "nombre", "raza"), layout.fields.map { it.name })
    }

    // y es float: empieza en 8, y el objeto se redondea a 8.
    @Test
    fun `los campos se alinean como en un registro`() {
        val layout = classLayout("class Punto { let x: integer; let y: float; }", "Punto")

        assertEquals(listOf(4, 8), layout.fields.drop(1).map { it.offset })
        assertEquals(16, layout.size)
    }

    @Test
    fun `una clase vacia solo tiene la casilla de la tabla`() {
        val layout = classLayout("class Vacia { }", "Vacia")

        assertEquals(4, layout.size)
        assertTrue(layout.methods.isEmpty())
    }

    // Un metodo nuevo va al final; el constructor no entra a la tabla.
    @Test
    fun `un metodo nuevo va al final y el constructor no esta en la tabla`() {
        val source = animals + """

            class Gato : Animal {
              function constructor(n: string) { this.nombre = n; }
              function ronronear() { print("rrr"); }
            }
        """.trimIndent()

        assertEquals(
            labels("Animal.hablar", "Animal.comer", "Gato.ronronear"),
            classLayout(source, "Gato").methods
        )
    }

    // this es el primer parametro escondido: desplazamiento 0.
    @Test
    fun `un metodo recibe this en el desplazamiento 0`() {
        val result = compile(animals)
        val hablar = assertNotNull(result.globalScope).child("Perro").child("hablar")
        val record = layoutOf(result).functions.getValue(hablar)

        assertEquals(ActivationRecordField("this", 0, 4), record.fields.first())
        val thisParameter = layoutOf(result).thisParameters.getValue(FunctionLabel("Perro.hablar"))
        assertEquals(StorageLocation.Frame(0), thisParameter.storage)
    }

    @Test
    fun `cada clase tiene el registro de su init`() {
        val record = layoutOf(compile(animals)).initializers.getValue("Perro")

        assertEquals(FunctionLabel("Perro.\$init"), record.function)
        assertEquals("this", record.fields.first().name)
        assertEquals(16, record.size)
    }

    // ── El foreach ─────────────────────────────────────────────────────────

    // $lista y $i van al registro, antes de la variable del bucle.
    @Test
    fun `un foreach agrega sus dos locales ocultas al registro`() {
        val result = compile("let l: integer[] = [1, 2];\nforeach (n in l) { print(n); }")

        assertEquals(
            listOf("\$lista", "\$i", "n"),
            layoutOf(result).main.fields.map { it.name }.filter { it !in linkFields }
        )
    }

    // El segundo foreach de la misma funcion lleva la linea, como dos variables con el
    // mismo nombre (decision 41).
    @Test
    fun `dos foreach en la misma funcion no se confunden en el TAC`() {
        val result = compile(
            "let l: integer[] = [1, 2];\n" +
                "foreach (a in l) { print(a); }\n" +
                "foreach (b in l) { print(b); }"
        )
        val names = layoutOf(result).forEachLocals.values.map { TacPrinter.address(Name(it.index)) }

        assertEquals(listOf("\$i", "\$i@3"), names)
    }

    private val linkFields = setOf("enlace de control", "enlace de acceso", "dirección de retorno")
}
