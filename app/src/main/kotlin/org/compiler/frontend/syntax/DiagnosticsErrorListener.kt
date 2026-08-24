package org.compiler.frontend.syntax

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.models.LexemeLocation

/**
 * Traduce los errores de ANTLR a CompilerError y los manda a Diagnostics.
 *
 * ANTLR llama a este mismo metodo para los errores lexicos y los sintacticos: se
 * distinguen por quien lo reporta, y solo el lexer hereda de Lexer.
 */
class DiagnosticsErrorListener(
    private val diagnostics: Diagnostics
) : BaseErrorListener() {

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        // ANTLR cuenta las columnas desde 0; LexemeLocation desde 1.
        val location = LexemeLocation(line = line, position = charPositionInLine + 1)

        val error = if (recognizer is Lexer) {
            CompilerError.LexerError(location, msg)
        } else {
            CompilerError.ParserError(location, msg)
        }

        diagnostics.report(error)
    }
}
