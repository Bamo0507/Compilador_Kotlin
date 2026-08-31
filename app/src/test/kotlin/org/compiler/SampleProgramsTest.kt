package org.compiler

import org.compiler.runtime.CompilerPipeline
import org.compiler.samples.SampleGroup
import org.compiler.samples.SamplePrograms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El selector de programas del IDE.
 *
 * Lo que se prueba es que el menu y la bateria de pruebas lean la MISMA fuente: si
 * se desincronizaran, el IDE mostraria ejemplos que nadie verifica.
 */
class SampleProgramsTest {

    @Test
    fun `estan los dos puntos de partida y los dos grupos de la bateria`() {
        val grupos = SamplePrograms.grouped()

        assertEquals(
            listOf(SampleGroup.STARTER, SampleGroup.VALID, SampleGroup.INVALID),
            grupos.keys.toList()
        )
        assertEquals(2, grupos.getValue(SampleGroup.STARTER).size)
        assertTrue(grupos.getValue(SampleGroup.VALID).isNotEmpty())
        assertTrue(grupos.getValue(SampleGroup.INVALID).isNotEmpty())
    }

    @Test
    fun `la opcion en blanco no trae codigo`() {
        val enBlanco = SamplePrograms.byId(SamplePrograms.BLANK_ID)

        assertNotNull(enBlanco)
        assertEquals("", enBlanco.source)
    }

    @Test
    fun `el programa por defecto es la demostracion y compila limpio`() {
        val porDefecto = SamplePrograms.default

        assertEquals(SamplePrograms.DEFAULT_ID, porDefecto.id)
        assertTrue(CompilerPipeline.compile(porDefecto.source).errors.isEmpty())
    }

    // El nombre sale de la anotacion `// NOMBRE:` del propio archivo, no del nombre
    // del archivo: sin esto el menu diria "tipos aritmetica" en vez de "Tipos:
    // aritmética".
    @Test
    fun `cada programa declara su nombre legible`() {
        val sinNombre = SamplePrograms.all
            .filter { it.group != SampleGroup.STARTER }
            .filterNot { it.source.startsWith("// NOMBRE:") }

        assertTrue(sinNombre.isEmpty(), "sin anotación // NOMBRE: ${sinNombre.map { it.id }}")
    }

    @Test
    fun `no hay nombres repetidos en el menu`() {
        val nombres = SamplePrograms.all.map { it.name }

        assertEquals(nombres.size, nombres.toSet().size, "nombres duplicados en $nombres")
    }

    // El punto del selector: cargar un ejemplo y darle a compilar tiene que
    // reproducir lo que verifica su prueba de la bateria.
    @Test
    fun `los validos compilan y los invalidos no`() {
        SamplePrograms.all.filter { it.group == SampleGroup.VALID }.forEach {
            assertTrue(
                CompilerPipeline.compile(it.source).errors.isEmpty(),
                "${it.id} debería compilar limpio"
            )
        }

        SamplePrograms.all.filter { it.group == SampleGroup.INVALID }.forEach {
            assertTrue(
                CompilerPipeline.compile(it.source).hasErrors,
                "${it.id} debería producir errores"
            )
        }
    }
}
