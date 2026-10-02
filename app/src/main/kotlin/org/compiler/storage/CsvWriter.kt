package org.compiler.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

object CsvWriter {
    fun write(file: File, header: List<String>, rows: List<List<String?>>) {
        require(header.isNotEmpty() && header.none { it.isEmpty() }) {
            "El CSV necesita columnas con nombre"
        }
        rows.forEachIndexed { index, row ->
            require(row.size == header.size) {
                "La fila ${index + 1} tiene ${row.size} campos, se esperaban ${header.size}"
            }
        }

        val target = file.toPath().toAbsolutePath()
        val parent = target.parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${file.name}.", ".tmp")
        try {
            Files.newBufferedWriter(temporary, Charsets.UTF_8).use { writer ->
                writer.write(header.joinToString(",") { escape(it) })
                writer.write("\r\n")
                rows.forEach { row ->
                    writer.write(row.joinToString(",") { escape(it) })
                    writer.write("\r\n")
                }
            }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun escape(value: String?): String {
        if (value == null) return ""
        if (value.isEmpty()) return "\"\""
        if (value.none { it == ',' || it == '"' || it == '\r' || it == '\n' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
