package org.compiler.samples

import java.io.File
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Los programas que ofrece el selector del editor.
 *
 * Agregar un .cps a src/main/resources/programas lo incorpora aqui y a la bateria de
 * pruebas sin tocar codigo, que es la misma propiedad que ya tenia la bateria.
 */
object SamplePrograms {

    const val BLANK_ID = "en-blanco"
    const val DEFAULT_ID = "validos/demo_completa"

    // El nombre legible lo declara el propio archivo en su primera linea. Sin la
    // anotacion se cae al nombre del archivo, asi que un .cps nuevo aparece igual.
    private const val NAME_PREFIX = "// NOMBRE:"

    val all: List<SampleProgram> by lazy { loadAll() }

    val default: SampleProgram get() = all.first { it.id == DEFAULT_ID }

    fun byId(id: String): SampleProgram? = all.firstOrNull { it.id == id }

    // Agrupados y en el orden en que se muestran en el menu.
    fun grouped(): Map<SampleGroup, List<SampleProgram>> =
        all.groupBy { it.group }.toSortedMap(compareBy { it.ordinal })

    private fun loadAll(): List<SampleProgram> {
        val fromFiles = listOf("validos" to SampleGroup.VALID, "invalidos" to SampleGroup.INVALID)
            .flatMap { (folder, group) -> readFolder(folder, group) }

        val demo = fromFiles.firstOrNull { it.id == DEFAULT_ID }

        val starters = listOfNotNull(
            demo?.copy(id = DEFAULT_ID, name = "Programa de demostración", group = SampleGroup.STARTER),
            SampleProgram(BLANK_ID, "Empezar en blanco", SampleGroup.STARTER, "")
        )

        return starters + fromFiles.filterNot { it.id == DEFAULT_ID }
    }

    private fun readFolder(folder: String, group: SampleGroup): List<SampleProgram> =
        resourcePaths("programas/$folder")
            .filter { it.fileName.toString().endsWith(".cps") }
            .sortedBy { it.fileName.toString() }
            .map { path ->
                val fileName = path.fileName.toString().removeSuffix(".cps")
                val source = Files.readString(path)
                SampleProgram(
                    id = "$folder/$fileName",
                    name = nameOf(source) ?: fileName.replace('_', ' '),
                    group = group,
                    source = source
                )
            }

    private fun nameOf(source: String): String? =
        source.lineSequence()
            .firstOrNull { it.startsWith(NAME_PREFIX) }
            ?.removePrefix(NAME_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    // Los recursos se leen como Path y no como File porque en un empaquetado la
    // carpeta vive dentro del jar; el FileSystem de la URI cubre los dos casos.
    private fun resourcePaths(folder: String): List<Path> {
        val url = SamplePrograms::class.java.classLoader.getResource(folder) ?: return emptyList()
        val uri = url.toURI()

        if (uri.scheme != "jar") {
            return Files.list(File(uri).toPath()).use { it.toList() }
        }

        val fileSystem = runCatching { FileSystems.getFileSystem(uri) }
            .recoverCatching { error ->
                if (error is FileSystemNotFoundException) {
                    FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                } else {
                    throw error
                }
            }
            .getOrThrow()

        return Files.list(fileSystem.getPath(folder)).use { it.toList() }
    }
}
