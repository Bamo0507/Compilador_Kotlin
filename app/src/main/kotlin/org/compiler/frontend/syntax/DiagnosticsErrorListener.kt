package org.compiler.frontend.syntax

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.models.LexemeLocation

// Recibe los errores que ANTLR detecta y los traduce a CompilerError.
//
// ANTLR usa el mismo callback para errores léxicos y sintácticos; se distinguen
// por quién lo reporta: si el recognizer es un Lexer, es un error léxico.
class DiagnosticsErrorListener(
    private val diagnostics: Diagnostics
) : BaseErrorListener() {

    override fun syntaxError(
        // * -> es como un tipo abstracto
        // evitmos casarnos con el formato que viene de ANTLR
      // para errores lexicos (Recognizer<Int, LexerATNSimulator>) o 
        // el de sintactico Recognizer<Token, ParserATNSimulator>
        recognizer: Recognizer<*, *>?,
        // El token que hace la ofensa, null si es lexico
        offendingSymbol: Any?,
        line: Int,                  // ya viene 1-based
        charPositionInLine: Int,    // viene 0-based

        // El mensaje que ANTLR ya armó. Viene en INGLES y con su propio formato:
        //   "token recognition error at: '@'"
        //   "mismatched input ';' expecting {'let', 'var', ...}"
        // Se usa tal cual. Reescribirlo en español seria posible a partir de
        // offendingSymbol, pero es trabajo extra sin ganar precision.
        msg: String,

        // La excepcion con el detalle interno del algoritmo: que regla estaba
        // reconociendo y que esperaba encontrar. Viene null cuando ANTLR se
        // recupero del error sin necesidad de lanzar, que es el caso mas comun.
        // No se usa: line, charPositionInLine y msg ya traen todo lo que el IDE
        // necesita mostrar.
        e: RecognitionException?
    ) {
        // ANTLR cuenta las columnas desde 0; LexemeLocation las cuenta desde 1.
        val location = LexemeLocation(line = line, position = charPositionInLine + 1)

        // El recognizer es la instancia que ANTLR usa para reconocer la entrada y
        // que reporta el error: el lexer o el parser. La verificacion `is Lexer`
        // funciona por la jerarquia de clases de ANTLR:
        //
        //   CompiscriptLexer  -> Lexer  -> Recognizer
        //   CompiscriptParser -> Parser -> Recognizer
        //
        // Los dos son Recognizer, pero solo el lexer hereda de Lexer. Por eso
        // `is Lexer` es verdadero exclusivamente cuando el error lo reporto el
        // lexer, y eso es lo unico que permite separarlos: ANTLR llama a ESTE
        // MISMO metodo para los dos.
        //
        // Este if produce DOS de los tres niveles de error del IDE. El tercero,
        // SemanticError, no pasa por aqui: lo reportan las fases 3, 4 y 5
        // llamando a diagnostics.report(...) directo.
        //
        // Sin el if habria que elegir un solo tipo para los dos casos, y el IDE
        // perderia esta distincion:
        //
        //   let x = @@@;   el @ no es un caracter de Compiscript      -> LexerError
        //   let x = ;      los caracteres son validos, el orden no    -> ParserError
        val error = if (recognizer is Lexer) {
            CompilerError.LexerError(location, msg)
        } else {
            CompilerError.ParserError(location, msg)
        }

        diagnostics.report(error)
    }
}
