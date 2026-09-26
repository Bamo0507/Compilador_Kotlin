package org.compiler.diagnostics

import org.compiler.models.LexemeLocation

// Una advertencia se reporta y se muestra, pero no detiene la ejecucion.
enum class Severity {
    ERROR,
    WARNING
}

/**
 * Todo lo que el compilador tiene que decirle al usuario, de cualquier etapa.
 *
 * ExecutionError no es una excepcion a proposito: el motor lo reporta y sigue
 * evaluando lo que pueda, y la lista de la GUI lo muestra junto a los demas sin
 * saber de que etapa viene.
 */
sealed interface CompilerError {
    val location: LexemeLocation
    val message: String

    // Casi todo es error. Solo SemanticError puede bajarla a advertencia.
    val severity: Severity get() = Severity.ERROR

    data class LexerError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    data class ParserError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    data class SemanticError(
        override val location: LexemeLocation,
        override val message: String,
        override val severity: Severity = Severity.ERROR
    ) : CompilerError

    // Solo se detecta ejecutando: clave repetida, llave foranea rota, una
    // subconsulta escalar que devolvio mas de una fila.
    data class ExecutionError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError
}
