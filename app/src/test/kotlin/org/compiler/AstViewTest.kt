// Ticket 9.3: el AST propio, en la forma que dibuja el lienzo.
package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.ast.toTreeView
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.frontend.syntax.toTreeView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AstViewTest {

    private fun viewOf(sql: String): TreeNodeView {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics), "No parsea: $sql")
        val script = SqlAstBuilder(diagnostics).visit(tree) as Script
        assertEquals(emptyList(), diagnostics.all(), "Reporto: $sql")
        return script.toTreeView()
    }

    private fun TreeNodeView.child(label: String): TreeNodeView =
        assertNotNull(children.firstOrNull { it.label == label }, "no hay hijo '$label' en '$this'")

    private fun TreeNodeView.size(): Int = 1 + children.sumOf { it.size() }

    private fun TreeNodeView.labels(): List<String> = listOf(label) + children.flatMap { it.labels() }

    // ── La raiz ────────────────────────────────────────────────────────────

    @Test
    fun `la raiz cuenta sus sentencias`() {
        // El FROM es obligatorio en la gramatica: no hay `SELECT 1` suelto.
        assertEquals("2 sentencias", viewOf("SELECT 1 FROM users; SELECT 2 FROM users;").detail)
        assertEquals("1 sentencia", viewOf("SELECT 1 FROM users;").detail)
    }

    // ── La consulta ────────────────────────────────────────────────────────

    /**
     * Las clausulas van en el orden en que SQL las EVALUA, no en el que se
     * escriben: el arbol se lee como la lista de pasos del motor.
     */
    @Test
    fun `las clausulas salen en orden de evaluacion`() {
        val select = viewOf(
            "SELECT name FROM users WHERE id > 1 GROUP BY name HAVING COUNT(*) > 0 ORDER BY name LIMIT 5;"
        ).children.single()

        assertEquals(
            listOf("from", "where", "group by", "having", "columnas", "order by", "limit"),
            select.children.map { it.label }
        )
    }

    @Test
    fun `una tabla con alias lo lleva en su detalle`() {
        val from = viewOf("SELECT * FROM users u;").children.single().child("from")

        assertEquals("users as u", from.children.single().detail)
    }

    @Test
    fun `un join cuelga su origen y su condicion`() {
        val select = viewOf("SELECT * FROM users JOIN posts ON posts.uid = users.id;").children.single()
        val join = select.child("join")

        assertEquals(listOf("tabla", "on"), join.children.map { it.label })
    }

    @Test
    fun `un alias del select se ve como tal`() {
        val columnas = viewOf("SELECT name AS quien FROM users;").children.single().child("columnas")

        assertEquals("as", columnas.children.single().label)
        assertEquals("quien", columnas.children.single().detail)
    }

    @Test
    fun `el orden descendente se distingue del ascendente`() {
        val orden = viewOf("SELECT 1 FROM users ORDER BY id DESC, name;").children.single().child("order by")

        assertEquals(listOf("desc", "asc"), orden.children.map { it.label })
    }

    // ── Las expresiones ────────────────────────────────────────────────────

    /**
     * Un literal y una columna son HOJAS.
     *
     * Sin eso, `id = 1` serian tres niveles para decir algo que cabe en uno, y el
     * arbol de una consulta con subconsultas se volveria ilegible a fuerza de
     * nodos que no aportan.
     */
    @Test
    fun `un literal y una columna no abren rama`() {
        val igual = viewOf("SELECT 1 FROM users WHERE id = 1;")
            .children.single().child("where").children.single()

        assertEquals("=", igual.label)
        assertEquals(listOf("id", "1"), igual.children.map { it.label })
        assertTrue(igual.children.all { it.children.isEmpty() }, "las hojas no tienen hijos")
        assertEquals(3, igual.size())
    }

    @Test
    fun `una columna calificada se escribe entera en su etiqueta`() {
        val where = viewOf("SELECT 1 FROM users u WHERE u.id = 1;").children.single().child("where")

        assertEquals("u.id", where.children.single().children.first().label)
    }

    @Test
    fun `un literal lleva su tipo en el detalle`() {
        val where = viewOf("SELECT 1 FROM users WHERE name = 'ana';").children.single().child("where")

        // La etiqueta es el VALOR, no el literal escrito: las comillas son
        // sintaxis y el arbol muestra lo que el literal significa.
        assertEquals("ana" to "VARCHAR(3)", where.children.single().children.last().let { it.label to it.detail })
    }

    @Test
    fun `una agregacion cuelga su argumento`() {
        val columnas = viewOf("SELECT COUNT(DISTINCT id) FROM users;").children.single().child("columnas")
        val count = columnas.children.single()

        assertEquals("COUNT", count.label)
        assertEquals("distinct", count.detail)
        assertEquals(listOf("id"), count.children.map { it.label })
    }

    // Una subconsulta trae su propio arbol entero debajo.
    @Test
    fun `una subconsulta abre otro select`() {
        val where = viewOf("SELECT 1 FROM users WHERE id IN (SELECT uid FROM posts);")
            .children.single().child("where")
        val dentro = where.children.single()

        assertEquals("in", dentro.label)
        assertEquals(listOf("id", "select"), dentro.children.map { it.label })
        assertTrue("from" in dentro.children.last().labels())
    }

    @Test
    fun `un exists cuelga su consulta`() {
        val where = viewOf("SELECT 1 FROM users WHERE EXISTS (SELECT 1 FROM posts);")
            .children.single().child("where")

        assertEquals("exists", where.children.single().label)
        assertEquals("select", where.children.single().children.single().label)
    }

    // ── Las otras sentencias ───────────────────────────────────────────────

    @Test
    fun `un create table cuelga sus columnas con tipo y restricciones`() {
        val create = viewOf(
            "CREATE TABLE users (id INT PRIMARY KEY, uid INT REFERENCES otros(id));"
        ).children.single()

        assertEquals("create table" to "users", create.label to create.detail)
        assertEquals(listOf("id", "uid"), create.children.map { it.label })
        assertEquals("INT PRIMARY KEY", create.children.first().detail)
        assertEquals("INT -> otros.id", create.children.last().detail)
    }

    @Test
    fun `un insert cuelga una rama por fila`() {
        val insert = viewOf("INSERT INTO users (id, name) VALUES (1, 'a'), (2, 'b');").children.single()

        assertEquals(listOf("columnas", "fila 1", "fila 2"), insert.children.map { it.label })
        assertEquals("id, name", insert.children.first().detail)
    }

    @Test
    fun `un update cuelga sus asignaciones y su where`() {
        val update = viewOf("UPDATE users SET name = 'x' WHERE id = 1;").children.single()

        assertEquals(listOf("set", "where"), update.children.map { it.label })
        assertEquals("name", update.children.first().detail)
    }

    @Test
    fun `las sentencias cortas caben en un solo nodo`() {
        assertEquals("drop table" to "users", viewOf("DROP TABLE users;").children.single().let { it.label to it.detail })
        assertEquals(
            "idx_a on users(name)",
            viewOf("CREATE INDEX idx_a ON users(name);").children.single().detail
        )
    }

    // ── Contra el arbol de ANTLR ───────────────────────────────────────────

    /**
     * Lo que el AST aporta, en un numero.
     *
     * El arbol de ANTLR tiene un nivel por regla de la gramatica, y la torre de
     * precedencia son once reglas encadenadas para una sola expresion. El AST
     * tiene un nodo por cosa que existe en el lenguaje.
     */
    @Test
    fun `el AST es mucho mas chico que el arbol de ANTLR`() {
        val sql = "SELECT name FROM users WHERE id = 1;"
        val diagnostics = Diagnostics()
        val parseTree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics)).toTreeView()

        assertTrue(
            viewOf(sql).size() * 2 < parseTree.size(),
            "AST ${viewOf(sql).size()} nodos contra ${parseTree.size()} del arbol sintactico"
        )
    }
}
