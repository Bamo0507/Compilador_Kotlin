package org.compiler.frontend.syntaxAnalyzer.visualization

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.ll1.models.LL1Table
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table
import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.sets.models.FirstSets
import org.compiler.frontend.syntaxAnalyzer.sets.models.FollowSets

object TableFormatter {

    // ── LL(1) ─────────────────────────────────────────────────────────────────

    fun formatLL1Table(table: LL1Table): String {
        val nonTerminals = mutableListOf<Symbol.NonTerminal>()
        val terminals    = mutableListOf<Symbol>()
        for ((nt, sym) in table.cells.keys) {
            if (nt !in nonTerminals) nonTerminals.add(nt)
            if (sym !in terminals)   terminals.add(sym)
        }

        // Cell content: "head->bodyConcat" if ≤ 6 chars; "bodyConcat" if longer; "ε" for epsilon body.
        fun cellContent(nt: Symbol.NonTerminal, sym: Symbol): String {
            val prod = table.cells[nt to sym] ?: return ""
            if (prod.body == listOf(Symbol.Epsilon)) return "ε"
            val bodyStr = prod.body.joinToString("") { it.name }
            val full    = "${prod.head.name}->$bodyStr"
            return if (full.length <= 6) full else bodyStr
        }

        val ntWidth   = maxOf(6, nonTerminals.maxOfOrNull { it.name.length } ?: 0)
        val termWidths = terminals.associateWith { sym ->
            maxOf(sym.name.length,
                  nonTerminals.maxOfOrNull { nt -> cellContent(nt, sym).length } ?: 0,
                  6)
        }

        return buildGrid(
            leftHeader        = "",
            leftCells         = nonTerminals.map { it.name },
            leftWidth         = ntWidth,
            colHeaders        = terminals.map { it.name },
            colWidths         = terminals.map { termWidths[it]!! },
            cells             = nonTerminals.map { nt -> terminals.map { sym -> cellContent(nt, sym) } },
            leftJustifyData   = true,
            rightJustifyCells = false
        )
    }

    // ── SLR(1) / LALR(1) ──────────────────────────────────────────────────────

    private fun encodeAction(action: Action): String = when (action) {
        is Action.Shift          -> "s${action.nextState}"
        is Action.Reduce         -> "r${action.production.id}"
        Action.Accept            -> "acc"
        is Action.Match,
        is Action.Expand         -> ""
    }

    // output format example:

    // State | id   | +    | *    | (    | )    | $   
    // ------+------+------+------+------+------+-----
    //     0 | s5   |      |      | s4   |      |     
    //     1 |      | s6   |      |      |      | acc 
    //     2 |      | r2   | s7   |      | r2   | r2  

    private fun formatActionTable(
        numStates:   Int,
        columns:     List<Symbol>,
        actionMap:   Map<Pair<Int, Symbol>, Action>,
        conflictFor: (Int, Symbol) -> List<Action>?
    ): String {
        val stateWidth = maxOf(5, (numStates - 1).toString().length)

        fun cell(state: Int, sym: Symbol): String {
            val conflict = conflictFor(state, sym)
            return if (conflict != null)
                conflict.joinToString("/") { encodeAction(it) }
            else
                actionMap[state to sym]?.let { encodeAction(it) } ?: ""
        }

        val colWidths = columns.associateWith { sym ->
            maxOf(sym.name.length,
                  (0 until numStates).maxOfOrNull { s -> cell(s, sym).length } ?: 0,
                  4)
        }

        return buildGrid(
            leftHeader        = "State",
            leftCells         = (0 until numStates).map { it.toString() },
            leftWidth         = stateWidth,
            colHeaders        = columns.map { it.name },
            colWidths         = columns.map { colWidths[it]!! },
            cells             = (0 until numStates).map { s -> columns.map { sym -> cell(s, sym) } },
            leftJustifyData   = false,
            rightJustifyCells = false
        )
    }

    // output format example:

    // State | E    | T    | F   
    // ------+------+------+-----
    //     0 |    1 |    2 |    3
    //     4 |    8 |    2 |    3
    private fun formatGotoTable(
        numStates: Int,
        columns:   List<Symbol.NonTerminal>,
        gotoMap:   Map<Pair<Int, Symbol>, Int>
    ): String {
        val stateWidth = maxOf(5, (numStates - 1).toString().length)

        fun cell(state: Int, nt: Symbol.NonTerminal): String =
            gotoMap[state to nt]?.toString() ?: ""

        val colWidths = columns.associateWith { nt ->
            maxOf(nt.name.length,
                  (0 until numStates).maxOfOrNull { s -> cell(s, nt).length } ?: 0,
                  4)
        }

        return buildGrid(
            leftHeader        = "State",
            leftCells         = (0 until numStates).map { it.toString() },
            leftWidth         = stateWidth,
            colHeaders        = columns.map { it.name },
            colWidths         = columns.map { colWidths[it]!! },
            cells             = (0 until numStates).map { s -> columns.map { nt -> cell(s, nt) } },
            leftJustifyData   = false,
            rightJustifyCells = true
        )
    }

    fun formatSLR1Action(table: SLR1Table): String {
        val columns = table.action.keys.map { it.second }.distinct()
        return formatActionTable(
            numStates   = table.numStates,
            columns     = columns,
            actionMap   = table.action,
            conflictFor = { state, sym ->
                table.conflicts.find { it.state == state && it.terminal == sym }?.actions
            }
        )
    }

    fun formatSLR1Goto(table: SLR1Table): String {
        val columns = table.goto.keys.mapNotNull { it.second as? Symbol.NonTerminal }.distinct()
        return formatGotoTable(table.numStates, columns, table.goto)
    }

    fun formatLALR1Action(table: LALR1Table): String {
        val columns = table.action.keys.map { it.second }.distinct()
        return formatActionTable(
            numStates   = table.numStates,
            columns     = columns,
            actionMap   = table.action,
            conflictFor = { state, sym ->
                table.conflicts.find { it.state == state && it.terminal == sym }?.actions
            }
        )
    }

    fun formatLALR1Goto(table: LALR1Table): String {
        val columns = table.goto.keys.mapNotNull { it.second as? Symbol.NonTerminal }.distinct()
        return formatGotoTable(table.numStates, columns, table.goto)
    }

    // ── FIRST / FOLLOW ────────────────────────────────────────────────────────

    fun formatFirstSets(firstSets: FirstSets): String   = formatSets(firstSets.results)
    fun formatFollowSets(followSets: FollowSets): String = formatSets(followSets.results)

    private fun formatSets(results: Map<Symbol.NonTerminal, Set<Symbol>>): String {
        val maxWidth = maxOf(6, results.keys.maxOfOrNull { it.name.length } ?: 0)
        return results.entries
            .sortedBy { it.key.name }
            .joinToString("\n") { (nt, symbols) ->
                val symsStr = symbols.sortedBy { it.name }.joinToString(", ") { it.name }
                "${nt.name.padEnd(maxWidth)} = { $symsStr }"
            }
    }

    // ── Grid builder ──────────────────────────────────────────────────────────
    //
    // Produces a table where:
    //   • All lines have exactly the same character count.
    //   • Non-last column cells:  "| content.padEnd/Start(w) "  (w+3 chars)
    //   • Last column cell:       "| content.padEnd/Start(w)"   (w+2 chars, no trailing space)
    //   • Separator non-last:     "+{'-' × (w+2)}"              (w+3 chars)
    //   • Separator last:         "+{'-' × (w+1)}"              (w+2 chars)

    private fun buildGrid(
        leftHeader:        String,
        leftCells:         List<String>,
        leftWidth:         Int,
        colHeaders:        List<String>,
        colWidths:         List<Int>,
        cells:             List<List<String>>,
        leftJustifyData:   Boolean,
        rightJustifyCells: Boolean
    ): String {
        val n     = colHeaders.size
        val lines = mutableListOf<String>()

        // Header row
        lines.add(buildString {
            append(leftHeader.padEnd(leftWidth))
            append(" ")
            for (j in 0 until n) {
                val w = colWidths[j]
                append("| ")
                append(colHeaders[j].padEnd(w))
                if (j < n - 1) append(" ")
            }
        })

        // Separator row
        lines.add(buildString {
            append("-".repeat(leftWidth + 1))
            for (j in 0 until n) {
                val dashes = if (j < n - 1) colWidths[j] + 2 else colWidths[j] + 1
                append("+")
                append("-".repeat(dashes))
            }
        })

        // Data rows
        for (i in leftCells.indices) {
            lines.add(buildString {
                if (leftJustifyData) append(leftCells[i].padEnd(leftWidth))
                else                 append(leftCells[i].padStart(leftWidth))
                append(" ")
                for (j in 0 until n) {
                    val w       = colWidths[j]
                    val content = cells[i][j]
                    append("| ")
                    if (rightJustifyCells) append(content.padStart(w))
                    else                   append(content.padEnd(w))
                    if (j < n - 1) append(" ")
                }
            })
        }

        return lines.joinToString("\n")
    }
}