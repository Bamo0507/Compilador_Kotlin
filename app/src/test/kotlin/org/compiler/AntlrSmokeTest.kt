package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifica que la generacion de ANTLR esta bien configurada.
 *
 * No prueba el lenguaje: prueba el BUILD. Si algo de la configuracion del ticket 0.4
 * se rompe —la bandera -visitor, el -package, el dependsOn—, falla aqui y no
 * veinte tickets mas adelante.
 */
class AntlrSmokeTest {

    private fun parse(source: String): CompiscriptParser {
        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        return CompiscriptParser(CommonTokenStream(lexer))
    }

    @Test
    fun `un programa minimo parsea sin errores`() {
        val parser = parse("let x: integer = 1;")

        val tree = parser.program()

        assertNotNull(tree)
        assertEquals(0, parser.numberOfSyntaxErrors)
    }

    @Test
    fun `un programa con clases, funciones y control de flujo parsea`() {
        val parser = parse(
            """
            class Animal {
              let nombre: string;
              function constructor(nombre: string) { this.nombre = nombre; }
              function hablar(): string { return this.nombre; }
            }

            function factorial(n: integer): integer {
              if (n <= 1) { return 1; }
              return n * factorial(n - 1);
            }

            let notas: integer[] = [90, 85, 100];
            foreach (nota in notas) {
              if (nota < 60) { continue; }
              print(nota);
            }
            """.trimIndent()
        )

        parser.program()

        assertEquals(0, parser.numberOfSyntaxErrors)
    }

    @Test
    fun `un programa mal formado reporta errores sintacticos`() {
        val parser = parse("let x: integer = ;")

        parser.program()

        assertTrue(parser.numberOfSyntaxErrors > 0)
    }

    // La bandera -visitor es lo unico que hace existir esta clase: por defecto ANTLR
    // genera SOLO el listener. Si alguien la quita del build, este test no compila.
    @Test
    fun `la bandera -visitor genero el BaseVisitor`() {
        val visitor = object : org.compiler.parser.CompiscriptBaseVisitor<String>() {
            override fun visitProgram(ctx: CompiscriptParser.ProgramContext): String =
                "visitado: ${ctx.statement().size} sentencias"
        }

        val tree = parse("let x: integer = 1; print(x);").program()

        assertEquals("visitado: 2 sentencias", visitor.visit(tree))
    }

    // La gramatica tiene 4 reglas con alternativas etiquetadas, asi que ANTLR genera
    // 42 metodos por regla + 10 por etiqueta. Si el numero cambia, cambio la
    // gramatica — y eso obliga a revisar el AstBuilder de la Fase 2.
    @Test
    fun `el BaseVisitor tiene un metodo por regla y por etiqueta`() {
        val metodos = org.compiler.parser.CompiscriptBaseVisitor::class.java
            .declaredMethods
            .count { it.name.startsWith("visit") }

        assertEquals(52, metodos)
    }
}
