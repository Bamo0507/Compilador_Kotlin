package org.compiler.samples

import java.io.File
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class SqlScript(
    val id: String,
    val name: String,
    val source: String
)

/**
 * Los scripts que ofrece el selector del editor.
 *
 * Agregar un .sql a src/main/resources/scripts lo incorpora sin tocar codigo, que
 * es la misma propiedad que tienen los programas de la bateria.
 */
object SqlScripts {

    const val BLANK_ID = "en-blanco"

    // Con cual abre la ventana. Es la demo que se arma su propia base, asi que
    // corre de entrada aunque el directorio de datos este vacio.
    const val DEFAULT_ID = "demo_rejilla"

    // El nombre legible lo declara el propio archivo en su primera linea. Sin la
    // anotacion se cae al nombre del archivo, asi que un .sql nuevo aparece igual.
    private const val NAME_PREFIX = "-- NOMBRE:"

    private const val FOLDER = "scripts"

    val all: List<SqlScript> by lazy { loadAll() }

    // El `?:` no es defensa de mas: con la carpeta vacia el .sql de la demo no
    // existe, y la ventana tiene que abrir igual.
    val default: SqlScript get() = byId(DEFAULT_ID) ?: all.first()

    fun byId(id: String): SqlScript? = all.firstOrNull { it.id == id }

    /**
     * Con la carpeta vacia quedan solo las dos opciones fijas.
     *
     * No es un caso raro: la carpeta nace vacia, y el selector tiene que servir
     * igual desde el primer dia.
     */
    private fun loadAll(): List<SqlScript> =
        listOf(SqlScript(BLANK_ID, "Empezar en blanco", "")) + readFolder()

    private fun readFolder(): List<SqlScript> =
        resourcePaths()
            .filter { it.fileName.toString().endsWith(".sql") }
            .sortedBy { it.fileName.toString() }
            .map { path ->
                val fileName = path.fileName.toString().removeSuffix(".sql")
                val source = Files.readString(path)
                SqlScript(
                    id = fileName,
                    name = nameOf(source) ?: fileName.replace('_', ' '),
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
    private fun resourcePaths(): List<Path> {
        val url = SqlScripts::class.java.classLoader.getResource(FOLDER) ?: return emptyList()
        val uri = url.toURI()

        if (uri.scheme != "jar") {
            return Files.list(File(uri).toPath()).use { it.toList() }
        }

        val fileSystem = runCatching { FileSystems.getFileSystem(uri) }
            .recoverCatching { error ->
                if (error is FileSystemNotFoundException) FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                else throw error
            }
            .getOrNull() ?: return emptyList()

        return Files.list(fileSystem.getPath(FOLDER)).use { it.toList() }
    }
}
