package org.compiler.catalog

import org.compiler.diagnostics.Diagnostics
import org.compiler.storage.DataDirectory
import org.compiler.types.BooleanType
import org.compiler.types.DateType
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

class CatalogLoaderTest {
    private fun load(root: File, diagnostics: Diagnostics = Diagnostics()): Catalog =
        CatalogLoader(DataDirectory(root), diagnostics).load()

    private fun writeTable(root: File, table: Table, csv: String = "archivo sin parsear") {
        File(root, "${table.name}.csv").writeText(csv)
        File(root, "${table.name}.json").writeText(SchemaJson.encode(table))
    }

    @Test
    fun `directorio vacio produce catalogo vacio sin errores`(@TempDir root: File) {
        val diagnostics = Diagnostics()

        assertTrue(load(root, diagnostics).tables.isEmpty())
        assertFalse(diagnostics.hasErrors)
    }

    @Test
    fun `carga tipos y restricciones de varias tablas sin leer las filas CSV`(@TempDir root: File) {
        writeTable(root, Table("users", listOf(
            Column("id", IntType, listOf(PrimaryKey)),
            Column("name", VarcharType(80), listOf(NotNull))
        )))
        writeTable(root, Table("posts", listOf(
            Column("user_id", IntType, listOf(ForeignKey("users", "id"))),
            Column("visible", BooleanType, listOf(Default("true")))
        )))
        writeTable(root, Table("events", listOf(Column("day", DateType, emptyList()))))
        val diagnostics = Diagnostics()

        val catalog = load(root, diagnostics)

        assertEquals(listOf("events", "posts", "users"), catalog.tables.keys.toList())
        assertEquals(IntType, catalog.table("users")?.column("id")?.type)
        assertEquals("id", catalog.table("users")?.primaryKey?.name)
        assertEquals(ForeignKey("users", "id"), catalog.table("posts")?.column("user_id")?.reference)
        assertEquals(listOf(Default("true")), catalog.table("posts")?.column("visible")?.constraints)
        assertFalse(diagnostics.hasErrors)
    }

    @Test
    fun `CSV sin esquema produce diagnostico y no entra al catalogo`(@TempDir root: File) {
        File(root, "users.csv").writeText("id\n")
        val diagnostics = Diagnostics()

        val catalog = load(root, diagnostics)

        assertNull(catalog.table("users"))
        assertTrue(diagnostics.all().single().message.contains("users.csv no tiene esquema"))
    }

    @Test
    fun `JSON sin CSV produce diagnostico pero no declara tabla`(@TempDir root: File) {
        File(root, "users.json").writeText(SchemaJson.encode(Table("users", listOf(Column("id", IntType, emptyList())))))
        val diagnostics = Diagnostics()

        val catalog = load(root, diagnostics)

        assertTrue(catalog.tables.isEmpty())
        assertTrue(diagnostics.all().single().message.contains("users.json no tiene datos"))
    }

    @Test
    fun `JSON mal formado no interrumpe la carga de otra tabla`(@TempDir root: File) {
        File(root, "broken.csv").writeText("id\n")
        File(root, "broken.json").writeText("{ mal formado")
        writeTable(root, Table("valid", listOf(Column("id", IntType, emptyList()))))
        val diagnostics = Diagnostics()

        val catalog = load(root, diagnostics)

        assertNotNull(catalog.table("valid"))
        assertNull(catalog.table("broken"))
        assertTrue(diagnostics.all().single().message.contains("el esquema de broken no se pudo leer"))
    }

    @Test
    fun `tipo desconocido reporta la tabla y columna afectadas`(@TempDir root: File) {
        File(root, "users.csv").writeText("id,name\n")
        File(root, "users.json").writeText("""
            {"columns":{"id":{"type":"INT"},"name":{"type":"VARCHA(80)"}}}
        """.trimIndent())
        val diagnostics = Diagnostics()

        val table = assertNotNull(load(root, diagnostics).table("users"))

        assertNotNull(table.column("id"))
        assertNull(table.column("name"))
        assertTrue(diagnostics.all().single().message.contains("VARCHA(80)' no reconocido en users.name"))
    }

    @Test
    fun `tipo interno no se acepta como tipo de columna persistida`(@TempDir root: File) {
        File(root, "users.csv").writeText("id\n")
        File(root, "users.json").writeText("""{"columns":{"id":{"type":"<error>"}}}""")
        val diagnostics = Diagnostics()

        load(root, diagnostics)

        assertTrue(diagnostics.all().single().message.contains("tipo '<error>' no reconocido"))
    }

    @Test
    fun `DEFAULT incompatible reporta error y sigue con otras tablas`(@TempDir root: File) {
        writeTable(root, Table("users", listOf(Column("age", IntType, listOf(Default("many"))))))
        writeTable(root, Table("posts", listOf(Column("id", IntType, emptyList()))))
        val diagnostics = Diagnostics()

        val catalog = load(root, diagnostics)

        assertNotNull(catalog.table("posts"))
        assertTrue(diagnostics.all().single().message.contains("el valor por omision de users.age no es INT"))
    }

    @Test
    fun `FK que apunta a tabla inexistente se detecta en segunda vuelta`(@TempDir root: File) {
        writeTable(root, Table("posts", listOf(
            Column("user_id", IntType, listOf(ForeignKey("users", "id")))
        )))
        val diagnostics = Diagnostics()

        load(root, diagnostics)

        assertTrue(diagnostics.all().single().message.contains("posts.user_id referencia la tabla 'users', que no existe"))
    }

    @Test
    fun `FK que apunta a columna inexistente se reporta`(@TempDir root: File) {
        writeTable(root, Table("posts", listOf(Column("user_id", IntType, listOf(ForeignKey("users", "missing"))))))
        writeTable(root, Table("users", listOf(Column("id", IntType, listOf(PrimaryKey)))))
        val diagnostics = Diagnostics()

        load(root, diagnostics)

        assertTrue(diagnostics.all().single().message.contains("users.missing', que no existe"))
    }

    @Test
    fun `ciclo de claves foraneas valido no produce errores`(@TempDir root: File) {
        writeTable(root, Table("alpha", listOf(Column("beta_id", IntType, listOf(ForeignKey("beta", "id"))),
            Column("id", IntType, listOf(PrimaryKey)))))
        writeTable(root, Table("beta", listOf(Column("alpha_id", IntType, listOf(ForeignKey("alpha", "id"))),
            Column("id", IntType, listOf(PrimaryKey)))))
        val diagnostics = Diagnostics()

        assertEquals(2, load(root, diagnostics).tables.size)
        assertFalse(diagnostics.hasErrors)
    }

    @Test
    fun `errores de archivos distintos se acumulan en una corrida`(@TempDir root: File) {
        File(root, "missing.csv").writeText("id\n")
        File(root, "orphan.json").writeText("{}")
        File(root, "broken.csv").writeText("id\n")
        File(root, "broken.json").writeText("{ bad")
        val diagnostics = Diagnostics()

        load(root, diagnostics)

        assertEquals(3, diagnostics.count)
        assertTrue(diagnostics.hasErrors)
    }
}
