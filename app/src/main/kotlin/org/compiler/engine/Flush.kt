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

    /**
     * Devuelve los archivos que escribio, en el orden en que los escribio.
     *
     * La interfaz los lista para que el usuario vea QUE cambio en el disco. Es la
     * otra mitad de la decision 5: si un script falla no se escribe nada, y para
     * que eso signifique algo hay que poder ver lo que si se escribe cuando sale
     * bien.
     */
    fun flush(changes: Changes, directory: DataDirectory): List<String> {
        val written = mutableListOf<String>()
        // Primero los esquemas: un CSV con una columna nueva necesita que su JSON
        // ya la declare para volver a leerse.
        changes.schemas.forEach { name ->
            val table = changes.catalog.table(name) ?: return@forEach
            directory.json(name).writeText(SchemaJson.encode(table))
            written += directory.json(name).name
        }

        changes.rows.forEach { (name, rows) ->
            val table = changes.catalog.table(name) ?: return@forEach
            val offsets = CsvWriter.write(
                directory.csv(name),
                table.columns.map { it.name },
                rows.map { encode(it, table) }
            )
            written += directory.csv(name).name
            written += rebuild(table, rows, offsets, directory)
        }

        changes.dropped.forEach { name ->
            if (directory.csv(name).delete()) written += "${directory.csv(name).name} (borrado)"
            if (directory.json(name).delete()) written += "${directory.json(name).name} (borrado)"
        }

        // Los de un DROP INDEX y los de las tablas que se borraron: el escritor
        // anoto los dos en la misma lista, porque los dos terminan igual.
        changes.droppedIndexes.forEach { index ->
            if (directory.index(index.name).delete()) written += "${index.fileName} (borrado)"
        }

        return written
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
    private fun rebuild(
        table: Table,
        rows: List<Row>,
        offsets: List<Long>,
        directory: DataDirectory
    ): List<String> = table.indexes.mapNotNull { index ->
        val position = table.indexOf(index.column)
        if (position < 0) return@mapNotNull null

        val values: List<Value> = rows.map { it.values[position] }
        IndexFile.write(
            directory.index(index.name),
            table.columns[position].type,
            IndexFile.build(values, offsets)
        )
        index.fileName
    }

    // Un campo vacio es un NULL, que es lo que ValueCodec devuelve como null.
    private fun encode(row: Row, table: Table): List<String?> =
        table.columns.mapIndexed { position, column ->
            row.values.getOrNull(position)?.let { ValueCodec.encode(it, column.type) }
        }
}
