package org.compiler.semantic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Que subconsultas miran hacia afuera.
 *
 * Una que no mira nada de afuera da siempre el mismo resultado, asi que el motor
 * la corre una vez y la guarda. Una que si mira cambia con cada fila. Es un
 * analisis que no reporta ningun error y que decide una optimizacion, y el dato ya
 * esta ahi: sale del nivel que dejo la resolucion de columnas.
 */
class CorrelationTest {

    @Test
    fun `una subconsulta que no mira hacia afuera no queda correlacionada`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(root, "SELECT 1 FROM users u WHERE u.id IN (SELECT uid FROM posts);")

        assertFalse(resolved.query.subqueries().single().correlated)
    }

    @Test
    fun `un EXISTS que usa una columna de afuera queda correlacionado`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(
            root,
            "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id);"
        )

        assertTrue(resolved.query.subqueries().single().correlated)
        assertFalse(resolved.query.correlated, "la consulta de mas afuera nunca correlaciona")
    }

    // Si la mas interna alcanza a la mas externa, la del medio tampoco se puede
    // cachear: cambia con la fila de arriba aunque ella no la nombre.
    @Test
    fun `una referencia que salta dos niveles marca a las dos de adentro`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(
            root,
            """
            SELECT 1 FROM users u
             WHERE EXISTS (SELECT 1 FROM posts p
                            WHERE EXISTS (SELECT 1 FROM posts q WHERE q.uid = u.id));
            """.trimIndent()
        )

        val middle = resolved.query.subqueries().single()
        assertTrue(middle.correlated)
        assertTrue(middle.subqueries().single().correlated)
        assertFalse(resolved.query.correlated)
    }

    // Y al reves: si la del medio es la que mira hacia afuera, la mas interna no
    // tiene por que marcarse.
    @Test
    fun `una referencia de un solo nivel no marca a la de mas adentro`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(
            root,
            """
            SELECT 1 FROM users u
             WHERE EXISTS (SELECT 1 FROM posts p
                            WHERE p.uid = u.id AND EXISTS (SELECT 1 FROM posts q WHERE q.uid = 1));
            """.trimIndent()
        )

        val middle = resolved.query.subqueries().single()
        assertTrue(middle.correlated)
        assertFalse(middle.subqueries().single().correlated)
    }

    // Una tabla derivada se ejecuta una sola vez por construccion: su ambito
    // cuelga del global, asi que no hay nada de afuera que pueda alcanzar.
    @Test
    fun `una tabla derivada nunca queda correlacionada`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = clean(root, "SELECT 1 FROM users u JOIN (SELECT uid FROM posts) d ON 1 = 1;")

        assertFalse(resolved.query.subqueries().single().correlated)
    }

    @Test
    fun `marcar la correlacion no reporta nada`(@TempDir root: File) {
        healthy(root, users, posts)
        val resolved = resolve(
            root,
            "SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id);"
        )

        assertEquals(emptyList(), resolved.errors)
    }
}
