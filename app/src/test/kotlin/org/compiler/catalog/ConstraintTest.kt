package org.compiler.catalog

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las siete restricciones y como se ven en el .json de esquema.
 *
 * Vive en el mismo paquete que el codigo porque catalogJson es `internal`.
 */
class ConstraintTest {

    private fun encode(constraint: Constraint) =
        catalogJson.encodeToString<Constraint>(constraint).replace(Regex("\\s+"), "")

    private fun decode(json: String) = catalogJson.decodeFromString<Constraint>(json)

    private val todas = listOf(
        PrimaryKey, NotNull, Nullable, Unique, AutoIncrement,
        ForeignKey("users", "id"),
        Default("0")
    )

    // El discriminador es "constraint" y no el "type" que kotlinx pone por
    // omision. Sin esa linea de configuracion haria falta escribir un
    // serializador a mano.
    @Test
    fun `una bandera sale como un objeto con su nombre`() {
        assertEquals("""{"constraint":"NOT_NULL"}""", encode(NotNull))
        assertEquals("""{"constraint":"PRIMARY_KEY"}""", encode(PrimaryKey))
        assertEquals("""{"constraint":"NULL"}""", encode(Nullable))
        assertEquals("""{"constraint":"UNIQUE"}""", encode(Unique))
        assertEquals("""{"constraint":"AUTOINCREMENT"}""", encode(AutoIncrement))
    }

    @Test
    fun `las que cargan datos los ponen al lado del discriminador`() {
        assertEquals(
            """{"constraint":"FOREIGN_KEY","table":"users","column":"id"}""",
            encode(ForeignKey("users", "id"))
        )
        assertEquals(
            """{"constraint":"DEFAULT","value":"true"}""",
            encode(Default("true"))
        )
    }

    @Test
    fun `las siete sobreviven la ida y vuelta`() {
        todas.forEach {
            assertEquals(it, decode(catalogJson.encodeToString<Constraint>(it)), "fallo $it")
        }
    }

    // Una lista mezclada es lo que de verdad guarda una columna.
    @Test
    fun `una lista mezclada se serializa completa`() {
        val constraints: List<Constraint> = listOf(NotNull, ForeignKey("users", "id"))
        val json = catalogJson.encodeToString(constraints)

        assertEquals(constraints, catalogJson.decodeFromString<List<Constraint>>(json))
    }

    // El archivo se abre a mano para revisarlo, asi que no puede salir en una
    // sola linea.
    @Test
    fun `el json se escribe con sangria`() {
        val json = catalogJson.encodeToString<Constraint>(ForeignKey("users", "id"))

        assertTrue(json.contains("\n"), "sin saltos de linea: $json")
        assertTrue(
            json.lines().any { it.startsWith(" ") && it.contains("\"table\"") },
            "sin sangria: $json"
        )
    }

    // El valor por omision viaja como texto, no como Value: BigDecimal,
    // LocalDate y LocalTime no traen serializador, y asi el archivo queda legible.
    @Test
    fun `el valor por omision se guarda como texto`() {
        assertEquals("""{"constraint":"DEFAULT","value":"2026-01-01"}""", encode(Default("2026-01-01")))
        assertEquals("""{"constraint":"DEFAULT","value":"1250.00"}""", encode(Default("1250.00")))
    }

    // Si alguien agrega una restriccion y olvida este `when`, no compila.
    @Test
    fun `el when sellado cubre las siete`() {
        assertEquals(7, todas.size)
        assertEquals(
            listOf("bandera", "bandera", "bandera", "bandera", "bandera", "carga", "carga"),
            todas.map { forma(it) }
        )
    }

    private fun forma(constraint: Constraint): String = when (constraint) {
        PrimaryKey, NotNull, Nullable, Unique, AutoIncrement -> "bandera"
        is ForeignKey, is Default -> "carga"
    }
}
