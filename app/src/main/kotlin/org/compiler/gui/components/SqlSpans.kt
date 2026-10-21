package org.compiler.gui.components

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.compiler.frontend.syntax.UpperCaseCharStream
import org.compiler.parser.SqlLexer

/**
 * De que color va cada pedazo del script.
 *
 * La CLASIFICACION se hace aqui, sin Compose: asi se prueba sin levantar una
 * ventana, y el Composable solo traduce cada familia a un color del tema.
 */
enum class SqlSpanKind {
    KEYWORD,
    TEXT,
    NUMBER,
    COMMENT,
    IDENTIFIER,
    SYMBOL
}

// `end` es exclusivo, como en las subcadenas de Kotlin y en los rangos de
// AnnotatedString.
class SqlSpan(val start: Int, val end: Int, val kind: SqlSpanKind)

/**
 * Los tramos del script, en orden y sin huecos que importen.
 *
 * Se apoya en el lexer de ANTLR, que ya sabe clasificar cada token: escribir un
 * segundo analizador para el resaltado seria pedir que los dos se desincronicen
 * en cuanto la gramatica cambie.
 *
 * Los comentarios y los espacios se descartan con `skip`, asi que NO llegan como
 * tokens. Los comentarios se buscan aparte para poder pintarlos en gris.
 */
fun sqlSpansOf(source: String): List<SqlSpan> {
    val lexer = SqlLexer(UpperCaseCharStream(CharStreams.fromString(source)))
    lexer.removeErrorListeners()

    val stream = CommonTokenStream(lexer)
    stream.fill()

    val spans = stream.tokens
        .filter { it.type != Token.EOF && it.startIndex <= it.stopIndex }
        .map { SqlSpan(it.startIndex, it.stopIndex + 1, kindOf(it)) }

    return (spans + commentSpansOf(source)).sortedBy { it.start }
}

/**
 * Una palabra clave no tiene nombre simbolico: la gramatica la escribe como
 * 'SELECT' y ANTLR le da un literal. Un Identifier es al reves. Esa diferencia es
 * justo la que separa lo que se resalta de lo que no.
 */
private fun kindOf(token: Token): SqlSpanKind {
    val symbolic = SqlLexer.VOCABULARY.getSymbolicName(token.type)

    return when (symbolic) {
        null -> if (token.text.firstOrNull()?.isLetter() == true) SqlSpanKind.KEYWORD else SqlSpanKind.SYMBOL
        "StringLit" -> SqlSpanKind.TEXT
        "IntegerLit", "DecimalLit" -> SqlSpanKind.NUMBER
        "Identifier" -> SqlSpanKind.IDENTIFIER
        else -> SqlSpanKind.SYMBOL
    }
}

/**
 * Los comentarios, buscados sobre el texto.
 *
 * El lexer los tira con `skip` y no quedan en ningun canal, asi que o se cambia la
 * gramatica o se buscan aqui. Se buscan aqui: la gramatica es la fuente de verdad
 * del ANALISIS, y el resaltado no deberia poder cambiarla.
 */
private fun commentSpansOf(source: String): List<SqlSpan> {
    val spans = mutableListOf<SqlSpan>()
    var index = 0

    while (index < source.length) {
        when {
            // Una comilla abierta se salta entera: un `--` dentro de un texto no
            // empieza un comentario.
            source[index] == '\'' -> index = endOfString(source, index)

            source.startsWith("--", index) -> {
                val end = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                spans += SqlSpan(index, end, SqlSpanKind.COMMENT)
                index = end
            }

            source.startsWith("/*", index) -> {
                val closing = source.indexOf("*/", index + 2)
                val end = if (closing >= 0) closing + 2 else source.length
                spans += SqlSpan(index, end, SqlSpanKind.COMMENT)
                index = end
            }

            else -> index++
        }
    }
    return spans
}

// La comilla simple se escapa duplicandola: 'dijo ''hola''' es un solo literal.
private fun endOfString(source: String, start: Int): Int {
    var index = start + 1
    while (index < source.length) {
        if (source[index] != '\'') {
            index++
            continue
        }
        if (index + 1 < source.length && source[index + 1] == '\'') {
            index += 2
            continue
        }
        return index + 1
    }
    return source.length
}
