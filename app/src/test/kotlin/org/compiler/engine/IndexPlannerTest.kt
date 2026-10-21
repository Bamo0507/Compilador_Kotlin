// Ticket 8.6: elegir el indice, leer por salto y reconstruir al volcar.
//
// La prueba de que el indice se USO no es el resultado, que es el mismo con y sin
// el: es cuantas filas se leyeron. De ahi que la sesion lleve las dos cuentas.
package org.compiler.engine

import org.compiler.catalog.Column
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Table
import org.compiler.storage.DataDirectory
import org.compiler.storage.IndexFile
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndexPlannerTest {

    // id es PRIMARY KEY, asi que carros nace con pk_carros sin que nadie lo cree.
    private val carros = Table(
        "carros",
        listOf(
            Column("id", IntType, listOf(PrimaryKey)),
            Column("marca", VarcharType(20), emptyList()),
            Column("anio", IntType, emptyList())
        )
    )

    // La misma tabla sin ninguna restriccion: es la que no tiene ningun indice, ni
    // siquiera automatico. Sirve para comparar el resultado con y sin indices.
    private val carrosSinIndices = Table(
        "carros",
        listOf(
            Column("id", IntType, emptyList()),
            Column("marca", VarcharType(20), emptyList()),
            Column("anio", IntType, emptyList())
        )
    )

    private val filas = listOf(
        listOf("1", "Toyota", "2020"),
        listOf("2", "Mazda", "2019"),
        listOf("3", "Toyota", "2021"),
        listOf("4", "Mazda", "2022"),
        listOf("5", "Toyota", "2018")
    )

    /**
     * La base con sus indices ya escritos.
     *
     * Se crea con un script y no a mano: asi los .idx salen del mismo camino que
     * los crea de verdad, y un desplazamiento mal calculado se veria aqui.
     */
    private fun base(root: File) {
        write(root, carros, emptyList())
        val inserts = filas.joinToString("\n") { "INSERT INTO carros VALUES (${it[0]}, '${it[1]}', ${it[2]});" }
        pipeline(root, "$inserts\nCREATE INDEX idx_marca ON carros(marca);\nCREATE INDEX idx_anio ON carros(anio);")
    }

    private fun ids(outcome: Outcome): List<String> = outcome.grid.map { it[0] }

    // ── El indice se usa ───────────────────────────────────────────────────

    /**
     * El objetivo de la fase: una tabla de cinco filas de la que se lee UNA.
     *
     * `reads` cuenta cargas completas de tabla y `indexedReads` filas traidas por
     * desplazamiento. Que la primera sea 0 es lo que prueba que el archivo no se
     * recorrio.
     */
    @Test
    fun `una igualdad sobre la clave primaria lee una sola fila`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE id = 3;")

        assertEquals(listOf("3"), ids(outcome))
        assertEquals(0, outcome.session.reads, "no deberia cargarse la tabla entera")
        assertEquals(1, outcome.session.indexedReads)
    }

    // La version grande del mismo test: mil filas en el archivo, una leida.
    @Test
    fun `sobre mil filas, una igualdad por clave primaria lee una`(@TempDir root: File) {
        write(root, carros, (1..1000).map { listOf("$it", "Marca $it", "${2000 + it % 25}") })

        val outcome = query(root, "SELECT * FROM carros WHERE id = 777;")

        assertEquals(listOf("777"), ids(outcome))
        assertEquals(0, outcome.session.reads)
        assertEquals(1, outcome.session.indexedReads)
    }

    @Test
    fun `una igualdad sobre una columna repetida trae solo sus filas`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Toyota';")

        assertEquals(listOf("1", "3", "5"), ids(outcome))
        assertEquals(0, outcome.session.reads)
        assertEquals(3, outcome.session.indexedReads)
    }

    /**
     * El rango es lo que justifica el arbol sobre una tabla hash: un HashMap solo
     * contesta la igualdad, y el enunciado habla de condiciones de WHERE.
     */
    @Test
    fun `un mayor que usa el indice y devuelve el rango correcto`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE anio > 2020;")

        assertEquals(listOf("3", "4"), ids(outcome))
        assertEquals(0, outcome.session.reads)
        assertEquals(2, outcome.session.indexedReads)
    }

    @Test
    fun `un rango cerrado lee solo los dos extremos y lo de en medio`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE anio >= 2019 AND anio <= 2020;")

        assertEquals(listOf("1", "2"), ids(outcome))
        assertEquals(0, outcome.session.reads)
    }

    // Un IN son varias igualdades: una busqueda en el arbol por cada valor.
    @Test
    fun `un IN sobre una columna indexada usa el indice`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE marca IN ('Mazda');")

        assertEquals(listOf("2", "4"), ids(outcome))
        assertEquals(0, outcome.session.reads)
        assertEquals(2, outcome.session.indexedReads)
    }

    // Toda fila del resultado cumple las dos condiciones, asi que basta con una
    // para decidir que filas leer; la otra se evalua normal sobre esas.
    @Test
    fun `con un AND basta una condicion indexada`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Mazda' AND anio > 2020;")

        assertEquals(listOf("4"), ids(outcome))
        assertEquals(0, outcome.session.reads)
    }

    // ── Cuando NO se usa ───────────────────────────────────────────────────

    /**
     * Un OR puede traer filas que el indice no señala: las de la otra rama.
     */
    @Test
    fun `un OR no usa indice`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Toyota' OR anio > 2021;")

        assertEquals(listOf("1", "3", "4", "5"), ids(outcome))
        assertEquals(1, outcome.session.reads, "deberia leer la tabla entera")
        assertEquals(0, outcome.session.indexedReads)
    }

    @Test
    fun `una columna sin indice no dispara lecturas por salto`(@TempDir root: File) {
        write(root, carrosSinIndices, filas)
        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Toyota';")

        assertEquals(listOf("1", "3", "5"), ids(outcome))
        assertEquals(0, outcome.session.indexedReads)
    }

    // Cargarla otra vez por saltos seria mas lento, no menos.
    @Test
    fun `una tabla ya cargada se filtra en memoria`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros;\nSELECT * FROM carros WHERE id = 3;")

        assertEquals(listOf("3"), outcome.results[1].rows.map { it.values[0].display() })
        assertEquals(1, outcome.session.reads)
        assertEquals(0, outcome.session.indexedReads, "ya estaba en memoria")
    }

    // Sin WHERE no hay nada que buscar en el arbol.
    @Test
    fun `sin WHERE se lee la tabla entera`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros;")

        assertEquals(5, outcome.result.rows.size)
        assertEquals(0, outcome.session.indexedReads)
    }

    // El camino del indice NO puebla el cache: sus filas son una parte de la
    // tabla, y marcarla como cargada haria que la consulta siguiente viera solo
    // esas.
    @Test
    fun `leer por indice no deja la tabla a medias en memoria`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE id = 3;\nSELECT * FROM carros;")

        assertEquals(5, outcome.results[1].rows.size)
    }

    // ── El resultado es el mismo ───────────────────────────────────────────

    /**
     * La garantia de fondo: el indice decide que filas se LEEN, no cuales pasan.
     * El WHERE se evalua igual sobre las que vuelven.
     */
    @Test
    fun `el resultado es identico con indice y sin el`(@TempDir root: File) {
        val consultas = listOf(
            "SELECT * FROM carros WHERE marca = 'Toyota';",
            "SELECT * FROM carros WHERE anio > 2020;",
            "SELECT * FROM carros WHERE id >= 3 AND marca = 'Toyota';",
            "SELECT * FROM carros WHERE marca <> 'Mazda';",
            "SELECT * FROM carros ORDER BY anio DESC;"
        )

        consultas.forEach { sql ->
            val conIndice = File(root, "con").also { it.mkdirs() }
            val sinIndice = File(root, "sin").also { it.mkdirs() }
            base(conIndice)
            write(sinIndice, carrosSinIndices, filas)

            assertEquals(query(sinIndice, sql).grid, query(conIndice, sql).grid, sql)
            conIndice.deleteRecursively()
            sinIndice.deleteRecursively()
        }
    }

    // ── La reconstruccion ──────────────────────────────────────────────────

    /**
     * Un DELETE reescribe el archivo entero, asi que TODOS los desplazamientos
     * cambian. Por eso los indices se reconstruyen en ese mismo recorrido en vez
     * de actualizarse de a poco: no hay nada que salvar.
     */
    @Test
    fun `despues de un DELETE los desplazamientos siguen apuntando bien`(@TempDir root: File) {
        base(root)
        pipeline(root, "DELETE FROM carros WHERE id = 1;")

        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Toyota';")
        assertEquals(listOf("3", "5"), ids(outcome))
        assertEquals(0, outcome.session.reads)
    }

    @Test
    fun `despues de un INSERT la fila nueva esta en el indice`(@TempDir root: File) {
        base(root)
        pipeline(root, "INSERT INTO carros VALUES (6, 'Honda', 2023);")

        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Honda';")
        assertEquals(listOf("6"), ids(outcome))
        assertEquals(0, outcome.session.reads)
    }

    @Test
    fun `despues de un UPDATE el indice sigue el valor nuevo`(@TempDir root: File) {
        base(root)
        pipeline(root, "UPDATE carros SET marca = 'Honda' WHERE id = 2;")

        assertEquals(listOf("2"), ids(query(root, "SELECT * FROM carros WHERE marca = 'Honda';")))
        assertEquals(listOf("4"), ids(query(root, "SELECT * FROM carros WHERE marca = 'Mazda';")))
    }

    // ── La clave foranea ───────────────────────────────────────────────────

    /**
     * Una columna referida es unica por la regla de la fase 5, asi que siempre
     * tiene indice. Validar la FK pasa de cargar la tabla referida a una busqueda
     * en el arbol.
     */
    @Test
    fun `validar una clave foranea no carga la tabla referida`(@TempDir root: File) {
        base(root)
        write(
            root,
            Table("multas", listOf(
                Column("carro", IntType, listOf(ForeignKey("carros", "id"))),
                Column("monto", IntType, emptyList())
            )),
            emptyList()
        )

        val outcome = runScript(root, "INSERT INTO multas VALUES (3, 100);")

        assertEquals(emptyList(), outcome.failures)
        assertFalse(outcome.session.isLoaded("carros"), "carros no deberia cargarse entera")
    }

    @Test
    fun `una clave foranea que no existe se reporta igual`(@TempDir root: File) {
        base(root)
        write(
            root,
            Table("multas", listOf(
                Column("carro", IntType, listOf(ForeignKey("carros", "id"))),
                Column("monto", IntType, emptyList())
            )),
            emptyList()
        )

        val outcome = runScript(root, "INSERT INTO multas VALUES (99, 100);")

        assertEquals(1, outcome.failures.size, outcome.failures.toString())
    }

    /**
     * Una fila insertada en este mismo script todavia no esta en el .idx: los
     * desplazamientos se asignan al volcar. Pero entonces la tabla SI esta en
     * memoria, y por eso la validacion cae al camino de siempre.
     */
    @Test
    fun `una fila insertada en el mismo script sirve de clave foranea`(@TempDir root: File) {
        base(root)
        write(
            root,
            Table("multas", listOf(
                Column("carro", IntType, listOf(ForeignKey("carros", "id"))),
                Column("monto", IntType, emptyList())
            )),
            emptyList()
        )

        val outcome = runScript(
            root,
            "INSERT INTO carros VALUES (9, 'Kia', 2024);\nINSERT INTO multas VALUES (9, 50);"
        )

        assertEquals(emptyList(), outcome.failures)
    }

    // ── Los archivos ───────────────────────────────────────────────────────

    @Test
    fun `CREATE TABLE escribe el idx de sus indices automaticos`(@TempDir root: File) {
        pipeline(root, "CREATE TABLE autos (id INT PRIMARY KEY, placa VARCHAR(10) UNIQUE);")

        assertEquals(listOf("pk_autos", "uq_autos_placa"), DataDirectory(root).indexFiles())
    }

    @Test
    fun `DROP INDEX borra su archivo`(@TempDir root: File) {
        base(root)
        pipeline(root, "DROP INDEX idx_marca;")

        assertFalse(DataDirectory(root).index("idx_marca").exists())
        assertTrue(DataDirectory(root).index("pk_carros").exists())
    }

    // Un indice es parte del esquema de su tabla: si la tabla se va, el .idx
    // tambien, sin dejar metadatos huerfanos.
    @Test
    fun `DROP TABLE se lleva los idx de esa tabla`(@TempDir root: File) {
        base(root)
        pipeline(root, "DROP TABLE carros;")

        assertEquals(emptyList(), DataDirectory(root).indexFiles())
        assertEquals(emptyList(), DataDirectory(root).tables())
    }

    // Carga perezosa: el catalogo lee los .json y ningun .idx. Uno se abre solo
    // cuando una consulta lo va a usar.
    @Test
    fun `el catalogo no abre ningun idx`(@TempDir root: File) {
        base(root)
        val outcome = query(root, "SELECT * FROM carros WHERE marca = 'Toyota';")

        // El .idx de anio existe en disco y esta consulta no lo necesita.
        assertTrue(DataDirectory(root).index("idx_anio").exists())
        assertEquals(3, outcome.session.indexedReads, "solo las filas de idx_marca")
    }

    // El .idx queda ordenado por valor: es un CSV que se lee a ojo.
    @Test
    fun `el idx escrito queda ordenado y con los desplazamientos reales`(@TempDir root: File) {
        base(root)
        val file = DataDirectory(root).index("idx_marca")

        assertEquals("value,offset", file.readLines().first())
        val entries = IndexFile.read(file, VarcharType(20))
        assertEquals(listOf("Mazda", "Toyota"), entries.keys.map { it.display() })
        assertEquals(3, assertNotNull(entries.values.last()).size)
    }
}
