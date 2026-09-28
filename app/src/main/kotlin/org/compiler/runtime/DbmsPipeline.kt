package org.compiler.runtime

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import org.compiler.frontend.syntax.toTreeView
import org.compiler.runtime.models.CompilationResult
import org.compiler.storage.DataDirectory


object DbmsPipeline {

    fun run(
        source: String,
        directory: DataDirectory = DataDirectory.DEFAULT,
        write: Boolean = true
    ): CompilationResult {
        val diagnostics = Diagnostics()

        // Etapa A. Sin arbol completo no hay nada que analizar.
        val parseTree = SqlSyntaxAnalyzer.parse(source, diagnostics)
            ?: return CompilationResult.failed(diagnostics, source)

        // El arbol de ANTLR se convierte AQUI y no en la GUI, para que ningun tipo
        // generado por ANTLR salga de este metodo.
        val parseTreeView = parseTree.toTreeView()

        // Etapa B. Puede reportar (un DATE '2026-02-30' parsea pero no es fecha),
        // y aun asi devuelve el AST completo: el error queda en la lista.
        val ast = SqlAstBuilder(diagnostics).visit(parseTree) as Script

        // Las etapas C a F se conectan en las fases 4, 5, 6 y 7.

        return CompilationResult(
            source = source,
            parseTreeView = parseTreeView,
            ast = ast,
            errors = diagnostics.all()
        )
    }
}
