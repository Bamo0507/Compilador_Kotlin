package org.compiler.semantic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Las reglas de la consulta, que casi todas salen del orden de evaluacion:
 * FROM, JOIN, WHERE, GROUP BY, HAVING, SELECT, DISTINCT, ORDER BY, LIMIT.
 *
 * No es el orden en que se escribe, y de ahi sale que un alias del SELECT sirva
 * en el ORDER BY y no en el WHERE.
 */
class QueryRulesTest {

    private fun errorsOf(root: File, sql: String): List<String> {
        healthy(root, users, posts)
        return checked(root, sql).errors
    }

    // ── Las clausulas que filtran dan BOOLEAN ──────────────────────────────

    @Test
    fun `un WHERE que no da BOOLEAN reporta`(@TempDir root: File) {
        assertEquals(
            listOf("WHERE espera BOOLEAN, recibio INT"),
            errorsOf(root, "SELECT 1 FROM users WHERE id + 1;")
        )
    }

    @Test
    fun `un ON que no da BOOLEAN reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la condicion del JOIN espera BOOLEAN, recibio INT"),
            errorsOf(root, "SELECT 1 FROM users u JOIN posts p ON u.id;")
        )
    }

    @Test
    fun `un HAVING que no da BOOLEAN reporta`(@TempDir root: File) {
        assertEquals(
            listOf("HAVING espera BOOLEAN, recibio INT"),
            errorsOf(root, "SELECT city FROM users GROUP BY city HAVING COUNT(id);")
        )
    }

    // ── El orden de evaluacion ─────────────────────────────────────────────

    @Test
    fun `un alias del SELECT no esta disponible en el WHERE`(@TempDir root: File) {
        assertEquals(
            listOf("'doble' se define en el SELECT y no esta disponible en WHERE"),
            errorsOf(root, "SELECT id + 1 AS doble FROM users WHERE doble > 40;")
        )
    }

    @Test
    fun `el mismo alias si esta disponible en el ORDER BY`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id + 1 AS doble FROM users ORDER BY doble;"))
    }

    // ── LIMIT ──────────────────────────────────────────────────────────────

    @Test
    fun `un LIMIT en cero reporta`(@TempDir root: File) {
        assertEquals(
            listOf("LIMIT espera un entero mayor que cero"),
            errorsOf(root, "SELECT id FROM users ORDER BY id LIMIT 0;")
        )
    }

    // Las filas salen en el orden del CSV, asi que en la practica es estable,
    // pero depender de eso es depender de un detalle de implementacion.
    @Test
    fun `un LIMIT sin ORDER BY avisa y no detiene`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "SELECT id FROM users LIMIT 5;")

        assertEquals(listOf("LIMIT sin ORDER BY no garantiza que filas salen"), resolved.warnings)
        assertFalse(resolved.diagnostics.hasErrors)
    }

    @Test
    fun `un LIMIT con ORDER BY no avisa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id FROM users ORDER BY id LIMIT 5;"))
    }

    // ── Los nombres del resultado ──────────────────────────────────────────

    @Test
    fun `dos alias de salida iguales reportan`(@TempDir root: File) {
        assertEquals(
            listOf("hay dos columnas llamadas 'total' en el resultado"),
            errorsOf(root, "SELECT id AS total, name AS total FROM users;")
        )
    }

    // Que un join repita un nombre de columna es normal y no se toca: la regla es
    // sobre los alias escritos.
    @Test
    fun `un asterisco que repite un nombre de columna no reporta`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT * FROM users u JOIN posts p ON u.id = p.uid;"))
    }

    // ── La comparacion con NULL ────────────────────────────────────────────

    // `x = NULL` da NULL, no true, asi que la condicion nunca se cumple. El motor
    // no lo puede detectar porque tecnicamente es valido.
    @Test
    fun `comparar con NULL avisa y no detiene`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "SELECT id FROM users WHERE city = NULL;")

        assertEquals(listOf("= NULL nunca es verdadero, se esperaba IS NULL"), resolved.warnings)
        assertFalse(resolved.diagnostics.hasErrors)
    }

    @Test
    fun `la advertencia aparece en la lista con su severidad`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "SELECT id FROM users WHERE city = NULL;")

        assertEquals(1, resolved.errors.size)
        assertTrue(resolved.failures.isEmpty())
    }

    @Test
    fun `IS NULL no avisa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "SELECT id FROM users WHERE city IS NULL;"))
    }

    @Test
    fun `un script con solo advertencias se ejecuta`(@TempDir root: File) {
        healthy(root, users)
        val resolved = checked(root, "SELECT id FROM users WHERE city = NULL LIMIT 3;")

        assertEquals(2, resolved.warnings.size)
        assertFalse(resolved.diagnostics.hasErrors)
    }
}
