package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypeCheckerExprTest {

    private fun expression(source: String): Expression {
        val parser = CompiscriptParser(CommonTokenStream(CompiscriptLexer(CharStreams.fromString(source))))
        val tree = parser.expression()
        assertEquals(0, parser.numberOfSyntaxErrors, "el fuente no parsea: $source")
        return AstBuilder().visit(tree) as Expression
    }

    private fun check(source: String, configure: (Scope) -> Unit = {}): Pair<Expression, Diagnostics> {
        val scope = Scope(ScopeKind.GLOBAL, "global", null)
        configure(scope)
        val diagnostics = Diagnostics()
        val expr = expression(source)
        TypeChecker(scope, diagnostics).checkExpression(expr)
        return expr to diagnostics
    }

    private fun declare(scope: Scope, name: String, type: org.compiler.frontend.semantic.symbols.Type, initialized: Boolean = true) {
        scope.declare(Symbol(
            name = name,
            kind = DeclarationKind.VARIABLE,
            type = type,
            location = LexemeLocation(1, 1),
            scopeName = "global",
            offset = 0,
            initialized = initialized
        ))
    }

    @Test
    fun `aritmetica y plegado numerico decoran el AST`() {
        val (expr, diagnostics) = check("1 + 2.5")

        assertEquals(0, diagnostics.count)
        assertEquals(FloatType, expr.type)
        assertEquals(3.5, expr.constantValue)
    }

    @Test
    fun `plegado respeta asociatividad izquierda`() {
        val (expr, diagnostics) = check("10 - 3 - 2")

        assertEquals(0, diagnostics.count)
        assertEquals(IntegerType, expr.type)
        assertEquals(5L, expr.constantValue)
    }

    @Test
    fun `concatenacion solo acepta dos strings`() {
        val (valid, validDiagnostics) = check("\"a\" + \"b\"")
        val (invalid, invalidDiagnostics) = check("\"a\" + 1")

        assertEquals(StringType, valid.type)
        assertEquals("ab", valid.constantValue)
        assertEquals(0, validDiagnostics.count)
        assertEquals(ErrorType, invalid.type)
        assertEquals(1, invalidDiagnostics.count)
    }

    @Test
    fun `operadores logicos relacionales e igualdad se pliegan`() {
        val (logical, logicalDiagnostics) = check("true && false")
        val (relational, relationalDiagnostics) = check("\"a\" < \"b\"")
        val (equality, equalityDiagnostics) = check("1 == 1.0")

        assertEquals(BooleanType, logical.type)
        assertEquals(false, logical.constantValue)
        assertEquals(BooleanType, relational.type)
        assertEquals(true, relational.constantValue)
        assertEquals(BooleanType, equality.type)
        assertEquals(true, equality.constantValue)
        assertEquals(0, logicalDiagnostics.count + relationalDiagnostics.count + equalityDiagnostics.count)
    }

    @Test
    fun `operadores con tipos incompatibles producen un solo error`() {
        val (expr, diagnostics) = check("(1 + \"a\") * 2")

        assertEquals(ErrorType, expr.type)
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `unarios validan y pliegan`() {
        val (negated, negatedDiagnostics) = check("-2.5")
        val (not, notDiagnostics) = check("!true")
        val (invalid, invalidDiagnostics) = check("!5")

        assertEquals(FloatType, negated.type)
        assertEquals(-2.5, negated.constantValue)
        assertEquals(BooleanType, not.type)
        assertEquals(false, not.constantValue)
        assertEquals(ErrorType, invalid.type)
        assertEquals(0, negatedDiagnostics.count + notDiagnostics.count)
        assertEquals(1, invalidDiagnostics.count)
    }

    @Test
    fun `dividir o modular entre cero constante es error`() {
        val (division, divisionDiagnostics) = check("10 / 0")
        val (modulo, moduloDiagnostics) = check("10 % 0")

        assertEquals(ErrorType, division.type)
        assertEquals(ErrorType, modulo.type)
        assertEquals(1, divisionDiagnostics.count)
        assertEquals(1, moduloDiagnostics.count)
    }

    @Test
    fun `identificador resuelve simbolo y conserva variables no constantes`() {
        val (expr, diagnostics) = check("x") { declare(it, "x", IntegerType) }
        val identifier = assertIs<org.compiler.frontend.ast.models.Identifier>(expr)

        assertEquals(IntegerType, expr.type)
        assertEquals(null, expr.constantValue)
        assertEquals("x", identifier.resolvedSymbol?.name)
        assertEquals(1, identifier.resolvedSymbol?.useCount)
        assertEquals(0, diagnostics.count)
    }

    @Test
    fun `identificador no declarado y no inicializado reportan errores`() {
        val (missing, missingDiagnostics) = check("x")
        val (uninitialized, uninitializedDiagnostics) = check("x") {
            declare(it, "x", IntegerType, initialized = false)
        }

        assertEquals(ErrorType, missing.type)
        assertEquals(1, missingDiagnostics.count)
        assertEquals(IntegerType, uninitialized.type)
        assertEquals(1, uninitializedDiagnostics.count)
    }

    @Test
    fun `ternario unifica sus ramas y solo pliega con condicion constante`() {
        val (constant, constantDiagnostics) = check("true ? 1 : 2.5")
        val (invalid, invalidDiagnostics) = check("1 ? 1 : 2")

        assertEquals(FloatType, constant.type)
        assertEquals(1L, constant.constantValue)
        assertEquals(0, constantDiagnostics.count)
        assertEquals(IntegerType, invalid.type)
        assertEquals(1, invalidDiagnostics.count)
    }

    @Test
    fun `arreglos unifican elementos y arreglos vacios usan null`() {
        val (numbers, numberDiagnostics) = check("[1, 2.5]")
        val (empty, emptyDiagnostics) = check("[]")
        val (invalid, invalidDiagnostics) = check("[1, \"a\"]")

        assertEquals(ArrayType(FloatType), numbers.type)
        assertEquals(ArrayType(org.compiler.frontend.semantic.symbols.NullType), empty.type)
        assertEquals(ArrayType(ErrorType), invalid.type)
        assertEquals(0, numberDiagnostics.count + emptyDiagnostics.count)
        assertEquals(1, invalidDiagnostics.count)
    }

    @Test
    fun `literal entero fuera de rango se rechaza`() {
        val (expr, diagnostics) = check("99999999999")

        assertEquals(ErrorType, expr.type)
        assertEquals(1, diagnostics.count)
    }
}
