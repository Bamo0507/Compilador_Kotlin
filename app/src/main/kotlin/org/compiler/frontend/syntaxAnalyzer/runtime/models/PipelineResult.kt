package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.lexicalAnalyzer.lexer.LexerResult
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Grammar
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Table
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.sets.models.FollowSets
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table

// Aggregates every artifact produced by Pipeline.runFull. Fields typed as
// nullable depend on which ParserMethod was used: a LL1 run does not produce
// SLR/LALR automata; an SLR run does not produce an LL1 table; and so on.
// Consumers (GUI, end-to-end tests) inspect `method` to know which fields are
// populated.
data class PipelineResult(
    val method: ParserMethod,
    val lexerResult: LexerResult,
    val originalGrammar: Grammar,
    val precedenceRewrittenGrammar: Grammar,
    val leftRecursionRewrittenGrammar: Grammar?,
    val firstSets: FirstSets,
    val followSets: FollowSets,
    val slr1Automaton: SLR1Automata?,
    val lalr1Automaton: SLR1Automata?,
    val ll1Table: LL1Table?,
    val slr1Table: SLR1Table?,
    val lalr1Table: LALR1Table?,
    val parseResult: ParseResult
)
