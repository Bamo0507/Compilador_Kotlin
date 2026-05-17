package org.compiler.frontend.syntaxAnalyzer.ll1

import org.compiler.frontend.models.TokenEntry
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Table
import org.compiler.frontend.syntaxAnalyzer.runtime.TokenStream
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseError
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseStep
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree

object LL1Parser {

    // Mutable nodes used for top-down tree construction. Converted to immutable ParseTree at Accept.
    private sealed interface MutableNode {
        class Internal(val symbol: Symbol.NonTerminal) : MutableNode {
            var production: Production? = null
            val children: MutableList<MutableNode> = mutableListOf()
        }
        class Leaf(val symbol: Symbol.Terminal) : MutableNode {
            var entry: TokenEntry? = null
        }
        data object Epsilon : MutableNode
    }

    private fun MutableNode.toParseTree(): ParseTree = when (this) {
        is MutableNode.Internal -> {
            val productionValue = production
            if (productionValue != null) ParseTree.InternalNode(symbol, productionValue, children.map { it.toParseTree() })
            else ParseTree.EpsilonNode
        }
        is MutableNode.Leaf -> {
            val entryValue = entry
            if (entryValue != null) ParseTree.LeafNode(symbol, entryValue) else ParseTree.EpsilonNode
        }
        MutableNode.Epsilon -> ParseTree.EpsilonNode
    }

    fun parse(
        entries: List<TokenEntry>,
        ignoredCategories: Set<String>,
        table: LL1Table
    ): ParseResult {
        val stream = TokenStream(entries, ignoredCategories)
        val trace = mutableListOf<ParseStep>()
        val errors = mutableListOf<ParseError>()

        // symbolStack: bottom = EndMarker, top = startSymbol (Dragon Book §4.4.4, Algorithm 4.34).
        // nodeStack is a parallel stack where each slot holds the corresponding mutable tree node.
        val root = MutableNode.Internal(table.startSymbol)
        val symbolStack = mutableListOf<Symbol>(Symbol.EndMarker, table.startSymbol)
        val nodeStack = mutableListOf<MutableNode?>(null, root)

        while (true) {
            val top = symbolStack.last()
            val nextEntry = stream.peek()
            val lookahead: Symbol =
                if (nextEntry != null) Symbol.Terminal(nextEntry.token.category)
                else Symbol.EndMarker

            when {
                // ACCEPT: both stack and input are exhausted.
                top == Symbol.EndMarker && lookahead == Symbol.EndMarker ->
                    return ParseResult.Accepted(trace, root.toParseTree(), errors)

                // Extra input after the parse completed: unrecoverable.
                top == Symbol.EndMarker -> {
                    errors.add(buildError(nextEntry, emptySet()))
                    return ParseResult.Rejected(trace, errors, root.toParseTree())
                }

                // Epsilon on the stack is a no-op: pop and continue.
                top == Symbol.Epsilon -> {
                    symbolStack.removeLast()
                    nodeStack.removeLast()
                }

                // MATCH: a terminal on top must equal the lookahead.
                top is Symbol.Terminal -> {
                    val leafSlot = nodeStack.last() as? MutableNode.Leaf
                    if (top == lookahead) {
                        // Record BEFORE consuming, so the trace shows the state that decided.
                        trace.add(ParseStep(emptyList(), symbolStack.toList(), stream.remaining(), Action.Match(top)))
                        val consumed = stream.consume()!!
                        leafSlot?.entry = consumed
                        symbolStack.removeLast()
                        nodeStack.removeLast()
                    } else {
                        // Missing terminal: pop it (assume the user omitted it), don't consume lookahead.
                        errors.add(buildError(nextEntry, setOf(top)))
                        symbolStack.removeLast()
                        nodeStack.removeLast()
                    }
                }

                // EXPAND: look up the production for (NonTerminal, lookahead).
                top is Symbol.NonTerminal -> {
                    val production = table.lookup(top, lookahead)
                    if (production != null) {
                        val internalSlot = nodeStack.last() as? MutableNode.Internal
                        // Record BEFORE expanding.
                        trace.add(ParseStep(emptyList(), symbolStack.toList(), stream.remaining(), Action.Expand(production)))
                        internalSlot?.production = production
                        symbolStack.removeLast()
                        nodeStack.removeLast()

                        val isEpsilon = production.body == listOf(Symbol.Epsilon)
                        if (isEpsilon) {
                            internalSlot?.children?.add(MutableNode.Epsilon)
                            // Epsilon produces nothing: don't push anything to symbolStack.
                        } else {
                            val childSlots = production.body.map { bodySymbol ->
                                when (bodySymbol) {
                                    is Symbol.NonTerminal -> MutableNode.Internal(bodySymbol)
                                    is Symbol.Terminal -> MutableNode.Leaf(bodySymbol)
                                    else -> MutableNode.Epsilon
                                }
                            }
                            internalSlot?.children?.addAll(childSlots)
                            // Push body in reverse so body[0] ends up on top.
                            for (bodyIndex in production.body.indices.reversed()) {
                                symbolStack.add(production.body[bodyIndex])
                                nodeStack.add(childSlots[bodyIndex])
                            }
                        }
                    } else {
                        // No production for (top, lookahead): panic-mode recovery (Dragon Book §4.4.5).
                        val expected = table.cells.keys.filter { it.first == top }.map { it.second }.toSet()
                        errors.add(buildError(nextEntry, expected))

                        // Discard tokens until a sync token (FOLLOW(top)) or EOF.
                        val syncSet = table.followOf(top)
                        while (stream.peek() != null) {
                            val currentSymbol = Symbol.Terminal(stream.peek()!!.token.category)
                            if (currentSymbol in syncSet) break
                            stream.consume()
                        }

                        // Pop top, treating it as if it derived epsilon.
                        symbolStack.removeLast()
                        nodeStack.removeLast()

                        // If the start symbol was popped and only EndMarker remains, recovery failed.
                        if (symbolStack.size == 1) {
                            return ParseResult.Rejected(trace, errors, root.toParseTree())
                        }
                    }
                }
            }
        }
    }

    // Builds a ParseError matching the format used by SLR1Parser:
    // "Syntax error at line L, column C: unexpected 'X'; expected one of: A, B, C"
    private fun buildError(nextEntry: TokenEntry?, expectedTokens: Set<Symbol>): ParseError {
        val locationDescription = if (nextEntry != null)
            "line ${nextEntry.location.line}, column ${nextEntry.location.position}"
        else
            "end of input"

        val foundDescription = if (nextEntry != null) "'${nextEntry.token.lexeme}'" else "<EOF>"

        val expectedDescription = if (expectedTokens.isEmpty()) "nothing"
        else expectedTokens.map { it.name }.sorted().joinToString(", ")

        return ParseError(
            message = "Syntax error at $locationDescription: unexpected $foundDescription; " +
                    "expected one of: $expectedDescription",
            location = nextEntry?.location,
            foundToken = nextEntry?.token,
            expectedTokens = expectedTokens
        )
    }
}
