// Ticket 9.6: los tokens tal como los ve la interfaz.
package org.compiler

import org.compiler.frontend.syntax.tokensOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenViewTest {

    private fun typesOf(sql: String) = tokensOf(sql).map { it.type }

    private fun lexemesOf(sql: String) = tokensOf(sql).map { it.lexeme }

    @Test
    fun `una consulta simple produce sus cinco tokens`() {
        val tokens = tokensOf("SELECT * FROM users;")

        assertEquals(listOf("SELECT", "*", "FROM", "Identifier", ";"), tokens.map { it.type })
        assertEquals(listOf("SELECT", "*", "FROM", "users", ";"), tokens.map { it.lexeme })
    }

    // Las columnas se cuentan desde 1, como en los mensajes de error: ANTLR las da
    // desde 0.
    @Test
    fun `cada token trae su linea y su columna`() {
        val tokens = tokensOf("SELECT *\nFROM users;")

        assertEquals(listOf(1, 1, 2, 2, 2), tokens.map { it.line })
        assertEquals(listOf(1, 8, 1, 6, 11), tokens.map { it.column })
    }

    /**
     * El detalle que hace util esta vista.
     *
     * El lexer ve el texto en mayusculas, porque asi `select` casa con 'SELECT'.
     * Si la tabla mostrara lo que el lexer ve, estaria mintiendo sobre lo que el
     * usuario tecleo.
     */
    @Test
    fun `el lexema es el original y el tipo el de la palabra clave`() {
        val tokens = tokensOf("select Name from Users;")

        assertEquals(listOf("SELECT", "Identifier", "FROM", "Identifier", ";"), tokens.map { it.type })
        assertEquals(listOf("select", "Name", "from", "Users", ";"), tokens.map { it.lexeme })
    }

    // La gramatica los descarta con `skip`, asi que nunca llegan a ser tokens.
    @Test
    fun `los comentarios y los espacios no aparecen`() {
        assertEquals(
            listOf("SELECT", "IntegerLit", ";"),
            typesOf("-- un comentario\nSELECT   1 ; /* otro */")
        )
    }

    @Test
    fun `los literales conservan su texto completo`() {
        assertEquals(
            listOf("StringLit", "DecimalLit", "IntegerLit"),
            typesOf("'hola' 3.5 42")
        )
        assertEquals(listOf("'hola'", "3.5", "42"), lexemesOf("'hola' 3.5 42"))
    }

    // La vista no corta: muestra lo que si se reconocio. El error ya lo reporto el
    // pipeline, y volver a escribirlo aqui seria ruido.
    @Test
    fun `un error lexico no esconde los demas tokens`() {
        assertEquals(listOf("SELECT", "FROM", "Identifier", ";"), typesOf("SELECT # FROM t;"))
    }

    @Test
    fun `un script vacio no produce tokens`() {
        assertTrue(tokensOf("").isEmpty())
        assertTrue(tokensOf("   \n  -- nada\n").isEmpty())
    }

    // El fin de archivo es una marca del analizador, no algo que el usuario escribio.
    @Test
    fun `el fin de archivo no es un token de la vista`() {
        assertTrue(typesOf("SELECT 1;").none { it == "EOF" })
    }
}
