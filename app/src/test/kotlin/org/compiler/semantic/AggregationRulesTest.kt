package org.compiler.semantic

import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.Type
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La agregacion, que es la regla que no se resuelve mirando un nodo aislado:
 * necesita el contexto de toda la consulta.
 */
class AggregationRulesTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).errors
    }

    // El tipo de la primera columna del SELECT.
    private fun firstType(root: File, sql: String): Type {
        healthy(root, users)
        val query = checked(root, sql).script.statements.single() as Query
        return (query.selection.first() as SelectExpression).expression.type
    }

    // ── La regla principal ─────────────────────────────────────────────────

    /**
     * Si una ciudad tiene cinco usuarios hay un grupo y cinco nombres. La consulta
     * no dice cual de los cinco deberia salir, asi que no tiene respuesta.
     */
    @Test
    fun `una columna que no esta agrupada ni agregada reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'name' no esta en el GROUP BY ni dentro de una agregacion"),
            errorsOf(root, "SELECT city, name, COUNT(id) FROM users GROUP BY city;")
        )
    }

    @Test
    fun `la columna agrupada si puede salir`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT city, COUNT(id) FROM users GROUP BY city;"))
    }

    @Test
    fun `una columna dentro de una agregacion no necesita estar agrupada`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT city, MAX(name) FROM users GROUP BY city;"))
    }

    // Sin GROUP BY, una agregacion agrupa todo en un solo grupo.
    @Test
    fun `COUNT sin GROUP BY es valido`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT COUNT(*) FROM users;"))
    }

    @Test
    fun `una columna suelta junto a una agregacion sin GROUP BY reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'name' no esta en el GROUP BY ni dentro de una agregacion"),
            errorsOf(root, "SELECT name, COUNT(*) FROM users;")
        )
    }

    @Test
    fun `un asterisco en una consulta agrupada reporta`(@TempDir root: File) {
        assertEquals(
            listOf("'*' no se puede usar en una consulta agrupada, nombre las columnas"),
            errorsOf(root, "SELECT * FROM users GROUP BY city;")
        )
    }

    @Test
    fun `la regla tambien alcanza al HAVING`(@TempDir root: File) {
        assertEquals(
            listOf("'name' no esta en el GROUP BY ni dentro de una agregacion"),
            errorsOf(root, "SELECT city FROM users GROUP BY city HAVING name = 'ana';")
        )
    }

    // ── Donde cabe una agregacion ──────────────────────────────────────────

    @Test
    fun `una agregacion en el WHERE reporta`(@TempDir root: File) {
        assertEquals(
            listOf("WHERE no puede usar funciones de agregacion, se esperaba HAVING"),
            errorsOf(root, "SELECT city FROM users WHERE COUNT(id) > 2 GROUP BY city;")
        )
    }

    @Test
    fun `una agregacion dentro de otra reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede anidar COUNT dentro de SUM"),
            errorsOf(root, "SELECT SUM(COUNT(id)) FROM users;")
        )
    }

    @Test
    fun `un HAVING sin agregacion reporta`(@TempDir root: File) {
        assertEquals(
            listOf("HAVING solo tiene sentido con GROUP BY"),
            errorsOf(root, "SELECT name FROM users HAVING name = 'ana';")
        )
    }

    // ── Lo que acepta cada funcion ─────────────────────────────────────────

    @Test
    fun `SUM sobre un texto reporta`(@TempDir root: File) {
        assertEquals(
            listOf("SUM espera un tipo numerico, recibio VARCHAR(80)"),
            errorsOf(root, "SELECT SUM(name) FROM users;")
        )
    }

    @Test
    fun `AVG sobre un texto reporta`(@TempDir root: File) {
        assertEquals(
            listOf("AVG espera un tipo numerico, recibio VARCHAR(80)"),
            errorsOf(root, "SELECT AVG(name) FROM users;")
        )
    }

    // El orden de BOOLEAN no significa nada, asi que su minimo tampoco.
    @Test
    fun `MIN sobre un logico reporta`(@TempDir root: File) {
        assertEquals(
            listOf("MIN no acepta BOOLEAN"),
            errorsOf(root, "SELECT MIN(id > 2) FROM users;")
        )
    }

    // ── Los tipos que devuelven ────────────────────────────────────────────

    @Test
    fun `COUNT siempre da INT`(@TempDir root: File) {
        assertEquals(IntType, firstType(root, "SELECT COUNT(*) FROM users;"))
        assertEquals(IntType, firstType(root, "SELECT COUNT(name) FROM users;"))
    }

    @Test
    fun `SUM conserva el tipo`(@TempDir root: File) {
        assertEquals(IntType, firstType(root, "SELECT SUM(id) FROM users;"))
    }

    // El promedio de enteros casi nunca es entero, y truncar seria una sorpresa
    // silenciosa.
    @Test
    fun `AVG siempre da FLOAT`(@TempDir root: File) {
        assertEquals(FloatType, firstType(root, "SELECT AVG(id) FROM users;"))
    }

    @Test
    fun `MIN y MAX conservan el tipo`(@TempDir root: File) {
        assertEquals(VarcharType(80), firstType(root, "SELECT MIN(name) FROM users;"))
        assertEquals(IntType, firstType(root, "SELECT MAX(id) FROM users;"))
    }
}
