package org.compiler.frontend.syntax

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.parser.CompiscriptLexer
import org.compiler.parser.CompiscriptParser

// Corre el lexer y el parser de ANTLR sobre el código fuente.
//
// Devuelve el árbol de análisis, o null si hubo errores sintácticos: sin un árbol
// confiable no tiene sentido seguir a la fase semántica.
object SyntaxAnalyzer {

    fun parse(source: String, diagnostics: Diagnostics): CompiscriptParser.ProgramContext? {
        val errorListener = DiagnosticsErrorListener(diagnostics)

        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        lexer.removeErrorListeners()      // fuera el que escribe a consola
        lexer.addErrorListener(errorListener)

        val parser = CompiscriptParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tree = parser.program()

        return if (diagnostics.hasErrors) null else tree
    }
}