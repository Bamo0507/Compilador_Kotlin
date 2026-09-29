package org.compiler.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DateType
import org.compiler.types.DecimalType
import org.compiler.types.ErrorType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.NullType
import org.compiler.types.TextType
import org.compiler.types.TimeType
import org.compiler.types.Type
import org.compiler.types.VarcharType
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaJsonTest {
    private val types = listOf(
        IntType, FloatType, DecimalType(10, 2), CharType(5), VarcharType(80),
        TextType, DateType, TimeType, BooleanType, NullType, ErrorType
    )

    private val constraints = listOf(
        PrimaryKey, NotNull, Nullable, Unique, AutoIncrement,
        ForeignKey("users", "id"), Default("2026-01-01")
    )

    @Test
    fun `los once tipos y las siete restricciones sobreviven la ida y vuelta`() {
        val original = Table("types", types.mapIndexed { index, type ->
            Column("column_$index", type, constraints)
        })

        val schema = SchemaJson.decode(SchemaJson.encode(original))
        assertEquals(original.columns.map { it.name }.toSet(), schema.columns.keys)
        original.columns.forEach { expected ->
            val stored = assertNotNull(schema.columns[expected.name])
            val actual = assertNotNull(stored.toColumn(expected.name))
            assertEquals(expected.name, actual.name)
            assertEquals(expected.type, actual.type)
            assertEquals(expected.constraints, actual.constraints)
        }
    }

    @Test
    fun `el json usa columnas por nombre tipos como texto y restricciones con discriminador`() {
        val table = Table("users", listOf(
            Column("id", IntType, listOf(PrimaryKey, AutoIncrement)),
            Column("name", VarcharType(80), listOf(NotNull)),
            Column("uid", IntType, listOf(ForeignKey("users", "id"))),
            Column("active", BooleanType, listOf(Default("true")))
        ))

        val root = Json.parseToJsonElement(SchemaJson.encode(table)).jsonObject
        assertEquals(setOf("columns"), root.keys)
        val columns = root.getValue("columns").jsonObject
        assertEquals("VARCHAR(80)", columns.getValue("name").jsonObject.getValue("type").jsonPrimitive.content)
        val idConstraints = columns.getValue("id").jsonObject.getValue("constraints").jsonArray
        assertEquals(listOf("PRIMARY_KEY", "AUTOINCREMENT"), idConstraints.map {
            it.jsonObject.getValue("constraint").jsonPrimitive.content
        })
        val foreignKey = columns.getValue("uid").jsonObject.getValue("constraints").jsonArray.single().jsonObject
        assertEquals("FOREIGN_KEY", foreignKey.getValue("constraint").jsonPrimitive.content)
        assertEquals("users", foreignKey.getValue("table").jsonPrimitive.content)
        assertEquals("id", foreignKey.getValue("column").jsonPrimitive.content)
        val default = columns.getValue("active").jsonObject.getValue("constraints").jsonArray.single().jsonObject
        assertEquals("true", default.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun `un esquema escrito a mano se reconstruye en el orden del header`() {
        val schema = SchemaJson.decode("""
            {
              "columns": {
                "name": { "type": "VARCHAR(80)", "constraints": [{ "constraint": "NOT_NULL" }] },
                "id": { "type": "INT", "constraints": [{ "constraint": "PRIMARY_KEY" }] }
              }
            }
        """.trimIndent())
        val header = listOf("id", "name")
        val table = Table("users", header.map { name ->
            assertNotNull(schema.columns[name]?.toColumn(name))
        })

        assertEquals(header, table.columns.map { it.name })
        assertEquals(0, table.indexOf("id"))
        assertEquals(IntType, table.column("id")?.type)
        assertEquals(VarcharType(80), table.column("name")?.type)
        assertEquals("id", table.primaryKey?.name)
    }

    @Test
    fun `tipos desconocidos conservan el texto para el diagnostico del cargador`() {
        val schema = SchemaJson.decode("""{"columns":{"name":{"type":"VARCHA(80)"}}}""")
        val column = assertNotNull(schema.columns["name"])

        assertEquals("VARCHA(80)", column.type)
        assertNull(column.toColumn("name"))
    }

    @Test
    fun `las restricciones omitidas y los esquemas vacios se leen`() {
        val schema = SchemaJson.decode("""{"columns":{"id":{"type":"INT"}}}""")
        assertEquals(emptyList(), schema.columns.getValue("id").constraints)
        assertTrue(SchemaJson.decode(SchemaJson.encode(Table("empty", emptyList()))).columns.isEmpty())

        val written = Json.parseToJsonElement(SchemaJson.encode(
            Table("users", listOf(Column("id", IntType, emptyList())))
        )).jsonObject.getValue("columns").jsonObject.getValue("id").jsonObject
        assertTrue(written.getValue("constraints").jsonArray.isEmpty())
    }

    @Test
    fun `los tipos parametrizados los alias y los espacios se interpretan`() {
        val cases: Map<String, Type> = mapOf(
            "DECIMAL(10,2)" to DecimalType(10, 2),
            "VARCHAR(80)" to VarcharType(80),
            "CHAR(5)" to CharType(5),
            " numeric ( 10 , 0 ) " to DecimalType(10, 0),
            "integer" to IntType,
            " boolean " to BooleanType,
            "varchar ( 80 )" to VarcharType(80),
            "DECIMAL(2,2)" to DecimalType(2, 2)
        )
        cases.forEach { (text, expected) -> assertEquals(expected, typeFromText(text), text) }
        types.forEach { assertEquals(it, typeFromText(it.name), it.name) }
    }

    @Test
    fun `los tipos incompletos invalidos o desbordados devuelven null`() {
        listOf(
            "", "UNKNOWN", "DECIMAL", "DECIMAL(10)", "DECIMAL(0,0)",
            "DECIMAL(2,5)", "DECIMAL(10,-1)", "DECIMAL(10,2,3)",
            "CHAR", "CHAR(0)", "CHAR(-1)", "CHAR(5,2)", "VARCHAR(0)",
            "VARCHAR(999999999999999999)", "DECIMAL(999999999999999999,2)",
            "DECIMAL(10,999999999999999999)", "INT(5)", "VARCHAR(80) extra"
        ).forEach { assertNull(typeFromText(it), it) }
    }

    @Test
    fun `el archivo json tiene sangria y conserva textos escapados`(@TempDir directory: File) {
        val default = Default("dijo \"hola\"\nC:\\datos")
        val table = Table("notes", listOf(Column("body", TextType, listOf(default))))
        val file = File(directory, "notes.json")
        file.writeText(SchemaJson.encode(table))

        val content = file.readText()
        assertTrue(content.contains('\n'))
        assertTrue(content.lines().any { it.startsWith(" ") && it.contains("\"body\"") })
        val column = SchemaJson.decode(content).columns.getValue("body")
        assertEquals(listOf(default), column.constraints)
    }

    @Test
    fun `json mal formado y restricciones desconocidas producen un error de serializacion`() {
        assertFailsWith<SerializationException> { SchemaJson.decode("{ invalid") }
        assertFailsWith<SerializationException> { SchemaJson.decode("""{"columns":{"id":{}}}""") }
        assertFailsWith<SerializationException> {
            SchemaJson.decode("""{"columns":{"id":{"type":"INT","constraints":[{"constraint":"CHECK"}]}}}""")
        }
    }
}
