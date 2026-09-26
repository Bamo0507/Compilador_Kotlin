package org.compiler.runtime.models

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity

/**
 * Todo lo que produce una corrida. La GUI lee de aqui y no llama a nada mas.
 *
 * Los campos que las etapas van agregando son nulables a proposito: un script que
 * no parsea no tiene AST, pero si tiene errores, y la GUI debe poder mostrar
 * resultados parciales en vez de reventar.
 */
data class CompilationResult(
    val source: String,
    val errors: List<CompilerError> = emptyList()
) {
    // Las ADVERTENCIA no cuentan: un script que solo tiene avisos se ejecuta.
    val hasErrors: Boolean get() = errors.any { it.severity == Severity.ERROR }

    val lexicalErrors: List<CompilerError.LexerError>
        get() = errors.filterIsInstance<CompilerError.LexerError>()

    val syntaxErrors: List<CompilerError.ParserError>
        get() = errors.filterIsInstance<CompilerError.ParserError>()

    val semanticErrors: List<CompilerError.SemanticError>
        get() = errors.filterIsInstance<CompilerError.SemanticError>()

    val executionErrors: List<CompilerError.ExecutionError>
        get() = errors.filterIsInstance<CompilerError.ExecutionError>()

    val warnings: List<CompilerError>
        get() = errors.filter { it.severity == Severity.WARNING }

    companion object {
        // Cuando la sintaxis falla no hay arbol ni catalogo, pero SI hay errores
        // que mostrar.
        fun failed(diagnostics: Diagnostics, source: String) =
            CompilationResult(source = source, errors = diagnostics.all())
    }
}
