package org.compiler.frontend.syntax

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.compiler.parser.SqlLexer

/**
 * Un token, en la forma que la interfaz entiende.
 *
 * Existe para que ningun tipo de `org.compiler.parser` salga de la etapa de
 * sintaxis: la GUI no deberia saber que el analizador lo genera ANTLR, igual que
 * no sabe que el arbol es un ParserRuleContext.
 */
class TokenView(
    // El nombre simbolico: "SELECT" para una palabra clave, "Identifier" para un
    // nombre.
    val type: String,

    // El texto TAL CUAL aparece en el script.
    val lexeme: String,

    val line: Int,
    val column: Int
)

/**
 * Los tokens de un script.
 *
 * No hay que escribir un lexer: el de ANTLR ya produce la lista, y esto solo la
 * traduce. Los espacios y los comentarios NO salen, porque la gramatica los
 * descarta con `skip` y nunca llegan a ser tokens.
 *
 * El detalle que importa es el lexema. El lexer ve el texto en mayusculas, porque
 * `UpperCaseCharStream` se las da asi para que `select` case con 'SELECT'. Pero
 * ese envoltorio solo cambia lo que el lexer COMPARA: el texto que devuelve lo
 * pide al flujo original, asi que `token.text` ya trae lo que el usuario tecleo.
 * Si no fuera asi, la vista mentiria sobre lo que esta escrito.
 */
fun tokensOf(source: String): List<TokenView> {
    val lexer = SqlLexer(UpperCaseCharStream(CharStreams.fromString(source)))

    // Esta es una VISTA: un error lexico ya lo reporto el pipeline, y volver a
    // escribirlo en la consola seria ruido. Se muestran los tokens que si se
    // reconocieron.
    lexer.removeErrorListeners()

    val stream = CommonTokenStream(lexer)
    stream.fill()

    return stream.tokens
        .filter { it.type != Token.EOF }
        .map { token ->
            TokenView(
                type = nameOf(token.type),
                lexeme = token.text.orEmpty(),

                // ANTLR cuenta las columnas desde 0 y las lineas desde 1. Se
                // normaliza aqui, igual que en LexemeLocation.
                line = token.line,
                column = token.charPositionInLine + 1
            )
        }
}

/**
 * El nombre de un tipo de token.
 *
 * Una palabra clave escrita en la gramatica como 'SELECT' no tiene nombre
 * simbolico, solo el literal entre comillas; un Identifier es al reves. Se toma el
 * que haya.
 */
private fun nameOf(type: Int): String {
    val vocabulary = SqlLexer.VOCABULARY
    return vocabulary.getSymbolicName(type)
        ?: vocabulary.getLiteralName(type)?.removeSurrounding("'")
        ?: "<$type>"
}
