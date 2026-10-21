package org.compiler.samples

import java.io.File
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class SqlScript(
    val id: String,
    val name: String,
    // El grupo que lo encabeza en el menu. Vacio para los que no viven en una
    // subcarpeta, que es el caso de la hoja en blanco.
    val group: String,
    val source: String
)

/**
 * Los scripts que ofrece el selector del editor.
 *
 * Agregar un .sql a src/main/resources/scripts lo incorpora sin tocar codigo, y
 * la subcarpeta donde cae es el grupo que lo encabeza en el menu. Es la misma
 * carpeta que lee la bateria de pruebas, asi que cada caso se puede correr a
 * mano desde la interfaz y automaticamente desde los tests.
 */
object SqlScripts {

    const val BLANK_ID = "en-blanco"

    // Con cual abre la ventana. Es la prueba completa, que se arma su propia base,
    // asi que corre de entrada aunque el directorio de datos este vacio.
    const val DEFAULT_ID = "01_completo/01_todo_el_sql"

    // El nombre legible lo declara el propio archivo en su primera linea. Sin la
    // anotacion se cae al nombre del archivo, asi que un .sql nuevo aparece igual.
    private const val NAME_PREFIX = "-- NOMBRE:"

    private const val FOLDER = "scripts"

    // Los numeros al frente ordenan las carpetas y los archivos; no son parte del
    // nombre que se muestra.
    private val ORDER_PREFIX = Regex("^\\d+_")

    val all: List<SqlScript> by lazy { loadAll() }

    // El `?:` no es defensa de mas: con la carpeta vacia el .sql de la prueba
    // completa no existe, y la ventana tiene que abrir igual.
    val default: SqlScript get() = byId(DEFAULT_ID) ?: all.first()

    fun byId(id: String): SqlScript? = all.firstOrNull { it.id == id }

    // Los grupos en el orden del menu, con sus scripts. El grupo vacio no lleva
    // encabezado.
    fun grouped(): Map<String, List<SqlScript>> = all.groupBy { it.group }

    /**
     * Con la carpeta vacia queda solo la opcion fija.
     *
     * No es un caso raro: la carpeta nace vacia, y el selector tiene que servir
     * igual desde el primer dia.
     */
    private fun loadAll(): List<SqlScript> =
        listOf(SqlScript(BLANK_ID, "Empezar en blanco", "", "")) + readFolder()

    private fun readFolder(): List<SqlScript> {
        val roots = resourceRoots()
        return roots.flatMap { root -> scriptsUnder(root) }.sortedBy { it.id }
    }

    private fun scriptsUnder(root: Path): List<SqlScript> =
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".sql") }
                .toList()
        }.map { path -> scriptAt(root, path) }

    private fun scriptAt(root: Path, path: Path): SqlScript {
        val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
        val id = relative.removeSuffix(".sql")
        val source = Files.readString(path)
        val folder = id.substringBeforeLast('/', "")

        return SqlScript(
            id = id,
            name = nameOf(source) ?: id.substringAfterLast('/').replace('_', ' '),
            group = readable(folder).uppercase(),
            source = source
        )
    }

    private fun readable(folder: String): String =
        folder.removePrefix("/").replace(ORDER_PREFIX, "").replace('_', ' ')

    private fun nameOf(source: String): String? =
        source.lineSequence()
            .firstOrNull { it.startsWith(NAME_PREFIX) }
            ?.removePrefix(NAME_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    // Los recursos se leen como Path y no como File porque en un empaquetado la
    // carpeta vive dentro del jar; el FileSystem de la URI cubre los dos casos.
    private fun resourceRoots(): List<Path> {
        val url = SqlScripts::class.java.classLoader.getResource(FOLDER) ?: return emptyList()
        val uri = url.toURI()

        if (uri.scheme != "jar") return listOf(File(uri).toPath())

        val fileSystem = runCatching { FileSystems.getFileSystem(uri) }
            .recoverCatching { error ->
                if (error is FileSystemNotFoundException) FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                else throw error
            }
            .getOrNull() ?: return emptyList()

        return listOf(fileSystem.getPath(FOLDER))
    }
}
