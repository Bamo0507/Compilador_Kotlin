package org.compiler.runtime

import org.compiler.catalog.CatalogLoader
import org.compiler.diagnostics.Diagnostics
import org.compiler.engine.Flush
import org.compiler.engine.Session
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.semantic.NameResolver
import org.compiler.frontend.semantic.SqlChecker
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

        // Etapa A. Sin arbol completo no hay nada que analizar, y es la UNICA que
        // corta por si sola.
        val parseTree = SqlSyntaxAnalyzer.parse(source, diagnostics)
            ?: return CompilationResult.failed(diagnostics, source)

        // El arbol de ANTLR se convierte AQUI y no en la GUI, para que ningun tipo
        // generado por ANTLR salga de este metodo.
        val parseTreeView = parseTree.toTreeView()

        // Etapa B. Puede reportar (un DATE '2026-02-30' parsea pero no es fecha),
        // y aun asi devuelve el AST completo: el error queda en la lista.
        val ast = SqlAstBuilder(diagnostics).visit(parseTree) as Script

        // Etapa C. Lee los .json, ningun .csv: esquema ansioso, datos perezosos.
        val catalog = CatalogLoader(directory, diagnostics).load()

        // Etapa D. Las dos mitades del semantico corren aunque C haya reportado,
        // para que el usuario vea todos sus problemas de una vez.
        val scope = NameResolver(catalog, directory, diagnostics).resolve(ast)
        SqlChecker(catalog, directory, diagnostics).check(ast)

        // Etapa E. Solo si no quedo ningun error. hasErrors ignora las
        // advertencias, asi que un script con avisos corre igual.
        val session = Session(directory, catalog, diagnostics)
        val execution = if (diagnostics.hasErrors) null else session.run(ast)

        // Etapa F. Solo si E tampoco fallo. Es la vuelta atras: una sentencia que
        // revienta a la mitad deja el directorio intacto, porque nada se escribio.
        if (write && execution != null && !diagnostics.hasErrors) {
            Flush.flush(session.changes(), directory)
        }

        return CompilationResult(
            source = source,
            parseTreeView = parseTreeView,
            ast = ast,
            catalog = catalog,
            scope = scope,
            execution = execution,
            errors = diagnostics.all()
        )
    }
}
