package org.compiler.catalog

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.models.LexemeLocation
import org.compiler.storage.DataDirectory
import org.compiler.types.ErrorType
import org.compiler.types.NullType
import org.compiler.types.ValueCodec
import java.io.IOException

class CatalogLoader(
    private val directory: DataDirectory,
    private val diagnostics: Diagnostics
) {
    /**
     * Las tablas que no se pudieron cargar, con el por que.
     *
     * Van aparte de los diagnosticos porque la vista del catalogo las necesita
     * ASOCIADAS a su tabla: un `users.csv` sin su `users.json` no esta en el
     * catalogo, y si la vista solo mostrara lo que si cargo, un directorio roto
     * se veria igual que uno sano.
     */
    val problems: Map<String, String> get() = broken

    private val broken = linkedMapOf<String, String>()

    fun load(): Catalog {
        broken.clear()
        val names = directory.tables()
        val tables = linkedMapOf<String, Table>()

        // Un JSON sin CSV no declara una tabla, pero si necesita un diagnostico.
        directory.root().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.nameWithoutExtension !in names }
            .sortedBy { it.name }
            .forEach { report("${it.name} no tiene datos") }

        names.forEach { name ->
            val file = directory.json(name)
            if (!file.isFile) {
                reportTable(name, "$name.csv no tiene esquema")
                return@forEach
            }

            val schema = try {
                SchemaJson.decode(file.readText(Charsets.UTF_8))
            } catch (error: IOException) {
                reportTable(name, "el esquema de $name no se pudo leer: ${error.message}")
                return@forEach
            } catch (error: IllegalArgumentException) {
                reportTable(name, "el esquema de $name no se pudo leer: ${error.message}")
                return@forEach
            }

            val columns = schema.columns.mapNotNull { (columnName, definition) ->
                val column = definition.toColumn(columnName)
                if (column == null || column.type == NullType || column.type == ErrorType) {
                    reportTable(name, "tipo '${definition.type}' no reconocido en $name.$columnName")
                    return@mapNotNull null
                }

                definition.constraints.filterIsInstance<Default>().forEach { default ->
                    if (ValueCodec.decode(default.value, column.type) == null) {
                        report("el valor por omision de $name.$columnName no es ${column.type.name}")
                    }
                }
                column
            }
            val indexes = schema.indexes.orEmpty().mapNotNull { (indexName, column) ->
                if (columns.none { it.name == column }) {
                    report("el indice '$indexName' de $name apunta a la columna '$column', que no existe")
                    return@mapNotNull null
                }
                // Que un indice sea automatico se deduce de su nombre: son los dos
                // prefijos que el catalogo se reserva, y nadie mas los puede usar.
                Index(indexName, name, column, auto = Index.isReserved(indexName))
            }
            tables[name] = Table(name, columns, indexes)
        }

        val catalog = Catalog(tables)
        // Una FK puede apuntar a una tabla cargada despues de la que la declara.
        catalog.tables.values.forEach { table ->
            table.columns.forEach { column ->
                column.constraints.filterIsInstance<ForeignKey>().forEach { reference ->
                    val target = catalog.table(reference.table)
                    when {
                        target == null -> report(
                            "${table.name}.${column.name} referencia la tabla '${reference.table}', que no existe"
                        )
                        target.column(reference.column) == null -> report(
                            "${table.name}.${column.name} referencia la columna " +
                                "'${reference.table}.${reference.column}', que no existe"
                        )
                    }
                }
            }
        }
        return catalog
    }

    private fun report(message: String) {
        // Los errores del catalogo vienen de archivos, no de una posicion SQL.
        diagnostics.report(CompilerError.SemanticError(LexemeLocation(1, 1), message))
    }

    // El mismo error, ademas anotado contra su tabla. La primera razon es la que
    // se muestra: las siguientes suelen ser consecuencia de la primera.
    private fun reportTable(table: String, message: String) {
        report(message)
        broken.putIfAbsent(table, message)
    }
}
