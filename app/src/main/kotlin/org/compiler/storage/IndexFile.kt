package org.compiler.storage

import java.io.File
import java.io.IOException
import java.util.TreeMap
import org.compiler.engine.ValueOrder
import org.compiler.types.Type
import org.compiler.types.Value
import org.compiler.types.ValueCodec

/**
 * El archivo de un indice: un CSV de dos columnas ordenado por valor.
 *
 *     value,offset
 *     Mazda,31
 *     Mazda,58
 *     Toyota,17
 *
 * Una columna no unica repite el valor en varias lineas, que es el caso normal.
 *
 * La extension es `.idx` y no `.csv` A PROPOSITO: DataDirectory lista los `.csv`
 * como tablas, y un indice no es una tabla. Por dentro es el mismo formato, asi
 * que se lee con el CsvReader de la fase 2 y no necesita parser propio.
 */
object IndexFile {

    const val VALUE_COLUMN = "value"
    const val OFFSET_COLUMN = "offset"

    /**
     * Las entradas en memoria, en un TreeMap.
     *
     * Es un arbol rojo-negro del JDK, no un HashMap, porque el enunciado habla de
     * CONDICIONES de WHERE y no solo de igualdad: con el arbol, `anio > 2020` es
     * un `tailMap` y `BETWEEN` es un `subMap`. Un arbol B+ a mano daria la misma
     * complejidad, y es un ejercicio de bases de datos y no de compiladores: el
     * B+ existe porque el indice no cabe en RAM, y el nuestro si cabe.
     */
    fun empty(): TreeMap<Value, MutableList<Long>> = TreeMap(VALUE_COMPARATOR)

    fun read(file: File, type: Type): TreeMap<Value, MutableList<Long>> {
        val entries = empty()
        if (!file.isFile) return entries

        val content = CsvReader.read(file)
        content.rows.forEachIndexed { number, fields ->
            val text = fields.getOrNull(0)
            val offset = fields.getOrNull(1)?.trim()?.toLongOrNull()
                ?: throw IOException("${file.name}: la fila ${number + 2} no tiene un desplazamiento")

            // El tipo de la columna indexada es lo que hace que un anio ordene como
            // numero y no como texto: 2, 10, 100 y no 10, 100, 2.
            val value = ValueCodec.decode(text, type)
                ?: throw IOException("${file.name}: '$text' no es un valor de ${type.name}")

            entries.getOrPut(value) { mutableListOf() }.add(offset)
        }
        return entries
    }

    fun write(file: File, type: Type, entries: TreeMap<Value, MutableList<Long>>) {
        val rows = entries.flatMap { (value, offsets) ->
            offsets.map { listOf(ValueCodec.encode(value, type), it.toString()) }
        }
        CsvWriter.write(file, listOf(VALUE_COLUMN, OFFSET_COLUMN), rows)
    }

    /**
     * El indice de una columna, armado del mismo recorrido que escribe el CSV.
     *
     * `values` son los valores de la columna indexada, fila por fila, y `offsets`
     * donde quedo cada una. Van en el mismo orden porque salen de la misma lista.
     */
    fun build(values: List<Value>, offsets: List<Long>): TreeMap<Value, MutableList<Long>> {
        val entries = empty()
        values.zip(offsets).forEach { (value, offset) ->
            entries.getOrPut(value) { mutableListOf() }.add(offset)
        }
        return entries
    }

    /**
     * El orden del arbol.
     *
     * Reusa el del motor, que es el que ya sabe comparar un INT con un DECIMAL. El
     * nulo no es menor ni mayor que nada, asi que se le da un lugar fijo: al
     * final, la misma convencion del ORDER BY ASC.
     */
    private val VALUE_COMPARATOR = Comparator<Value> { left, right ->
        ValueOrder.forSort(left, right, descending = false)
    }
}
