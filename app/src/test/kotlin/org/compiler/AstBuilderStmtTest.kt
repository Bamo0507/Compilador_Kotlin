// Tests del Ticket 2.3: la mitad de sentencias y declaraciones del AstBuilder.
//
// El punto de entrada es parser.program(), el mismo que usa el compilador real.
// Casi todos los tests parsean UNA sentencia y la sacan con stmt().
//
// Igual que en AstBuilderExprTest, se compara estructura (casts + campos) y no el
// data class completo, porque la igualdad de los nodos incluye la location.
package org.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.Block
import org.compiler.frontend.ast.models.Break
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.Continue
import org.compiler.frontend.ast.models.DoWhile
import org.compiler.frontend.ast.models.ExpressionStatement
import org.compiler.frontend.ast.models.ForEach
import org.compiler.frontend.ast.models.For
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.If
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.Print
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.Return
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While
import org.compiler.gui.state.AppState
import org.compiler.models.LexemeLocation
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AstBuilderStmtTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun program(source: String): Program {
        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        val parser = CompiscriptParser(CommonTokenStream(lexer))
        val tree = parser.program()

        assertEquals(0, parser.numberOfSyntaxErrors, "el fuente no parsea: $source")

        return AstBuilder().visit(tree) as Program
    }

    // Para las sentencias sueltas: un programa de UNA sentencia.
    private fun stmt(source: String): Statement = program(source).statements.single()

    // ── Los dos caminos de la asignacion ───────────────────────────────────
    //
    // `x = 5;` entra por la regla `assignment`; `lista[0] = 5;` NO coincide con
    // ella (exige Identifier pelado a la izquierda) y entra por
    // expressionStatement. El AstBuilder debe normalizar AMBOS al mismo nodo
    // Assignment, o una de las dos formas se cae en silencio.

    @Test
    fun `asignacion simple produce Assignment con Identifier`() {
        val asg = assertIs<Assignment>(stmt("x = 5;"))

        assertEquals("x", assertIs<Identifier>(asg.target).name)
        assertEquals(5L, assertIs<Literal>(asg.value).value)
    }

    @Test
    fun `asignacion indexada tambien produce Assignment, via expressionStatement`() {
        val asg = assertIs<Assignment>(stmt("lista[0] = 5;"))

        val target = assertIs<IndexAccess>(asg.target)
        assertEquals("lista", assertIs<Identifier>(target.target).name)
        assertEquals(5L, assertIs<Literal>(asg.value).value)
    }

    @Test
    fun `asignacion a propiedad produce Assignment con PropertyAccess`() {
        val asg = assertIs<Assignment>(stmt("perro.nombre = \"Toby\";"))

        val target = assertIs<PropertyAccess>(asg.target)
        assertEquals("nombre", target.propertyName)
        assertEquals("perro", assertIs<Identifier>(target.target).name)
        assertEquals("Toby", assertIs<Literal>(asg.value).value)
    }

    // La normalizacion es SOLO a nivel de sentencia: anidada, la asignacion sigue
    // siendo AssignmentExpression. La Fase 4 valida las dos por separado.
    @Test
    fun `una asignacion anidada sigue siendo AssignmentExpression`() {
        val decl = assertIs<VariableDeclaration>(stmt("let y = (x = 5);"))

        assertIs<AssignmentExpression>(decl.initializer)
    }

    @Test
    fun `una expresion que no asigna produce ExpressionStatement`() {
        val es = assertIs<ExpressionStatement>(stmt("f();"))

        assertIs<FunctionCall>(es.expr)
    }

    // ── Declaraciones ──────────────────────────────────────────────────────

    @Test
    fun `let sin tipo ni inicializador deja ambos en null`() {
        val decl = assertIs<VariableDeclaration>(stmt("let x;"))

        assertEquals("x", decl.name)
        assertNull(decl.declaredType)
        assertNull(decl.initializer)
        assertFalse(decl.isConstant)
    }

    @Test
    fun `let completo puebla tipo e inicializador`() {
        val decl = assertIs<VariableDeclaration>(stmt("let x: integer = 1;"))

        assertEquals("integer", decl.declaredType?.baseName)
        assertEquals(0, decl.declaredType?.arrayDimensions)
        assertEquals(1L, assertIs<Literal>(decl.initializer!!).value)
    }

    @Test
    fun `const produce el mismo nodo con isConstant`() {
        val decl = assertIs<VariableDeclaration>(stmt("const PI: integer = 314;"))

        assertTrue(decl.isConstant)
        assertEquals("PI", decl.name)
        assertEquals(314L, assertIs<Literal>(decl.initializer!!).value)
    }

    // TypeReference guarda lo ESCRITO, sin resolver: las dimensiones son la
    // cantidad de pares de corchetes.
    @Test
    fun `el tipo arreglo cuenta sus dimensiones`() {
        val matriz = assertIs<VariableDeclaration>(stmt("let m: integer[][];"))
        assertEquals("integer", matriz.declaredType?.baseName)
        assertEquals(2, matriz.declaredType?.arrayDimensions)
        assertEquals("integer[][]", matriz.declaredType?.name)

        // Una clase aun no declarada es valida aqui: la resuelve la Fase 3.
        val perro = assertIs<VariableDeclaration>(stmt("let p: Perro;"))
        assertEquals("Perro", perro.declaredType?.baseName)
        assertEquals(0, perro.declaredType?.arrayDimensions)
    }

    @Test
    fun `una funcion puebla parametros, retorno y cuerpo`() {
        val fn = assertIs<FunctionDeclaration>(
            stmt("function f(a: integer, b): string { return \"x\"; }")
        )

        assertEquals("f", fn.name)
        assertEquals(2, fn.parameters.size)
        assertEquals("a", fn.parameters[0].name)
        assertEquals("integer", fn.parameters[0].declaredType?.baseName)
        assertEquals("b", fn.parameters[1].name)
        assertNull(fn.parameters[1].declaredType)      // sin anotar: lo infiere la Fase 4
        assertEquals("string", fn.returnType?.baseName)
        assertEquals(1, fn.body.statements.size)
    }

    @Test
    fun `una funcion sin parametros ni retorno deja null y lista vacia`() {
        val fn = assertIs<FunctionDeclaration>(stmt("function g() { }"))

        assertTrue(fn.parameters.isEmpty())
        assertNull(fn.returnType)
        assertTrue(fn.body.statements.isEmpty())
    }

    @Test
    fun `una clase con superclase la registra por nombre`() {
        val cls = assertIs<ClassDeclaration>(stmt("class Perro : Animal { }"))

        assertEquals("Perro", cls.name)
        assertEquals("Animal", cls.superclassName)
        assertTrue(cls.members.isEmpty())
    }

    @Test
    fun `una clase sin superclase deja null`() {
        val cls = assertIs<ClassDeclaration>(stmt("class Animal { }"))

        assertNull(cls.superclassName)
    }

    @Test
    fun `los miembros de una clase son declaraciones`() {
        val cls = assertIs<ClassDeclaration>(
            stmt(
                """
                class Animal {
                  let nombre: string;
                  function hablar(): string { return this.nombre; }
                }
                """.trimIndent()
            )
        )

        assertEquals(2, cls.members.size)
        assertIs<VariableDeclaration>(cls.members[0])
        assertIs<FunctionDeclaration>(cls.members[1])
    }

    // ── El for y sus tres campos opcionales ────────────────────────────────

    @Test
    fun `un for completo puebla los tres campos`() {
        val f = assertIs<For>(stmt("for (let i: integer = 0; i < 3; i = i + 1) { }"))

        assertIs<VariableDeclaration>(f.initializer)
        assertNotNull(f.condition)
        assertNotNull(f.update)
    }

    // EL TEST QUE ATRAPA EL ERROR DE POSICION DE TOKENS. Aqui viene UNA sola
    // expresion y es la actualizacion. Si el builder asumiera "expression(0) es la
    // condicion", el compilador creeria que `i = i + 1` es la condicion del ciclo.
    @Test
    fun `un for sin condicion no confunde la actualizacion con la condicion`() {
        val f = assertIs<For>(stmt("for (;; i = i + 1) { }"))

        assertNull(f.initializer)
        assertNull(f.condition)
        assertIs<AssignmentExpression>(f.update)
    }

    @Test
    fun `un for con solo inicializador deja condicion y actualizacion en null`() {
        val f = assertIs<For>(stmt("for (let i: integer = 0;;) { }"))

        assertIs<VariableDeclaration>(f.initializer)
        assertNull(f.condition)
        assertNull(f.update)
    }

    // El ';' del inicializador vacio NO debe confundirse con el separador.
    @Test
    fun `un for sin inicializador puebla condicion y actualizacion`() {
        val f = assertIs<For>(stmt("for (; i < 3; i = i + 1) { }"))

        assertNull(f.initializer)
        assertNotNull(f.condition)
        assertNotNull(f.update)
    }

    @Test
    fun `un for con assignment como inicializador tambien funciona`() {
        val f = assertIs<For>(stmt("for (i = 0; i < 3;) { }"))

        assertIs<Assignment>(f.initializer)
        assertNotNull(f.condition)
        assertNull(f.update)
    }

    // ── Control de flujo ───────────────────────────────────────────────────

    @Test
    fun `if con y sin else`() {
        val conElse = assertIs<If>(stmt("if (x) { } else { print(1); }"))
        assertEquals(1, conElse.elseBranch?.statements?.size)

        val sinElse = assertIs<If>(stmt("if (x) { }"))
        assertNull(sinElse.elseBranch)
    }

    @Test
    fun `while y doWhile guardan condicion y cuerpo`() {
        val w = assertIs<While>(stmt("while (x) { print(1); }"))
        assertIs<Identifier>(w.condition)
        assertEquals(1, w.body.statements.size)

        val dw = assertIs<DoWhile>(stmt("do { print(1); } while (x);"))
        assertIs<Identifier>(dw.condition)
        assertEquals(1, dw.body.statements.size)
    }

    @Test
    fun `foreach guarda la variable y el iterable`() {
        val fe = assertIs<ForEach>(stmt("foreach (nota in notas) { print(nota); }"))

        assertEquals("nota", fe.variableName)
        assertEquals("notas", assertIs<Identifier>(fe.iterable).name)
    }

    @Test
    fun `tryCatch guarda los dos bloques y el parametro`() {
        val tc = assertIs<TryCatch>(stmt("try { f(); } catch (err) { print(err); }"))

        assertEquals("err", tc.catchParameterName)
        assertEquals(1, tc.tryBlock.statements.size)
        assertEquals(1, tc.catchBlock.statements.size)
    }

    // ── Switch ─────────────────────────────────────────────────────────────

    // ESTE TEST ATRAPA EL visitSwitchCase FALTANTE: sin el, el default de ANTLR
    // devuelve el ultimo statement del cuerpo (un Statement, no un SwitchCase) y
    // el `as SwitchCase` lanza ClassCastException.
    @Test
    fun `un switch con dos cases construye ambos, aun con cuerpo vacio`() {
        val sw = assertIs<Switch>(stmt("switch (x) { case 1: print(1); case 2: }"))

        assertEquals(2, sw.cases.size)
        assertEquals(1L, assertIs<Literal>(sw.cases[0].value).value)
        assertEquals(1, sw.cases[0].body.size)
        assertTrue(sw.cases[1].body.isEmpty())
    }

    // La Fase 5 usa esta diferencia para decidir si un switch garantiza retorno:
    // sin default un valor no cubierto pasa de largo.
    @Test
    fun `sin default es null, default vacio es lista vacia`() {
        assertNull(assertIs<Switch>(stmt("switch (x) { }")).defaultBody)

        val conDefault = assertIs<Switch>(stmt("switch (x) { default: }"))
        assertEquals(emptyList(), conDefault.defaultBody)
    }

    @Test
    fun `el default con sentencias las construye`() {
        val sw = assertIs<Switch>(stmt("switch (x) { case 1: default: print(0); }"))

        assertEquals(1, sw.defaultBody?.size)
        assertIs<Print>(sw.defaultBody!![0])
    }

    // ── Las simples ────────────────────────────────────────────────────────

    @Test
    fun `print, return, break y continue`() {
        assertIs<Identifier>(assertIs<Print>(stmt("print(x);")).expr)

        assertEquals(5L, assertIs<Literal>(assertIs<Return>(stmt("return 5;")).value!!).value)
        assertNull(assertIs<Return>(stmt("return;")).value)

        assertIs<Break>(stmt("break;"))
        assertIs<Continue>(stmt("continue;"))
    }

    @Test
    fun `un bloque anida sus sentencias`() {
        val block = assertIs<Block>(stmt("{ let x; { print(1); } }"))

        assertEquals(2, block.statements.size)
        assertIs<VariableDeclaration>(block.statements[0])
        assertEquals(1, assertIs<Block>(block.statements[1]).statements.size)
    }

    // ── El programa completo ───────────────────────────────────────────────

    @Test
    fun `program junta las sentencias en orden`() {
        val prog = program("let x: integer = 1;\nprint(x);")

        assertEquals(2, prog.statements.size)
        assertIs<VariableDeclaration>(prog.statements[0])
        assertIs<Print>(prog.statements[1])
    }

    @Test
    fun `un programa vacio produce un Program sin sentencias`() {
        assertTrue(program("").statements.isEmpty())
    }

    // Criterio de aceptacion de la fase: el programa por defecto del IDE se
    // convierte completo, sin excepciones y sin nodos perdidos.
    @Test
    fun `el programa por defecto del IDE construye su AST completo`() {
        val prog = program(AppState().sourceContent)

        assertTrue(prog.statements.isNotEmpty())
    }

    // ── Ubicaciones ────────────────────────────────────────────────────────

    @Test
    fun `cada sentencia lleva la ubicacion de su primer token`() {
        val prog = program("let x;\n  print(x);")

        assertEquals(LexemeLocation(line = 1, position = 1), prog.statements[0].location)
        assertEquals(LexemeLocation(line = 2, position = 3), prog.statements[1].location)
    }
}
