package org.compiler.engine

import org.compiler.catalog.Index
import org.compiler.catalog.SchemaJson
import org.compiler.catalog.Table
import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import org.compiler.storage.IndexFile
import org.compiler.types.ValueCodec
import org.compiler.types.Value

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
            val offsets = CsvWriter.write(
                directory.csv(name),
                table.columns.map { it.name },
                rows.map { encode(it, table) }
            )
            rebuild(table, rows, offsets, directory)
        }

        changes.dropped.forEach { name ->
            directory.csv(name).delete()
            directory.json(name).delete()
        }

        // Los de un DROP INDEX y los de las tablas que se borraron: el escritor
        // anoto los dos en la misma lista, porque los dos terminan igual.
        changes.droppedIndexes.forEach { directory.index(it.name).delete() }
    }

    /**
     * Los indices de una tabla se RECONSTRUYEN enteros en el mismo recorrido que
     * acaba de escribir su CSV.
     *
     * Nada se actualiza de forma incremental. Un UPDATE o un DELETE reescriben la
     * tabla entera, asi que todos los desplazamientos cambian de todos modos, y
     * rehacerlos cuesta un recorrido de filas que ya estan en memoria. A cambio,
     * desaparece una familia entera de errores: un indice no puede quedar apuntando
     * a un byte que ya no es el inicio de su fila.
     */
    private fun rebuild(table: Table, rows: List<Row>, offsets: List<Long>, directory: DataDirectory) {
        table.indexes.forEach { index ->
            val position = table.indexOf(index.column)
            if (position < 0) return@forEach

            val values: List<Value> = rows.map { it.values[position] }
            IndexFile.write(
                directory.index(index.name),
                table.columns[position].type,
                IndexFile.build(values, offsets)
            )
        }
    }

    // Un campo vacio es un NULL, que es lo que ValueCodec devuelve como null.
    private fun encode(row: Row, table: Table): List<String?> =
        table.columns.mapIndexed { position, column ->
            row.values.getOrNull(position)?.let { ValueCodec.encode(it, column.type) }
        }
}
