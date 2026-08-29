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
 * Tests del ticket 4.4: el recorrido de sentencias.
 *
 * A diferencia de los otros dos tickets del TypeChecker, estos corren el pipeline
 * completo —parser, AST, Pasada 1, Pasada 2— porque la apertura de ambitos y las
 * declaraciones locales solo se pueden observar sobre un programa entero.
 */
class TypeCheckerStmtTest {

    private class Resultado(val global: Scope, val diagnostics: Diagnostics) {
        val mensajes: List<String> get() = diagnostics.all().map { it.message }
    }

    // Pasa por SyntaxAnalyzer y no por parser.program() directo, igual que el pipeline
    // real: ANTLR se recupera de un error sintactico y entrega un arbol con huecos, y
    // el AstBuilder revienta con esos. El fail explicito hace visible si un caso de
    // prueba no parsea, en vez de que salga como NullPointerException.
    private fun verificar(fuente: String): Resultado {
        val diagnostics = Diagnostics()
        val tree = SyntaxAnalyzer.parse(fuente, diagnostics)
            ?: fail("el fuente no parsea: ${diagnostics.all().map { it.message }}")

        val ast = AstBuilder().visit(tree) as Program

        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        return Resultado(collector.globalScope, diagnostics)
    }

    private fun valido(fuente: String) {
        val r = verificar(fuente)
        assertTrue(r.mensajes.isEmpty(), "no deberia haber errores: ${r.mensajes}")
    }

    private fun conError(fuente: String, fragmento: String) {
        val r = verificar(fuente)
        assertTrue(
            r.mensajes.any { it.contains(fragmento) },
            "se esperaba un error con '$fragmento', se obtuvo: ${r.mensajes}"
        )
    }

    // ── Declaracion de variable ────────────────────────────────────────────

    @Test
    fun `declaraciones validas`() {
        valido("let x: integer = 1;")
        valido("let x: float = 1;")          // ensanchamiento
        valido("let x = 5;")                 // tipo inferido
        valido("const PI: integer = 314;")
    }

    @Test
    fun `declaraciones invalidas`() {
        conError("let x: integer = \"a\";", "No se puede asignar")
        conError("let x: integer = 1.5;", "No se puede asignar")   // sin estrechamiento
        conError("let x;", "necesita un tipo anotado o un valor inicial")
    }

    // ── Asignacion ─────────────────────────────────────────────────────────

    @Test
    fun `no se puede reasignar una constante`() {
        conError("const PI: integer = 314; PI = 3;", "No se puede reasignar")
    }

    // Mutar el contenido no es reasignar la constante.
    @Test
    fun `si se puede mutar el contenido de un arreglo constante`() {
        valido("const lista: integer[] = [1, 2]; lista[0] = 5;")
    }

    @Test
    fun `la asignacion valida el tipo`() {
        conError("let x: integer = 1; x = \"a\";", "No se puede asignar")
    }

    // ── Control de flujo ───────────────────────────────────────────────────

    @Test
    fun `condiciones validas`() {
        valido("if (true) { }")
        valido("let x: integer = 1; while (x < 3) { x = x + 1; }")
        valido("do { } while (false);")
        valido("for (let i: integer = 0; i < 3; i = i + 1) { }")
    }

    @Test
    fun `condiciones no booleanas`() {
        conError("if (1) { }", "debe ser boolean")
        conError("while (\"a\") { }", "debe ser boolean")
        conError("do { } while (1);", "debe ser boolean")
    }

    // La asignacion devuelve el tipo de la variable, no boolean.
    @Test
    fun `if con una asignacion adentro es error`() {
        conError("let x: integer = 1; if (x = 1) { }", "debe ser boolean")
    }

    @Test
    fun `foreach infiere el tipo del elemento`() {
        valido("foreach (n in [1, 2, 3]) { let doble: integer = n * 2; }")
        conError("foreach (n in 5) { }", "solo recorre listas")
    }

    @Test
    fun `switch exige que sujeto y case sean comparables`() {
        valido("let x: integer = 1; switch (x) { case 1: print(x); }")
        conError("let x: integer = 1; switch (x) { case \"a\": }", "no se puede comparar")
    }

    @Test
    fun `el parametro del catch es string`() {
        valido("try { } catch (err) { print(\"Error: \" + err); }")
        conError("try { } catch (err) { let n: integer = err; }", "No se puede asignar")
    }

    // ── Funciones ──────────────────────────────────────────────────────────

    @Test
    fun `retorno compatible con el declarado`() {
        valido("function f(): integer { return 1; }")
        conError("function f(): integer { return \"a\"; }", "debe devolver")
    }

    // Decision 15: sin anotar es void, no se infiere del cuerpo.
    @Test
    fun `una funcion sin tipo de retorno es void`() {
        valido("function f() { print(1); }")
        valido("function f() { return; }")
        conError("function f() { return 1; }", "debe devolver")
    }

    // El cuerpo no abre otro ambito: parametro y local del primer nivel chocan.
    @Test
    fun `un parametro y una local con el mismo nombre chocan`() {
        conError("function f(x: integer) { let x: string = \"a\"; }", "ya fue declarado")
    }

    @Test
    fun `recursion`() {
        valido("function fact(n: integer): integer { if (n <= 1) { return 1; } return n * fact(n - 1); }")
    }

    // ── Clases ─────────────────────────────────────────────────────────────

    @Test
    fun `el inicializador de un campo se verifica`() {
        conError("class A { let x: integer = \"hola\"; }", "al campo")
    }

    @Test
    fun `sobrescribir con otra firma es error`() {
        conError(
            """
            class Animal { function hablar(): string { return "ruido"; } }
            class Perro : Animal { function hablar(): integer { return 5; } }
            """.trimIndent(),
            "sobrescribe el de la superclase"
        )
    }

    @Test
    fun `sobrescribir con la misma firma es valido`() {
        valido(
            """
            class Animal { function hablar(): string { return "ruido"; } }
            class Perro : Animal { function hablar(): string { return "guau"; } }
            """.trimIndent()
        )
    }

    // ── El arbol de ambitos que queda ──────────────────────────────────────

    @Test
    fun `cada construccion abre su ambito con su nombre`() {
        val r = verificar(
            """
            function procesar(): integer {
              for (let i: integer = 0; i < 3; i = i + 1) {
                if (i > 1) { }
              }
              return 0;
            }
            """.trimIndent()
        )

        val procesar = r.global.children.single { it.name == "procesar" }
        assertEquals(ScopeKind.FUNCTION, procesar.kind)

        val bucle = procesar.children.single()
        assertEquals(ScopeKind.LOOP, bucle.kind)
        assertTrue(bucle.name.startsWith("for@"))

        val rama = bucle.children.single()
        assertEquals(ScopeKind.BLOCK, rama.kind)
        assertTrue(rama.name.startsWith("if@"))
    }

    @Test
    fun `los parametros quedan en el ambito de la funcion`() {
        val r = verificar("function suma(a: integer, b: integer): integer { return a + b; }")

        val suma = r.global.children.single { it.name == "suma" }
        val a = suma.lookupLocal("a")
        assertNotNull(a)
        assertEquals(DeclarationKind.PARAMETER, a.kind)
        assertEquals(IntegerType, a.type)
        assertEquals(0, a.offset)
        assertEquals(1, suma.lookupLocal("b")!!.offset)
    }

    // Una clase produce UN solo Scope: checkClassDeclaration lo recupera, no lo abre.
    @Test
    fun `una clase no duplica su ambito`() {
        val r = verificar("class Animal { let nombre: string; }")

        assertEquals(1, r.global.children.count { it.name == "Animal" })
        assertEquals(StringType, r.global.lookupLocal("Animal")!!.memberScope!!.lookupLocal("nombre")!!.type)
    }

    @Test
    fun `el shadowing en un bloque anidado es valido`() {
        valido("let x: integer = 1; { let x: string = \"a\"; }")
    }

    // ── Estructural ────────────────────────────────────────────────────────

    @Test
    fun `una cascada produce un solo error`() {
        val r = verificar("let x: integer = (1 + \"a\") * 2;")
        assertEquals(1, r.diagnostics.count, "errores: ${r.mensajes}")
    }
}
