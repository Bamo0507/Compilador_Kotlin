package org.compiler.gui.components

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Severity
import org.compiler.engine.StatementOutcome
import org.compiler.runtime.models.CompilationResult


class ChangeSummary(
    val headline: String,

    // Una por archivo escrito, o una por cosa que explicar cuando no se escribio.
    val lines: List<String>,

    val applied: Boolean
)

fun changeSummaryOf(result: CompilationResult?): ChangeSummary? {
    if (result == null) return null

    val failure = firstFailureOf(result)
    if (failure != null) return discarded(result, failure)

    // Un script de puras consultas no cambia nada, y decirlo es mejor que no
    // decir nada: confirma que corrio.
    if (result.written.isEmpty()) {
        return ChangeSummary("No habia nada que escribir", listOf("el script solo consulta"), applied = true)
    }

    return ChangeSummary(
        headline = "Se escribieron ${countOf(result.written.size, "archivo", "archivos")}",
        lines = result.written,
        applied = true
    )
}

/**
 * El mensaje que importa.
 *
 * Dice en que sentencia se corto, cuantas se habian aplicado antes, y que esas
 * tambien se descartaron. Las tres cosas, porque las tres son la pregunta que se
 * hace quien ve el error.
 */
private fun discarded(result: CompilationResult, failure: CompilerError): ChangeSummary {
    val done = result.execution?.outcomes?.count { it is StatementOutcome.Written } ?: 0

    return ChangeSummary(
        headline = "No se escribio nada",
        lines = buildList {
            add("la sentencia de la linea ${failure.location.line} fallo: ${failure.message}")
            if (done > 0) add("las ${countOf(done, "sentencia anterior", "sentencias anteriores")} se descartaron")
            add("el directorio de datos quedo sin cambios")
        },
        applied = false
    )
}

// Las ADVERTENCIAS no cuentan: un script que solo avisa se ejecuta y se escribe.
private fun firstFailureOf(result: CompilationResult): CompilerError? =
    result.errors.firstOrNull { it.severity == Severity.ERROR }

private fun countOf(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"
