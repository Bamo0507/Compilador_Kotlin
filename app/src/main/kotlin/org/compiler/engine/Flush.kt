package org.compiler.engine

import org.compiler.catalog.SchemaJson
import org.compiler.catalog.Table
import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import org.compiler.types.ValueCodec

/**
 * Lleva al disco lo que la corrida cambio, y solo eso.
 *
 * Se llama una sola vez, al final, y solo si no hubo ningun error. Esa es la
 * vuelta atras: si una sentencia falla, las anteriores quedaron aplicadas en
 * memoria, pero como nada toco el disco, el directorio queda identico.
 *
 * No conoce la sesion, solo su reporte, asi que se prueba pasandole un `Changes`
 * armado a mano sin montar una corrida entera.
 */
object Flush {

    fun flush(changes: Changes, directory: DataDirectory) {
        // Primero los esquemas: un CSV con una columna nueva necesita que su JSON
        // ya la declare para volver a leerse.
        changes.schemas.forEach { name ->
            val table = changes.catalog.table(name) ?: return@forEach
            directory.json(name).writeText(SchemaJson.encode(table))
        }

        changes.rows.forEach { (name, rows) ->
            val table = changes.catalog.table(name) ?: return@forEach
            CsvWriter.write(directory.csv(name), table.columns.map { it.name }, rows.map { encode(it, table) })
        }

        changes.dropped.forEach { name ->
            directory.csv(name).delete()
            directory.json(name).delete()
        }
    }

    // Un campo vacio es un NULL, que es lo que ValueCodec devuelve como null.
    private fun encode(row: Row, table: Table): List<String?> =
        table.columns.mapIndexed { position, column ->
            row.values.getOrNull(position)?.let { ValueCodec.encode(it, column.type) }
        }
}
