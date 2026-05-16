package org.compiler.frontend.syntaxAnalyzer.lalr1

import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Conflict
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.slr1.SLR1TableBuilder
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata

object LALR1TableBuilder {

    // The LALR(1) table-building algorithm is identical to SLR(1)'s once the automaton has been
    // merged by core. We delegate to SLR1TableBuilder and rewrap the result so callers see the
    // LALR(1) type. Conflicts are translated one-to-one with the same data.
    fun build(mergedAutomata: SLR1Automata): LALR1Table {
        val slr1Table = SLR1TableBuilder.build(mergedAutomata)
        return LALR1Table(
            action = slr1Table.action,
            goto = slr1Table.goto,
            numStates = slr1Table.numStates,
            conflicts = slr1Table.conflicts.map { conflict ->
                LALR1Conflict(
                    state = conflict.state,
                    terminal = conflict.terminal,
                    type = conflict.type,
                    actions = conflict.actions
                )
            }
        )
    }
}
