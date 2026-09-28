package org.compiler.frontend.syntax

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.compiler.diagnostics.Diagnostics
import org.compiler.parser.SqlLexer
import org.compiler.parser.SqlParser


object SqlSyntaxAnalyzer {

    // Devuelve null si ESTA llamada produjo un error lexico o sintactico.
    fun parse(source: String, diagnostics: Diagnostics): SqlParser.ScriptContext? {
        val before = syntaxErrorCount(diagnostics)
        val tree = parserFor(source, diagnostics).script()
        return if (syntaxErrorCount(diagnostics) > before) null else tree
    }

    internal fun parserFor(source: String, diagnostics: Diagnostics): SqlParser {
        val listener = DiagnosticsErrorListener(diagnostics)

        val lexer = SqlLexer(UpperCaseCharStream(CharStreams.fromString(source)))

        // la lista de la GUI, no a la terminal.
        lexer.removeErrorListeners()
        lexer.addErrorListener(listener)

        val parser = SqlParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(listener)

        return parser
    }

    private fun syntaxErrorCount(diagnostics: Diagnostics): Int =
        diagnostics.lexical().size + diagnostics.syntactic().size
}
