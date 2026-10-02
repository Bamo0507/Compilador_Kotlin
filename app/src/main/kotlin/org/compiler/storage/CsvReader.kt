package org.compiler.storage

import java.io.File
import java.io.IOException

object CsvReader {
    data class CsvContent(val header: List<String>, val rows: List<List<String?>>)

    fun read(file: File): CsvContent {
        val source = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        if (source.isEmpty()) throw IOException("${file.name} no tiene encabezado")

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
                        throw IOException("${file.name} contiene comillas CSV mal formadas")
                    else -> {
                        field.append(character)
                        recordStarted = true
                    }
                }
            }
            index++
        }

        if (quoted) throw IOException("${file.name} termina dentro de un campo entre comillas")
        if (recordStarted || record.isNotEmpty() || field.isNotEmpty() || wasQuoted) finishRecord()

        val header = records.firstOrNull()
            ?: throw IOException("${file.name} no tiene encabezado")
        if (header.any { it.isNullOrEmpty() }) {
            throw IOException("${file.name} tiene una columna sin nombre")
        }
        records.drop(1).forEachIndexed { rowIndex, row ->
            if (row.size != header.size) {
                throw IOException("${file.name}: la fila ${rowIndex + 2} tiene ${row.size} campos, se esperaban ${header.size}")
            }
        }
        return CsvContent(header.filterNotNull(), records.drop(1))
    }
}
