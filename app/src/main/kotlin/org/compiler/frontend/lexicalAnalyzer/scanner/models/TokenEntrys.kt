package org.compiler.frontend.lexicalAnalyzer.scanner.models

import org.compiler.frontend.models.Token
import java.util.LinkedList

object TokenEntrys {
    val tokens: LinkedList<Token> = LinkedList()

    fun addToken(token: Token) = tokens.add(token)
    fun clear() = tokens.clear()
}
