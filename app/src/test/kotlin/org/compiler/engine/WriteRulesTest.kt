package org.compiler.engine

import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que se revisa antes de aceptar una fila.
 *
 * Es lo que de verdad protege la base, porque el DBMS confia en lo que el mismo
 * escribio: valida lo que entra, no lo que ya esta guardado.
 */
class WriteRulesTest {

    private fun base(root: File) {
        database(root)
        write(root, notesTable, emptyList())
    }

    private fun errorsOf(root: File, sql: String): List<String> {
        base(root)
        return runScript(root, sql).errors
    }

    // ── El valor por omision y la clave que se genera sola ─────────────────

    @Test
    fun `una columna omitida toma su valor por omision`(@TempDir root: File) {
        base(root)
        val rows = runScript(root, "INSERT INTO notes (score) VALUES (1.5);").rowsOf("notes")

        assertEquals(StringValue("sin texto"), rows.single().values[1])
    }

    @Test
    fun `una columna omitida sin valor por omision queda en nulo`(@TempDir root: File) {
        base(root)
        val rows = runScript(root, "INSERT INTO notes (label) VALUES ('hola');").rowsOf("notes")

        assertEquals(NullValue, rows.single().values[2])
    }

    @Test
    fun `sobre tabla vacia el AUTOINCREMENT arranca en uno`(@TempDir root: File) {
        base(root)
        val rows = runScript(root, "INSERT INTO notes (label) VALUES ('hola');").rowsOf("notes")

        assertEquals(IntValue(1), rows.single().values[0])
    }

    // El siguiente es el maximo actual mas uno, sin guardar estado en ningun lado.
    @Test
    fun `el AUTOINCREMENT sigue desde el mayor que ya hay`(@TempDir root: File) {
        base(root)
        val rows = runScript(
            root,
            "INSERT INTO notes (label) VALUES ('una'); INSERT INTO notes (label) VALUES ('otra');"
        ).rowsOf("notes")

        assertEquals(listOf(IntValue(1), IntValue(2)), rows.map { it.values[0] })
    }

    // El valor generado tiene que pasar por la revision de la clave primaria, y por
    // eso se asigna ANTES: si se revisara primero, no tendria que revisar.
    @Test
    fun `el valor generado respeta la clave primaria`(@TempDir root: File) {
        base(root)
        val rows = runScript(
            root,
            "INSERT INTO notes (id, label) VALUES (7, 'puesta'); INSERT INTO notes (label) VALUES ('generada');"
        ).rowsOf("notes")

        assertEquals(listOf(IntValue(7), IntValue(8)), rows.map { it.values[0] })
    }

    // ── Unicidad ───────────────────────────────────────────────────────────

    @Test
    fun `una clave primaria repetida reporta con el valor`(@TempDir root: File) {
        assertEquals(
            listOf("ya existe una fila con id = 1 en 'users'"),
            errorsOf(root, "INSERT INTO users (id, name) VALUES (1, 'Otra');")
        )
    }

    // Aunque ninguna de las dos este todavia en disco: las dos estan en memoria.
    @Test
    fun `una clave repetida dentro del mismo script reporta`(@TempDir root: File) {
        assertEquals(
            listOf("ya existe una fila con id = 9 en 'users'"),
            errorsOf(
                root,
                "INSERT INTO users (id, name) VALUES (9, 'Una'); INSERT INTO users (id, name) VALUES (9, 'Otra');"
            )
        )
    }

    // ── Llaves foraneas ────────────────────────────────────────────────────

    @Test
    fun `una llave foranea hacia un valor que no existe reporta`(@TempDir root: File) {
        assertEquals(
            listOf("posts.uid = 99 no existe en users.id"),
            errorsOf(root, "INSERT INTO posts (uid, title) VALUES (99, 'Huerfano');")
        )
    }

    @Test
    fun `una llave foranea hacia un valor que si existe pasa`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "INSERT INTO posts (uid, title) VALUES (2, 'Nuevo');"))
    }

    // Un nulo no referencia a nadie, asi que no hay nada que verificar.
    @Test
    fun `una llave foranea en nulo no se verifica`(@TempDir root: File) {
        assertEquals(emptyList(), errorsOf(root, "INSERT INTO posts (title) VALUES ('Suelto');"))
    }

    // ── Nulos ──────────────────────────────────────────────────────────────

    /**
     * La fase 5 atrapa un NULL escrito. Este llega de una subconsulta que no
     * devolvio filas, y eso no se podia saber sin los datos.
     */
    @Test
    fun `un nulo que aparece al ejecutar reporta`(@TempDir root: File) {
        assertEquals(
            listOf("la columna 'name' no acepta nulos"),
            errorsOf(root, "INSERT INTO users (id, name) VALUES (9, (SELECT name FROM users WHERE id = 99));")
        )
    }

    // ── El largo y la escala ───────────────────────────────────────────────

    /**
     * La fase 5 revisa el largo solo sobre literales, porque una concatenacion no
     * tiene largo conocido hasta evaluarla.
     */
    @Test
    fun `un texto calculado que no cabe reporta`(@TempDir root: File) {
        base(root)
        val errors = runScript(root, "INSERT INTO notes (label) VALUES ('doce chars ' || 'doce chars ');").errors

        assertEquals(listOf("'doce chars doce chars ' no cabe en VARCHAR(20)"), errors)
    }

    // Los decimales de mas se redondean, que es lo que hace un DBMS al insertar.
    @Test
    fun `un decimal con mas escala se redondea`(@TempDir root: File) {
        base(root)
        val rows = runScript(root, "INSERT INTO notes (label, score) VALUES ('x', 1.567);").rowsOf("notes")

        assertEquals("1.57", rows.single().values[2].display())
    }

    // La parte entera no se recorta: ahi el numero no cabe y no hay nada que hacer.
    @Test
    fun `un decimal con parte entera de mas reporta`(@TempDir root: File) {
        base(root)
        val errors = runScript(root, "INSERT INTO notes (label, score) VALUES ('x', 12345.6);").errors

        assertEquals(listOf("12345.6 no cabe en DECIMAL(5,2)"), errors)
    }

    // CHAR se rellena al entrar, igual que al leer: el largo lo dice el tipo.
    @Test
    fun `un CHAR se rellena hasta su largo`(@TempDir root: File) {
        base(root)
        write(root, samplesTable, emptyList())
        val rows = runScript(root, "INSERT INTO samples (fixed) VALUES ('a');").rowsOf("samples")

        assertEquals(StringValue("a  "), rows.single().values[3])
    }
}
