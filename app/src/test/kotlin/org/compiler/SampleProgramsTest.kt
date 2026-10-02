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
        val groups = SamplePrograms.grouped()

        assertEquals(
            listOf(SampleGroup.STARTER, SampleGroup.VALID, SampleGroup.INVALID),
            groups.keys.toList()
        )
        assertEquals(2, groups.getValue(SampleGroup.STARTER).size)
        assertTrue(groups.getValue(SampleGroup.VALID).isNotEmpty())
        assertTrue(groups.getValue(SampleGroup.INVALID).isNotEmpty())
    }

    @Test
    fun `la opcion en blanco no trae codigo`() {
        val blank = SamplePrograms.byId(SamplePrograms.BLANK_ID)

        assertNotNull(blank)
        assertEquals("", blank.source)
    }

    @Test
    fun `el programa por defecto es la demostracion y compila limpio`() {
        val defaultSample = SamplePrograms.default

        assertEquals(SamplePrograms.DEFAULT_ID, defaultSample.id)
        assertTrue(CompilerPipeline.compile(defaultSample.source).errors.isEmpty())
    }

    // El nombre sale de la anotacion `// NOMBRE:` del propio archivo, no del nombre
    // del archivo: sin esto el menu diria "tipos aritmetica" en vez de "Tipos:
    // aritmética".
    @Test
    fun `cada programa declara su nombre legible`() {
        val unnamed = SamplePrograms.all
            .filter { it.group != SampleGroup.STARTER }
            .filterNot { it.source.startsWith("// NOMBRE:") }

        assertTrue(unnamed.isEmpty(), "sin anotación // NOMBRE: ${unnamed.map { it.id }}")
    }

    @Test
    fun `no hay nombres repetidos en el menu`() {
        val names = SamplePrograms.all.map { it.name }

        assertEquals(names.size, names.toSet().size, "nombres duplicados en $names")
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
