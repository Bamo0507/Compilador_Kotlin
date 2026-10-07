package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.syntax.SyntaxAnalyzer
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.StringType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * El recorrido de sentencias del TypeChecker.
 *
 * A diferencia de los tests de expresiones y llamadas, estos corren el pipeline
 * completo —parser, AST, Pasada 1, Pasada 2— porque la apertura de ambitos y las
 * declaraciones locales solo se pueden observar sobre un programa entero.
 */
class TypeCheckerStmtTest {

    private class CheckResult(val global: Scope, val diagnostics: Diagnostics) {
        val messages: List<String> get() = diagnostics.all().map { it.message }
    }

    // Pasa por SyntaxAnalyzer y no por parser.program() directo, igual que el pipeline
    // real: ANTLR se recupera de un error sintactico y entrega un arbol con huecos, y
    // el AstBuilder revienta con esos. El fail explicito hace visible si un caso de
    // prueba no parsea, en vez de que salga como NullPointerException.
    private fun checkProgram(source: String): CheckResult {
        val diagnostics = Diagnostics()
        val tree = SyntaxAnalyzer.parse(source, diagnostics)
            ?: fail("el fuente no parsea: ${diagnostics.all().map { it.message }}")

        val ast = AstBuilder().visit(tree) as Program

        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        return CheckResult(collector.globalScope, diagnostics)
    }

    private fun assertValid(source: String) {
        val r = checkProgram(source)
        assertTrue(r.messages.isEmpty(), "no deberia haber errores: ${r.messages}")
    }

    private fun assertError(source: String, fragment: String) {
        val r = checkProgram(source)
        assertTrue(
            r.messages.any { it.contains(fragment) },
            "se esperaba un error con '$fragment', se obtuvo: ${r.messages}"
        )
    }

    // ── Declaracion de variable ────────────────────────────────────────────

    @Test
    fun `declaraciones validas`() {
        assertValid("let x: integer = 1;")
        assertValid("let x: float = 1;")          // ensanchamiento
        assertValid("let x = 5;")                 // tipo inferido
        assertValid("const PI: integer = 314;")
    }

    @Test
    fun `declaraciones invalidas`() {
        assertError("let x: integer = \"a\";", "No se puede asignar")
        assertError("let x: integer = 1.5;", "No se puede asignar")   // sin estrechamiento
        assertError("let x;", "necesita un tipo anotado o un valor inicial")
    }

    // ── Asignacion ─────────────────────────────────────────────────────────

    @Test
    fun `no se puede reasignar una constante`() {
        assertError("const PI: integer = 314; PI = 3;", "No se puede reasignar")
    }

    // Mutar el contenido no es reasignar la constante.
    @Test
    fun `si se puede mutar el contenido de un arreglo constante`() {
        assertValid("const lista: integer[] = [1, 2]; lista[0] = 5;")
    }

    @Test
    fun `la asignacion valida el tipo`() {
        assertError("let x: integer = 1; x = \"a\";", "No se puede asignar")
    }

    // ── Control de flujo ───────────────────────────────────────────────────

    @Test
    fun `condiciones validas`() {
        assertValid("if (true) { }")
        assertValid("let x: integer = 1; while (x < 3) { x = x + 1; }")
        assertValid("do { } while (false);")
        assertValid("for (let i: integer = 0; i < 3; i = i + 1) { }")
    }

    @Test
    fun `condiciones no booleanas`() {
        assertError("if (1) { }", "debe ser boolean")
        assertError("while (\"a\") { }", "debe ser boolean")
        assertError("do { } while (1);", "debe ser boolean")
    }

    // La asignacion devuelve el tipo de la variable, no boolean.
    @Test
    fun `if con una asignacion adentro es error`() {
        assertError("let x: integer = 1; if (x = 1) { }", "debe ser boolean")
    }

    @Test
    fun `foreach infiere el tipo del elemento`() {
        assertValid("foreach (n in [1, 2, 3]) { let doble: integer = n * 2; }")
        assertError("foreach (n in 5) { }", "solo recorre listas")
    }

    @Test
    fun `switch exige que sujeto y case sean comparables`() {
        assertValid("let x: integer = 1; switch (x) { case 1: print(x); }")
        assertError("let x: integer = 1; switch (x) { case \"a\": }", "no se puede comparar")
    }

    @Test
    fun `el parametro del catch es string`() {
        assertValid("try { } catch (err) { print(\"Error: \" + err); }")
        assertError("try { } catch (err) { let n: integer = err; }", "No se puede asignar")
    }

    // ── Funciones ──────────────────────────────────────────────────────────

    @Test
    fun `retorno compatible con el declarado`() {
        assertValid("function f(): integer { return 1; }")
        assertError("function f(): integer { return \"a\"; }", "debe devolver")
    }

    // Sin anotar es void: no se infiere del cuerpo.
    @Test
    fun `una funcion sin tipo de retorno es void`() {
        assertValid("function f() { print(1); }")
        assertValid("function f() { return; }")
        assertError("function f() { return 1; }", "debe devolver")
    }

    // El cuerpo no abre otro ambito: parametro y local del primer nivel chocan.
    @Test
    fun `un parametro y una local con el mismo nombre chocan`() {
        assertError("function f(x: integer) { let x: string = \"a\"; }", "ya fue declarado")
    }

    @Test
    fun `recursion`() {
        assertValid("function fact(n: integer): integer { if (n <= 1) { return 1; } return n * fact(n - 1); }")
    }

    // ── Clases ─────────────────────────────────────────────────────────────

    @Test
    fun `el inicializador de un campo se verifica`() {
        assertError("class A { let x: integer = \"hola\"; }", "al campo")
    }

    @Test
    fun `sobrescribir con otra firma es error`() {
        assertError(
            """
            class Animal { function hablar(): string { return "ruido"; } }
            class Perro : Animal { function hablar(): integer { return 5; } }
            """.trimIndent(),
            "sobrescribe el de la superclase"
        )
    }

    @Test
    fun `sobrescribir con la misma firma es valido`() {
        assertValid(
            """
            class Animal { function hablar(): string { return "ruido"; } }
            class Perro : Animal { function hablar(): string { return "guau"; } }
            """.trimIndent()
        )
    }

    // ── El arbol de ambitos que queda ──────────────────────────────────────

    @Test
    fun `cada construccion abre su ambito con su nombre`() {
        val r = checkProgram(
            """
            function procesar(): integer {
              for (let i: integer = 0; i < 3; i = i + 1) {
                if (i > 1) { }
              }
              return 0;
            }
            """.trimIndent()
        )

        val process = r.global.children.single { it.name == "procesar" }
        assertEquals(ScopeKind.FUNCTION, process.kind)

        val loop = process.children.single()
        assertEquals(ScopeKind.LOOP, loop.kind)
        assertTrue(loop.name.startsWith("for@"))

        val branch = loop.children.single()
        assertEquals(ScopeKind.BLOCK, branch.kind)
        assertTrue(branch.name.startsWith("if@"))
    }

    @Test
    fun `los parametros quedan en el ambito de la funcion`() {
        val r = checkProgram("function suma(a: integer, b: integer): integer { return a + b; }")

        val sum = r.global.children.single { it.name == "suma" }
        val a = sum.lookupLocal("a")
        assertNotNull(a)
        assertEquals(DeclarationKind.PARAMETER, a.kind)
        assertEquals(IntegerType, a.type)
        assertEquals(0, a.offset)
        assertEquals(1, sum.lookupLocal("b")!!.offset)
    }

    // Una clase produce UN solo Scope: checkClassDeclaration lo recupera, no lo abre.
    @Test
    fun `una clase no duplica su ambito`() {
        val r = checkProgram("class Animal { let nombre: string; }")

        assertEquals(1, r.global.children.count { it.name == "Animal" })
        assertEquals(StringType, r.global.lookupLocal("Animal")!!.memberScope!!.lookupLocal("nombre")!!.type)
    }

    @Test
    fun `el shadowing en un bloque anidado es valido`() {
        assertValid("let x: integer = 1; { let x: string = \"a\"; }")
    }

    // ── Estructural ────────────────────────────────────────────────────────

    @Test
    fun `una cascada produce un solo error`() {
        val r = checkProgram("let x: integer = (1 + \"a\") * 2;")
        assertEquals(1, r.diagnostics.count, "errores: ${r.messages}")
    }


    // ── Constructores y miembros de clase ──────────────────────────────────

    // El constructor no participa del subtipado: new nombra la clase exacta.
    @Test
    fun `una subclase puede declarar un constructor con otra firma`() {
        assertValid(
            """
            class Animal { let nombre: string; function constructor(n: string) { this.nombre = n; } }
            class Perro : Animal {
              let raza: string;
              function constructor(n: string, r: string) { this.nombre = n; this.raza = r; }
            }
            let p: Perro = new Perro("Toby", "lab");
            """.trimIndent()
        )
    }

    @Test
    fun `un miembro sin this no es visible y el error sugiere this`() {
        assertError(
            "class Contador { let cuenta: integer = 0; function sumar() { cuenta = cuenta + 1; } }",
            "¿Quisiste decir 'this.cuenta'?"
        )
        assertError(
            "class A { function f(): integer { return 1; } function g(): integer { return f(); } }",
            "¿Quisiste decir 'this.f'?"
        )
        assertError("class A { let x: integer = 1; let y: integer = x; }", "¿Quisiste decir 'this.x'?")
    }

    @Test
    fun `un miembro con this es valido`() {
        assertValid("class Contador { let cuenta: integer = 0; function sumar() { this.cuenta = this.cuenta + 1; } }")
        assertValid("class A { let x: integer = 1; let y: integer = this.x + 1; }")
    }

    // Como en TypeScript: el nombre suelto salta la clase y encuentra la global.
    @Test
    fun `un nombre suelto en un metodo encuentra la global aunque haya un campo igual`() {
        val r = checkProgram(
            "let cuenta: string = \"global\"; class A { let cuenta: integer = 0; function f(): string { return cuenta; } }"
        )
        assertTrue(r.messages.isEmpty(), "no deberia haber errores: ${r.messages}")
    }

    // ── Funciones como valores ─────────────────────────────────────────────

    // Una funcion solo se llama: guardarla para llamarla despues podria dejarla
    // apuntando a un registro de activacion que ya no existe.
    @Test
    fun `una funcion solo se puede llamar`() {
        val sum = "function sumar(a: integer, b: integer): integer { return a + b; }"
        assertValid("$sum let r: integer = sumar(2, 3);")
        assertValid("$sum print(sumar(1, 2) * 10);")
        assertValid(
            "function externa(): integer { function interna(): integer { return 1; } " +
                "return interna(); }"
        )

        assertError("$sum let g = sumar;", "solo se puede llamar")
        assertError(
            "function plantilla(): integer { return 0; } let guardada = plantilla; " +
                "function externa() { function interna(): integer { return 1; } " +
                "guardada = interna; }",
            "solo se puede llamar"
        )
        assertError(
            "class Perro { function hablar(): string { return \"guau\"; } } " +
                "let p: Perro = new Perro(); let h = p.hablar;",
            "solo se puede llamar"
        )
    }

    // ── print ──────────────────────────────────────────────────────────────

    @Test
    fun `print acepta solo tipos simples`() {
        assertValid("print(1); print(2.5); print(\"a\"); print(true);")

        assertError("print([1, 2]);", "print solo acepta")
        assertError("class A { } print(new A());", "print solo acepta")
        assertError("print(null);", "print solo acepta")
        assertError("function f() { } print(f());", "print solo acepta")
    }

    // ── Orden de revision del nivel superior ───────────────────────────────

    @Test
    fun `una funcion puede usar una global declarada mas abajo`() {
        assertValid("function mostrar() { print(contador); } let contador: integer = 5; mostrar();")
        assertValid("class A { function f(): integer { return limite; } } let limite: integer = 3;")
    }
}
