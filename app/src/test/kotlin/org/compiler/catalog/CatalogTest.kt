package org.compiler.catalog

import org.compiler.types.IntType
import org.compiler.types.VarcharType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CatalogTest {
    private val id = Column("id", IntType, listOf(PrimaryKey))
    private val name = Column("name", VarcharType(80), listOf(NotNull))
    private val users = Table("users", listOf(id, name))

    @Test
    fun `el catalogo busca tablas y acepta estar vacio`() {
        val catalog = Catalog(mapOf("users" to users))

        assertSame(users, catalog.table("users"))
        assertNull(catalog.table("missing"))
        assertTrue(Catalog(emptyMap()).tables.isEmpty())
    }

    @Test
    fun `las columnas conservan su orden y sus indices`() {
        assertEquals(listOf("id", "name"), users.columns.map { it.name })
        assertSame(id, users.column("id"))
        assertSame(name, users.column("name"))
        assertEquals(0, users.indexOf("id"))
        assertEquals(1, users.indexOf("name"))
        assertNull(users.column("missing"))
        assertEquals(-1, users.indexOf("missing"))
    }

    @Test
    fun `la clave primaria se encuentra sin exigir NOT NULL explicito`() {
        assertSame(id, users.primaryKey)
        assertFalse(id.hasConstraint<NotNull>())
        assertFalse(id.nullable)
        assertNull(Table("logs", listOf(name)).primaryKey)
    }

    @Test
    fun `la nulabilidad depende de NOT NULL y PRIMARY KEY`() {
        assertTrue(Column("optional", IntType, emptyList()).nullable)
        assertTrue(Column("optional", IntType, listOf(Nullable)).nullable)
        assertFalse(name.nullable)
        assertFalse(Column("id", IntType, listOf(PrimaryKey, Nullable)).nullable)
        assertFalse(Column("required", IntType, listOf(NotNull, Nullable)).nullable)
    }

    @Test
    fun `las restricciones y la referencia se consultan por su tipo`() {
        val foreignKey = ForeignKey("users", "id")
        val column = Column("user_id", IntType, listOf(NotNull, foreignKey))

        assertTrue(column.hasConstraint<NotNull>())
        assertTrue(column.hasConstraint<ForeignKey>())
        assertFalse(column.hasConstraint<PrimaryKey>())
        assertSame(foreignKey, column.reference)
        assertNull(id.reference)
        assertEquals(VarcharType(80), name.type)
    }

    @Test
    fun `modificar las colecciones originales no cambia los modelos`() {
        val constraints = mutableListOf<Constraint>(PrimaryKey)
        val column = Column("id", IntType, constraints)
        val columns = mutableListOf(column)
        val table = Table("users", columns)
        val tables = mutableMapOf("users" to table)
        val catalog = Catalog(tables)

        constraints.clear()
        columns.clear()
        tables.clear()

        assertSame(table, catalog.table("users"))
        assertSame(column, table.column("id"))
        assertSame(column, table.primaryKey)
        assertFalse(column.nullable)
        assertEquals(listOf(PrimaryKey), column.constraints)
    }
}
