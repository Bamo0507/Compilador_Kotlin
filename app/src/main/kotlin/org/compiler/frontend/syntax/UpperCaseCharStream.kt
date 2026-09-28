package org.compiler.frontend.syntax

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.misc.Interval

class UpperCaseCharStream(private val source: CharStream) : CharStream {

    // EOF es -1 y no tiene mayuscula: pasa tal cual.
    override fun LA(i: Int): Int {
        val c = source.LA(i)
        return if (c <= 0) c else Character.toUpperCase(c)
    }

    override fun getText(interval: Interval): String = source.getText(interval)

    // El resto solo delega: posicion, marcas y tamano no cambian con las mayusculas.
    override fun consume() = source.consume()

    override fun mark(): Int = source.mark()

    override fun release(marker: Int) = source.release(marker)

    override fun index(): Int = source.index()

    override fun seek(index: Int) = source.seek(index)

    override fun size(): Int = source.size()

    override fun getSourceName(): String = source.sourceName
}
