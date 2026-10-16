package org.compiler.semantic

import org.compiler.frontend.semantic.schemaOf
import org.compiler.frontend.semantic.sources
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La resolucion del FROM: que tabla es cada nombre, donde arrancan sus columnas
 * dentro de la fila, y la guarda que compara el CSV contra el esquema.
 */
class TableResolutionTest {

    // ── El catalogo en el ambito global ────────────────────────────────────

    @Test
    fun `el ambito global trae la base completa`(@TempDir root: File) {
        healthy(root, users)
        val global = resolve(root, "SELECT 1 FROM users;").global

        assertEquals(listOf("users", "posts"), global.localSymbols().map { it.name })
        assertEquals(DeclarationKind.TABLE, assertNotNull(global.lookup("users")).kind)
    }

    // Un alias de afuera no es una tabla: el FROM se resuelve contra el catalogo,
    // no subiendo por los ambitos.
    @Test
    fun `un alias de la consulta externa no sirve como tabla de una subconsulta`(@TempDir root: File) {
        healthy(root, users, posts)

        val errors = resolve(root, "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM u);").errors

        assertEquals(listOf("la tabla 'u' no existe"), errors)
    }

    // ── Tablas y alias ─────────────────────────────────────────────────────

    @Test
    fun `una tabla sin alias se declara con su propio nombre`(@TempDir root: File) {
        healthy(root, users)
        val scope = clean(root, "SELECT 1 FROM users;").scope

        val source = assertNotNull(scope.lookupLocal("users"))
        assertEquals(DeclarationKind.TABLE, source.kind)
        assertEquals(users.columns.size, assertNotNull(source.table).columns.size)
        assertEquals(0, source.index)
    }

    @Test
    fun `una tabla con alias se declara por el alias y no por su nombre`(@TempDir root: File) {
        healthy(root, users)
        val scope = clean(root, "SELECT 1 FROM users u;").scope

        val alias = assertNotNull(scope.lookupLocal("u"))
        assertEquals(DeclarationKind.ALIAS, alias.kind)
        assertEquals("users", alias.sourceTable)
        assertNull(scope.lookupLocal("users"))
    }

    @Test
    fun `una tabla que no existe reporta y el analisis sigue`(@TempDir root: File) {
        healthy(root, users)
        val resolved = resolve(root, "SELECT 1 FROM fantasma; SELECT 1 FROM users;")

        assertEquals(listOf("la tabla 'fantasma' no existe"), resolved.errors)
        assertNotNull(resolved.queries[1].scope?.lookupLocal("users"))
    }

    @Test
    fun `un nombre repetido en el FROM reporta`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = resolve(root, "SELECT 1 FROM users u JOIN posts u ON 1 = 1;")

        assertEquals(listOf("el nombre 'u' se repite en el FROM"), resolved.errors)
    }

    // ── El desplazamiento del JOIN ─────────────────────────────────────────

    // En un JOIN la fila de trabajo es la concatenacion de las dos, asi que las
    // columnas del segundo origen arrancan donde terminan las del primero.
    @Test
    fun `el segundo origen de un JOIN arranca donde acaba el primero`(@TempDir root: File) {
        healthy(root, users, posts)
        val scope = clean(root, "SELECT 1 FROM users u JOIN posts p ON u.id = p.uid;").scope

        assertEquals(0, assertNotNull(scope.lookupLocal("u")).index)
        assertEquals(users.columns.size, assertNotNull(scope.lookupLocal("p")).index)
    }

    @Test
    fun `una tabla que no existe no corre el desplazamiento`(@TempDir root: File) {
        healthy(root, posts)
        val resolved = resolve(root, "SELECT 1 FROM fantasma JOIN posts p ON 1 = 1;")

        assertEquals(0, assertNotNull(resolved.scope.lookupLocal("p")).index)
    }

    // ── La guarda de cordura ───────────────────────────────────────────────

    @Test
    fun `un CSV que cuadra con su esquema no reporta`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(emptyList(), resolve(root, "SELECT 1 FROM users;").errors)
    }

    @Test
    fun `una columna de mas en el archivo reporta`(@TempDir root: File) {
        File(root, "users.csv").writeText("id,name,city,email\r\n")

        assertEquals(
            listOf("users.csv fue editado fuera del DBMS: el archivo tiene la columna 'email', que no esta en el esquema"),
            resolve(root, "SELECT 1 FROM users;").errors
        )
    }

    @Test
    fun `una columna de menos en el archivo reporta`(@TempDir root: File) {
        File(root, "users.csv").writeText("id,name\r\n")

        assertEquals(
            listOf("users.csv fue editado fuera del DBMS: al archivo le falta la columna 'city'"),
            resolve(root, "SELECT 1 FROM users;").errors
        )
    }

    @Test
    fun `las mismas columnas en otro orden reportan`(@TempDir root: File) {
        File(root, "users.csv").writeText("name,id,city\r\n")

        assertEquals(
            listOf("users.csv fue editado fuera del DBMS: las columnas del archivo estan en otro orden"),
            resolve(root, "SELECT 1 FROM users;").errors
        )
    }

    @Test
    fun `un CSV ilegible reporta en vez de reventar`(@TempDir root: File) {
        File(root, "users.csv").writeText("")

        assertTrue(resolve(root, "SELECT 1 FROM users;").errors.single().startsWith("users.csv no se pudo leer"))
    }

    // La tabla se abre una sola vez: de otro modo un script de diez consultas
    // sobre la misma tabla daria diez veces el mismo mensaje.
    @Test
    fun `una tabla rota usada dos veces reporta una sola vez`(@TempDir root: File) {
        File(root, "users.csv").writeText("id,name,city,email\r\n")

        assertEquals(1, resolve(root, "SELECT 1 FROM users; SELECT 1 FROM users u;").errors.size)
    }

    // Solo se abren los archivos de las tablas que el script toca.
    @Test
    fun `una tabla que el script no usa no se abre`(@TempDir root: File) {
        healthy(root, users)
        File(root, "posts.csv").writeText("basura,que,no,cuadra\r\n")

        assertEquals(emptyList(), resolve(root, "SELECT 1 FROM users;").errors)
    }

    // ── Subconsultas ───────────────────────────────────────────────────────

    @Test
    fun `una subconsulta del WHERE cuelga del ambito de la consulta`(@TempDir root: File) {
        healthy(root, users, posts)
        val outer = clean(root, "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts p);").scope

        val inner = outer.children.single()
        assertSame(outer, inner.parent)
        assertNotNull(inner.lookupLocal("p"))
        assertNull(inner.lookupLocal("u"))
    }

    // Una tabla derivada se ejecuta UNA vez, asi que no puede mirar hacia afuera:
    // su ambito cuelga del global y no del de la consulta que la contiene.
    @Test
    fun `una tabla derivada no ve el FROM de la consulta que la contiene`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = resolve(root, "SELECT 1 FROM users u JOIN (SELECT uid FROM posts) d ON 1 = 1;")

        val derived = assertNotNull(resolved.query.subqueries().single().scope)
        assertEquals("global", assertNotNull(derived.parent).name)
        assertNull(derived.lookup("u"))
    }

    @Test
    fun `una tabla derivada se declara con las columnas que produce`(@TempDir root: File) {
        healthy(root, users)
        val scope = clean(root, "SELECT 1 FROM (SELECT id, name FROM users) d;").scope

        val derived = assertNotNull(assertNotNull(scope.lookupLocal("d")).table)
        assertEquals(listOf("id", "name"), derived.columns.map { it.name })
        assertEquals(listOf(IntType, VarcharType(80)), derived.columns.map { it.type })
    }

    @Test
    fun `una tabla derivada desplaza a la siguiente por su cantidad de columnas`(@TempDir root: File) {
        healthy(root, users, posts)
        val scope = clean(root, "SELECT 1 FROM (SELECT id, name FROM users) d JOIN posts p ON 1 = 1;").scope

        assertEquals(2, assertNotNull(scope.lookupLocal("p")).index)
    }

    // ── El esquema de salida ───────────────────────────────────────────────

    @Test
    fun `un asterisco se expande en el orden del FROM`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(root, "SELECT * FROM users u JOIN posts p ON 1 = 1;")

        assertEquals(
            listOf("id", "name", "city", "uid", "title", "city"),
            schemaOf(resolved.query).map { it.name }
        )
    }

    @Test
    fun `un asterisco calificado solo trae las de su origen`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(root, "SELECT p.* FROM users u JOIN posts p ON 1 = 1;")

        assertEquals(listOf("uid", "title", "city"), schemaOf(resolved.query).map { it.name })
    }

    @Test
    fun `un asterisco con un calificador que nadie declaro reporta`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(
            listOf("no hay ninguna tabla llamada 'z'"),
            resolve(root, "SELECT z.* FROM users u;").errors
        )
    }

    @Test
    fun `el nombre sale del AS, y si no hay, de la columna`(@TempDir root: File) {
        healthy(root, users)
        val schema = schemaOf(clean(root, "SELECT u.id AS clave, u.name FROM users u;").query)

        assertEquals(listOf("clave", "name"), schema.map { it.name })
        assertEquals(listOf(IntType, VarcharType(80)), schema.map { it.type })
    }

    // Se puede mostrar en la rejilla, pero nadie la puede nombrar desde afuera.
    @Test
    fun `una expresion calculada sin AS queda sin nombre`(@TempDir root: File) {
        healthy(root, users)
        val resolved = clean(root, "SELECT id + 1 FROM users;")

        assertNull(schemaOf(resolved.query).single().name)
    }

    @Test
    fun `los origenes de un ambito salen en el orden del FROM`(@TempDir root: File) {
        healthy(root, users, posts)
        val scope = clean(root, "SELECT 1 FROM users u JOIN posts p ON 1 = 1;").scope

        assertEquals(listOf("u", "p"), scope.sources().map { it.name })
    }
}
