package org.compiler.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

object CsvWriter {

    /**
     * Escribe la tabla y devuelve el desplazamiento en bytes donde quedo cada
     * fila, en el mismo orden en que se recibieron.
     *
     * Los desplazamientos son lo que hace util un indice: con ellos se salta
     * directo a una fila en vez de recorrer el archivo. Salen de contar bytes
     * mientras se escribe, asi que no cuestan un recorrido aparte.
     */
    fun write(file: File, header: List<String>, rows: List<List<String?>>): List<Long> {
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

        val offsets = ArrayList<Long>(rows.size)
        try {
            Files.newBufferedWriter(temporary, Charsets.UTF_8).use { writer ->
                var offset = lineOf(header.map { it }).let { line ->
                    writer.write(line)
                    byteLength(line)
                }

                rows.forEach { row ->
                    offsets.add(offset)
                    val line = lineOf(row)
                    writer.write(line)
                    offset += byteLength(line)
                }
            }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return offsets
    }

    private fun lineOf(fields: List<String?>): String =
        fields.joinToString(",") { escape(it) } + LINE_BREAK

    // El largo en BYTES, no en caracteres: una tilde ocupa dos en UTF-8 y un
    // desplazamiento contado en caracteres apuntaria al lugar equivocado.
    private fun byteLength(line: String): Long =
        line.toByteArray(Charsets.UTF_8).size.toLong()

    private fun escape(value: String?): String {
        if (value == null) return ""
        if (value.isEmpty()) return "\"\""
        if (value.none { it == ',' || it == '"' || it == '\r' || it == '\n' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private const val LINE_BREAK = "\r\n"
}
