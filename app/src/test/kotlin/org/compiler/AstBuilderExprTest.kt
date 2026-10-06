// Tests de la mitad de expresiones del AstBuilder.
//
// El punto de entrada es parser.expression(), no parser.program(): aqui se prueban
// expresiones sueltas, sin envolverlas en sentencias. Cualquier regla de ANTLR
// sirve como punto de entrada.
//
// Los arboles esperados se comparan por ESTRUCTURA (casts + campos), no con
// assertEquals sobre el data class completo: la igualdad de los nodos incluye la
// location, y escribir las ubicaciones exactas de cada sub-nodo haria los tests
// ilegibles. La location se verifica aparte, en su propio test.
package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.ArrayLiteral
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.ObjectCreation
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.ThisReference
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.NullType
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AstBuilderExprTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun expr(source: String): Expression {
        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        val parser = CompiscriptParser(CommonTokenStream(lexer))
        val tree = parser.expression()

        assertEquals(0, parser.numberOfSyntaxErrors, "el fuente no parsea: $source")

        return AstBuilder().visit(tree) as Expression
    }

    // Verifica que `e` es una operacion binaria con el operador dado y devuelve
    // sus dos lados, para seguir bajando por el arbol.
    private fun binary(e: Expression, op: BinaryOperator): Pair<Expression, Expression> {
        val b = assertIs<BinaryOperation>(e)
        assertEquals(op, b.operator)
        return b.left to b.right
    }

    private fun assertLiteral(e: Expression, value: Any?, type: Type) {
        val lit = assertIs<Literal>(e)
        assertEquals(value, lit.value)
        assertEquals(type, lit.literalType)
    }

    private fun assertIdentifier(e: Expression, name: String) {
        assertEquals(name, assertIs<Identifier>(e).name)
    }

    // ── Colapso de la torre ────────────────────────────────────────────────

    // La razon de ser de la fase: `x` atraviesa 13 niveles de la gramatica y aun
    // asi debe salir UN nodo, no una cadena.
    @Test
    fun `un identificador solo produce un unico nodo`() {
        assertIdentifier(expr("x"), "x")
    }

    // ── Plegado a la izquierda ─────────────────────────────────────────────

    // EL TEST MAS IMPORTANTE DEL AstBuilder. Si el plegado sale al reves, el arbol es
    // 10-(3-2) y el programa calcula 9 en vez de 5, sin ningun error visible.
    @Test
    fun `la resta pliega a la izquierda`() {
        val (left, right) = binary(expr("10 - 3 - 2"), BinaryOperator.SUBTRACT)

        assertLiteral(right, 2L, IntegerType)
        val (ll, lr) = binary(left, BinaryOperator.SUBTRACT)
        assertLiteral(ll, 10L, IntegerType)
        assertLiteral(lr, 3L, IntegerType)
    }

    @Test
    fun `la division pliega a la izquierda`() {
        val (left, right) = binary(expr("2 / 4 / 8"), BinaryOperator.DIVIDE)

        assertLiteral(right, 8L, IntegerType)
        val (ll, lr) = binary(left, BinaryOperator.DIVIDE)
        assertLiteral(ll, 2L, IntegerType)
        assertLiteral(lr, 4L, IntegerType)
    }

    // Aqui el plegado no protege el valor ((p&&q)&&r da lo mismo que p&&(q&&r)):
    // protege el ORDEN de evaluacion del cortocircuito del Interpreter.
    @Test
    fun `el and pliega a la izquierda`() {
        val (left, right) = binary(expr("p && q && r"), BinaryOperator.AND)

        assertIdentifier(right, "r")
        val (ll, lr) = binary(left, BinaryOperator.AND)
        assertIdentifier(ll, "p")
        assertIdentifier(lr, "q")
    }

    // ── Precedencia: sale de la torre, no del plegado ──────────────────────

    @Test
    fun `multiplicar liga mas fuerte que sumar`() {
        val (left, right) = binary(expr("3 + 5 * 2"), BinaryOperator.ADD)

        assertLiteral(left, 3L, IntegerType)
        val (rl, rr) = binary(right, BinaryOperator.MULTIPLY)
        assertLiteral(rl, 5L, IntegerType)
        assertLiteral(rr, 2L, IntegerType)
    }

    @Test
    fun `and liga mas fuerte que or`() {
        val (left, right) = binary(expr("a || b && c"), BinaryOperator.OR)

        assertIdentifier(left, "a")
        val (rl, rr) = binary(right, BinaryOperator.AND)
        assertIdentifier(rl, "b")
        assertIdentifier(rr, "c")
    }

    // Los parentesis DESAPARECEN: le dieron forma al arbol al parsear y el AST
    // guarda la forma, no la notacion.
    @Test
    fun `los parentesis cambian el arbol pero no dejan nodo`() {
        val (left, right) = binary(expr("(1 + 2) * 3"), BinaryOperator.MULTIPLY)

        assertLiteral(right, 3L, IntegerType)
        val (ll, lr) = binary(left, BinaryOperator.ADD)
        assertLiteral(ll, 1L, IntegerType)
        assertLiteral(lr, 2L, IntegerType)
    }

    // ── Unarios ────────────────────────────────────────────────────────────

    @Test
    fun `menos unario`() {
        val u = assertIs<UnaryOperation>(expr("-x"))

        assertEquals(UnaryOperator.NEGATE, u.operator)
        assertIdentifier(u.operand, "x")
    }

    // La gramatica es recursiva por la derecha en unaryExpr, asi que la doble
    // negacion anida sin trabajo extra.
    @Test
    fun `doble negacion anida`() {
        val outside = assertIs<UnaryOperation>(expr("!!x"))

        assertEquals(UnaryOperator.NOT, outside.operator)
        val inside = assertIs<UnaryOperation>(outside.operand)
        assertEquals(UnaryOperator.NOT, inside.operator)
        assertIdentifier(inside.operand, "x")
    }

    // ── Ternario ───────────────────────────────────────────────────────────

    @Test
    fun `el ternario anida a la derecha`() {
        val t = assertIs<TernaryOperation>(expr("a ? b : c ? d : e"))

        assertIdentifier(t.condition, "a")
        assertIdentifier(t.ifTrue, "b")
        val nested = assertIs<TernaryOperation>(t.ifFalse)
        assertIdentifier(nested.condition, "c")
        assertIdentifier(nested.ifTrue, "d")
        assertIdentifier(nested.ifFalse, "e")
    }

    // ── Literales: el tipo se decide por la forma del texto ────────────────

    @Test
    fun `los literales llevan su tipo`() {
        assertLiteral(expr("3"), 3L, IntegerType)
        assertLiteral(expr("3.14"), 3.14, FloatType)
        assertLiteral(expr("\"hola\""), "hola", StringType)
        assertLiteral(expr("true"), true, BooleanType)
        assertLiteral(expr("false"), false, BooleanType)
        assertLiteral(expr("null"), null, NullType)
    }

    // toLong y no toInt: este numero es sintacticamente valido pero no cabe en Int.
    // El AstBuilder lo guarda; el desborde lo reporta el TypeChecker con linea y columna.
    @Test
    fun `un entero que no cabe en Int se guarda como Long`() {
        assertLiteral(expr("99999999999"), 99_999_999_999L, IntegerType)
    }

    @Test
    fun `arreglo vacio y arreglo con elementos`() {
        assertTrue(assertIs<ArrayLiteral>(expr("[]")).elements.isEmpty())

        val arr = assertIs<ArrayLiteral>(expr("[1, 2, 3]"))
        assertEquals(3, arr.elements.size)
        assertLiteral(arr.elements[0], 1L, IntegerType)
        assertLiteral(arr.elements[2], 3L, IntegerType)
    }

    // ── La cadena de sufijos ───────────────────────────────────────────────

    // La llamada a metodo NO es un nodo aparte: es un FunctionCall cuyo callee es
    // un PropertyAccess. El TypeChecker maneja ese caso explicitamente.
    @Test
    fun `llamada a metodo es FunctionCall sobre PropertyAccess`() {
        val call = assertIs<FunctionCall>(expr("perro.hablar()"))

        assertTrue(call.arguments.isEmpty())
        val access = assertIs<PropertyAccess>(call.callee)
        assertEquals("hablar", access.propertyName)
        assertIdentifier(access.target, "perro")
    }

    @Test
    fun `el indexado encadenado envuelve hacia afuera`() {
        val outside = assertIs<IndexAccess>(expr("lista[0][1]"))

        assertLiteral(outside.index, 1L, IntegerType)
        val inside = assertIs<IndexAccess>(outside.target)
        assertLiteral(inside.index, 0L, IntegerType)
        assertIdentifier(inside.target, "lista")
    }

    @Test
    fun `el acceso a propiedad encadenado envuelve hacia afuera`() {
        val outside = assertIs<PropertyAccess>(expr("perro.dueno.nombre"))

        assertEquals("nombre", outside.propertyName)
        val inside = assertIs<PropertyAccess>(outside.target)
        assertEquals("dueno", inside.propertyName)
        assertIdentifier(inside.target, "perro")
    }

    @Test
    fun `una llamada con argumentos los construye en orden`() {
        val call = assertIs<FunctionCall>(expr("f(1, x)"))

        assertIdentifier(call.callee, "f")
        assertEquals(2, call.arguments.size)
        assertLiteral(call.arguments[0], 1L, IntegerType)
        assertIdentifier(call.arguments[1], "x")
    }

    // ── Asignacion como expresion ──────────────────────────────────────────

    // Recursiva por la derecha en la gramatica: a = (b = c) sale gratis.
    @Test
    fun `la asignacion anida a la derecha`() {
        val outside = assertIs<AssignmentExpression>(expr("a = b = c"))

        assertIdentifier(outside.target, "a")
        val inside = assertIs<AssignmentExpression>(outside.value)
        assertIdentifier(inside.target, "b")
        assertIdentifier(inside.value, "c")
    }

    // OJO: `obj.prop` entra por AssignExpr (leftHandSide absorbe el `.prop` como
    // sufijo), NO por PropertyAssignExpr. El resultado es el mismo porque
    // visitLeftHandSide arma el PropertyAccess. Este test verifica la FORMA del
    // AST, sin importar por cual alternativa entro.
    @Test
    fun `asignar a una propiedad produce PropertyAccess como target`() {
        val asg = assertIs<AssignmentExpression>(expr("obj.prop = 5"))

        assertLiteral(asg.value, 5L, IntegerType)
        val target = assertIs<PropertyAccess>(asg.target)
        assertEquals("prop", target.propertyName)
        assertIdentifier(target.target, "obj")
    }

    // ── Los atomos ─────────────────────────────────────────────────────────

    @Test
    fun `new con y sin argumentos`() {
        val withArguments = assertIs<ObjectCreation>(expr("new Perro(\"Toby\")"))
        assertEquals("Perro", withArguments.className)
        assertLiteral(withArguments.arguments.single(), "Toby", StringType)

        val withoutArguments = assertIs<ObjectCreation>(expr("new Animal()"))
        assertEquals("Animal", withoutArguments.className)
        assertTrue(withoutArguments.arguments.isEmpty())
    }

    @Test
    fun `this produce ThisReference`() {
        assertIs<ThisReference>(expr("this"))
    }

    // ── Ubicaciones ────────────────────────────────────────────────────────

    // Cada nodo lleva la location de su PRIMER token, con columna 1-based. El fold
    // le da a la operacion la location de su operando izquierdo.
    @Test
    fun `cada nodo lleva la ubicacion de su primer token`() {
        val sum = assertIs<BinaryOperation>(expr("x + y"))

        assertEquals(LexemeLocation(line = 1, position = 1), sum.location)
        assertEquals(LexemeLocation(line = 1, position = 1), sum.left.location)
        assertEquals(LexemeLocation(line = 1, position = 5), sum.right.location)
    }

    @Test
    fun `las ubicaciones cruzan lineas`() {
        val call = assertIs<FunctionCall>(expr("f(\n  x\n)"))

        assertEquals(LexemeLocation(line = 2, position = 3), call.arguments.single().location)
    }
}
