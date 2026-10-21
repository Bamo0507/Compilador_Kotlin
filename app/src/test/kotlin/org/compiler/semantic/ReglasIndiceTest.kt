// Ticket 8.5: las reglas semanticas de CREATE INDEX y DROP INDEX, y las dos que
// tocan sentencias viejas.
//
// El catalogo de ResolverFixture trae `users` con PRIMARY KEY en id, asi que
// `pk_users` existe desde el principio sin que nadie lo cree.
package org.compiler.semantic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ReglasIndiceTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).failures
    }

    private fun warningsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).warnings
    }

    // ── CREATE INDEX ───────────────────────────────────────────────────────

    @Test
    fun `un CREATE INDEX correcto no reporta`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "CREATE INDEX idx_city ON users(city);"))
    }

    @Test
    fun `la tabla tiene que existir`(@TempDir root: File) {
        assertEquals(
            listOf("la tabla 'fantasma' no existe"),
            errorsOf(root, "CREATE INDEX idx_x ON fantasma(a);")
        )
    }

    @Test
    fun `la columna tiene que existir en esa tabla`(@TempDir root: File) {
        assertEquals(
            listOf("'users' no tiene columna 'ciudad'"),
            errorsOf(root, "CREATE INDEX idx_x ON users(ciudad);")
        )
    }

    @Test
    fun `el nombre no puede estar tomado`(@TempDir root: File) {
        assertEquals(
            listOf("ya existe un indice llamado 'idx_city'"),
            errorsOf(
                root,
                "CREATE INDEX idx_city ON users(city);\nCREATE INDEX idx_city ON posts(city);"
            )
        )
    }

    /**
     * El mensaje que justifica que la gramatica acepte varias columnas.
     *
     * Con un solo Identifier en la gramatica, esto diria "se esperaba ')'".
     */
    @Test
    fun `los indices son de una sola columna`(@TempDir root: File) {
        assertEquals(
            listOf("los indices son de una sola columna, se recibieron 2"),
            errorsOf(root, "CREATE INDEX idx_x ON users(name, city);")
        )
    }

    // pk_ y uq_ son los prefijos que el catalogo se reserva para los automaticos.
    @Test
    fun `los nombres reservados no se pueden usar a mano`(@TempDir root: File) {
        assertEquals(
            listOf("'pk_otra' es un nombre reservado para indices automaticos"),
            errorsOf(root, "CREATE INDEX pk_otra ON users(city);")
        )
        assertEquals(
            listOf("'uq_otra' es un nombre reservado para indices automaticos"),
            errorsOf(root, "CREATE INDEX uq_otra ON users(city);")
        )
    }

    /**
     * Advertencia y no error: el indice nuevo funcionaria, solo que duplica
     * trabajo. Con los indices automaticos es la que evita indexar otra vez lo que
     * la clave primaria ya traia.
     */
    @Test
    fun `indexar una columna que ya tiene indice solo advierte`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "CREATE INDEX idx_id ON users(id);"))
        assertEquals(
            listOf("'users.id' ya esta indexada por 'pk_users'"),
            warningsOf(root, "CREATE INDEX idx_id ON users(id);")
        )
    }

    // El catalogo muta con el script: una tabla creada arriba existe mas abajo.
    @Test
    fun `se puede indexar una tabla creada en el mismo script`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(
                root,
                "CREATE TABLE autos (id INT PRIMARY KEY, marca VARCHAR(20));\n" +
                    "CREATE INDEX idx_marca ON autos(marca);"
            )
        )
    }

    // ── DROP INDEX ─────────────────────────────────────────────────────────

    @Test
    fun `el indice tiene que existir`(@TempDir root: File) {
        assertEquals(
            listOf("no existe un indice llamado 'idx_marca'"),
            errorsOf(root, "DROP INDEX idx_marca;")
        )
    }

    @Test
    fun `un indice creado en el mismo script se puede borrar`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(root, "CREATE INDEX idx_city ON users(city);\nDROP INDEX idx_city;")
        )
    }

    /**
     * Un automatico respalda una restriccion. Si se pudiera borrar, la restriccion
     * quedaria sin indice y nada volveria a crearlo.
     */
    @Test
    fun `un indice automatico no se puede borrar`(@TempDir root: File) {
        assertEquals(
            listOf("'pk_users' respalda la clave primaria y no se puede borrar"),
            errorsOf(root, "DROP INDEX pk_users;")
        )
    }

    // ── Las dos que tocan sentencias viejas ────────────────────────────────

    @Test
    fun `no se puede borrar una columna indexada`(@TempDir root: File) {
        assertEquals(
            listOf("'users.city' esta indexada por 'idx_city', hay que borrar el indice primero"),
            errorsOf(
                root,
                "CREATE INDEX idx_city ON users(city);\nALTER TABLE users DROP COLUMN city;"
            )
        )
    }

    @Test
    fun `borrando el indice primero, la columna si se puede borrar`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(
                root,
                "CREATE INDEX idx_city ON users(city);\n" +
                    "DROP INDEX idx_city;\n" +
                    "ALTER TABLE users DROP COLUMN city;"
            )
        )
    }

    // DROP TABLE no pregunta por los indices: se los lleva, porque son parte del
    // esquema de la tabla.
    @Test
    fun `DROP TABLE no se queja de los indices de esa tabla`(@TempDir root: File) {
        assertEquals(
            emptyList(),
            errorsOf(
                root,
                "CREATE TABLE autos (id INT, marca VARCHAR(20));\n" +
                    "CREATE INDEX idx_marca ON autos(marca);\n" +
                    "DROP TABLE autos;"
            )
        )
    }
}
