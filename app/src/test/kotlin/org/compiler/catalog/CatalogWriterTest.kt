package org.compiler.catalog

import org.compiler.diagnostics.Diagnostics
import org.compiler.storage.CsvReader
import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import org.compiler.types.DateType
import org.compiler.types.IntType
import org.compiler.types.TextType
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CatalogWriterTest {
    private val id = Column("id", IntType, listOf(PrimaryKey))
    private val name = Column("name", VarcharType(80), emptyList())
    private val users = Table("users", listOf(id, name))

    @Test
    fun `agregar restriccion deja intacto el catalogo original`() {
        val original = Catalog(mapOf("users" to users))

        val updated = original.addConstraint("users", "name", NotNull)

        assertEquals(emptyList(), original.table("users")?.column("name")?.constraints)
        assertEquals(listOf(NotNull), updated.table("users")?.column("name")?.constraints)
        assertEquals(listOf("id", "name"), updated.table("users")?.columns?.map { it.name })
        assertSame(original.table("users")?.column("id"), updated.table("users")?.column("id"))
    }

    @Test
    fun `agregar restriccion a tabla o columna inexistente no cambia nada`() {
        val original = Catalog(mapOf("users" to users))

        assertSame(original, original.addConstraint("missing", "id", Unique))
        assertSame(original, original.addConstraint("users", "missing", Unique))
        assertSame(original, original.addConstraint("users", "id", PrimaryKey))
    }

    @Test
    fun `agregar y quitar columnas produce nuevos catalogos`() {
        val original = Catalog(mapOf("users" to users))
        val active = Column("active", IntType, emptyList())

        val added = original.addColumn("users", active)
        val dropped = added.dropColumn("users", "name")

        assertEquals(listOf("id", "name"), original.table("users")?.columns?.map { it.name })
        assertEquals(listOf("id", "name", "active"), added.table("users")?.columns?.map { it.name })
        assertEquals(listOf("id", "active"), dropped.table("users")?.columns?.map { it.name })
        assertSame(original, original.addColumn("missing", active))
        assertSame(original, original.addColumn("users", id))
        assertSame(original, original.dropColumn("missing", "id"))
        assertSame(original, original.dropColumn("users", "missing"))
    }

    @Test
    fun `agregar y quitar tablas no muta el original`() {
        val original = Catalog(mapOf("users" to users))
        val posts = Table("posts", listOf(Column("id", IntType, emptyList())))

        val added = original.addTable(posts)
        val dropped = added.dropTable("users")

        assertEquals(listOf("users"), original.tables.keys.toList())
        assertEquals(listOf("users", "posts"), added.tables.keys.toList())
        assertEquals(listOf("posts"), dropped.tables.keys.toList())
        assertSame(original, original.dropTable("missing"))
        assertSame(original, original.addTable(users))
    }

    @Test
    fun `volcar un catalogo sin cambios no crea archivos`(@TempDir root: File) {
        val catalog = Catalog(mapOf("users" to users))

        CatalogWriter.flush(catalog, catalog, DataDirectory(root))

        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `tablas equivalentes por contenido tampoco se reescriben`(@TempDir root: File) {
        val directory = DataDirectory(root)
        val original = Catalog(mapOf("users" to users))
        val json = directory.json("users")
        json.writeText("contenido que no debe cambiar")
        val csv = directory.csv("users")
        csv.writeText("id,name\r\n1,Ana\r\n")
        val oldTime = FileTime.fromMillis(1_000)
        Files.setLastModifiedTime(json.toPath(), oldTime)
        val equivalent = Catalog(mapOf("users" to Table("users", listOf(
            Column("id", IntType, listOf(PrimaryKey)),
            Column("name", VarcharType(80), emptyList())
        ))))

        CatalogWriter.flush(original, equivalent, directory)

        assertEquals("contenido que no debe cambiar", json.readText())
        assertEquals(oldTime, Files.getLastModifiedTime(json.toPath()))
        assertEquals("id,name\r\n1,Ana\r\n", csv.readText())
    }

    @Test
    fun `una tabla nueva crea JSON y CSV con encabezado`(@TempDir root: File) {
        val directory = DataDirectory(root)

        CatalogWriter.flush(Catalog(emptyMap()), Catalog(mapOf("users" to users)), directory)

        assertTrue(directory.json("users").isFile)
        assertEquals(listOf("id", "name"), CsvReader.read(directory.csv("users")).header)
        assertTrue(CsvReader.read(directory.csv("users")).rows.isEmpty())
        val diagnostics = Diagnostics()
        assertNotNull(CatalogLoader(directory, diagnostics).load().table("users"))
        assertFalse(diagnostics.hasErrors)
    }

    @Test
    fun `solo los JSON modificados se reescriben y el CSV queda intacto`(@TempDir root: File) {
        val directory = DataDirectory(root)
        val posts = Table("posts", listOf(Column("id", IntType, emptyList())))
        val original = Catalog(mapOf("users" to users, "posts" to posts))
        CatalogWriter.flush(Catalog(emptyMap()), original, directory)
        CsvWriter.write(directory.csv("users"), listOf("id", "name"), listOf(listOf("1", "Ana")))
        val usersCsvBefore = directory.csv("users").readBytes()
        val postsJsonBefore = directory.json("posts").readBytes()
        val postsCsvBefore = directory.csv("posts").readBytes()

        CatalogWriter.flush(original, original.addConstraint("users", "name", NotNull), directory)

        val usersSchema = SchemaJson.decode(directory.json("users").readText())
        assertEquals(listOf(NotNull), usersSchema.columns.getValue("name").constraints)
        assertTrue(usersCsvBefore.contentEquals(directory.csv("users").readBytes()))
        assertTrue(postsJsonBefore.contentEquals(directory.json("posts").readBytes()))
        assertTrue(postsCsvBefore.contentEquals(directory.csv("posts").readBytes()))
    }

    @Test
    fun `eliminar tabla borra su par de archivos solamente`(@TempDir root: File) {
        val directory = DataDirectory(root)
        val posts = Table("posts", listOf(Column("id", IntType, emptyList())))
        val original = Catalog(mapOf("users" to users, "posts" to posts))
        CatalogWriter.flush(Catalog(emptyMap()), original, directory)

        CatalogWriter.flush(original, original.dropTable("users"), directory)

        assertFalse(directory.csv("users").exists())
        assertFalse(directory.json("users").exists())
        assertTrue(directory.csv("posts").exists())
        assertTrue(directory.json("posts").exists())
    }

    @Test
    fun `esquema existente se carga y vuelve a escribir sin alterar filas`(@TempDir root: File) {
        val directory = DataDirectory(root)
        val table = Table("events", listOf(
            Column("id", IntType, listOf(PrimaryKey)),
            Column("note", TextType, emptyList()),
            Column("day", DateType, emptyList())
        ))
        val rows = listOf(
            listOf("1", "dijo \"hola\",\notra linea", "2026-10-03"),
            listOf("2", "", null)
        )
        CsvWriter.write(directory.csv("events"), table.columns.map { it.name }, rows)
        directory.json("events").writeText(SchemaJson.encode(table))
        val csvBefore = directory.csv("events").readBytes()
        val jsonBefore = directory.json("events").readBytes()
        val diagnostics = Diagnostics()
        val loaded = CatalogLoader(directory, diagnostics).load()

        // Fuerza una escritura del JSON original sin modificar sus datos.
        val changedOriginal = loaded.addConstraint("events", "note", NotNull)
        CatalogWriter.flush(changedOriginal, loaded, directory)

        assertFalse(diagnostics.hasErrors)
        assertTrue(csvBefore.contentEquals(directory.csv("events").readBytes()))
        assertTrue(jsonBefore.contentEquals(directory.json("events").readBytes()))
        assertEquals(rows, CsvReader.read(directory.csv("events")).rows)
    }

    @Test
    fun `crear y borrar la misma tabla en memoria no toca disco`(@TempDir root: File) {
        val initial = Catalog(emptyMap())
        val final = initial.addTable(users).dropTable("users")

        CatalogWriter.flush(initial, final, DataDirectory(root))

        assertNull(final.table("users"))
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
