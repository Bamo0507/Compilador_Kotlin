package org.compiler.storage

import java.io.File
import java.io.IOException

object CsvReader {
    data class CsvContent(val header: List<String>, val rows: List<List<String?>>)

    fun read(file: File): CsvContent {
        val source = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        if (source.isEmpty()) throw IOException("${file.name} no tiene encabezado")

        val records = parse(source, file.name)
        val header = headerOf(records, file.name)
        records.drop(1).forEachIndexed { rowIndex, row ->
            if (row.size != header.size) {
                throw IOException("${file.name}: la fila ${rowIndex + 2} tiene ${row.size} campos, se esperaban ${header.size}")
            }
        }
        return CsvContent(header, records.drop(1))
    }

    /**
     * Solo la primera linea.
     *
     * La guarda de cordura compara nombres de columnas, no datos, asi que no hay
     * por que abrir entera una tabla de miles de filas para eso.
     */
    fun header(file: File): List<String> {
        val first = file.useLines(Charsets.UTF_8) { it.firstOrNull() }
            .orEmpty()
            .removePrefix("\uFEFF")
        if (first.isEmpty()) throw IOException("${file.name} no tiene encabezado")

        return headerOf(parse(first, file.name), file.name)
    }

    private fun headerOf(records: List<List<String?>>, name: String): List<String> {
        val header = records.firstOrNull() ?: throw IOException("$name no tiene encabezado")
        if (header.any { it.isNullOrEmpty() }) throw IOException("$name tiene una columna sin nombre")

        return header.filterNotNull()
    }

    /**
     * El parser, para quien ya tiene el texto en la mano.
     *
     * Lo usa RowReader con UNA linea: separar campos tiene las mismas reglas de
     * comillas y comas que el archivo entero, y duplicarlas seria pedir que se
     * desincronicen.
     */
    internal fun read(source: String, name: String): List<List<String?>> = parse(source, name)

    private fun parse(source: String, name: String): List<List<String?>> {
        val records = mutableListOf<List<String?>>()
        val record = mutableListOf<String?>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var wasQuoted = false
        var recordStarted = false
        var index = 0

        fun finishField() {
            record += if (wasQuoted) field.toString() else field.toString().ifEmpty { null }
            field.setLength(0)
            wasQuoted = false
            closedQuote = false
        }

        fun finishRecord() {
            finishField()
            records += record.toList()
            record.clear()
            recordStarted = false
        }

        while (index < source.length) {
            val character = source[index]
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < source.length && source[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                        closedQuote = true
                    }
                } else {
                    field.append(character)
                }
            } else {
                when {
                    character == ',' -> {
                        finishField()
                        recordStarted = true
                    }
                    character == '\r' || character == '\n' -> {
                        finishRecord()
                        if (character == '\r' && index + 1 < source.length && source[index + 1] == '\n') {
                            index++
                        }
                    }
                    character == '"' && field.isEmpty() && !closedQuote -> {
                        quoted = true
                        wasQuoted = true
                        recordStarted = true
                    }
                    character == '"' || closedQuote ->
                        throw IOException("$name contiene comillas CSV mal formadas")
                    else -> {
                        field.append(character)
                        recordStarted = true
                    }
                }
            }
            index++
        }

        if (quoted) throw IOException("$name termina dentro de un campo entre comillas")
        if (recordStarted || record.isNotEmpty() || field.isNotEmpty() || wasQuoted) finishRecord()

        return records
    }
}
