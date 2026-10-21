package org.compiler.bateria

import org.compiler.engine.StatementOutcome
import org.compiler.runtime.models.CompilationResult
import org.compiler.samples.SqlScript
import org.compiler.samples.SqlScripts
import java.io.File

/**
 * Lo que un script de la bateria dice de si mismo.
 *
 * Las anotaciones van en comentarios al inicio del archivo, asi que el mismo .sql
 * sirve para las dos cosas: lo corre la bateria y lo abre el selector del editor.
 */
class ScriptCase(
    val script: SqlScript,
    val expectation: Expectation,
    // Filas del ULTIMO SELECT del script, que es el que cierra el caso.
    val rows: Int?,
    val line: Int?,
    val message: String?
) {
    val id: String get() = script.id

    override fun toString(): String = "${script.id} · ${script.name}"
}

enum class Expectation {
    // Corre entero y no reporta nada, ni siquiera avisos.
    CLEAN,

    // Reporta al menos un error, y por eso no ejecuta.
    ERROR,

    // Solo avisa, asi que SI ejecuta: es lo que estos casos prueban.
    WARNING
}

object Bateria {

    // Todos los casos, menos la hoja en blanco, que no es un script.
    val cases: List<ScriptCase> by lazy {
        SqlScripts.all.filter { it.id != SqlScripts.BLANK_ID }.map { parse(it) }
    }

    fun of(expectation: Expectation): List<ScriptCase> = cases.filter { it.expectation == expectation }

    private fun parse(script: SqlScript): ScriptCase {
        val expected = annotation(script, "ESPERADO")
            ?: error("${script.id} no declara ESPERADO")

        return ScriptCase(
            script = script,
            expectation = expectationOf(expected, script.id),
            rows = annotation(script, "FILAS")?.toIntOrNull(),
            line = annotation(script, "LINEA")?.toIntOrNull(),
            message = annotation(script, "MENSAJE")
        )
    }

    private fun expectationOf(text: String, id: String): Expectation = when (text) {
        "sin errores" -> Expectation.CLEAN
        "error" -> Expectation.ERROR
        "advertencia" -> Expectation.WARNING
        else -> error("$id declara un ESPERADO que no se reconoce: '$text'")
    }

    private fun annotation(script: SqlScript, key: String): String? =
        script.source.lineSequence()
            .takeWhile { it.startsWith("--") || it.isBlank() }
            .firstOrNull { it.startsWith("-- $key:") }
            ?.removePrefix("-- $key:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * El estado del directorio, para comparar antes y despues.
 *
 * Cada script de la bateria es autosuficiente: crea sus tablas, las usa y las
 * borra, asi que el directorio tiene que quedar como estaba. Eso es lo que deja
 * correrlos en cualquier orden y las veces que haga falta.
 */
fun snapshot(directory: File): Map<String, String> =
    directory.walkTopDown()
        .filter { it.isFile }
        .associate { it.relativeTo(directory).path to it.readText() }

// Las filas del ultimo SELECT, o null si el script no consulta nada.
fun lastGrid(result: CompilationResult): Int? =
    result.execution?.outcomes?.filterIsInstance<StatementOutcome.Rows>()?.lastOrNull()?.result?.rows?.size

fun describe(result: CompilationResult): String =
    result.errors.joinToString("\n") { "  linea ${it.location.line}: ${it.message}" }
