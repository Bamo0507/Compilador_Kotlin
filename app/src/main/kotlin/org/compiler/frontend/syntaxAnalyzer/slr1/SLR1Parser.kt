package org.compiler.frontend.syntaxAnalyzer.slr1

import org.compiler.frontend.models.TokenEntry
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
        entries: List<TokenEntry>,
        ignoredCategories: Set<String>,
        table: SLR1Table
    ): ParseResult {
        val stream = TokenStream(entries, ignoredCategories)

        // Initial state 0 is already on the state stack; the symbol and tree stacks are empty.
        val stateStack = mutableListOf(0)
        val symbolStack = mutableListOf<Symbol>()
        val treeStack = mutableListOf<ParseTree>()
        val trace = mutableListOf<ParseStep>()
        val errors = mutableListOf<ParseError>()

        while (true) {
            val currentState = stateStack.last()
            val nextEntry = stream.peek()
            val lookahead: Symbol =
                if (nextEntry != null) Symbol.Terminal(nextEntry.token.category)
                else Symbol.EndMarker

            val action = table.action[currentState to lookahead]

            // No entry in ACTION means syntax error: try panic-mode recovery.
            if (action == null) {
                errors.add(buildError(nextEntry, currentState, table))
                val recovered = attemptRecovery(stateStack, symbolStack, treeStack, stream, table)
                if (!recovered) {
                    return ParseResult.Rejected(trace, errors, treeStack.lastOrNull())
                }
                continue
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
                    // Consume the entry, push it on the symbol stack and the tree stack,
                    // and push the new state on the state stack.
                    val consumed = stream.consume()!!
                    val terminal = Symbol.Terminal(consumed.token.category)
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
                    return ParseResult.Accepted(trace, treeStack.last(), errors)
                }
            }
        }
    }

    // Constructs the structured ParseError for the cell (currentState, lookahead) that had no action.
    // The expected set is derived from all terminals/EndMarker that DO have an action at this state.
    // Message format: "Syntax error at line L, column C: unexpected 'X'; expected one of: A, B, C"
    private fun buildError(
        nextEntry: TokenEntry?,
        currentState: Int,
        table: SLR1Table
    ): ParseError {
        val expectedTokens = table.action.keys
            .filter { it.first == currentState }
            .map { it.second }
            .toSet()

        val locationDescription = if (nextEntry != null) {
            "line ${nextEntry.location.line}, column ${nextEntry.location.position}"
        } else {
            "end of input"
        }
        val foundDescription = if (nextEntry != null) {
            "'${nextEntry.token.lexeme}'"
        } else {
            "<EOF>"
        }
        val expectedDescription = if (expectedTokens.isEmpty()) {
            "nothing"
        } else {
            expectedTokens.map { it.name }.sorted().joinToString(", ")
        }

        val message = "Syntax error at $locationDescription: unexpected $foundDescription; " +
                "expected one of: $expectedDescription"

        return ParseError(
            message = message,
            location = nextEntry?.location,
            foundToken = nextEntry?.token,
            expectedTokens = expectedTokens
        )
    }

    // Panic-mode recovery. Strategy:
    //   1. Always discard the current token first to guarantee forward progress.
    //   2. Look for a state on the stack whose ACTION accepts the next lookahead;
    //      if found, pop the stack to that state and continue parsing.
    //   3. If no state accepts the new lookahead, discard another token and retry.
    //   4. If we run out of input and no state accepts EndMarker, recovery fails.
    private fun attemptRecovery(
        stateStack: MutableList<Int>,
        symbolStack: MutableList<Symbol>,
        treeStack: MutableList<ParseTree>,
        stream: TokenStream,
        table: SLR1Table
    ): Boolean {
        // If we're already at EOF, the only chance is to find a state accepting EndMarker
        // by popping the stack (no further input to skip).
        if (stream.peek() == null) {
            return popToStateAccepting(Symbol.EndMarker, stateStack, symbolStack, treeStack, table)
        }

        // Always consume the offending token first.
        stream.consume()

        while (true) {
            val nextEntry = stream.peek()
            val lookahead: Symbol =
                if (nextEntry != null) Symbol.Terminal(nextEntry.token.category)
                else Symbol.EndMarker

            if (popToStateAccepting(lookahead, stateStack, symbolStack, treeStack, table)) {
                return true
            }

            // No state on the stack accepts the current lookahead.
            // If at EOF we can't skip further; recovery fails.
            if (nextEntry == null) return false
            stream.consume()
        }
    }

    // Searches the state stack from top to bottom for a state where ACTION[state, lookahead]
    // is defined. If found, pops the three stacks down to that state and returns true.
    // The three stacks remain in sync (state stack has one more element than the others).
    private fun popToStateAccepting(
        lookahead: Symbol,
        stateStack: MutableList<Int>,
        symbolStack: MutableList<Symbol>,
        treeStack: MutableList<ParseTree>,
        table: SLR1Table
    ): Boolean {
        for (i in stateStack.indices.reversed()) {
            val state = stateStack[i]
            if (table.action.containsKey(state to lookahead)) {
                val statesToPop = stateStack.size - 1 - i
                repeat(statesToPop) {
                    stateStack.removeLast()
                    symbolStack.removeLast()
                    treeStack.removeLast()
                }
                return true
            }
        }
        return false
    }
}
