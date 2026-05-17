package org.compiler.frontend.syntaxAnalyzer.runtime

import org.compiler.frontend.lexicalAnalyzer.lexer.Lexer
import org.compiler.frontend.syntaxAnalyzer.grammar.GrammarValidator
import org.compiler.frontend.syntaxAnalyzer.grammar.LeftRecursionRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.PrecedenceRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.ValidationError.Severity
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1AutomatonMerger
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1Parser
import org.compiler.frontend.syntaxAnalyzer.lalr1.LALR1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1Parser
import org.compiler.frontend.syntaxAnalyzer.ll1.LL1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import org.compiler.frontend.syntaxAnalyzer.runtime.models.PipelineResult
import org.compiler.frontend.syntaxAnalyzer.sets.FirstSetComputer
import org.compiler.frontend.syntaxAnalyzer.sets.FollowSetComputer
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1AutomataBuilder
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1Parser
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1TableBuilder

// Single entry point that chains the entire compilation: lex, parse-grammar,
// validate, rewrite, FIRST/FOLLOW, table construction, and parse.
//
// Stages A..E are common to every parser method. Stage F branches on `method`
// because each algorithm needs its own table/automaton. LL(1) is the only
// method that requires left-recursion elimination: SLR(1) and LALR(1) keep
// left-recursive productions intentionally (they yield smaller automata).
object Pipeline {

    fun runFull(
        yalexContent: String,
        yalpContent: String,
        inputContent: String,
        method: ParserMethod
    ): PipelineResult {
        // Stage A -- lexing.
        val lexerResult = Lexer.tokenize(yalexContent, inputContent)
        val lexerCategories = lexerResult.automata.keys

        // Stage B -- grammar parsing and validation.
        val originalGrammar = YalpReader.parse(yalpContent)
        val validationIssues = GrammarValidator.validate(originalGrammar, lexerCategories)
        val blockingIssues = validationIssues.filter { it.severity == Severity.ERROR }
        if (blockingIssues.isNotEmpty()) {
            error("Grammar validation failed: ${blockingIssues.joinToString("; ") { it.message }}")
        }

        // Stage C -- precedence rewriting is applied to every method.
        val precedenceRewrittenGrammar = PrecedenceRewriter.rewrite(originalGrammar)

        // Stage D -- LL(1) only: eliminate left recursion. SLR/LALR skip this.
        val leftRecursionRewrittenGrammar = when (method) {
            ParserMethod.LL1 -> LeftRecursionRewriter.eliminateLeftRecursion(precedenceRewrittenGrammar)
            else -> null
        }
        val workingGrammar = leftRecursionRewrittenGrammar ?: precedenceRewrittenGrammar

        // Stage E -- FIRST/FOLLOW over the working grammar.
        val firstSets = FirstSetComputer.compute(workingGrammar)
        val followSets = FollowSetComputer.compute(workingGrammar, firstSets)

        val ignoredCategories = workingGrammar.ignoredTokens.map { it.name }.toSet()
        val tokenEntries = lexerResult.entries

        // Stage F -- method-specific table construction and parsing.
        return when (method) {
            ParserMethod.LL1 -> {
                val ll1Table = LL1TableBuilder.build(workingGrammar, firstSets, followSets)
                val parseResult = LL1Parser.parse(tokenEntries, ignoredCategories, ll1Table)
                PipelineResult(
                    method = method,
                    lexerResult = lexerResult,
                    originalGrammar = originalGrammar,
                    precedenceRewrittenGrammar = precedenceRewrittenGrammar,
                    leftRecursionRewrittenGrammar = leftRecursionRewrittenGrammar,
                    firstSets = firstSets,
                    followSets = followSets,
                    slr1Automaton = null,
                    lalr1Automaton = null,
                    ll1Table = ll1Table,
                    slr1Table = null,
                    lalr1Table = null,
                    parseResult = parseResult
                )
            }
            ParserMethod.SLR1 -> {
                val slr1Automaton = SLR1AutomataBuilder.build(workingGrammar, firstSets)
                val slr1Table = SLR1TableBuilder.build(slr1Automaton)
                val parseResult = SLR1Parser.parse(tokenEntries, ignoredCategories, slr1Table)
                PipelineResult(
                    method = method,
                    lexerResult = lexerResult,
                    originalGrammar = originalGrammar,
                    precedenceRewrittenGrammar = precedenceRewrittenGrammar,
                    leftRecursionRewrittenGrammar = null,
                    firstSets = firstSets,
                    followSets = followSets,
                    slr1Automaton = slr1Automaton,
                    lalr1Automaton = null,
                    ll1Table = null,
                    slr1Table = slr1Table,
                    lalr1Table = null,
                    parseResult = parseResult
                )
            }
            ParserMethod.LALR1 -> {
                val slr1Automaton = SLR1AutomataBuilder.build(workingGrammar, firstSets)
                val mergedAutomaton = LALR1AutomatonMerger.mergeFromSLR1(slr1Automaton)
                val lalr1Table = LALR1TableBuilder.build(mergedAutomaton)
                val parseResult = LALR1Parser.parse(tokenEntries, ignoredCategories, lalr1Table)
                PipelineResult(
                    method = method,
                    lexerResult = lexerResult,
                    originalGrammar = originalGrammar,
                    precedenceRewrittenGrammar = precedenceRewrittenGrammar,
                    leftRecursionRewrittenGrammar = null,
                    firstSets = firstSets,
                    followSets = followSets,
                    slr1Automaton = slr1Automaton,
                    lalr1Automaton = mergedAutomaton,
                    ll1Table = null,
                    slr1Table = null,
                    lalr1Table = lalr1Table,
                    parseResult = parseResult
                )
            }
        }
    }
}
