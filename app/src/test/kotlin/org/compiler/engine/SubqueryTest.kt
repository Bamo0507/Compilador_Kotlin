package org.compiler.engine

import org.compiler.catalog.Column
import org.compiler.catalog.Table
import org.compiler.types.IntType
import org.compiler.types.NullValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las cuatro formas de subconsulta, y las dos decisiones que las hacen usables:
 * cachear la que no mira hacia afuera, y cortar el EXISTS en la primera fila.
 */
class SubqueryTest {

    private val many = Table("many", listOf(Column("uid", IntType, emptyList())))

    // Mil filas donde solo una coincide con el usuario 1, en la posicion que se
    // pida. Sirve para ver si el EXISTS corta o recorre todo.
    private fun haystack(root: File, matchFirst: Boolean) {
        val rows = (1..1000).map { listOf(if (it == if (matchFirst) 1 else 1001) "1" else "999") }
        write(root, many, rows)
    }

    // ── El cache de lo no correlacionado ───────────────────────────────────

    /**
     * Una subconsulta que no mira hacia afuera da siempre lo mismo. Sin cache, un
     * IN dentro de un WHERE sobre 10,000 filas se ejecutaria 10,000 veces.
     */
    @Test
    fun `un IN no correlacionado ejecuta la interna una sola vez`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT name FROM users WHERE id IN (SELECT uid FROM posts);")

        assertEquals(listOf("Ana", "Beto", "Cora"), outcome.grid.map { it.single() })
        assertEquals(2, outcome.session.executions, "la externa y la interna una vez cada una")
    }

    @Test
    fun `un EXISTS correlacionado ejecuta la interna una vez por fila de afuera`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT u.name FROM users u WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id);")

        assertEquals(listOf("Ana", "Beto", "Cora"), outcome.grid.map { it.single() })
        assertEquals(5, outcome.session.executions, "la externa mas una por cada uno de los cuatro usuarios")
    }

    // ── El corte del EXISTS ────────────────────────────────────────────────

    /**
     * Un EXISTS correlacionado corre por cada fila de afuera. Sin el corte
     * recorreria la tabla entera cada vez, solo para descartar el resultado.
     *
     * El AND tambien corta: `id = 1` falso ya decide la fila, asi que el EXISTS
     * solo se evalua para el usuario 1.
     *
     * Desde la fase 8, `u.id = 1` ademas usa el indice de la clave primaria, asi
     * que de users se lee UNA fila en vez de las cuatro.
     */
    @Test
    fun `el EXISTS para en la primera coincidencia`(@TempDir root: File) {
        database(root)
        haystack(root, matchFirst = true)

        val outcome = query(
            root,
            "SELECT u.name FROM users u WHERE u.id = 1 AND EXISTS (SELECT 1 FROM many m WHERE m.uid = u.id);"
        )

        assertEquals(listOf(listOf("Ana")), outcome.grid)
        assertEquals(2, outcome.session.examined, "un usuario, por el indice, y una sola fila de las mil")
    }

    @Test
    fun `sin coincidencia si recorre la tabla entera`(@TempDir root: File) {
        database(root)
        haystack(root, matchFirst = false)

        val outcome = query(
            root,
            "SELECT u.name FROM users u WHERE u.id = 1 AND EXISTS (SELECT 1 FROM many m WHERE m.uid = u.id);"
        )

        assertEquals(emptyList(), outcome.grid)
        assertEquals(1001, outcome.session.examined, "un usuario, por el indice, y las mil filas de many")
    }

    // ── La escalar ─────────────────────────────────────────────────────────

    @Test
    fun `una escalar devuelve su unico valor`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf(listOf("Beto")),
            query(root, "SELECT name FROM users WHERE age = (SELECT MAX(age) FROM users);").grid
        )
    }

    @Test
    fun `una escalar sin filas da NULL`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT (SELECT name FROM users WHERE id = 99) FROM users WHERE id = 1;")

        assertEquals(NullValue, outcome.column(0).single())
    }

    // Que devuelva a lo sumo una fila depende de los datos, asi que no se podia
    // saber en el analisis: es un error de ejecucion.
    @Test
    fun `una escalar con varias filas reporta al ejecutar`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "SELECT (SELECT name FROM users) FROM users WHERE id = 1;")

        assertEquals(listOf("la subconsulta devolvio 4 filas, se esperaba a lo sumo una"), outcome.errors)
    }

    // ── IN y NOT IN ────────────────────────────────────────────────────────

    @Test
    fun `NOT IN deja lo que no esta`(@TempDir root: File) {
        database(root)

        assertEquals(
            listOf(listOf("Dani")),
            query(root, "SELECT name FROM users WHERE id NOT IN (SELECT uid FROM posts);").grid
        )
    }

    @Test
    fun `un IN correlacionado si se vuelve a ejecutar`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            "SELECT u.name FROM users u WHERE u.id IN (SELECT p.uid FROM posts p WHERE p.views > u.age);"
        )

        // Cora queda fuera porque su edad es nula y la comparacion da nulo.
        assertEquals(listOf("Ana", "Beto"), outcome.grid.map { it.single() })
        assertTrue(outcome.session.executions > 2, "una por fila de afuera, no una sola")
    }

    // ── Anidamiento ────────────────────────────────────────────────────────

    @Test
    fun `tres niveles dan el resultado correcto`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            SELECT u.name FROM users u
             WHERE EXISTS (SELECT 1 FROM posts p
                            WHERE p.uid = u.id
                              AND EXISTS (SELECT 1 FROM posts q WHERE q.views > p.views));
            """.trimIndent()
        )

        assertEquals(listOf("Ana", "Cora"), outcome.grid.map { it.single() }.sorted())
    }

    // ── La tabla derivada ──────────────────────────────────────────────────

    @Test
    fun `una tabla derivada se usa como si fuera una tabla`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT d.name FROM (SELECT name FROM users WHERE city = 'GT') d ORDER BY d.name;")

        assertEquals(listOf(listOf("Ana"), listOf("Beto")), outcome.grid)
    }

    @Test
    fun `una tabla derivada con agregacion adentro funciona`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            SELECT d.city, d.total
              FROM (SELECT city, COUNT(*) AS total FROM users GROUP BY city) d
             WHERE d.total > 1;
            """.trimIndent()
        )

        assertEquals(listOf(listOf("GT", "2")), outcome.grid)
    }

    // No puede ser correlacionada, porque su ambito no ve el de afuera, asi que se
    // ejecuta una vez aunque la de afuera tenga muchas filas.
    @Test
    fun `una tabla derivada se ejecuta una sola vez`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT d.name FROM (SELECT name FROM users) d;")

        assertEquals(2, outcome.session.executions)
    }

    @Test
    fun `una tabla derivada se puede unir con una tabla real`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            """
            SELECT u.name, d.total
              FROM users u
              JOIN (SELECT uid, COUNT(*) AS total FROM posts GROUP BY uid) d ON d.uid = u.id
             ORDER BY u.name;
            """.trimIndent()
        )

        assertEquals(listOf(listOf("Ana", "2"), listOf("Beto", "1"), listOf("Cora", "1")), outcome.grid)
    }
}
