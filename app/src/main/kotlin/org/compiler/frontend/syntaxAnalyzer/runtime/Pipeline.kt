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
// Stages A..E are common to every parser method. The pipeline builds all parser
// artifacts in one pass so GUI method changes only need to rerun the selected
// parser over the already-built tokens and tables.
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

        // Stage D -- build the two grammar variants needed by the supported methods.
        val leftRecursionRewrittenGrammar =
            LeftRecursionRewriter.eliminateLeftRecursion(precedenceRewrittenGrammar)

        // Stage E -- FIRST/FOLLOW over both working grammars.
        val ll1FirstSets = FirstSetComputer.compute(leftRecursionRewrittenGrammar)
        val ll1FollowSets = FollowSetComputer.compute(leftRecursionRewrittenGrammar, ll1FirstSets)
        val lrFirstSets = FirstSetComputer.compute(precedenceRewrittenGrammar)
        val lrFollowSets = FollowSetComputer.compute(precedenceRewrittenGrammar, lrFirstSets)

        val ll1Table = LL1TableBuilder.build(leftRecursionRewrittenGrammar, ll1FirstSets, ll1FollowSets)
        val slr1Automaton = SLR1AutomataBuilder.build(precedenceRewrittenGrammar, lrFirstSets)
        val slr1Table = SLR1TableBuilder.build(slr1Automaton)
        val lalr1Automaton = LALR1AutomatonMerger.mergeFromSLR1(slr1Automaton)
        val lalr1Table = LALR1TableBuilder.build(lalr1Automaton)

        val tokenEntries = lexerResult.entries
        val ignoredCategories = precedenceRewrittenGrammar.ignoredTokens.map { it.name }.toSet()

        val parseResult = when (method) {
            ParserMethod.LL1 -> LL1Parser.parse(tokenEntries, ignoredCategories, ll1Table)
            ParserMethod.SLR1 -> SLR1Parser.parse(tokenEntries, ignoredCategories, slr1Table)
            ParserMethod.LALR1 -> LALR1Parser.parse(tokenEntries, ignoredCategories, lalr1Table)
        }

        val activeFirstSets = if (method == ParserMethod.LL1) ll1FirstSets else lrFirstSets
        val activeFollowSets = if (method == ParserMethod.LL1) ll1FollowSets else lrFollowSets

        return PipelineResult(
            method = method,
            lexerResult = lexerResult,
            originalGrammar = originalGrammar,
            precedenceRewrittenGrammar = precedenceRewrittenGrammar,
            leftRecursionRewrittenGrammar = leftRecursionRewrittenGrammar,
            firstSets = activeFirstSets,
            followSets = activeFollowSets,
            ll1FirstSets = ll1FirstSets,
            ll1FollowSets = ll1FollowSets,
            lrFirstSets = lrFirstSets,
            lrFollowSets = lrFollowSets,
            slr1Automaton = slr1Automaton,
            lalr1Automaton = lalr1Automaton,
            ll1Table = ll1Table,
            slr1Table = slr1Table,
            lalr1Table = lalr1Table,
            parseResult = parseResult
        )
    }
}
