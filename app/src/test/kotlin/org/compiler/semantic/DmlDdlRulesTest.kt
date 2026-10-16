package org.compiler.semantic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Las reglas de las sentencias que no son consultas.
 *
 * El catalogo avanza con el script: una tabla creada arriba existe mas abajo, que
 * es como la vera el motor.
 */
class DmlDdlRulesTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).errors
    }

    // ── INSERT ─────────────────────────────────────────────────────────────

    @Test
    fun `un INSERT sobre una tabla que no existe reporta`(@TempDir root: File) {
        assertEquals(listOf("la tabla 'fantasma' no existe"), errorsOf(root, "INSERT INTO fantasma VALUES (1);"))
    }

    @Test
    fun `un INSERT completo y bien tipado pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "INSERT INTO users VALUES (1, 'ana', 'gt');"))
    }

    @Test
    fun `una cantidad de valores distinta reporta`(@TempDir root: File) {
        assertEquals(
            listOf("se esperaban 3 valores, se recibieron 2"),
            errorsOf(root, "INSERT INTO users VALUES (1, 'ana');")
        )
    }

    @Test
    fun `un valor de otro tipo reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede guardar VARCHAR(3) en la columna 'id' de tipo INT"),
            errorsOf(root, "INSERT INTO users (id, name) VALUES ('ana', 'ana');")
        )
    }

    // El largo se revisa solo sobre literales: `name || city` no tiene largo
    // conocido hasta ejecutarlo.
    @Test
    fun `un texto que no cabe en la columna reporta`(@TempDir root: File) {
        val largo = "x".repeat(41)

        assertEquals(
            listOf("'$largo' no cabe en VARCHAR(40)"),
            errorsOf(root, "INSERT INTO users (name, city) VALUES ('ana', '$largo');")
        )
    }

    @Test
    fun `un NULL en una columna que no lo acepta reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 'name' no acepta nulos"),
            errorsOf(root, "INSERT INTO users (name) VALUES (NULL);")
        )
    }

    @Test
    fun `una columna obligatoria omitida reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 'name' no acepta nulos y no tiene valor por omision"),
            errorsOf(root, "INSERT INTO users (city) VALUES ('gt');")
        )
    }

    // id es AUTOINCREMENT y title tiene DEFAULT, asi que omitirlas esta bien.
    @Test
    fun `omitir una columna con AUTOINCREMENT o DEFAULT pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "INSERT INTO users (name) VALUES ('ana');"))
        assertEquals(emptyList(), errorsOf(root, "INSERT INTO posts (uid) VALUES (1);"))
    }

    @Test
    fun `una columna nombrada que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users' no tiene columna 'fantasma'"),
            errorsOf(root, "INSERT INTO users (fantasma) VALUES (1);")
        )
    }

    @Test
    fun `una columna nombrada dos veces reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 'id' se nombra dos veces"),
            errorsOf(root, "INSERT INTO users (id, id) VALUES (1, 2);")
        )
    }

    // ── UPDATE y DELETE ────────────────────────────────────────────────────

    @Test
    fun `un UPDATE bien formado pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "UPDATE users SET name = 'ana' WHERE id = 1;"))
    }

    @Test
    fun `asignar a una columna que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users' no tiene columna 'fantasma'"),
            errorsOf(root, "UPDATE users SET fantasma = 1 WHERE id = 1;")
        )
    }

    @Test
    fun `asignar dos veces la misma columna reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 'name' se asigna dos veces"),
            errorsOf(root, "UPDATE users SET name = 'a', name = 'b' WHERE id = 1;")
        )
    }

    @Test
    fun `asignar un valor de otro tipo reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede guardar INT en la columna 'name' de tipo VARCHAR(80)"),
            errorsOf(root, "UPDATE users SET name = 1 WHERE id = 1;")
        )
    }

    @Test
    fun `asignar a una columna AUTOINCREMENT reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'id' es AUTOINCREMENT y no se puede asignar"),
            errorsOf(root, "UPDATE users SET id = 2 WHERE name = 'ana';")
        )
    }

    @Test
    fun `un WHERE de UPDATE que no da BOOLEAN reporta`(@TempDir root: File) {
        assertEquals(
            listOf("WHERE espera BOOLEAN, recibio INT"),
            errorsOf(root, "UPDATE users SET name = 'ana' WHERE id;")
        )
    }

    // Tocar la tabla entera es valido y a veces es lo que se quiere, asi que avisa
    // y deja correr.
    @Test
    fun `un UPDATE sin WHERE avisa y no detiene`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "UPDATE users SET city = 'gt';")

        assertEquals(listOf("esto afecta todas las filas de 'users'"), resolved.warnings)
        assertFalse(resolved.diagnostics.hasErrors)
    }

    @Test
    fun `un DELETE sin WHERE avisa`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(listOf("esto afecta todas las filas de 'users'"), checked(root, "DELETE FROM users;").warnings)
    }

    @Test
    fun `un DELETE con WHERE pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "DELETE FROM users WHERE id = 1;"))
    }

    // ── CREATE TABLE ───────────────────────────────────────────────────────

    @Test
    fun `crear una tabla que ya existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la tabla 'users' ya existe"),
            errorsOf(root, "CREATE TABLE users (id INT);")
        )
    }

    @Test
    fun `dos columnas con el mismo nombre reportan`(@TempDir root: File) {
        assertEquals(
            listOf("'t' declara dos columnas llamadas 'a'"),
            errorsOf(root, "CREATE TABLE t (a INT, a TEXT);")
        )
    }

    @Test
    fun `dos claves primarias reportan`(@TempDir root: File) {
        assertEquals(
            listOf("'t' declara dos claves primarias"),
            errorsOf(root, "CREATE TABLE t (a INT PRIMARY KEY, b INT PRIMARY KEY);")
        )
    }

    @Test
    fun `NOT NULL y NULL juntos reportan`(@TempDir root: File) {
        assertEquals(
            listOf("'a' se declara NOT NULL y NULL a la vez"),
            errorsOf(root, "CREATE TABLE t (a INT NOT NULL NULL);")
        )
    }

    @Test
    fun `AUTOINCREMENT sobre algo que no es INT reporta`(@TempDir root: File) {
        assertEquals(
            listOf("AUTOINCREMENT solo aplica a INT"),
            errorsOf(root, "CREATE TABLE t (a VARCHAR(5) AUTOINCREMENT);")
        )
    }

    @Test
    fun `un DEFAULT de otro tipo reporta`(@TempDir root: File) {
        assertEquals(
            listOf("el valor por omision de 'a' no es INT"),
            errorsOf(root, "CREATE TABLE t (a INT DEFAULT 'x');")
        )
    }

    @Test
    fun `DEFAULT y AUTOINCREMENT juntos reportan`(@TempDir root: File) {
        assertEquals(
            listOf("'a' no puede tener DEFAULT y AUTOINCREMENT a la vez"),
            errorsOf(root, "CREATE TABLE t (a INT AUTOINCREMENT DEFAULT 1);")
        )
    }

    @Test
    fun `una DECIMAL con mas escala que precision reporta`(@TempDir root: File) {
        assertEquals(
            listOf("DECIMAL(2,5) no es valido: la escala excede la precision"),
            errorsOf(root, "CREATE TABLE t (a DECIMAL(2,5));")
        )
    }

    @Test
    fun `un largo en cero reporta`(@TempDir root: File) {
        assertEquals(
            listOf("CHAR(0) no es valido: el largo debe ser mayor que cero"),
            errorsOf(root, "CREATE TABLE t (a CHAR(0));")
        )
    }

    // ── Las llaves foraneas ────────────────────────────────────────────────

    @Test
    fun `una FK hacia una tabla que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'t.a' referencia la tabla 'fantasma', que no existe"),
            errorsOf(root, "CREATE TABLE t (a INT REFERENCES fantasma(id));")
        )
    }

    @Test
    fun `una FK hacia una columna que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'t.a' referencia la columna 'users.fantasma', que no existe"),
            errorsOf(root, "CREATE TABLE t (a INT REFERENCES users(fantasma));")
        )
    }

    // Si la columna destino no es unica, un valor podria casar con varias filas y
    // la referencia no identificaria ninguna.
    @Test
    fun `una FK hacia una columna que no es unica reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'t.a' referencia 'users.name', que no es unica"),
            errorsOf(root, "CREATE TABLE t (a VARCHAR(80) REFERENCES users(name));")
        )
    }

    @Test
    fun `una FK de otro tipo reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'t.a' es VARCHAR(5) y 'users.id' es INT"),
            errorsOf(root, "CREATE TABLE t (a VARCHAR(5) REFERENCES users(id));")
        )
    }

    @Test
    fun `una FK bien formada pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "CREATE TABLE t (a INT REFERENCES users(id));"))
    }

    // ── El catalogo avanza con el script ───────────────────────────────────

    @Test
    fun `una tabla creada arriba se puede usar abajo`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(root, "CREATE TABLE t (a INT, b TEXT); INSERT INTO t VALUES (1, 'x');")
        )
    }

    @Test
    fun `una tabla borrada arriba ya no existe abajo`(@TempDir root: File) {
        assertEquals(
            listOf("la tabla 'posts' no existe"),
            errorsOf(root, "DROP TABLE posts; INSERT INTO posts (uid) VALUES (1);")
        )
    }

    // ── ALTER TABLE ────────────────────────────────────────────────────────

    @Test
    fun `agregar una columna que ya existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users' ya tiene una columna 'name'"),
            errorsOf(root, "ALTER TABLE users ADD name INT;")
        )
    }

    // La unica regla del DDL que necesita mirar los datos.
    @Test
    fun `agregar NOT NULL sin DEFAULT a una tabla con filas reporta`(@TempDir root: File) {
        withRows(root, users, 12)

        assertEquals(
            listOf("agregar 'edad' NOT NULL dejaria 12 filas invalidas, use DEFAULT"),
            checked(root, "ALTER TABLE users ADD edad INT NOT NULL;").errors
        )
    }

    @Test
    fun `agregar NOT NULL con DEFAULT pasa`(@TempDir root: File) {
        withRows(root, users, 12)

        assertEquals(emptyList(), checked(root, "ALTER TABLE users ADD edad INT NOT NULL DEFAULT 0;").errors)
    }

    @Test
    fun `agregar NOT NULL a una tabla vacia pasa`(@TempDir root: File) {
        healthy(root, users)

        assertEquals(emptyList(), checked(root, "ALTER TABLE users ADD edad INT NOT NULL;").errors)
    }

    @Test
    fun `quitar una columna que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users' no tiene columna 'fantasma'"),
            errorsOf(root, "ALTER TABLE users DROP COLUMN fantasma;")
        )
    }

    @Test
    fun `quitar una columna referenciada por una FK reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users.id' es referenciada por 'posts.uid'"),
            errorsOf(root, "ALTER TABLE users DROP COLUMN id;")
        )
    }

    @Test
    fun `quitar la ultima columna reporta`(@TempDir root: File) {
        assertEquals(
            listOf("una tabla no puede quedarse sin columnas"),
            errorsOf(root, "CREATE TABLE t (a INT); ALTER TABLE t DROP COLUMN a;")
        )
    }

    // ── DROP TABLE ─────────────────────────────────────────────────────────

    @Test
    fun `borrar una tabla que no existe reporta`(@TempDir root: File) {
        assertEquals(listOf("la tabla 'fantasma' no existe"), errorsOf(root, "DROP TABLE fantasma;"))
    }

    @Test
    fun `borrar una tabla referenciada reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'users' es referenciada por 'posts.uid'"),
            errorsOf(root, "DROP TABLE users;")
        )
    }

    @Test
    fun `borrar una tabla que nadie referencia pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "DROP TABLE posts;"))
    }
}
