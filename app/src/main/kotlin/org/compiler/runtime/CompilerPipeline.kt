package org.compiler.runtime

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.AstBuilder
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.semantic.DeclarationCollector
import org.compiler.frontend.semantic.FlowAnalyzer
import org.compiler.frontend.semantic.LivenessReportBuilder
import org.compiler.frontend.semantic.TypeChecker
import org.compiler.frontend.syntax.SyntaxAnalyzer
import org.compiler.frontend.syntax.toTreeView
import org.compiler.interpreter.Interpreter
import org.compiler.runtime.models.CompilationResult

/**
 * Encadena todas las fases del compilador.
 *
 * Siempre devuelve lo que alcanzo a producir, para que la GUI muestre resultados
 * parciales: un fuente que no parsea no tiene AST, pero si tiene errores.
 */
object CompilerPipeline {

    fun compile(source: String, execute: Boolean = true): CompilationResult {
        val diagnostics = Diagnostics()

        // Etapa A: sintaxis. Es la unica que corta el pipeline: sin arbol no hay
        // nada que analizar.
        val parseTree = SyntaxAnalyzer.parse(source, diagnostics)
            ?: return CompilationResult.failed(diagnostics, source)

        // El arbol de ANTLR se convierte AQUI y no en la GUI, para que el tipo
        // ProgramContext no salga de esta funcion.
        val parseTreeView = parseTree.toTreeView()

        // Etapa B: AST propio.
        val ast = AstBuilder().visit(parseTree) as Program

        // Etapa C: pasada 1, declaraciones.
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)

        // Etapa D: pasada 2, tipos. Corre AUNQUE la etapa C haya reportado errores,
        // asi el usuario ve todos sus problemas de una vez y no de uno en uno.
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        // Etapa E: flujo y vivacidad.
        FlowAnalyzer(diagnostics).analyze(ast)
        val garbageCollectorReport = LivenessReportBuilder().build(collector.globalScope)

        // Etapa F: ejecucion, solo si no quedo ningun error. Ejecutar codigo mal
        // tipado da basura en vez de un mensaje util.
        val execution =
            if (execute && !diagnostics.hasErrors) Interpreter().run(ast) else null

        return CompilationResult(
            source = source,
            parseTreeView = parseTreeView,
            ast = ast,
            globalScope = collector.globalScope,
            garbageCollectorReport = garbageCollectorReport,
            errors = diagnostics.all(),
            execution = execution
        )
    }
}
