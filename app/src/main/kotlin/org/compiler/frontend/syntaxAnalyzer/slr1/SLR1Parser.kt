package org.compiler.frontend.syntaxAnalyzer.slr1

import org.compiler.frontend.models.Token
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.runtime.TokenStream
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseError
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseStep
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table

object SLR1Parser {

    fun parse(
        tokens: List<Token>,
        ignoredCategories: Set<String>,
        table: SLR1Table
    ): ParseResult {
        val stream = TokenStream(tokens, ignoredCategories)

        // Initial state 0 is already on the state stack; the symbol and tree stacks are empty.
        val stateStack = mutableListOf(0)
        val symbolStack = mutableListOf<Symbol>()
        val treeStack = mutableListOf<ParseTree>()
        val trace = mutableListOf<ParseStep>()

        while (true) {
            val currentState = stateStack.last()
            val nextToken = stream.peek()
            val lookahead: Symbol =
                if (nextToken != null) Symbol.Terminal(nextToken.category)
                else Symbol.EndMarker

            val action = table.action[currentState to lookahead]

            // No entry in ACTION means syntax error: the parser cannot continue.
            if (action == null) {
                val expectedTokens = table.action.keys
                    .filter { it.first == currentState }
                    .map { it.second }
                    .toSet()
                val error = ParseError(
                    message = "Unexpected token '${nextToken?.lexeme ?: "<EOF>"}' at state $currentState",
                    location = null,
                    foundToken = nextToken,
                    expectedTokens = expectedTokens
                )
                return ParseResult.Rejected(trace, error, treeStack.lastOrNull())
            }

            // Record the step BEFORE applying the action: the trace shows the state
            // that decided what to do, not the state that resulted from doing it.
            trace.add(ParseStep(
                stack = stateStack.toList(),
                symbols = symbolStack.toList(),
                remainingInput = stream.remaining(),
                action = action
            ))

            when (action) {
                is Action.Shift -> {
                    // Consume the token, push it on the symbol stack and the tree stack,
                    // and push the new state on the state stack.
                    val consumed = stream.consume()!!
                    val terminal = Symbol.Terminal(consumed.category)
                    symbolStack.add(terminal)
                    treeStack.add(ParseTree.LeafNode(terminal, consumed))
                    stateStack.add(action.nextState)
                }

                is Action.Reduce -> {
                    val production = action.production
                    val isEpsilon = production.body == listOf(Symbol.Epsilon)

                    // Pop |body| from all three stacks. Epsilon productions don't pop anything;
                    // they reduce a logically empty body and place an EpsilonNode as the only child.
                    val children = mutableListOf<ParseTree>()
                    if (isEpsilon) {
                        children.add(ParseTree.EpsilonNode)
                    } else {
                        for (i in 0 until production.body.size) {
                            stateStack.removeLast()
                            symbolStack.removeLast()
                            // add(0, ...) reverses the pop order so children stay left-to-right.
                            children.add(0, treeStack.removeLast())
                        }
                    }

                    // Push the head: a symbol and a fresh InternalNode for the parse tree.
                    symbolStack.add(production.head)
                    treeStack.add(ParseTree.InternalNode(production.head, production, children))

                    // GOTO: consult the table with the state now on top and the head we just pushed.
                    val topState = stateStack.last()
                    val nextState = table.goto[topState to production.head]
                        ?: error("Missing GOTO entry for state $topState with symbol ${production.head.name}")
                    stateStack.add(nextState)
                }

                Action.Accept -> {
                    // Tree stack holds exactly one tree: the root of the parse tree.
                    return ParseResult.Accepted(trace, treeStack.last())
                }
            }
        }
    }
}
