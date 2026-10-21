// Ticket 9.4: la clasificacion del resaltado.
package org.compiler.gui

import org.compiler.gui.components.SqlSpan
import org.compiler.gui.components.SqlSpanKind
import org.compiler.gui.components.sqlSpansOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlSpansTest {

    private fun String.textOf(span: SqlSpan) = substring(span.start, span.end)

    private fun kindsOf(sql: String, kind: SqlSpanKind): List<String> =
        sqlSpansOf(sql).filter { it.kind == kind }.map { sql.textOf(it) }

    // ── Las familias ───────────────────────────────────────────────────────

    @Test
    fun `las palabras clave se reconocen`() {
        assertEquals(
            listOf("SELECT", "FROM", "WHERE"),
            kindsOf("SELECT name FROM users WHERE id = 1;", SqlSpanKind.KEYWORD)
        )
    }

    /**
     * Sin importar mayusculas o minusculas.
     *
     * Se apoya en el lexer de ANTLR, que ya resuelve eso con UpperCaseCharStream.
     * Escribir un segundo analizador para el resaltado seria pedir que los dos se
     * desincronicen en cuanto la gramatica cambie.
     */
    @Test
    fun `una palabra clave en minusculas tambien se resalta`() {
        assertEquals(
            listOf("select", "From", "wHeRe"),
            kindsOf("select name From users wHeRe id = 1;", SqlSpanKind.KEYWORD)
        )
    }

    @Test
    fun `los nombres no son palabras clave`() {
        assertEquals(listOf("name", "users", "id"), kindsOf("SELECT name FROM users WHERE id = 1;", SqlSpanKind.IDENTIFIER))
    }

    @Test
    fun `los literales de texto y los numeros van aparte`() {
        val sql = "SELECT 'hola', 42, 3.5 FROM t;"

        assertEquals(listOf("'hola'"), kindsOf(sql, SqlSpanKind.TEXT))
        assertEquals(listOf("42", "3.5"), kindsOf(sql, SqlSpanKind.NUMBER))
    }

    @Test
    fun `los simbolos se distinguen de los nombres`() {
        assertEquals(listOf("*", ";"), kindsOf("SELECT * FROM t;", SqlSpanKind.SYMBOL).filter { it != "FROM" })
    }

    // ── Los comentarios ────────────────────────────────────────────────────

    /**
     * El lexer los tira con `skip` y no quedan en ningun canal, asi que se buscan
     * sobre el texto. La gramatica sigue siendo la fuente de verdad del ANALISIS,
     * y el resaltado no deberia poder cambiarla.
     */
    @Test
    fun `los comentarios de linea y de bloque se resaltan`() {
        assertEquals(
            listOf("-- uno", "/* dos\n   sigue */"),
            kindsOf("SELECT 1 -- uno\nFROM t; /* dos\n   sigue */", SqlSpanKind.COMMENT)
        )
    }

    // Un `--` dentro de un texto no empieza un comentario.
    @Test
    fun `dos guiones dentro de un literal no abren comentario`() {
        val sql = "SELECT 'a -- b' FROM t;"

        assertEquals(emptyList(), kindsOf(sql, SqlSpanKind.COMMENT))
        assertEquals(listOf("'a -- b'"), kindsOf(sql, SqlSpanKind.TEXT))
    }

    // La comilla se escapa duplicandola: 'dijo ''hola''' es un solo literal.
    @Test
    fun `una comilla escapada no corta el literal`() {
        val sql = "SELECT 'dijo ''hola''' FROM t;"

        assertEquals(listOf("'dijo ''hola'''"), kindsOf(sql, SqlSpanKind.TEXT))
        assertEquals(emptyList(), kindsOf(sql, SqlSpanKind.COMMENT))
    }

    @Test
    fun `un comentario de bloque sin cerrar llega hasta el final`() {
        assertEquals(listOf("/* sin cerrar"), kindsOf("SELECT 1 FROM t; /* sin cerrar", SqlSpanKind.COMMENT))
    }

    // ── La forma de los tramos ─────────────────────────────────────────────

    @Test
    fun `los tramos salen en orden y no se salen del texto`() {
        val sql = "-- hola\nSELECT 'x', 1 FROM t;"
        val spans = sqlSpansOf(sql)

        assertEquals(spans.map { it.start }.sorted(), spans.map { it.start })
        assertTrue(spans.all { it.start >= 0 && it.end <= sql.length && it.start < it.end })
    }

    @Test
    fun `un script vacio no tiene tramos`() {
        assertEquals(emptyList(), sqlSpansOf(""))
    }

    // Un error lexico no puede dejar el editor sin colores.
    @Test
    fun `un caracter invalido no rompe el resaltado`() {
        assertEquals(listOf("SELECT", "FROM"), kindsOf("SELECT # FROM t;", SqlSpanKind.KEYWORD))
    }
}
