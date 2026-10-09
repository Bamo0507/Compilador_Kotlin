// Ticket 5.3: new, $init, this, campos y metodos con despacho por tabla. Tambien el
// chequeo de null del ticket 5.5 sobre objetos.
//
// Se compara el texto del TAC de UNA funcion: la que nombra cada test.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TacGeneratorObjectTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun program(source: String): TacProgram {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        return assertNotNull(result.tac, "El TAC salio null para:\n$source")
    }

    private fun lines(source: String): List<String> =
        TacPrinter.print(program(source).instructions).lines().map { it.trim() }

    // El cuerpo de una funcion, sin begin_func ni end_func.
    private fun body(source: String, function: String): List<String> {
        val all = lines(source)
        val start = all.indexOfFirst { it.startsWith("begin_func $function,") }
        assertTrue(start >= 0, "No hay funcion $function en:\n${all.joinToString("\n")}")
        val end = all.indexOf("end_func $function")
        return all.subList(start + 1, end)
    }

    private val animals = """
        class Animal {
          let nombre: string;
          function constructor(nombre: string) { this.nombre = nombre; }
          function hablar(): string { return "..."; }
          function comer(): string { return this.nombre + " come"; }
        }
        class Perro : Animal {
          let raza: string;
          function hablar(): string { return "guau"; }
        }
    """.trimIndent()

    private val nullCheckA = listOf("if a != null goto L1", "throw \"Acceso a null (línea 12)\"", "L1:")

    // ── Las tablas de metodos ──────────────────────────────────────────────

    // Datos estaticos al inicio del TAC, antes del main (decision 46).
    @Test
    fun `las tablas de metodos van antes del main`() {
        assertEquals(
            listOf(
                "vtable Animal: Animal.hablar, Animal.comer",
                "vtable Perro: Perro.hablar, Animal.comer",
                "begin_func \$main, 16"
            ),
            lines(animals).take(3)
        )
    }

    // ── new ────────────────────────────────────────────────────────────────

    // Los cuatro pasos: pedir el bloque, apuntar a la tabla, $init y el constructor.
    // Perro no declara constructor: usa el de Animal, igual que el TypeChecker.
    @Test
    fun `new con el constructor heredado`() {
        assertEquals(
            listOf(
                "t1 = alloc 12",
                "t1[0] = vtable.Perro",
                "param t1",
                "call Perro.\$init, 1",
                "param t1",
                "param \"Toby\"",
                "call Animal.constructor, 2",
                "p = t1"
            ),
            body("$animals\nlet p: Perro = new Perro(\"Toby\");", "\$main")
        )
    }

    @Test
    fun `new con constructor propio llama al de su clase`() {
        val source = """
            class Punto {
              let x: integer;
              function constructor(x: integer) { this.x = x; }
            }
            let p: Punto = new Punto(3);
        """.trimIndent()

        assertTrue("call Punto.constructor, 2" in body(source, "\$main"))
    }

    // Sin constructor en toda la cadena: solo $init.
    @Test
    fun `new sin constructor solo inicializa los campos`() {
        assertEquals(
            listOf("t1 = alloc 8", "t1[0] = vtable.A", "param t1", "call A.\$init, 1", "a = t1"),
            body("class A { let x: integer; }\nlet a: A = new A();", "\$main")
        )
    }

    // ── $init (decision 44) ────────────────────────────────────────────────

    // Primero el $init del padre, despues los campos propios con su valor por defecto.
    @Test
    fun `el init de una subclase llama al del padre y despues inicializa sus campos`() {
        assertEquals(
            listOf("param this", "call Animal.\$init, 1", "this[8] = \"\""),
            body(animals, "Perro.\$init")
        )
    }

    // y se inicializa leyendo x, que ya se asigno: los campos van en orden.
    @Test
    fun `un inicializador puede leer un campo anterior con this`() {
        val source = "class A { let x: integer = 1; let y: integer = this.x + 1; }"

        assertEquals(
            listOf("this[4] = 1", "t1 = this[4]", "t1 = t1 + 1", "this[8] = t1"),
            body(source, "A.\$init")
        )
    }

    // ── Campos ─────────────────────────────────────────────────────────────

    // El chequeo de null va antes de leer (decision 45).
    @Test
    fun `leer un campo chequea null antes`() {
        val source = "$animals\nlet a: Animal = new Animal(\"x\");\nprint(a.nombre);"

        assertEquals(
            nullCheckA + listOf("t1 = a[4]", "print_s t1"),
            body(source, "\$main").drop(8)
        )
    }

    @Test
    fun `escribir un campo chequea null y escribe en su desplazamiento`() {
        val source = "$animals\nlet p: Perro = new Perro(\"x\");\np.raza = \"lab\";"

        assertEquals(
            listOf("if p != null goto L1", "throw \"Acceso a null (línea 12)\"", "L1:", "p[8] = \"lab\""),
            body(source, "\$main").drop(8)
        )
    }

    // this nunca es null: sin chequeo.
    @Test
    fun `this no lleva chequeo de null`() {
        assertEquals(listOf("this[4] = nombre"), body(animals, "Animal.constructor"))
        assertEquals(
            listOf("t1 = this[4]", "t1 = t1 concat \" come\"", "return t1"),
            body(animals, "Animal.comer")
        )
    }

    // Un entero escrito en un campo float se convierte, como en una variable.
    @Test
    fun `un entero escrito en un campo float se convierte`() {
        val source = "class A { let f: float; }\nlet a: A = new A();\na.f = 3;"

        assertTrue("a[8] = 3.0" in body(source, "\$main"))
    }

    // ── Metodos y despacho ─────────────────────────────────────────────────

    // La posicion sale de la tabla de Animal, la clase declarada; en ejecucion a[0]
    // apunta a la tabla de Perro, y se llama a Perro.hablar.
    @Test
    fun `una llamada a metodo busca la funcion en la tabla del objeto`() {
        val source = "$animals\nlet a: Animal = new Perro(\"x\");\nprint(a.hablar());"

        assertEquals(
            nullCheckA + listOf("t1 = a[0]", "t1 = t1[0]", "param a", "t1 = call t1, 1", "print_s t1"),
            body(source, "\$main").drop(8)
        )
    }

    // comer esta en la posicion 1: desplazamiento 4 dentro de la tabla.
    @Test
    fun `la posicion del metodo da el desplazamiento dentro de la tabla`() {
        val source = "$animals\nlet a: Animal = new Perro(\"x\");\na.comer();"

        assertTrue("t1 = t1[4]" in body(source, "\$main"))
        assertTrue("call t1, 1" in body(source, "\$main"))
    }

    // this.hablar() tambien despacha: si una subclase lo sobrescribio, se llama al suyo.
    @Test
    fun `this punto metodo tambien despacha por la tabla y sin chequeo`() {
        val source = """
            class A {
              function nombre(): string { return "A"; }
              function saludo(n: integer): string { return this.nombre(); }
            }
        """.trimIndent()

        assertEquals(
            listOf("t1 = this[0]", "t1 = t1[0]", "param this", "t1 = call t1, 1", "return t1"),
            body(source, "A.saludo")
        )
    }

    // this va primero, despues los argumentos.
    @Test
    fun `los argumentos van despues de this`() {
        val source = """
            class Calc { function suma(a: integer, b: float): float { return a + b; } }
            let c: Calc = new Calc();
            let r: float = c.suma(1, 2);
        """.trimIndent()

        val main = body(source, "\$main")
        val call = main.indexOf("t1 = call t1, 3")
        assertEquals(listOf("param c", "param 1", "param 2.0"), main.subList(call - 3, call))
    }

    // Una funcion anidada en un metodo lee el this de su padre con un salto.
    @Test
    fun `una funcion anidada en un metodo usa this con un salto`() {
        val source = """
            class A {
              let x: integer = 1;
              function f(n: integer): integer {
                function g(): integer { return this.x + n; }
                return g();
              }
            }
        """.trimIndent()

        assertEquals(listOf("t1 = this^1[4]", "t1 = t1 + n^1", "return t1"), body(source, "A.f.g"))
    }

    // ── Asignacion anidada a un campo ──────────────────────────────────────

    // El valor de `(a.x = 5)` es lo que se escribio.
    @Test
    fun `una asignacion anidada a un campo devuelve el valor escrito`() {
        val source = "class A { let x: integer; }\nlet a: A = new A();\nlet y: integer = (a.x = 5);"

        assertEquals(
            listOf("if a != null goto L1", "throw \"Acceso a null (línea 3)\"", "L1:", "a[4] = 5", "y = 5"),
            body(source, "\$main").drop(5)
        )
    }

    // ── La bateria ─────────────────────────────────────────────────────────

    // El objetivo de la fase: el programa completo de la demo genera TAC.
    @Test
    fun `demo_completa genera TAC`() {
        val demo = assertNotNull(SamplePrograms.byId("validos/demo_completa"))
        program(demo.source)
    }
}
