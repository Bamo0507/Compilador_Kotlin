package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeclarationCollectorTest {

    private class CheckResult(val global: Scope, val diagnostics: Diagnostics) {
        val messages: List<String> get() = diagnostics.all().map { it.message }
    }

    private fun collectDeclarations(source: String): CheckResult {
        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        val parser = CompiscriptParser(CommonTokenStream(lexer))
        val ast = AstBuilder().visit(parser.program()) as Program

        val diagnostics = Diagnostics()
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)

        return CheckResult(collector.globalScope, diagnostics)
    }

    //=============================================
    //  Casos validos
    //=============================================

    @Test
    fun `registra la firma de una funcion sin entrar al cuerpo`() {
        val r = collectDeclarations(
            """
            function suma(a: integer, b: integer): integer {
              let interna: integer = 0;
              return a + b;
            }
            """.trimIndent()
        )

        val sum = r.global.lookupLocal("suma")
        assertNotNull(sum)
        assertEquals(DeclarationKind.FUNCTION, sum.kind)
        assertEquals(FunctionType(listOf(IntegerType, IntegerType), IntegerType), sum.type)

        // `interna` vive en el cuerpo, y la Pasada 1 no entra: la declara la Pasada 2.
        assertNull(r.global.lookupLocal("interna"))
        assertTrue(r.messages.isEmpty())
    }

    @Test
    fun `referencia adelantada entre funciones`() {
        val r = collectDeclarations(
            """
            function a(): integer { return b(); }
            function b(): integer { return 1; }
            """.trimIndent()
        )

        assertNotNull(r.global.lookupLocal("a"))
        assertNotNull(r.global.lookupLocal("b"))
        assertTrue(r.messages.isEmpty())
    }

    // El test que justifica las dos rondas: cada clase usa a la otra como tipo de campo.
    @Test
    fun `clases mutuamente referenciadas`() {
        val r = collectDeclarations(
            """
            class A { let b: B; }
            class B { let a: A; }
            """.trimIndent()
        )

        assertTrue(r.messages.isEmpty(), "no deberia haber errores: ${r.messages}")
        assertEquals(ClassType("B"), r.global.lookupLocal("A")!!.memberScope!!.lookupLocal("b")!!.type)
        assertEquals(ClassType("A"), r.global.lookupLocal("B")!!.memberScope!!.lookupLocal("a")!!.type)
    }

    @Test
    fun `los miembros de una clase quedan en su ambito, con isMember`() {
        val r = collectDeclarations(
            """
            class Animal {
              let nombre: string;
              function hablar(): string { return this.nombre; }
            }
            """.trimIndent()
        )

        val animal = r.global.lookupLocal("Animal")!!.memberScope!!

        val name = animal.lookupLocal("nombre")!!
        assertEquals(StringType, name.type)
        assertTrue(name.isMember)

        val speak = animal.lookupLocal("hablar")!!
        assertEquals(FunctionType(emptyList(), StringType), speak.type)
        assertTrue(speak.isMember)
        assertEquals(listOf("nombre", "hablar"), animal.localSymbols().map { it.name })
    }

    // La herencia ENLAZA, no copia: `nombre` sigue viviendo en el ambito de Animal.
    @Test
    fun `la superclase se enlaza y el campo heredado se encuentra`() {
        val r = collectDeclarations(
            """
            class Animal { let nombre: string; }
            class Perro : Animal { function hablar(): string { return this.nombre; } }
            """.trimIndent()
        )

        val animal = r.global.lookupLocal("Animal")!!.memberScope!!
        val dog = r.global.lookupLocal("Perro")!!.memberScope!!

        assertSame(animal, dog.superclass)
        assertNull(dog.lookupLocal("nombre"))          // no se copio
        assertNotNull(dog.lookupMember("nombre"))      // se encuentra por la cadena
        assertTrue(r.messages.isEmpty())
    }

    @Test
    fun `las clases quedan enumerables desde globalScope`() {
        val r = collectDeclarations("class A { } class B { } function f() { }")

        assertEquals(listOf("A", "B"), r.global.children.map { it.name })
    }

    //=============================================
    //  Errores
    //=============================================

    @Test
    fun `dos funciones con el mismo nombre`() {
        val r = collectDeclarations("function f(): integer { return 1; } function f(): integer { return 2; }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("ya fue declarado"))
    }

    @Test
    fun `dos clases con el mismo nombre no dejan un ambito huerfano`() {
        val r = collectDeclarations("class Perro { let nombre: string; } class Perro { let raza: string; }")

        assertEquals(1, r.diagnostics.count)

        // Un solo ambito Perro, y sin el campo de la segunda declaracion.
        assertEquals(1, r.global.children.count { it.name == "Perro" })
        val dog = r.global.lookupLocal("Perro")!!.memberScope!!
        assertNotNull(dog.lookupLocal("nombre"))
        assertNull(dog.lookupLocal("raza"))
    }

    @Test
    fun `dos campos con el mismo nombre en la misma clase`() {
        val r = collectDeclarations("class A { let x: integer; let x: string; }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("ya fue declarado"))
    }

    @Test
    fun `heredar de algo que no es una clase declarada`() {
        val r = collectDeclarations("class Perro : NoExiste { }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("no es una clase declarada"))
    }

    // Sin la deteccion, lookupMember entraria en recursion infinita y el compilador
    // colgaria en vez de dar un error.
    @Test
    fun `herencia circular se reporta y el compilador no cuelga`() {
        val r = collectDeclarations("class A : B { } class B : A { }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("Herencia circular"))

        // La cadena quedo acotada: una de las dos no tiene superclase.
        val a = r.global.lookupLocal("A")!!.memberScope!!
        val b = r.global.lookupLocal("B")!!.memberScope!!
        assertTrue(a.superclass == null || b.superclass == null)
    }

    @Test
    fun `herencia de si misma`() {
        val r = collectDeclarations("class A : A { }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("Herencia circular"))
    }

    @Test
    fun `un campo de tipo inexistente`() {
        val r = collectDeclarations("class A { let x: NoExiste; }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("no está declarado"))
    }

    @Test
    fun `un campo sin tipo anotado`() {
        val r = collectDeclarations("class A { let x = 5; }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("necesita un tipo anotado"))
    }

    @Test
    fun `el constructor no puede declarar tipo de retorno`() {
        val r = collectDeclarations("class A { function constructor(): integer { return 1; } }")

        assertEquals(1, r.diagnostics.count)
        assertTrue(r.messages.first().contains("no puede declarar tipo de retorno"))
    }

    //=============================================
    //  Lo que la Pasada 1 deliberadamente NO hace
    //=============================================

    // Si las registrara, `print(x); let x = 5;` pasaria: las variables NO deben ser
    // referenciables hacia adelante.
    @Test
    fun `no registra variables del nivel superior`() {
        val r = collectDeclarations("let x: integer = 5; const PI: integer = 314;")

        assertNull(r.global.lookupLocal("x"))
        assertNull(r.global.lookupLocal("PI"))
        assertTrue(r.messages.isEmpty())
    }

    @Test
    fun `no entra a los bloques`() {
        val r = collectDeclarations("{ function interna(): integer { return 1; } }")

        assertNull(r.global.lookupLocal("interna"))
        assertTrue(r.messages.isEmpty())
    }
}
