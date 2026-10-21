// Ticket 9.4: el selector de scripts.
package org.compiler.gui

import org.compiler.samples.SqlScripts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlScriptsTest {

    /**
     * La opcion fija siempre esta, incluso con la carpeta vacia.
     *
     * No es un caso raro: la carpeta nace vacia y el selector tiene que servir
     * igual desde el primer dia.
     */
    @Test
    fun `empezar en blanco siempre esta y no trae texto`() {
        val blanco = assertNotNull(SqlScripts.byId(SqlScripts.BLANK_ID))

        assertEquals("Empezar en blanco", blanco.name)
        assertEquals("", blanco.source)
        assertEquals(SqlScripts.BLANK_ID, SqlScripts.all.first().id, "va primero en el menu")
    }

    // El nombre legible lo declara el propio archivo: agregar un .sql lo incorpora
    // sin tocar codigo.
    @Test
    fun `un script lleva el nombre que declara su anotacion`() {
        val conNombre = SqlScripts.all.filter { it.id != SqlScripts.BLANK_ID }

        conNombre.forEach { script ->
            assertTrue(script.name.isNotBlank(), "${script.id} sin nombre")
            assertTrue(script.source.isNotBlank(), "${script.id} sin contenido")
        }
    }

    // Con la carpeta vacia el .sql de la demo no existe y `default` cae en la hoja
    // en blanco: la ventana tiene que abrir igual.
    @Test
    fun `el script por defecto existe siempre`() {
        val porDefecto = SqlScripts.default

        assertTrue(SqlScripts.all.contains(porDefecto))
        if (SqlScripts.byId(SqlScripts.DEFAULT_ID) != null) {
            assertEquals(SqlScripts.DEFAULT_ID, porDefecto.id)
        } else {
            assertEquals(SqlScripts.BLANK_ID, porDefecto.id)
        }
    }

    @Test
    fun `los identificadores no se repiten`() {
        val ids = SqlScripts.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `un identificador que no existe devuelve null`() {
        assertEquals(null, SqlScripts.byId("no-existe"))
    }
}
