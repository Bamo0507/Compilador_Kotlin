package org.compiler.bateria

import org.compiler.runtime.DbmsPipeline
import org.compiler.storage.DataDirectory
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Los scripts que tienen que correr sin reportar nada.
 *
 * Un caso por archivo, armado al vuelo: agregar un .sql a la carpeta agrega su
 * prueba sin tocar Kotlin, y lo agrega tambien al selector de la interfaz, que
 * lee la misma carpeta.
 *
 * Cada caso corre sobre su propio subdirectorio temporal. Ninguno usa
 * `DataDirectory.DEFAULT`: eso es lo que evita que la bateria toque el `data/`
 * de verdad.
 */
class ScriptsValidosTest {

    @TestFactory
    fun `los scripts validos corren sin reportar nada`(@TempDir temp: File): List<DynamicTest> =
        Bateria.of(Expectation.CLEAN).map { case ->
            DynamicTest.dynamicTest(case.toString()) { run(case, File(temp, case.id)) }
        }

    private fun run(case: ScriptCase, directory: File) {
        directory.mkdirs()
        val before = snapshot(directory)

        val result = DbmsPipeline.run(case.script.source, DataDirectory(directory))

        assertTrue(result.errors.isEmpty(), "${case.id} reporto:\n${describe(result)}")
        assertNotNull(result.execution, "${case.id} no llego a ejecutarse")

        case.rows?.let {
            assertEquals(it, lastGrid(result), "${case.id}: filas del ultimo SELECT")
        }

        // Cada script se limpia solo, asi que el directorio queda como estaba. Es
        // lo que permite correrlos en cualquier orden y repetirlos sin reiniciar
        // nada.
        assertEquals(before, snapshot(directory), "${case.id} dejo archivos atras")
    }

    // Sin esto, un .sql mal anotado se quedaria fuera de las dos baterias y nadie
    // lo notaria.
    @Test
    fun `todos los scripts declaran lo que esperan`() {
        val total = Bateria.cases.size
        val clasificados = Expectation.entries.sumOf { Bateria.of(it).size }

        assertEquals(total, clasificados)
        assertTrue(total > 0, "la carpeta de scripts esta vacia")
    }

    // Un caso que dice "sin errores" y ademas cuenta filas es el que de verdad
    // prueba el motor: sin la cuenta, bastaria con que no reventara.
    @Test
    fun `casi todos los validos cuentan sus filas`() {
        val validos = Bateria.of(Expectation.CLEAN)
        val sinCuenta = validos.filter { it.rows == null }

        assertTrue(sinCuenta.isEmpty(), "sin anotacion FILAS: ${sinCuenta.map { it.id }}")
    }
}
