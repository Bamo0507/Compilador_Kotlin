package org.compiler.frontend.syntaxAnalyzer.grammar

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Associativity
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.productionsByHead

object PrecedenceRewriter {

    fun rewrite(grammar: Grammar): Grammar {
        if (grammar.precedenceTable.isEmpty()) return grammar

        val operatorTerminals = grammar.precedenceTable.flatMap { level -> level.operators }.toSet()
        // Which precedence level each operator was declared at. A production may override
        // this with %prec, which is how the same token can be additive as a binary operator
        // and top-priority as a unary one.
        val levelIndexOfOperator = buildMap {
            grammar.precedenceTable.forEachIndexed { levelIndex, level ->
                level.operators.forEach { operator -> putIfAbsent(operator, levelIndex) }
            }
        }
        val takenNames = grammar.nonTerminals.map { it.name }.toMutableSet()
        val newProductions = mutableListOf<Production>()
        val newNonTerminals = grammar.nonTerminals.toMutableSet()
        var productionId = 1

        for ((head, originalProductions) in grammar.productionsByHead) {
            val classifications = originalProductions.map { production ->
                production to classify(production, head, operatorTerminals)
            }
            val hasOperatorProduction = classifications.any { (_, classification) ->
                classification !is ProductionClassification.Atomic
            }

            if (!hasOperatorProduction) {
                originalProductions.forEach { production ->
                    newProductions.add(Production(productionId++, head, production.body))
                }
                continue
            }

            val synthetic = generateSyntheticNonTerminals(
                head = head,
                levelCount = grammar.precedenceTable.size,
                takenNames = takenNames
            )
            newNonTerminals.addAll(synthetic.levels)
            newNonTerminals.add(synthetic.atom)

            newProductions.add(Production(productionId++, head, listOf(synthetic.levels[0])))

            // Group the operators by the level they will be EMITTED at, which is the level of
            // the production's %prec label when it has one, and the operator's own level
            // otherwise. Grouping per production (instead of per operator) is what allows one
            // token to appear at two different levels in the cascade.
            val binaryOperatorsByLevel = mutableMapOf<Int, MutableSet<Symbol.Terminal>>()
            val unaryOperatorsByLevel = mutableMapOf<Int, MutableSet<Symbol.Terminal>>()
            val fixedRightOperatorsByLevel =
                mutableMapOf<Int, MutableSet<Pair<Symbol.Terminal, Symbol>>>()
            val ternaryOperatorsByLevel =
                mutableMapOf<Int, MutableSet<Pair<Symbol.Terminal, Symbol.Terminal>>>()

            classifications.forEach { (production, classification) ->
                val operator = when (classification) {
                    is ProductionClassification.BinaryOperator -> classification.operator
                    is ProductionClassification.UnaryPrefixOperator -> classification.operator
                    is ProductionClassification.BinaryWithFixedRight -> classification.operator
                    // The level comes from the opening operator (QUESTION), not the closing one.
                    is ProductionClassification.TernaryOperator -> classification.firstOperator
                    ProductionClassification.Atomic -> return@forEach
                }
                val levelIndex = production.precedenceLabel?.let { levelIndexOfOperator[it] }
                    ?: levelIndexOfOperator[operator]
                    ?: return@forEach

                when (classification) {
                    is ProductionClassification.BinaryOperator ->
                        binaryOperatorsByLevel.getOrPut(levelIndex) { linkedSetOf() }.add(operator)
                    is ProductionClassification.UnaryPrefixOperator ->
                        unaryOperatorsByLevel.getOrPut(levelIndex) { linkedSetOf() }.add(operator)
                    is ProductionClassification.BinaryWithFixedRight ->
                        fixedRightOperatorsByLevel.getOrPut(levelIndex) { linkedSetOf() }
                            .add(operator to classification.rightOperand)
                    is ProductionClassification.TernaryOperator ->
                        ternaryOperatorsByLevel.getOrPut(levelIndex) { linkedSetOf() }
                            .add(classification.firstOperator to classification.secondOperator)
                    ProductionClassification.Atomic -> Unit
                }
            }

            for ((levelIndex, level) in grammar.precedenceTable.withIndex()) {
                val currentLevel = synthetic.levels[levelIndex]
                val nextLevel = synthetic.levels.getOrNull(levelIndex + 1) ?: synthetic.atom

                val binaryHere = binaryOperatorsByLevel[levelIndex].orEmpty()
                val unaryHere = unaryOperatorsByLevel[levelIndex].orEmpty()
                val fixedRightHere = fixedRightOperatorsByLevel[levelIndex].orEmpty()
                val ternaryHere = ternaryOperatorsByLevel[levelIndex].orEmpty()

                // Declaration order first so the generated grammar stays stable; operators
                // redirected here by %prec come after.
                val operatorsHere = linkedSetOf<Symbol.Terminal>().apply {
                    addAll(
                        level.operators.filter { operator ->
                            operator in binaryHere ||
                                operator in unaryHere ||
                                fixedRightHere.any { it.first == operator } ||
                                ternaryHere.any { it.first == operator }
                        }
                    )
                    addAll(binaryHere)
                    addAll(unaryHere)
                    addAll(fixedRightHere.map { it.first })
                    addAll(ternaryHere.map { it.first })
                }

                for (operator in operatorsHere) {
                    if (operator in binaryHere) {
                        val body = when (level.associativity) {
                            Associativity.LEFT -> listOf(currentLevel, operator, nextLevel)
                            Associativity.RIGHT -> listOf(nextLevel, operator, currentLevel)
                        }
                        newProductions.add(Production(productionId++, currentLevel, body))
                    }
                    if (operator in unaryHere) {
                        newProductions.add(
                            Production(productionId++, currentLevel, listOf(operator, currentLevel))
                        )
                    }
                    fixedRightHere
                        .filter { it.first == operator }
                        .forEach { (fixedOperator, rightOperand) ->
                            // Recursive on the side that chains, exactly like the plain binary
                            // case; the right operand is copied through untouched.
                            val body = when (level.associativity) {
                                Associativity.LEFT -> listOf(currentLevel, fixedOperator, rightOperand)
                                Associativity.RIGHT -> listOf(nextLevel, fixedOperator, rightOperand)
                            }
                            newProductions.add(Production(productionId++, currentLevel, body))
                        }
                    ternaryHere
                        .filter { it.first == operator }
                        .forEach { (firstOperator, secondOperator) ->
                            // The middle slot takes the loosest level (levels[0]) because both
                            // separators delimit it; the chaining side recurses on this level so
                            // "a ? b : c ? d : e" nests to the right, as Java requires.
                            val loosestLevel = synthetic.levels.first()
                            val body = when (level.associativity) {
                                Associativity.RIGHT -> listOf(
                                    nextLevel, firstOperator, loosestLevel, secondOperator, currentLevel
                                )
                                Associativity.LEFT -> listOf(
                                    currentLevel, firstOperator, loosestLevel, secondOperator, nextLevel
                                )
                            }
                            newProductions.add(Production(productionId++, currentLevel, body))
                        }
                }

                newProductions.add(Production(productionId++, currentLevel, listOf(nextLevel)))
            }

            classifications
                .filter { (_, classification) -> classification is ProductionClassification.Atomic }
                .forEach { (production, _) ->
                    newProductions.add(Production(productionId++, synthetic.atom, production.body))
                }
        }

        return grammar.copy(
            productions = newProductions,
            nonTerminals = newNonTerminals
        )
    }

    private sealed class ProductionClassification {
        data class BinaryOperator(val operator: Symbol.Terminal) : ProductionClassification()
        data class UnaryPrefixOperator(val operator: Symbol.Terminal) : ProductionClassification()

        // Only the LEFT operand takes part in the cascade; the right one is a fixed symbol.
        // This is the shape of "expr KW_INSTANCEOF decl_type": instanceof has a precedence
        // level like any relational operator, but its right side is a type, not an expression.
        data class BinaryWithFixedRight(
            val operator: Symbol.Terminal,
            val rightOperand: Symbol
        ) : ProductionClassification()

        // Two operators wrapping a middle operand: "expr QUESTION expr COLON expr". The middle
        // slot is delimited on both sides, so it can hold the LOOSEST level (a full
        // expression) without ambiguity -- which is why "a ? b = 1 : c" is legal Java.
        data class TernaryOperator(
            val firstOperator: Symbol.Terminal,
            val secondOperator: Symbol.Terminal
        ) : ProductionClassification()

        data object Atomic : ProductionClassification()
    }

    private fun classify(
        production: Production,
        head: Symbol.NonTerminal,
        operatorTerminals: Set<Symbol.Terminal>
    ): ProductionClassification {
        val body = production.body

        if (body.size == 3 && body[0] == head && body[2] == head) {
            val middle = body[1]
            if (middle is Symbol.Terminal && middle in operatorTerminals) {
                return ProductionClassification.BinaryOperator(middle)
            }
        }

        // Same shape as above but the right operand is something else (a type, typically).
        // Checked after the plain binary case so "expr OP expr" always wins.
        if (body.size == 3 && body[0] == head && body[2] != head) {
            val middle = body[1]
            if (middle is Symbol.Terminal && middle in operatorTerminals) {
                return ProductionClassification.BinaryWithFixedRight(middle, body[2])
            }
        }

        if (body.size == 2 && body[1] == head) {
            val prefix = body[0]
            if (prefix is Symbol.Terminal && prefix in operatorTerminals) {
                return ProductionClassification.UnaryPrefixOperator(prefix)
            }
        }

        // Ternary: three operands of the same non-terminal separated by two terminals. Only
        // the first separator needs a declared precedence level -- the second one is just a
        // closing delimiter, so COLON stays out of the precedence table entirely.
        if (body.size == 5 && body[0] == head && body[2] == head && body[4] == head) {
            val firstOperator = body[1]
            val secondOperator = body[3]
            if (firstOperator is Symbol.Terminal &&
                firstOperator in operatorTerminals &&
                secondOperator is Symbol.Terminal
            ) {
                return ProductionClassification.TernaryOperator(firstOperator, secondOperator)
            }
        }

        return ProductionClassification.Atomic
    }

    private data class SyntheticNonTerminals(
        val levels: List<Symbol.NonTerminal>,
        val atom: Symbol.NonTerminal
    )

    private fun generateSyntheticNonTerminals(
        head: Symbol.NonTerminal,
        levelCount: Int,
        takenNames: MutableSet<String>
    ): SyntheticNonTerminals {
        val levels = (0 until levelCount).map { levelIndex ->
            val baseName = "${head.name}_lvl$levelIndex"
            val uniqueName = pickUniqueName(baseName, takenNames)
            takenNames.add(uniqueName)
            Symbol.NonTerminal(uniqueName)
        }

        val atomName = pickUniqueName("${head.name}_atom", takenNames)
        takenNames.add(atomName)

        return SyntheticNonTerminals(levels, Symbol.NonTerminal(atomName))
    }

    private fun pickUniqueName(baseName: String, takenNames: Set<String>): String {
        if (baseName !in takenNames) return baseName
        var counter = 2
        while ("${baseName}_$counter" in takenNames) counter++
        return "${baseName}_$counter"
    }
}
