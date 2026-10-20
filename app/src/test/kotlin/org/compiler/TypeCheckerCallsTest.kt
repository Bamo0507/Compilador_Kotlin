package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.ListOperation
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.VoidType
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeCheckerCallsTest {

    private fun expression(source: String): Expression {
        val parser = CompiscriptParser(CommonTokenStream(CompiscriptLexer(CharStreams.fromString(source))))
        val tree = parser.expression()
        assertEquals(0, parser.numberOfSyntaxErrors, "el fuente no parsea: $source")
        return AstBuilder().visit(tree) as Expression
    }

    private fun checkerWith(source: String): Triple<TypeChecker, Diagnostics, Scope> {
        val parser = CompiscriptParser(CommonTokenStream(CompiscriptLexer(CharStreams.fromString(source))))
        val program = AstBuilder().visit(parser.program()) as Program
        val diagnostics = Diagnostics()
        val collector = DeclarationCollector(diagnostics)
        collector.collect(program)
        return Triple(TypeChecker(collector.globalScope, diagnostics), diagnostics, collector.globalScope)
    }

    private fun declare(scope: Scope, name: String, type: org.compiler.frontend.semantic.symbols.Type) {
        scope.declare(Symbol(
            name = name,
            kind = DeclarationKind.VARIABLE,
            type = type,
            location = LexemeLocation(1, 1),
            scopeName = scope.name,
            initialized = true
        ))
    }

    @Test
    fun `una llamada valida devuelve el tipo de retorno`() {
        val (checker, diagnostics, _) = checkerWith("function f(a: integer): string { return \"x\"; }")
        val expr = expression("f(1)")

        checker.checkExpression(expr)

        assertEquals(StringType, expr.type)
        assertEquals(0, diagnostics.count)
    }

    @Test
    fun `las llamadas validan cantidad y tipo de argumentos`() {
        val (checker, diagnostics, _) = checkerWith("function f(a: integer): string { return \"x\"; }")
        val wrongCount = expression("f(1, 2)")
        val wrongType = expression("f(\"a\")")

        checker.checkExpression(wrongCount)
        checker.checkExpression(wrongType)

        assertEquals(StringType, wrongCount.type)
        assertEquals(StringType, wrongType.type)
        assertEquals(2, diagnostics.count)
    }

    @Test
    fun `llamar un valor que no es funcion es error`() {
        val (checker, diagnostics, global) = checkerWith("")
        // El nombre se declara directamente porque la Pasada 1 no registra variables.
        declare(global, "x", IntegerType)
        val expr = expression("x()")

        checker.checkExpression(expr)

        assertEquals(ErrorType, expr.type)
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `propiedades propias y heredadas se resuelven`() {
        val (checker, diagnostics, global) = checkerWith(
            "class Animal { let nombre: string; } class Perro : Animal { let edad: integer; }"
        )
        declare(global, "perro", ClassType("Perro"))
        val inherited = expression("perro.nombre")
        val own = expression("perro.edad")

        checker.checkExpression(inherited)
        checker.checkExpression(own)

        assertEquals(StringType, inherited.type)
        assertEquals(IntegerType, own.type)
        assertEquals(0, diagnostics.count)
    }

    @Test
    fun `propiedad inexistente y acceso no objeto son errores`() {
        val (checker, diagnostics, global) = checkerWith("class Perro { }")
        declare(global, "perro", ClassType("Perro"))
        declare(global, "numero", IntegerType)

        val missing = expression("perro.noExiste")
        val notObject = expression("numero.campo")
        checker.checkExpression(missing)
        checker.checkExpression(notObject)

        assertEquals(ErrorType, missing.type)
        assertEquals(ErrorType, notObject.type)
        assertEquals(2, diagnostics.count)
    }

    @Test
    fun `new valida constructores propios heredados e implicitos`() {
        val (checker, diagnostics, _) = checkerWith(
            "class Animal { function constructor(nombre: string) { } } class Perro : Animal { } class Gato { }"
        )
        val inherited = expression("new Perro(\"Toby\")")
        val wrongCount = expression("new Perro()")
        val implicit = expression("new Gato()")

        checker.checkExpression(inherited)
        checker.checkExpression(wrongCount)
        checker.checkExpression(implicit)

        assertEquals(ClassType("Perro"), inherited.type)
        assertEquals(ClassType("Perro"), wrongCount.type)
        assertEquals(ClassType("Gato"), implicit.type)
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `new de clase inexistente es error`() {
        val (checker, diagnostics, _) = checkerWith("")
        val expr = expression("new NoExiste()")

        checker.checkExpression(expr)

        assertEquals(ErrorType, expr.type)
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `this fuera de clase es error`() {
        val (checker, diagnostics, _) = checkerWith("")
        val expr = expression("this")

        checker.checkExpression(expr)

        assertEquals(ErrorType, expr.type)
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `this dentro de una clase tiene el tipo de esa clase`() {
        val (checker, diagnostics, global) = checkerWith("class Perro { }")
        val classScope = global.lookupLocal("Perro")!!.memberScope!!
        val expr = expression("this")

        checker.checkExpression(expr, classScope)

        assertEquals(ClassType("Perro"), expr.type)
        assertEquals(0, diagnostics.count)
    }

    @Test
    fun `indexacion valida y casos invalidos`() {
        val (checker, diagnostics, global) = checkerWith("")
        declare(global, "lista", ArrayType(IntegerType))
        declare(global, "numero", IntegerType)

        val valid = expression("lista[0]")
        val wrongIndex = expression("lista[\"a\"]")
        val negative = expression("lista[-1]")
        val notArray = expression("numero[0]")
        checker.checkExpression(valid)
        checker.checkExpression(wrongIndex)
        checker.checkExpression(negative)
        checker.checkExpression(notArray)

        assertEquals(IntegerType, valid.type)
        assertEquals(IntegerType, wrongIndex.type)
        assertEquals(IntegerType, negative.type)
        assertEquals(ErrorType, notArray.type)
        assertEquals(3, diagnostics.count)
    }

    // ── push y length ─────────────────────────────────────────────────────

    @Test
    fun `push recibe un elemento de la lista y no devuelve nada`() {
        val (checker, diagnostics, global) = checkerWith("")
        declare(global, "notas", ArrayType(IntegerType))
        declare(global, "precios", ArrayType(FloatType))

        val push = expression("notas.push(30)")
        val widened = expression("precios.push(3)")
        checker.checkExpression(push)
        checker.checkExpression(widened)

        assertEquals(VoidType, push.type)
        assertEquals(ListOperation.PUSH, ((push as FunctionCall).callee as PropertyAccess).listOperation)
        assertEquals(VoidType, widened.type)
        assertEquals(0, diagnostics.count)
    }

    @Test
    fun `push con un elemento de otro tipo es error`() {
        val (checker, diagnostics, global) = checkerWith("")
        declare(global, "notas", ArrayType(IntegerType))

        checker.checkExpression(expression("notas.push(\"treinta\")"))
        checker.checkExpression(expression("notas.push(1, 2)"))

        assertEquals(2, diagnostics.count)
    }

    @Test
    fun `length es un entero de solo lectura`() {
        val (checker, diagnostics, global) = checkerWith("")
        declare(global, "notas", ArrayType(IntegerType))

        val length = expression("notas.length")
        checker.checkExpression(length)
        checker.checkExpression(expression("notas.length = 5"))
        checker.checkExpression(expression("notas.length()"))

        assertEquals(IntegerType, length.type)
        assertEquals(ListOperation.LENGTH, (length as PropertyAccess).listOperation)
        assertEquals(2, diagnostics.count)
    }

    @Test
    fun `push sin llamarlo y otros nombres sobre una lista son errores`() {
        val (checker, diagnostics, global) = checkerWith("")
        declare(global, "notas", ArrayType(IntegerType))

        val withoutCall = expression("notas.push")
        val unknown = expression("notas.pop()")
        checker.checkExpression(withoutCall)
        checker.checkExpression(unknown)

        assertEquals(ErrorType, withoutCall.type)
        assertEquals(ErrorType, unknown.type)
        assertEquals(2, diagnostics.count)
    }

    @Test
    fun `un metodo push de una clase sigue siendo un metodo`() {
        val (checker, diagnostics, global) = checkerWith(
            "class Pila { function push(x: integer): integer { return x; } }"
        )
        declare(global, "pila", ClassType("Pila"))

        val call = expression("pila.push(1)")
        checker.checkExpression(call)

        assertEquals(IntegerType, call.type)
        assertEquals(null, ((call as FunctionCall).callee as PropertyAccess).listOperation)
        assertEquals(0, diagnostics.count)
    }
}
