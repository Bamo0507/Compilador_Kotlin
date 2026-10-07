// El generador de TAC y la etapa G del pipeline.
//
// Se compara el TEXTO del TAC, con TacPrinter: es lo que se lee en el documento y en
// el IDE, y deja ver los temporales tal como se reciclan.
//
// El invariante del pool —al terminar cada sentencia del nivel superior no queda
// ningun temporal vivo— lo verifica el propio generador con un `check`, asi que se
// cumple en TODOS estos tests: si alguno lo violara, lanzaria.
package org.compiler

import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.runtime.CompilerPipeline
import org.compiler.runtime.models.CompilationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacGeneratorExprTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun compile(source: String): CompilationResult =
        CompilerPipeline.compile(source, execute = false)

    // El TAC de `code`, con las variables ya declaradas en la linea 1. Las lineas del
    // preludio (`a = 1`, `b = 2`, ...) se descartan: no son lo que se prueba.
    private fun tac(code: String, variables: String = "a b c d x y"): List<String> {
        val names = variables.split(" ").filter { it.isNotBlank() }
        val prelude = names.joinToString(" ") { "let $it: integer = 1;" }

        val result = compile("$prelude\n$code")
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")

        val program = assertNotNull(result.tac, "El TAC salio null para:\n$code")
        // Sin el begin_func y el end_func del main: lo que se prueba es su cuerpo.
        return TacPrinter.print(program.instructions).lines()
            .filterNot { it.startsWith("begin_func \$main") || it.startsWith("end_func \$main") }
            .drop(names.size)
            .map { it.trim() }
    }

    private fun temporaryCount(code: String): Int {
        val prelude = "let a: integer = 1; let b: integer = 2; " +
            "let c: integer = 3; let d: integer = 4;"
        return assertNotNull(compile("$prelude\n$code").tac).temporaryCount
    }

    // ── Expresiones ────────────────────────────────────────────────────────

    // El TypeChecker ya plego 3 + 5: no se emite ninguna suma.
    @Test
    fun `una expresion constante se imprime ya calculada`() {
        assertEquals(listOf("print_i 8"), tac("print(3 + 5);", variables = ""))
    }

    // En un arbol el pool se comporta como el contador clasico: un solo temporal.
    @Test
    fun `una cadena de operaciones usa un solo temporal`() {
        assertEquals(
            listOf("t1 = b * c", "t1 = a + t1", "t1 = t1 - d", "x = t1"),
            tac("let x: integer = a + b * c - d;", variables = "a b c d")
        )
        assertEquals(1, temporaryCount("let x: integer = a + b * c - d;"))
    }

    // EL OBJETIVO DE LA FASE: la diapositiva 19 usa cinco temporales para esta
    // expresion. Con el GDA, b - c se calcula una vez; con el pool, los nombres se
    // reciclan. Quedan dos.
    @Test
    fun `el ejemplo de la diapositiva 19 usa dos temporales`() {
        assertEquals(
            listOf(
                "t1 = b - c",
                "t2 = a * t1",
                "t2 = a + t2",
                "t1 = t1 * d",
                "t1 = t2 + t1",
                "r = t1"
            ),
            tac("let r: integer = a + a * (b - c) + (b - c) * d;", variables = "a b c d")
        )
        assertEquals(2, temporaryCount("let r: integer = a + a * (b - c) + (b - c) * d;"))
    }

    @Test
    fun `un entero en una suma de flotantes se convierte primero`() {
        assertEquals(
            listOf("t1 = inttofloat x", "t1 = t1 +f 2.5", "f = t1"),
            tac("let f: float = x + 2.5;", variables = "x")
        )
    }

    @Test
    fun `una suma de strings es una concatenacion`() {
        assertEquals(
            listOf("nombre = \"b\"", "t1 = \"a\" concat nombre", "s = t1"),
            tac("let nombre: string = \"b\";\nlet s: string = \"a\" + nombre;", variables = "")
        )
    }

    // Una comparacion produce un booleano como VALOR, no un salto.
    @Test
    fun `una comparacion guarda su resultado`() {
        assertEquals(
            listOf("t1 = x < y", "b = t1"),
            tac("let b: boolean = x < y;", variables = "x y")
        )
    }

    @Test
    fun `menos unario y negacion`() {
        assertEquals(
            listOf("t1 = - a", "n = t1", "t1 = a < b", "t1 = ! t1", "p = t1"),
            tac("let n: integer = -a; let p: boolean = !(a < b);", variables = "a b")
        )
    }

    // ── El chequeo de division entre cero ──────────────────────────────────

    // El divisor es una variable: el TypeChecker no pudo decidir, asi que el TAC lleva
    // el chequeo en linea, con la linea del fuente en el mensaje.
    @Test
    fun `dividir entre una variable emite su chequeo`() {
        assertEquals(
            listOf(
                "if b != 0 goto L1",
                "throw \"División entre cero (línea 2)\"",
                "L1:",
                "t1 = a / b",
                "q = t1"
            ),
            tac("let q: integer = a / b;", variables = "a b")
        )
    }

    // Un divisor constante distinto de cero es seguro: el cero constante ya lo rechazo
    // el TypeChecker.
    @Test
    fun `dividir entre una constante no emite chequeo`() {
        assertEquals(
            listOf("t1 = a / 2", "q = t1"),
            tac("let q: integer = a / 2;", variables = "a")
        )
    }

    // El modulo tambien divide, y el divisor puede ser un temporal.
    @Test
    fun `el modulo entre una expresion tambien se chequea`() {
        assertEquals(
            listOf(
                "t1 = b - c",
                "if t1 != 0 goto L1",
                "throw \"División entre cero (línea 2)\"",
                "L1:",
                "t1 = a % t1",
                "m = t1"
            ),
            tac("let m: integer = a % (b - c);", variables = "a b c")
        )
    }

    // En flotantes, dividir entre cero da Infinity: no es un error. Misma regla que el
    // interprete.
    @Test
    fun `la division de flotantes no se chequea`() {
        assertEquals(
            listOf("f = 1.5", "t1 = 2.0 /f f", "g = t1"),
            tac("let f: float = 1.5; let g: float = 2.0 / f;", variables = "")
        )
    }

    // ── Sentencias ─────────────────────────────────────────────────────────

    // Una variable sin inicializar arranca en el cero de su tipo, igual que en el
    // interprete.
    @Test
    fun `una declaracion sin valor copia el cero de su tipo`() {
        assertEquals(
            listOf("n = 0", "g = 0.0", "s = \"\"", "b = false"),
            tac("let n: integer; let g: float; let s: string; let b: boolean;", variables = "")
        )
    }

    @Test
    fun `asignar y reasignar copian al destino`() {
        assertEquals(listOf("t1 = a + 1", "a = t1"), tac("a = a + 1;", variables = "a"))
    }

    @Test
    fun `print usa la instruccion de su tipo`() {
        assertEquals(
            listOf("print_i a", "print_s \"hola\"", "print_b true", "print_f 2.5"),
            tac("print(a); print(\"hola\"); print(true); print(2.5);", variables = "a")
        )
    }

    // Un bloque no genera nada propio: solo sus sentencias.
    @Test
    fun `un bloque emite sus sentencias en orden`() {
        assertEquals(
            listOf("t1 = a * b", "z = t1", "print_i z"),
            tac("{ let z: integer = a * b; print(z); }", variables = "a b")
        )
    }

    // ── Asignaciones anidadas ──────────────────────────────────────────────

    // El valor de `b = 5` es lo que quedo en b.
    @Test
    fun `una asignacion anidada emite la copia y devuelve el destino`() {
        assertEquals(listOf("b = 5", "a = b"), tac("a = (b = 5);", variables = "a b"))
    }

    // La x de la izquierda vale lo que valia ANTES de la asignacion: se evalua
    // primero. Como una hoja no genera instruccion, se copia antes a un temporal; sin
    // eso, la suma leeria la x nueva y daria 10 en vez de 6.
    @Test
    fun `un operando que la derecha reasigna se copia antes`() {
        assertEquals(
            listOf("t1 = x", "x = 5", "t1 = t1 + x", "r = t1"),
            tac("let r: integer = x + (x = 5);", variables = "x")
        )
    }

    // ── La etapa G del pipeline ────────────────────────────────────────────

    @Test
    fun `un programa valido trae su TAC en el resultado`() {
        assertNotNull(compile("let a: integer = 1;\nprint(a);").tac)
    }

    // Lo que el generador aun no traduce es un TODO. El pipeline lo atrapa y deja el
    // TAC en null: el IDE no se cae con un programa que todavia no se puede traducir.
    @Test
    fun `un programa con algo que aun no se traduce deja el TAC en null sin lanzar`() {
        val result = compile("class A { }\nlet a: A = new A();")

        assertTrue(result.errors.isEmpty())
        assertNull(result.tac)
    }

    @Test
    fun `un programa con errores semanticos no genera TAC`() {
        val result = compile("let q: integer = \"hola\";")

        assertTrue(result.hasErrors)
        assertNull(result.tac)
    }
}
