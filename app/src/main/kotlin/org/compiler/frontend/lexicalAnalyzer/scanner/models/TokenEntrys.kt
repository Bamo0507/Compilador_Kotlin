package org.compiler.frontend.lexicalAnalyzer.scanner.models

import org.compiler.frontend.models.Token
import java.util.LinkedList

object TokenEntrys {
    val tokens: LinkedList<Token> = LinkedList()
    val errors: LinkedList<ErrorEntry> = LinkedList()

    fun addToken(token: Token) = tokens.add(token)
    fun addError(error: ErrorEntry) = errors.add(error)
    fun clear() {
        tokens.clear()
        errors.clear()
    }
}
