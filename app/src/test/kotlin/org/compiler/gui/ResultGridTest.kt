// Ticket 9.1: la rejilla como datos.
//
// Se prueba lo que DECIDE que se ve —el texto de cada celda, si es un nulo, de que
// lado se alinea y cuanto mide cada columna— sin levantar una ventana. El
// Composable solo pinta esto.
package org.compiler.gui

import org.compiler.engine.StatementOutcome
import org.compiler.engine.database
import org.compiler.engine.labelOf
import org.compiler.engine.pipeline
import org.compiler.engine.query
import org.compiler.engine.usersTable
import org.compiler.engine.write
import org.compiler.gui.components.MAX_CELL_CHARS
import org.compiler.gui.components.gridOf
import org.compiler.gui.components.measuredText
import org.compiler.gui.components.rowCountOf
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResultGridTest {

    private fun gridFor(root: File, sql: String) = gridOf(query(root, sql).result)

    private fun outcomesOf(root: File, sql: String): List<StatementOutcome> =
        assertNotNull(pipeline(root, sql, write = false).execution).outcomes

    // ── Las columnas ───────────────────────────────────────────────────────

    @Test
    fun `un SELECT de tres columnas trae sus nombres y sus tipos`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT id, name, age FROM users;")

        assertEquals(listOf("id", "name", "age"), grid.columns.map { it.name })
        assertEquals(listOf("INT", "VARCHAR(80)", "INT"), grid.columns.map { it.type })
        assertEquals(4, grid.rows.size)
    }

    // Los numeros van a la derecha para que las unidades queden una debajo de otra.
    @Test
    fun `los tipos numericos se alinean a la derecha y el resto a la izquierda`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT id, name, age FROM users;")

        assertEquals(listOf(true, false, true), grid.columns.map { it.numeric })
    }

    // Una expresion sin AS no tiene nombre: la rejilla igual necesita un encabezado.
    @Test
    fun `una columna sin nombre se titula por su posicion`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT id + 1 FROM users;")

        assertEquals(listOf("columna 1"), grid.columns.map { it.name })
    }

    // ── Las celdas ─────────────────────────────────────────────────────────

    @Test
    fun `cada fila trae el texto de sus celdas`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT id, name FROM users WHERE id = 1;")

        assertEquals(listOf(listOf("1", "Ana")), grid.rows.map { row -> row.map { it.text } })
    }

    /**
     * La distincion que da sentido al ticket.
     *
     * Si la rejilla mostrara el nulo y la cadena vacia igual, se perderia la misma
     * diferencia que el CSV guarda con el campo vacio contra `""`. El nulo lleva su
     * bandera, y con ella se pinta en gris y en cursiva.
     */
    @Test
    fun `un NULL se distingue de la cadena vacia`(@TempDir root: File) {
        write(root, usersTable, listOf(listOf("1", "Ana", null, "30"), listOf("2", "Beto", "", "40")))
        val grid = gridFor(root, "SELECT city FROM users;")

        val nulo = grid.rows[0].single()
        val vacia = grid.rows[1].single()

        assertTrue(nulo.isNull)
        assertEquals("NULL", nulo.text)
        assertFalse(vacia.isNull, "la cadena vacia no es un nulo")
        assertEquals("", vacia.text)
    }

    // ── El ancho de las columnas ───────────────────────────────────────────

    /**
     * El ancho sale del texto MAS ANCHO de la columna, encabezado incluido.
     *
     * Se devuelve el texto y no un numero de pixeles: quien dibuja lo mide con la
     * fuente real, que es lo unico que sobrevive a otra densidad de pantalla.
     */
    @Test
    fun `el ancho de una columna es su texto mas largo`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT name FROM users;")

        // "VARCHAR(80)" del encabezado es mas largo que cualquier nombre.
        assertEquals("VARCHAR(80)", grid.widest.single())
    }

    @Test
    fun `una celda mas larga que el encabezado manda`(@TempDir root: File) {
        write(root, usersTable, listOf(listOf("1", "Maria Fernanda de los Angeles", "GT", "30")))
        val grid = gridFor(root, "SELECT name FROM users;")

        assertEquals("Maria Fernanda de los Angeles", grid.widest.single())
    }

    // El tope se aplica al MEDIR: asi un TEXT larguisimo no empuja a las demas
    // columnas fuera de la pantalla. La celda conserva su texto completo y quien
    // dibuja lo recorta con elipsis.
    @Test
    fun `el ancho se topa pero la celda guarda su texto completo`(@TempDir root: File) {
        val largo = "x".repeat(MAX_CELL_CHARS + 20)
        write(root, usersTable, listOf(listOf("1", largo, "GT", "30")))
        val grid = gridFor(root, "SELECT name FROM users;")

        assertEquals(largo, grid.rows.single().single().text)
        assertEquals(MAX_CELL_CHARS, measuredText(grid.widest.single()).length)
    }

    @Test
    fun `un texto corto se mide completo`() {
        assertEquals("Ana", measuredText("Ana"))
    }

    // ── Una rejilla por sentencia ──────────────────────────────────────────

    @Test
    fun `tres SELECT dejan tres resultados numerados`(@TempDir root: File) {
        database(root)
        val outcomes = outcomesOf(
            root,
            "SELECT id FROM users;\nSELECT name FROM users;\nSELECT city FROM users;"
        )

        assertEquals(listOf(1, 2, 3), outcomes.map { it.number })
        assertTrue(outcomes.all { it is StatementOutcome.Rows })
        assertEquals(listOf("SELECT", "SELECT", "SELECT"), outcomes.map { it.label })
    }

    /**
     * Lo que no consulta no deja rejilla sino una linea que dice que cambio.
     *
     * Las cuentas salen del escritor, que es el unico que sabe cuantas filas
     * entraron o cambiaron en el momento de aplicarlas.
     */
    @Test
    fun `las sentencias que escriben dejan su linea de resumen`(@TempDir root: File) {
        database(root)
        val outcomes = outcomesOf(
            root,
            "INSERT INTO users VALUES (9, 'Nuevo', 'GT', 20);\n" +
                "UPDATE users SET city = 'MX' WHERE id = 1;\n" +
                "DELETE FROM users WHERE id = 99;\n" +
                "CREATE TABLE eventos (id INT);"
        )

        assertEquals(
            listOf(
                "INSERT INTO users" to "1 row insertada",
                "UPDATE users" to "1 row modificada",
                "DELETE FROM users" to "0 rows borradas",
                "CREATE TABLE eventos" to "table creada"
            ),
            outcomes.map { it.label to assertIs<StatementOutcome.Written>(it).detail }
        )
    }

    @Test
    fun `el resumen concuerda en numero`(@TempDir root: File) {
        database(root)
        val outcomes = outcomesOf(
            root,
            "INSERT INTO users VALUES (8, 'Uno', 'GT', 20), (9, 'Dos', 'GT', 21);\n" +
                "UPDATE users SET city = 'MX' WHERE city = 'GT';"
        )

        assertEquals(
            listOf("2 rows insertadas", "4 rows modificadas"),
            outcomes.map { assertIs<StatementOutcome.Written>(it).detail }
        )
    }

    // Una escritura que falla no reporta: decir "2 rows insertadas" de algo que no
    // va a llegar al disco seria mentir.
    @Test
    fun `una sentencia que falla no deja resumen`(@TempDir root: File) {
        database(root)
        val result = pipeline(
            root,
            "INSERT INTO users VALUES (9, 'Nuevo', 'GT', 20);\n" +
                "INSERT INTO users VALUES (1, 'Repetido', 'GT', 20);",
            write = false
        )

        assertEquals(1, assertNotNull(result.execution).outcomes.size, "solo la primera")
        assertTrue(result.hasErrors)
    }

    // Una consulta SI deja su rejilla aunque haya avisado de algo: devolvio filas.
    @Test
    fun `una consulta que reporta en ejecucion igual deja su rejilla`(@TempDir root: File) {
        database(root)
        val outcomes = outcomesOf(root, "SELECT age / 0 FROM users WHERE id = 1;")

        assertEquals(1, outcomes.size)
        val grid = gridOf(assertIs<StatementOutcome.Rows>(outcomes.single()).result)
        assertEquals("NULL", grid.rows.single().single().text)
    }

    // ── Detalles de la cabecera ────────────────────────────────────────────

    @Test
    fun `el conteo de filas se escribe en singular cuando es una`() {
        assertEquals("1 row", rowCountOf(1))
        assertEquals("0 rows", rowCountOf(0))
        assertEquals("12 rows", rowCountOf(12))
    }

    // El nombre sale del AST y no del texto: el script puede venir en minusculas o
    // partido en varias lineas.
    @Test
    fun `la etiqueta de una sentencia sale del AST`(@TempDir root: File) {
        database(root)
        val script = assertNotNull(pipeline(root, "insert into users\n  values (9, 'x', 'GT', 1);", write = false).ast)

        assertEquals("INSERT INTO users", labelOf(script.statements.single()))
    }

    @Test
    fun `un SELECT sin filas deja una rejilla vacia`(@TempDir root: File) {
        database(root)
        val grid = gridFor(root, "SELECT id FROM users WHERE id = 999;")

        assertTrue(grid.isEmpty)
        assertEquals(listOf("id"), grid.columns.map { it.name })
    }
}
