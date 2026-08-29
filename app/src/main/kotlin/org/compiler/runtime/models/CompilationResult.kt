package org.compiler.runtime.models

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.semantic.models.GarbageCollectorReport
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.interpreter.ExecutionResult

/**
 * Todo lo que produce una compilacion. La GUI lee de aqui y no llama a nada mas.
 *
 * Los campos son nullables a proposito: si el fuente no parsea no hay AST, y los
 * tipos obligan a la GUI a mostrar "no disponible" en vez de reventar.
 *
 * Fijate en lo que NO aparece: ningun tipo de org.compiler.parser. El arbol de
 * ANTLR entra como TreeNodeView, ya convertido por el pipeline.
 */
data class CompilationResult(
    val source: String,
    val parseTreeView: TreeNodeView?,
    val ast: Program?,
    val globalScope: Scope?,
    val garbageCollectorReport: GarbageCollectorReport?,
    val errors: List<CompilerError>,
    val execution: ExecutionResult?
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    val lexicalErrors: List<CompilerError.LexerError>
        get() = errors.filterIsInstance<CompilerError.LexerError>()

    val syntaxErrors: List<CompilerError.ParserError>
        get() = errors.filterIsInstance<CompilerError.ParserError>()

    val semanticErrors: List<CompilerError.SemanticError>
        get() = errors.filterIsInstance<CompilerError.SemanticError>()

    companion object {
        // Cuando la sintaxis falla no hay AST ni tabla de simbolos, pero SI hay
        // errores que mostrar.
        fun failed(diagnostics: Diagnostics, source: String) = CompilationResult(
            source = source,
            parseTreeView = null,
            ast = null,
            globalScope = null,
            garbageCollectorReport = null,
            errors = diagnostics.all(),
            execution = null
        )
    }
}
