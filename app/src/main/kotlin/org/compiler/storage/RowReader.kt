package org.compiler.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Una sola fila del CSV, por su desplazamiento en bytes.
 *
 * Es la otra mitad del indice: el `.idx` dice en que byte esta la fila y esto la
 * trae sin recorrer el archivo. Es lo que hace que `WHERE id = 7` sobre mil filas
 * lea una.
 */
object RowReader {

    /**
     * La fila que empieza en `offset`, ya separada en campos.
     *
     * Los desplazamientos salen de `CsvWriter.write`, que los cuenta en BYTES
     * mientras escribe; por eso aqui tambien se cuentan bytes.
     */
    fun readAt(file: File, offset: Long): List<String?> {
        val line = lineAt(file, offset)
        if (line.isEmpty()) throw IOException("${file.name}: no hay fila en el byte $offset")

        // Se reusa el parser del CsvReader: una fila con comas o comillas adentro
        // se separa con las mismas reglas que el archivo entero.
        return CsvReader.read(line, file.name).lastOrNull()
            ?: throw IOException("${file.name}: la fila del byte $offset no se pudo leer")
    }

    /**
     * Los bytes desde `offset` hasta el salto de linea, decodificados como UTF-8.
     *
     * No se usa `RandomAccessFile.readLine()` A PROPOSITO: ese metodo lee cada byte
     * como un caracter, que es latin-1, y un nombre con tilde saldria partido en
     * dos caracteres raros. Hay que juntar los bytes y decodificarlos de una vez.
     */
    private fun lineAt(file: File, offset: Long): String {
        RandomAccessFile(file, "r").use { handle ->
            if (offset < 0 || offset >= handle.length()) {
                throw IOException("${file.name}: el byte $offset esta fuera del archivo")
            }
            handle.seek(offset)

            val bytes = ByteArrayOutputStream()
            var byte = handle.read()
            while (byte != -1 && byte != NEW_LINE) {
                bytes.write(byte)
                byte = handle.read()
            }
            // El archivo se escribe con \r\n; el \r queda del lado de la linea.
            return String(bytes.toByteArray(), Charsets.UTF_8).removeSuffix("\r")
        }
    }

    private const val NEW_LINE = '\n'.code
}
