package org.compiler.semantic

import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.DeclareResult
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import org.compiler.types.IntType
import org.compiler.types.Type
import org.compiler.types.VarcharType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * El arbol de ambitos, que es lo que resuelve los alias de una consulta.
 *
 * Lo que mas importa aqui es `resolve`: devuelve el simbolo Y cuantos niveles
 * subio para encontrarlo, y ese numero es el que el motor usa despues para saber
 * de que fila de la pila sacar el valor.
 */
class ScopeTest {

    private fun global() = Scope(ScopeKind.GLOBAL, "global", parent = null)

    private fun symbol(
        name: String,
        kind: DeclarationKind = DeclarationKind.ALIAS,
        type: Type = IntType,
        sourceTable: String? = null,
        index: Int = -1
    ) = Symbol(
        name = name,
        kind = kind,
        type = type,
        location = LexemeLocation(1, 1),
        scopeName = "query",
        sourceTable = sourceTable,
        index = index
    )

    // ── Declarar ───────────────────────────────────────────────────────────

    @Test
    fun `un nombre declarado se encuentra en su propio nivel`() {
        val scope = global()
        assertIs<DeclareResult.Ok>(scope.declare(symbol("users")))

        assertEquals("users", scope.lookup("users")?.name)
    }

    // Dos tablas con el mismo alias en el mismo FROM.
    @Test
    fun `redeclarar en el mismo nivel falla y devuelve el anterior`() {
        val scope = global()
        scope.declare(symbol("u", sourceTable = "users"))

        val result = scope.declare(symbol("u", sourceTable = "posts"))

        val failure = assertIs<DeclareResult.AlreadyDeclared>(result)
        assertEquals("users", failure.previous.sourceTable)
    }

    // Una subconsulta puede reusar el alias de afuera. Es legal y comun.
    @Test
    fun `un alias de adentro tapa al de afuera`() {
        val outer = global()
        outer.declare(symbol("u", sourceTable = "users"))

        val inner = outer.openChild(ScopeKind.QUERY, "subquery")
        assertIs<DeclareResult.Ok>(inner.declare(symbol("u", sourceTable = "posts")))

        assertEquals("posts", inner.lookup("u")?.sourceTable)
        assertEquals("users", outer.lookup("u")?.sourceTable)
    }

    @Test
    fun `lookupLocal no sube por la cadena`() {
        val outer = global()
        outer.declare(symbol("users"))
        val inner = outer.openChild(ScopeKind.QUERY, "subquery")

        assertEquals("users", inner.lookup("users")?.name)
        assertNull(inner.lookupLocal("users"))
    }

    @Test
    fun `un nombre que no existe devuelve null`() {
        assertNull(global().lookup("fantasma"))
        assertNull(global().resolve("fantasma"))
    }

    // ── El nivel, que es lo que la fase 4 necesita ─────────────────────────

    @Test
    fun `un nombre del nivel actual resuelve en cero`() {
        val scope = global()
        scope.declare(symbol("u"))

        assertEquals(0, scope.resolve("u")?.level)
    }

    // El caso del EXISTS correlacionado: `p` es local y `u` viene de afuera.
    @Test
    fun `un nombre de la consulta externa resuelve en uno`() {
        val outer = global()
        outer.declare(symbol("u", sourceTable = "users"))

        val inner = outer.openChild(ScopeKind.QUERY, "subquery")
        inner.declare(symbol("p", sourceTable = "posts"))

        assertEquals(0, inner.resolve("p")?.level)
        assertEquals(1, inner.resolve("u")?.level)
    }

    @Test
    fun `tres niveles de anidamiento cuentan dos saltos`() {
        val level0 = global()
        level0.declare(symbol("a"))
        val level1 = level0.openChild(ScopeKind.QUERY, "q1")
        val level2 = level1.openChild(ScopeKind.QUERY, "q2")

        assertEquals(2, level2.resolve("a")?.level)
    }

    // Si tapar no reiniciara el nivel, el motor sacaria el valor de la fila
    // equivocada.
    @Test
    fun `un nombre tapado resuelve en cero, no en el nivel del de afuera`() {
        val outer = global()
        outer.declare(symbol("u", sourceTable = "users"))
        val inner = outer.openChild(ScopeKind.QUERY, "subquery")
        inner.declare(symbol("u", sourceTable = "posts"))

        val resolution = inner.resolve("u")

        assertEquals(0, resolution?.level)
        assertEquals("posts", resolution?.symbol?.sourceTable)
    }

    // ── El arbol ───────────────────────────────────────────────────────────

    // El arbol sobrevive la corrida entera: la GUI lo dibuja despues.
    @Test
    fun `los hijos quedan colgados del padre en orden`() {
        val root = global()
        val first = root.openChild(ScopeKind.QUERY, "q1")
        val second = root.openChild(ScopeKind.QUERY, "q2")

        assertEquals(2, root.children.size)
        assertSame(first, root.children[0])
        assertSame(second, root.children[1])
        assertSame(root, first.parent)
    }

    @Test
    fun `los simbolos salen en orden de declaracion`() {
        val scope = global()
        scope.declare(symbol("id", DeclarationKind.COLUMN, IntType, "users", 0))
        scope.declare(symbol("name", DeclarationKind.COLUMN, VarcharType(80), "users", 1))

        assertEquals(listOf("id", "name"), scope.localSymbols().map { it.name })
        assertEquals(listOf(0, 1), scope.localSymbols().map { it.index })
    }
}
