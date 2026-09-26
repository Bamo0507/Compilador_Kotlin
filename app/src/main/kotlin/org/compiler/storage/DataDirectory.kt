package org.compiler.storage

import java.io.File

/**
 * El directorio donde viven las tablas.
 *
 * Es una CLASE y no un `object` con la ruta escrita adentro, porque los tests
 * necesitan apuntarla a un temporal. Si fuera fija, toda la bateria escribiria
 * sobre la base real y el orden en que corran los tests cambiaria sus resultados.
 */
class DataDirectory(private val path: File) {

    fun root(): File = path.apply { if (!exists()) mkdirs() }

    fun csv(table: String) = File(root(), "$table.csv")

    fun json(table: String) = File(root(), "$table.json")

    // La existencia de la tabla la da el CSV, no el JSON: el directorio ES el
    // catalogo, asi que no hay un archivo aparte que se pueda quedar viejo.
    fun tables(): List<String> =
        root().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "csv" }
            .map { it.nameWithoutExtension }
            .sorted()

    companion object {
        // La de produccion. Relativa a la raiz del repo y no al home del usuario,
        // porque el punto de guardar en CSV es poder verlos al lado del codigo.
        val DEFAULT = DataDirectory(File("data"))
    }
}
