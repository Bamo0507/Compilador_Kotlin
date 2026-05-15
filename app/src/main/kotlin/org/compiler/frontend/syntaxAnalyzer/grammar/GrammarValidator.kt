package org.compiler.frontend.syntaxAnalyzer.grammar

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.ValidationError
import org.compiler.frontend.syntaxAnalyzer.grammar.models.ValidationError.Severity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead

object GrammarValidator {

    fun validate(grammar: Grammar, lexerCategories: Set<String>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        checkUndeclaredTokens(grammar, lexerCategories, errors)
        checkUndefinedNonTerminals(grammar, errors)
        checkCycles(grammar, errors)
        checkUnreachableNonTerminals(grammar, errors)
        checkDuplicateProductions(grammar, errors)

        return errors
    }

    // Each terminal declared in the .yalp must exist as a category the lexer can produce.
    private fun checkUndeclaredTokens(
        grammar: Grammar,
        lexerCategories: Set<String>,
        errors: MutableList<ValidationError>
    ) {
        grammar.terminals
            .filter { terminal -> terminal.name !in lexerCategories }
            .forEach { terminal ->
                errors.add(ValidationError(
                    "Token '${terminal.name}' declared in .yalp but not produced by the lexer"
                ))
            }
    }

    // Every non-terminal that appears in a production body must be defined as
    // the head of at least one production.
    private fun checkUndefinedNonTerminals(grammar: Grammar, errors: MutableList<ValidationError>) {
        val definedHeads = grammar.productions.map { production -> production.head }.toSet()
        val reported = mutableSetOf<Symbol.NonTerminal>()

        grammar.productions.forEach { production ->
            production.body.filterIsInstance<Symbol.NonTerminal>()
                .filter { nonTerminal -> nonTerminal !in definedHeads && nonTerminal !in reported }
                .forEach { nonTerminal ->
                    errors.add(ValidationError("Non-terminal '${nonTerminal.name}' is used but never defined"))
                    reported.add(nonTerminal)
                }
        }
    }

    // Detects cycles of the form A ->+ A through unit productions (A -> B, B -> A).
    // A cycle makes left-recursion elimination loop infinitely — must be reported as error.
    private fun checkCycles(grammar: Grammar, errors: MutableList<ValidationError>) {
        val unitGraph = buildUnitGraph(grammar)
        // Nodes fully explored from all their neighbors — skipped if encountered again.
        val globalVisited = mutableSetOf<Symbol.NonTerminal>()
        val reported = mutableSetOf<Symbol.NonTerminal>()

        fun dfs(
            node: Symbol.NonTerminal,
            pathSet: MutableSet<Symbol.NonTerminal>, // nodes in the current DFS path
            pathList: MutableList<Symbol.NonTerminal> // same nodes as an ordered list — used to reconstruct the cycle for the error message
        ) {
            if (node in pathSet) {
                val cycleStart = pathList.indexOf(node)
                val cycle = pathList.subList(cycleStart, pathList.size) + node
                if (cycle.none { nonTerminal -> nonTerminal in reported }) {
                    val cycleStr = cycle.joinToString(" -> ") { nonTerminal -> nonTerminal.name }
                    errors.add(ValidationError("Cycle detected: $cycleStr"))
                    reported.addAll(cycle)
                }
                return
            }
            if (node in globalVisited) return

            pathSet.add(node)
            pathList.add(node)
            unitGraph[node]?.forEach { neighbor -> dfs(neighbor, pathSet, pathList) }
            pathList.removeLast()
            pathSet.remove(node)
            globalVisited.add(node)
        }

        grammar.nonTerminals.forEach { nonTerminal ->
            if (nonTerminal !in reported) dfs(nonTerminal, mutableSetOf(), mutableListOf())
        }
    }

    // A non-terminal is unreachable if no derivation from the start symbol ever reaches it.
    // BFS from startSymbol through all reachable non-terminals.
    private fun checkUnreachableNonTerminals(grammar: Grammar, errors: MutableList<ValidationError>) {
        val reachable = mutableSetOf<Symbol.NonTerminal>()
        val queue = ArrayDeque<Symbol.NonTerminal>()

        reachable.add(grammar.startSymbol)
        queue.add(grammar.startSymbol)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            grammar.productionsByHead[current]?.forEach { production ->
                production.body.filterIsInstance<Symbol.NonTerminal>()
                    .filter { nonTerminal -> nonTerminal !in reachable }
                    .forEach { nonTerminal ->
                        reachable.add(nonTerminal)
                        queue.add(nonTerminal)
                    }
            }
        }

        grammar.nonTerminals
            .filter { nonTerminal -> nonTerminal !in reachable }
            .forEach { nonTerminal ->
                errors.add(ValidationError(
                    "Non-terminal '${nonTerminal.name}' is unreachable from start symbol '${grammar.startSymbol.name}'",
                    Severity.WARNING
                ))
            }
    }

    // Two productions with identical head and body are almost always a copy-paste error.
    private fun checkDuplicateProductions(grammar: Grammar, errors: MutableList<ValidationError>) {
        val seen = mutableSetOf<Pair<Symbol.NonTerminal, List<Symbol>>>()

        grammar.productions.forEach { production ->
            val key = production.head to production.body
            if (!seen.add(key)) {
                val bodyStr = if (production.body.isEmpty()) "ε"
                              else production.body.joinToString(" ") { symbol -> symbol.name }
                errors.add(ValidationError(
                    "Duplicate production: '${production.head.name} -> $bodyStr'",
                    Severity.WARNING
                ))
            }
        }
    }

    // A unit production is one whose body is exactly one non-terminal (e.g. A -> B).
    // This function builds a directed graph where each edge A -> B means
    // "A derives B in a single unit production step", used to detect cycles.
    private fun buildUnitGraph(grammar: Grammar): Map<Symbol.NonTerminal, Set<Symbol.NonTerminal>> {
        val graph = mutableMapOf<Symbol.NonTerminal, MutableSet<Symbol.NonTerminal>>()
        grammar.productions
            .filter { production -> production.body.size == 1 && production.body[0] is Symbol.NonTerminal }
            .forEach { production ->
                graph.getOrPut(production.head) { mutableSetOf() }
                    .add(production.body[0] as Symbol.NonTerminal)
            }
        return graph
    }
}
