package org.compiler.frontend.syntaxAnalyzer.lalr1

import org.compiler.frontend.models.TokenEntry
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1Parser
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table

object LALR1Parser {

    // The LALR(1) parsing algorithm is exactly the same shift-reduce loop as SLR(1) -- only the
    // table changed (built from a merged automaton). We adapt LALR1Table -> SLR1Table and delegate
    // so there is a single source of truth for the parsing algorithm.
    fun parse(
        entries: List<TokenEntry>,
        ignoredCategories: Set<String>,
        table: LALR1Table
    ): ParseResult {
        val slr1Table = SLR1Table(
            action = table.action,
            goto = table.goto,
            numStates = table.numStates
        )
        return SLR1Parser.parse(entries, ignoredCategories, slr1Table)
    }
}
