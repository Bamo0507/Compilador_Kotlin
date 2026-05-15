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

            val binaryOperatorsUsed = classifications.mapNotNull { (_, classification) ->
                (classification as? ProductionClassification.BinaryOperator)?.operator
            }.toSet()
            val unaryPrefixOperatorsUsed = classifications.mapNotNull { (_, classification) ->
                (classification as? ProductionClassification.UnaryPrefixOperator)?.operator
            }.toSet()

            for ((levelIndex, level) in grammar.precedenceTable.withIndex()) {
                val currentLevel = synthetic.levels[levelIndex]
                val nextLevel = synthetic.levels.getOrNull(levelIndex + 1) ?: synthetic.atom

                for (operator in level.operators) {
                    if (operator in binaryOperatorsUsed) {
                        val body = when (level.associativity) {
                            Associativity.LEFT -> listOf(currentLevel, operator, nextLevel)
                            Associativity.RIGHT -> listOf(nextLevel, operator, currentLevel)
                        }
                        newProductions.add(Production(productionId++, currentLevel, body))
                    }
                    if (operator in unaryPrefixOperatorsUsed) {
                        newProductions.add(
                            Production(productionId++, currentLevel, listOf(operator, currentLevel))
                        )
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

        if (body.size == 2 && body[1] == head) {
            val prefix = body[0]
            if (prefix is Symbol.Terminal && prefix in operatorTerminals) {
                return ProductionClassification.UnaryPrefixOperator(prefix)
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
