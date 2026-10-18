package org.compiler.engine

import org.compiler.types.IntValue
import org.compiler.types.StringValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INSERT, UPDATE y DELETE.
 *
 * Las tres trabajan sobre las filas de la sesion y ninguna toca el disco: el
 * archivo no cambia hasta el volcado.
 */
class WriterDmlTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        database(root)
        return runScript(root, sql).errors
    }

    private fun names(outcome: Outcome): List<String> = outcome.grid.map { it.single() }

    // ── Lo que el mismo script ve ──────────────────────────────────────────

    // Es el caso que justifica que el cache de lectura y el almacen de escritura
    // sean el mismo objeto.
    @Test
    fun `un INSERT y luego un SELECT ven la fila nueva`(@TempDir root: File) {
        database(root)
        val outcome = query(
            root,
            "INSERT INTO users (id, name) VALUES (9, 'Nueva'); SELECT name FROM users WHERE id = 9;"
        )

        assertEquals(listOf("Nueva"), names(outcome))
    }

    @Test
    fun `un DELETE y luego un SELECT ya no la ven`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "DELETE FROM users WHERE id = 4; SELECT name FROM users ORDER BY id;")

        assertEquals(listOf("Ana", "Beto", "Cora"), names(outcome))
    }

    @Test
    fun `un UPDATE y luego un SELECT ven el valor nuevo`(@TempDir root: File) {
        database(root)
        val outcome = query(root, "UPDATE users SET city = 'CR' WHERE id = 1; SELECT city FROM users WHERE id = 1;")

        assertEquals(listOf("CR"), names(outcome))
    }

    // ── INSERT ─────────────────────────────────────────────────────────────

    @Test
    fun `un INSERT de varias filas las agrega todas`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "INSERT INTO users (id, name) VALUES (8, 'Una'), (9, 'Otra');")

        assertEquals(6, outcome.rowsOf("users").size)
    }

    @Test
    fun `un INSERT sin nombrar columnas usa el orden del esquema`(@TempDir root: File) {
        database(root)
        val rows = runScript(root, "INSERT INTO users VALUES (9, 'Nueva', 'CR', 20);").rowsOf("users")

        assertEquals(StringValue("CR"), rows.last().values[2])
    }

    // Un INSERT puede traer una subconsulta, aunque no pueda nombrar columnas de
    // la tabla que llena.
    @Test
    fun `un INSERT puede calcular un valor con una subconsulta`(@TempDir root: File) {
        database(root)
        val rows = runScript(
            root,
            "INSERT INTO users (id, name, age) VALUES (9, 'Nueva', (SELECT MAX(age) FROM users));"
        ).rowsOf("users")

        assertEquals(IntValue(40), rows.last().values[3])
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    fun `un UPDATE sin WHERE alcanza todas las filas`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "UPDATE users SET city = 'CR';")

        assertTrue(outcome.rowsOf("users").all { it.values[2] == StringValue("CR") })
        assertEquals(listOf("esto afecta todas las filas de 'users'"), outcome.diagnostics.warnings().map { it.message })
    }

    /**
     * Cambiar la edad no puede romper la clave primaria, pero la validacion mira
     * la fila entera, asi que hay que asegurarse de que no se confunda sola.
     */
    @Test
    fun `cambiar una columna no relacionada no reporta duplicado`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "UPDATE users SET age = 50 WHERE id = 1;"))
    }

    // La fila que se cambia se excluye por POSICION: si no, ponerle el valor que ya
    // tenia se reportaria como duplicado consigo misma.
    @Test
    fun `poner a una fila el id que ya tiene no reporta`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "UPDATE users SET id = 1 WHERE id = 1;"))
    }

    @Test
    fun `poner a una fila el id de otra si reporta`(@TempDir root: File) {
        assertEquals(
            listOf("ya existe una fila con id = 2 en 'users'"),
            errorsOf(root, "UPDATE users SET id = 2 WHERE id = 1;")
        )
    }

    @Test
    fun `un UPDATE puede calcular con la fila que esta cambiando`(@TempDir root: File) {
        database(root)
        val rows = runScript(root, "UPDATE users SET age = age + 10 WHERE id = 1;").rowsOf("users")

        assertEquals(IntValue(40), rows.first().values[3])
    }

    @Test
    fun `un UPDATE que no alcanza ninguna fila no marca la tabla`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "UPDATE users SET city = 'CR' WHERE id = 99;")

        assertTrue(outcome.session.changes().rows.isEmpty())
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    // Las llaves foraneas se revisan al reves al borrar.
    @Test
    fun `borrar una fila referenciada reporta con el conteo`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede borrar users.id = 1: lo referencian 2 filas de posts"),
            errorsOf(root, "DELETE FROM users WHERE id = 1;")
        )
    }

    @Test
    fun `borrar una fila que nadie referencia pasa`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "DELETE FROM users WHERE id = 4;")

        assertEquals(3, outcome.rowsOf("users").size)
        assertEquals(emptyList(), outcome.failures)
    }

    @Test
    fun `un DELETE sin WHERE borra todo y la advertencia no lo detiene`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "DELETE FROM posts;")

        assertTrue(outcome.rowsOf("posts").isEmpty())
        assertEquals(emptyList(), outcome.failures)
    }

    @Test
    fun `un DELETE que no alcanza nada deja la tabla igual`(@TempDir root: File) {
        database(root)
        val outcome = runScript(root, "DELETE FROM users WHERE id = 99;")

        assertEquals(4, outcome.rowsOf("users").size)
        assertTrue(outcome.session.changes().rows.isEmpty())
    }

    // ── Nada llega al disco ────────────────────────────────────────────────

    @Test
    fun `el archivo no cambia hasta el volcado`(@TempDir root: File) {
        database(root)
        val before = lines(root, "users")

        runScript(root, "INSERT INTO users (id, name) VALUES (9, 'Nueva');")

        assertEquals(before, lines(root, "users"))
    }
}
