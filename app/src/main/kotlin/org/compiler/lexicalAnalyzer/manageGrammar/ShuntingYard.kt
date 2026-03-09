package org.compiler.lexicalAnalyzer.manageGrammar

import org.compiler.lexicalAnalyzer.manageGrammar.utils.ALL_OPERATORS
import org.compiler.lexicalAnalyzer.manageGrammar.utils.UNARY_OPERATORS
import org.compiler.lexicalAnalyzer.manageGrammar.utils.getPrecedence

// Converts a normalized infix regex to postfix using the Shunting-Yard algorithm.
// Input is expected to already be normalized
fun infixToPostfix(regex: String): String {
    val postfix = StringBuilder()
    val stack = ArrayDeque<Char>()

    var index = 0
    while (index < regex.length) {
        val current = regex[index]
        when {
            // Single-quoted char literal — atomic operand, goes directly to output
            current == '\'' -> {
                postfix.append(current); index++ // opening quote
                while (index < regex.length && regex[index] != '\'') {
                    postfix.append(regex[index]); index++
                }
                if (index < regex.length) { postfix.append(regex[index]); index++ } // closing quote
            }

            // Unary operators go directly to output (already postfix in the input)
            current in UNARY_OPERATORS -> {
                postfix.append(current)
                index++
            }

            current == '(' -> {
                stack.addFirst(current)
                index++
            }

            // Binary operators and '.' — apply precedence rules
            current in ALL_OPERATORS -> {
                while (stack.isNotEmpty() && stack.first() != '(' &&
                    getPrecedence(stack.first()) >= getPrecedence(current)
                ) {
                    postfix.append(stack.removeFirst())
                }
                stack.addFirst(current)
                index++
            }

            current == ')' -> {
                while (stack.isNotEmpty() && stack.first() != '(') {
                    postfix.append(stack.removeFirst())
                }
                if (stack.isNotEmpty() && stack.first() == '(') stack.removeFirst()
                index++
            }

            // Regular operand (single char, digit, ε, etc.)
            else -> {
                postfix.append(current)
                index++
            }
        }
    }

    while (stack.isNotEmpty()) {
        val top = stack.removeFirst()
        if (top == '(') error("Unbalanced parentheses: missing ')'")
        postfix.append(top)
    }

    return postfix.toString()
}
