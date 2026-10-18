package org.compiler.engine

import org.compiler.catalog.Column
import org.compiler.catalog.Table
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El join, que son bucles anidados.
 *
 * La fila resultante es la concatenacion de las dos, en el orden del FROM, que es
 * justo donde apuntan los indices que dejo la fase 4: el join no reordena nada.
 */
class JoinTest {

    private val tags = Table("tags", listOf(
        Column("post", IntType, emptyList()),
        Column("label", VarcharType(20), emptyList())
    ))

    @Test
    fun `un join con coincidencias devuelve un par por cada una`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT u.name, p.title FROM users u JOIN posts p ON u.id = p.uid ORDER BY p.title;")

        assertEquals(
            listOf(listOf("Beto", "Algo"), listOf("Ana", "Chao"), listOf("Ana", "Hola"), listOf("Cora", "Solo")),
            outcome.grid
        )
    }

    @Test
    fun `sin coincidencias no devuelve filas`(@TempDir root: File) {
        database(root)

        assertEquals(emptyList(), query(root, "SELECT u.name FROM users u JOIN posts p ON u.id = p.uid + 100;").grid)
    }

    // Dani no tiene posts, asi que no aparece: es un join interno.
    @Test
    fun `una fila sin pareja se queda fuera`(@TempDir root: File) {
        database(root)
        val names = query(root, "SELECT u.name FROM users u JOIN posts p ON u.id = p.uid;")
            .grid.map { it.single() }.distinct()

        assertEquals(listOf("Ana", "Beto", "Cora"), names.sorted())
    }

    @Test
    fun `la fila compuesta tiene las columnas de las dos`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT * FROM users u JOIN posts p ON u.id = p.uid ORDER BY p.title;")

        assertEquals(usersTable.columns.size + postsTable.columns.size, outcome.result.rows.first().values.size)
        assertEquals(listOf("id", "name", "city", "age", "uid", "title", "views"), outcome.names)
    }

    // El indice de la segunda tabla ya viene desplazado desde la fase 4, asi que
    // `p.title` apunta al lugar correcto de la fila concatenada.
    @Test
    fun `una columna de la segunda tabla se lee del tramo que le toca`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT p.views, u.id FROM users u JOIN posts p ON u.id = p.uid ORDER BY p.views;")

        assertEquals(listOf(listOf("10", "3"), listOf("50", "1"), listOf("150", "1"), listOf("200", "2")), outcome.grid)
    }

    @Test
    fun `un asterisco calificado trae solo el tramo de su origen`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT p.* FROM users u JOIN posts p ON u.id = p.uid ORDER BY p.views;")

        assertEquals(listOf("uid", "title", "views"), outcome.names)
        assertEquals(listOf("3", "Solo", "10"), outcome.grid.first())
    }

    @Test
    fun `tres tablas encadenadas asocian de izquierda a derecha`(@TempDir root: File) {
        database(root)
        write(root, tags, listOf(listOf("150", "viral"), listOf("200", "nuevo")))

        val outcome = query(
            root,
            """
            SELECT u.name, t.label FROM users u
             JOIN posts p ON u.id = p.uid
             JOIN tags t ON t.post = p.views
             ORDER BY t.label;
            """.trimIndent()
        )

        assertEquals(listOf(listOf("Beto", "nuevo"), listOf("Ana", "viral")), outcome.grid)
    }

    @Test
    fun `un autojoin compara una tabla consigo misma`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            "SELECT a.name, b.name FROM users a JOIN users b ON a.city = b.city AND a.id < b.id;"
        )

        assertEquals(listOf(listOf("Ana", "Beto")), outcome.grid)
    }

    // La condicion del ON se evalua sobre la fila ya concatenada, asi que puede
    // mirar columnas de los dos lados.
    @Test
    fun `el ON puede usar cualquier columna de las dos tablas`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "SELECT p.title FROM users u JOIN posts p ON u.id = p.uid AND p.views > 100;")

        assertEquals(listOf(listOf("Hola"), listOf("Algo")), outcome.grid)
    }
}
