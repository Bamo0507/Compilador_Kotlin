package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.interpreter.ExecutionResult
import org.compiler.interpreter.Interpreter
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterpreterTest {

    // ── Infraestructura: el pipeline completo ──────────────────────────────

    private fun run(source: String): ExecutionResult {
        val parser = CompiscriptParser(
            CommonTokenStream(CompiscriptLexer(CharStreams.fromString(source)))
        )
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "el fuente no parsea:\n$source")

        val ast = AstBuilder().visit(tree) as Program

        val diagnostics = Diagnostics()
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, diagnostics).check(ast)
        assertEquals(
            0, diagnostics.count,
            "errores semanticos en:\n$source\n${diagnostics.all().joinToString("\n")}"
        )

        return Interpreter().run(ast)
    }

    // Ejecuta y exige que termine SIN error de ejecucion.
    private fun output(source: String): List<String> {
        val result = run(source)
        assertNull(result.runtimeError, "error de ejecucion inesperado: ${result.runtimeError?.message}")
        return result.output
    }

    // ── Expresiones y variables ────────────────────────────────────────────

    @Test
    fun `la aritmetica constante sale del plegado de la fase 4`() {
        assertEquals(listOf("8"), output("print(3 + 5);"))
        assertEquals(listOf("10"), output("print(2 * (4 + 1));"))
    }

    // Verifica el plegado a la izquierda de la Fase 2: (10-3)-2 = 5, no 10-(3-2) = 9.
    @Test
    fun `la resta asocia a la izquierda tambien al ejecutar`() {
        assertEquals(listOf("5"), output("print(10 - 3 - 2);"))
    }

    @Test
    fun `concatenacion, flotantes y logicos`() {
        assertEquals(listOf("Hola mundo"), output("print(\"Hola \" + \"mundo\");"))
        assertEquals(listOf("4.5"), output("print(3.5 + 1);"))
        assertEquals(listOf("false"), output("print(true && false);"))
    }

    @Test
    fun `una variable cambia de valor`() {
        assertEquals(listOf("2"), output("let x = 1; x = x + 1; print(x);"))
    }

    // Verifica assign vs define: la asignacion dentro del bloque modifica la x de
    // afuera, no crea otra.
    @Test
    fun `asignar dentro de un bloque modifica la variable de afuera`() {
        assertEquals(listOf("2"), output("let x = 1; { x = 2; } print(x);"))
    }

    // Y el shadowing: `let` adentro crea OTRA variable que muere con el bloque.
    @Test
    fun `declarar dentro de un bloque tapa sin modificar`() {
        assertEquals(listOf("1"), output("let x = 1; { let x = 2; } print(x);"))
    }

    @Test
    fun `los numericos se comparan como double`() {
        assertEquals(listOf("true"), output("print(1 == 1.0);"))
    }

    // ── Control de flujo ───────────────────────────────────────────────────

    @Test
    fun `while ejecuta mientras la condicion se cumple`() {
        assertEquals(
            listOf("0", "1", "2"),
            output("let i = 0; while (i < 3) { print(i); i = i + 1; }")
        )
    }

    @Test
    fun `foreach recorre el arreglo en orden`() {
        assertEquals(listOf("1", "2", "3"), output("foreach (n in [1, 2, 3]) { print(n); }"))
    }

    @Test
    fun `continue salta la vuelta actual`() {
        assertEquals(
            listOf("1", "3"),
            output("foreach (n in [1, 2, 3]) { if (n == 2) { continue; } print(n); }")
        )
    }

    @Test
    fun `break corta el for`() {
        assertEquals(
            listOf("0", "1"),
            output("for (let i = 0; i < 5; i = i + 1) { if (i == 2) { break; } print(i); }")
        )
    }

    // El continue SI ejecuta la actualizacion del for: si no, el bucle donde el
    // continue salta el incremento se quedaria infinito.
    @Test
    fun `continue en un for ejecuta la actualizacion`() {
        assertEquals(
            listOf("2"),
            output(
                """
                let i = 0;
                for (let j = 0; j < 3; j = j + 1) {
                  if (j == 1) { continue; }
                  i = i + 1;
                }
                print(i);
                """.trimIndent()
            )
        )
    }

    // Sin fall-through: el case que coincide ejecuta SU cuerpo y el switch termina.
    @Test
    fun `switch sin fall-through`() {
        assertEquals(
            listOf("b"),
            output("switch (2) { case 1: print(\"a\"); case 2: print(\"b\"); }")
        )
    }

    @Test
    fun `switch sin coincidencia ni default no hace nada`() {
        assertEquals(emptyList(), output("switch (9) { case 1: print(\"a\"); }"))
    }

    // ── Funciones, recursion y closures ────────────────────────────────────

    @Test
    fun `una funcion recibe argumentos y devuelve`() {
        assertEquals(
            listOf("5"),
            output(
                """
                function suma(a: integer, b: integer): integer { return a + b; }
                print(suma(2, 3));
                """.trimIndent()
            )
        )
    }

    // El factorial de Especificaciones.md: recursion de verdad.
    @Test
    fun `factorial de 5 es 120`() {
        assertEquals(
            listOf("120"),
            output(
                """
                function factorial(n: integer): integer {
                  if (n <= 1) { return 1; }
                  return n * factorial(n - 1);
                }
                print(factorial(5));
                """.trimIndent()
            )
        )
    }

    // TEST DE CLOSURE: la funcion anidada ve la local de la de afuera porque su
    @Test
    fun `una funcion anidada ve las locales de la de afuera`() {
        assertEquals(
            listOf("11"),
            output(
                """
                function afuera(): integer {
                  let n: integer = 10;
                  function adentro(): integer { return n + 1; }
                  return adentro();
                }
                print(afuera());
                """.trimIndent()
            )
        )
    }

    // La recursion infinita se vuelve RuntimeError, no StackOverflowError: la app
    // no muere y el error es atrapable.
    @Test
    fun `la recursion infinita produce un error atrapable`() {
        val result = run(
            """
            function f(): integer { return f(); }
            print(f());
            """.trimIndent()
        )

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("Recursión demasiado profunda"))
    }

    // El finally de invoke deja callDepth correcto: despues de atrapar una recursion
    // profunda, una llamada normal debe funcionar.
    @Test
    fun `atrapar una recursion profunda no rompe las llamadas siguientes`() {
        assertEquals(
            listOf("atrapado", "1"),
            output(
                """
                function f(): integer { return f(); }
                function g(): integer { return 1; }
                try { print(f()); } catch (err) { print("atrapado"); }
                print(g());
                """.trimIndent()
            )
        )
    }

    // ── Clases ─────────────────────────────────────────────────────────────

    // El Animal/Perro de Especificaciones.md, compartido por varios tests.
    private val jerarquia = """
        class Animal {
          let nombre: string;
          function constructor(nombre: string) { this.nombre = nombre; }
          function hablar(): string { return this.nombre + " hace ruido."; }
        }
        class Perro : Animal {
          function hablar(): string { return this.nombre + " ladra."; }
        }
    """.trimIndent()

    @Test
    fun `un objeto guarda sus campos y los usa en sus metodos`() {
        assertEquals(
            listOf("Rex hace ruido."),
            output("$jerarquia\nlet a: Animal = new Animal(\"Rex\");\nprint(a.hablar());")
        )
    }

    @Test
    fun `una subclase sobrescribe el metodo del padre`() {
        assertEquals(
            listOf("Toby ladra."),
            output("$jerarquia\nlet p: Perro = new Perro(\"Toby\");\nprint(p.hablar());")
        )
    }

    // EL TEST DEL DESPACHO
    @Test
    fun `el despacho usa la clase real del objeto, no el tipo declarado`() {
        assertEquals(
            listOf("Toby ladra."),
            output("$jerarquia\nlet a: Animal = new Perro(\"Toby\");\nprint(a.hablar());")
        )
    }

    @Test
    fun `los campos sin inicializar arrancan en el cero de su tipo`() {
        assertEquals(
            listOf("0"),
            output("class A { let n: integer; }\nprint(new A().n);")
        )
        assertEquals(
            listOf(""),
            output("class A { let s: string; }\nprint(new A().s);")
        )
    }

    // Las clases SI arrancan en null: son referencias, y null es legitimo ahi.
    @Test
    fun `los campos de tipo clase arrancan en null`() {
        assertEquals(
            listOf("null"),
            output("class A { let otro: A; }\nprint(new A().otro);")
        )
    }

    // Test de que ObjectValue y ArrayValue NO son data class: identidad, no valor.
    @Test
    fun `dos objetos nuevos nunca son iguales`() {
        assertEquals(
            listOf("false"),
            output("$jerarquia\nprint(new Perro(\"Toby\") == new Perro(\"Toby\"));")
        )
        assertEquals(listOf("false"), output("print([1, 2] == [1, 2]);"))
    }

    // ── Errores en ejecucion: la propiedad realizable ──────────────────────

    @Test
    fun `un indice fuera de rango produce RuntimeError con su linea`() {
        val result = run("let lista: integer[] = [1, 2, 3];\nprint(lista[10]);")

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("fuera de rango"))
        assertEquals(2, result.runtimeError.location.line)
    }

    // El divisor es una VARIABLE: la Fase 4 no pudo decidir, se decide aqui.
    // (1 / 0 literal ni llega: lo rechaza el TypeChecker en compilacion.)
    @Test
    fun `la division entre cero dinamica produce RuntimeError`() {
        val result = run("let d: integer = 0;\nprint(1 / d);")

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("División entre cero"))
    }

    // La salida producida ANTES del error se conserva: el usuario ve hasta donde
    // llego su programa.
    @Test
    fun `la salida previa al error se conserva`() {
        val result = run("print(1);\nlet lista: integer[] = [];\nprint(lista[0]);")

        assertEquals(listOf("1"), result.output)
        assertNotNull(result.runtimeError)
    }

    // El try/catch de Especificaciones.md: el error se atrapa, el programa sigue.
    @Test
    fun `try catch atrapa el error de ejecucion y no aborta`() {
        val salida = output(
            """
            let lista: integer[] = [1, 2, 3];
            try {
              let peligro: integer = lista[100];
            } catch (err) {
              print("Error atrapado: " + err);
            }
            print("sigo vivo");
            """.trimIndent()
        )

        assertEquals(2, salida.size)
        assertTrue(salida[0].startsWith("Error atrapado: "))
        assertTrue(salida[0].contains("fuera de rango"))
        assertEquals("sigo vivo", salida[1])
    }

    // Un return dentro de un try NO lo captura el catch: ControlFlowSignal no es un
    // error del usuario. Un catch descuidado de RuntimeException se lo tragaria.
    @Test
    fun `return dentro de un try sale de la funcion`() {
        assertEquals(
            listOf("42"),
            output(
                """
                function f(): integer {
                  try { return 42; } catch (err) { return 0; }
                  return 1;
                }
                print(f());
                """.trimIndent()
            )
        )
    }

    // ── Desreferenciar null ────────────────────────────────────────────────
    //
    // `let a: integer[] = null;` es legal para el TypeChecker, asi que usar ese nulo
    // solo se puede detectar ejecutando. Sin los chequeos dinamicos esto lanzaba una
    // ClassCastException que se escapaba del interprete.

    @Test
    fun `indexar un nulo produce RuntimeError y no una excepcion de Kotlin`() {
        val result = run("let a: integer[] = null;\nprint(a[0]);")

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("Se esperaba una lista"))
        assertEquals(2, result.runtimeError.location.line)
    }

    @Test
    fun `asignar a un campo de un nulo produce RuntimeError`() {
        val result = run(
            """
            class P { let n: string; }
            let p: P = null;
            p.n = "x";
            """.trimIndent()
        )

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("Se esperaba un objeto"))
    }

    @Test
    fun `recorrer un nulo con foreach produce RuntimeError`() {
        val result = run("let a: integer[] = null;\nforeach (x in a) { print(x); }")

        assertNotNull(result.runtimeError)
        assertTrue(result.runtimeError.message.contains("Se esperaba una lista"))
    }

    // Lo que hacia falta: el error tiene que ser del lenguaje para que el try/catch
    // del propio programa lo pueda atrapar.
    @Test
    fun `el try catch del programa atrapa el desreferenciado de nulo`() {
        assertEquals(
            listOf("atrapado"),
            output(
                """
                let a: integer[] = null;
                try { print(a[0]); } catch (err) { print("atrapado"); }
                """.trimIndent()
            )
        )
    }
}
