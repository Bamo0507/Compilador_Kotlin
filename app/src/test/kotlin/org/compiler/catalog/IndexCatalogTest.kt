// Ticket 8.2: el modelo Index, su lugar en el catalogo y en el .json de esquema.
package org.compiler.catalog

import org.compiler.diagnostics.Diagnostics
import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import org.compiler.types.IntType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexCatalogTest {

    private val carros = Table(
        "carros",
        listOf(
            Column("id", IntType, listOf(PrimaryKey)),
            Column("marca", VarcharType(20), emptyList()),
            Column("duenio", VarcharType(30), listOf(Unique))
        )
    )

    private fun write(root: File, table: Table) {
        File(root, "${table.name}.json").writeText(SchemaJson.encode(table))
        CsvWriter.write(File(root, "${table.name}.csv"), table.columns.map { it.name }, emptyList())
    }

    private fun load(root: File): Pair<Catalog, List<String>> {
        val diagnostics = Diagnostics()
        val catalog = CatalogLoader(DataDirectory(root), diagnostics).load()
        return catalog to diagnostics.all().map { it.message }
    }

    // ── Los indices automaticos ────────────────────────────────────────────

    /**
     * PRIMARY KEY y UNIQUE generan su indice solos.
     *
     * Lo que gana no son las escrituras sino las consultas: `WHERE id = 7` usa un
     * indice sin que nadie haya escrito un CREATE INDEX.
     */
    @Test
    fun `PRIMARY KEY y UNIQUE dejan sus dos indices`() {
        val indexes = carros.indexes

        assertEquals(listOf("pk_carros", "uq_carros_duenio"), indexes.map { it.name })
        assertEquals(listOf("id", "duenio"), indexes.map { it.column })
        assertTrue(indexes.all { it.auto }, "los dos nacen del catalogo, no de un CREATE INDEX")
    }

    @Test
    fun `una tabla sin restricciones no genera indices`() {
        val simple = Table("notas", listOf(Column("texto", VarcharType(10), emptyList())))

        assertEquals(emptyList(), simple.indexes)
    }

    // Los automaticos se DERIVAN de las restricciones, no se guardan: no hay forma
    // de construir una tabla con PRIMARY KEY y sin su indice.
    @Test
    fun `una tabla armada a mano ya trae sus indices automaticos`() {
        assertEquals(listOf("pk_carros", "uq_carros_duenio"), carros.indexes.map { it.name })
    }

    // ── La tabla y el catalogo ─────────────────────────────────────────────

    @Test
    fun `indexOn encuentra el indice de una columna y null si no hay`() {
        assertEquals("pk_carros", assertNotNull(carros.indexOn("id")).name)
        assertNull(carros.indexOn("marca"))
    }

    @Test
    fun `addIndex y dropIndex mueven el indice dentro de su tabla`() {
        val catalog = Catalog(mapOf("carros" to carros))
            .addIndex(Index("idx_marca", "carros", "marca", auto = false))

        assertEquals("idx_marca", assertNotNull(catalog.index("idx_marca")).name)
        assertEquals("idx_marca", assertNotNull(catalog.table("carros")).indexOn("marca")?.name)
        assertNull(catalog.dropIndex("idx_marca").index("idx_marca"))
    }

    // Borrar la columna se lleva su indice: no puede quedar uno apuntando a nada.
    @Test
    fun `dropColumn se lleva el indice de esa columna`() {
        val catalog = Catalog(mapOf("carros" to carros))

        assertNull(catalog.dropColumn("carros", "duenio").index("uq_carros_duenio"))
    }

    // ── El .json ───────────────────────────────────────────────────────────

    @Test
    fun `la seccion indexes sobrevive la ida y vuelta del JSON`(@TempDir root: File) {
        write(root, Table("carros", carros.columns, listOf(Index("idx_marca", "carros", "marca", auto = false))))

        val (catalog, errors) = load(root)

        assertEquals(emptyList(), errors)
        assertEquals(
            listOf("pk_carros", "uq_carros_duenio", "idx_marca"),
            assertNotNull(catalog.table("carros")).indexes.map { it.name }
        )
    }

    // Que un indice sea automatico se deduce de su nombre: son los dos prefijos
    // que el catalogo se reserva.
    @Test
    fun `al recargar, los automaticos se reconocen por su nombre`(@TempDir root: File) {
        write(root, Table("carros", carros.columns, listOf(Index("idx_marca", "carros", "marca", auto = false))))

        val indexes = assertNotNull(load(root).first.table("carros")).indexes.associateBy { it.name }

        assertTrue(assertNotNull(indexes["pk_carros"]).auto)
        assertTrue(assertNotNull(indexes["uq_carros_duenio"]).auto)
        assertFalse(assertNotNull(indexes["idx_marca"]).auto)
    }

    // Un objeto vacio seria ruido en un archivo cuyo punto es leerse a ojo.
    @Test
    fun `una tabla sin indices escribe el JSON sin la clave`() {
        val json = SchemaJson.encode(Table("notas", listOf(Column("texto", VarcharType(10), emptyList()))))

        assertFalse(json.contains("indexes"), json)
    }

    @Test
    fun `un indice que apunta a una columna inexistente reporta`(@TempDir root: File) {
        write(root, Table("carros", carros.columns, listOf(Index("idx_x", "carros", "marka", auto = false))))

        val (catalog, errors) = load(root)

        assertEquals(
            listOf("el indice 'idx_x' de carros apunta a la columna 'marka', que no existe"),
            errors
        )
        // Los automaticos siguen, porque se derivan de las restricciones; el roto no.
        assertEquals(
            listOf("pk_carros", "uq_carros_duenio"),
            assertNotNull(catalog.table("carros")).indexes.map { it.name }
        )
    }
}
