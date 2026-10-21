package org.compiler.runtime.models

import org.compiler.catalog.Catalog
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.diagnostics.Severity
import org.compiler.engine.ExecutionResult
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.semantic.symbols.Scope


data class CompilationResult(
    val source: String,

    // Etapa A: el arbol de ANTLR, listo para dibujar. null si no parseo.
    val parseTreeView: TreeNodeView? = null,

    // Etapa B: el AST propio. null si no parseo.
    val ast: Script? = null,

    // Etapa C: la base como estaba al arrancar. null si no se llego a cargar.
    val catalog: Catalog? = null,

    // Las tablas que la etapa C no pudo leer, con su motivo. No estan en el
    // catalogo, asi que la vista las necesita por aqui o no se verian.
    val catalogProblems: Map<String, String> = emptyMap(),

    // Etapa D: el arbol de ambitos, que la interfaz dibuja.
    val scope: Scope? = null,

    // Etapa E: lo que dejo cada sentencia. null si el analisis reporto algo y no
    // se llego a ejecutar.
    val execution: ExecutionResult? = null,

    // Etapa F: los archivos que el volcado escribio, en orden. Vacia si no se
    // escribio nada, que es lo que el panel de cambios tiene que poder decir.
    val written: List<String> = emptyList(),

    val errors: List<CompilerError> = emptyList()
) {
    val results get() = execution?.results.orEmpty()

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
