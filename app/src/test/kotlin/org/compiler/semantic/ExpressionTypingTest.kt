package org.compiler.semantic

import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.SelectExpression
import org.compiler.types.BooleanType
import org.compiler.types.DecimalType
import org.compiler.types.ErrorType
import org.compiler.types.IntType
import org.compiler.types.TextType
import org.compiler.types.Type
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * El tipo de cada expresion, que sube de los hijos al padre.
 *
 * Es el atributo sintetizado del proyecto. El ambito, en cambio, bajo de arriba
 * hacia abajo en la fase anterior.
 */
class ExpressionTypingTest {

    // El WHERE entero, que siempre tiene que dar BOOLEAN.
    private fun whereType(root: File, text: String): Type {
        healthy(root, users)
        val query = checked(root, "SELECT 1 FROM users WHERE $text;").script.statements.single() as Query
        return (query.where ?: error("sin WHERE")).type
    }

    // El lado izquierdo de la comparacion, que es donde se prueban los tipos que
    // no son BOOLEAN.
    private fun typeOf(root: File, text: String): Type {
        healthy(root, users)
        val query = checked(root, "SELECT 1 FROM users WHERE $text;").script.statements.single() as Query
        return (query.where as? Binary ?: error("se esperaba una comparacion")).left.type
    }

    private fun errorsOf(root: File, text: String): List<String> {
        healthy(root, users)
        return checked(root, "SELECT 1 FROM users WHERE $text;").errors
    }

    // ── Los tipos que salen ────────────────────────────────────────────────

    @Test
    fun `la aritmetica de enteros da entero`(@TempDir root: File) {
        assertEquals(IntType, typeOf(root, "id + 1 > 0"))
    }

    // Un INT entra a la aritmetica decimal como DECIMAL(19,0), que es lo que cabe
    // en un Long.
    @Test
    fun `mezclar entero con decimal da decimal`(@TempDir root: File) {
        assertIs<DecimalType>(typeOf(root, "id + 1.5 > 0"))
    }

    @Test
    fun `concatenar da TEXT`(@TempDir root: File) {
        assertEquals(TextType, typeOf(root, "name || city > 'a'"))
    }

    @Test
    fun `una comparacion da BOOLEAN`(@TempDir root: File) {
        assertEquals(BooleanType, whereType(root, "id > 3"))
    }

    @Test
    fun `IS NULL acepta cualquier tipo y da BOOLEAN`(@TempDir root: File) {
        assertEquals(BooleanType, whereType(root, "city IS NULL"))
        assertEquals(emptyList(), errorsOf(root, "id IS NOT NULL"))
    }

    @Test
    fun `ningun nodo de una consulta valida queda sin tipo`(@TempDir root: File) {
        healthy(root, users)
        val query = checked(root, "SELECT id + 1 FROM users WHERE name || city = 'ab' AND id > 2;")
            .script.statements.single() as Query

        assertNotEquals(ErrorType, (query.where ?: error("sin WHERE")).type)
        query.selection.forEach { assertNotEquals(ErrorType, (it as SelectExpression).expression.type) }
    }

    // ── Lo que no tiene sentido ────────────────────────────────────────────

    @Test
    fun `sumar un numero con un texto reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar + entre INT y VARCHAR(1)"),
            errorsOf(root, "1 + 'a' > 0")
        )
    }

    // ErrorType se propaga sin volver a reportar: de otro modo un error se
    // multiplicaria por cada operador que lo contiene.
    @Test
    fun `un error adentro no se reporta de nuevo afuera`(@TempDir root: File) {
        assertEquals(1, errorsOf(root, "(1 + 'a') * 2 > 0").size)
    }

    @Test
    fun `comparar familias distintas reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar > entre DATE y TIME"),
            errorsOf(root, "DATE '2026-01-01' > TIME '10:00:00'")
        )
    }

    @Test
    fun `el orden de BOOLEAN no significa nada`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar < entre BOOLEAN y BOOLEAN"),
            errorsOf(root, "TRUE < FALSE")
        )
    }

    @Test
    fun `concatenar un numero reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar || entre VARCHAR(80) y INT"),
            errorsOf(root, "name || id = 'a'")
        )
    }

    @Test
    fun `AND sobre algo que no es logico reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede aplicar AND entre INT y BOOLEAN"),
            errorsOf(root, "id AND TRUE")
        )
    }

    @Test
    fun `negar un texto reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede negar VARCHAR(80), se esperaba un tipo numerico"),
            errorsOf(root, "-name = 'a'")
        )
    }

    @Test
    fun `NOT sobre algo que no es logico reporta`(@TempDir root: File) {
        assertEquals(listOf("NOT espera BOOLEAN, recibio INT"), errorsOf(root, "NOT id"))
    }

    @Test
    fun `un elemento de un IN que no se puede comparar reporta`(@TempDir root: File) {
        assertEquals(
            listOf("no se puede comparar INT con VARCHAR(4)"),
            errorsOf(root, "id IN (1, 2, 'tres')")
        )
    }

    // Una columna que no se resolvio llega con ErrorType, y eso corta la cascada
    // en vez de encadenar un error por cada operador.
    @Test
    fun `una columna inexistente no genera errores de tipo encima`(@TempDir root: File) {
        healthy(root, users)
        val errors = checked(root, "SELECT 1 FROM users WHERE fantasma + 1 > 0;").errors

        assertEquals(listOf("no existe la columna 'fantasma'"), errors)
    }
}
