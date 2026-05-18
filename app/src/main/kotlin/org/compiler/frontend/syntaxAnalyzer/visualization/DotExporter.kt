package org.compiler.frontend.syntaxAnalyzer.visualization

import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Automata
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Item
import java.io.IOException


// ========= Input ==================================
// data class SLR1Automata(
//     val states: List<SLR1State>,
//     val transitions: Map<Pair<Int, Symbol>, Int>,
//     val initialState: SLR1State,
//     val augmentedGrammar: Grammar
// )

// ========= Output =================================
// digraph SLR1 {
//   rankdir=LR;
//   node [shape=box, style=rounded, fontname="Courier"];

//   start [shape=none, label=""];
//   start -> S0;

//   S0 [label="State 0\nS' -> . S\nS -> . a"];
//   S1 [label="State 1\nS -> a ."];
//   S2 [label="State 2\nS' -> S .", peripheries=2];

//   S0 -> S1 [label="a"];
//   S0 -> S2 [label="S"];
// }

object DotExporter {

    private fun formatItem(item: SLR1Item): String {
        val body = item.production.body
        val parts = mutableListOf<String>()
        body.forEachIndexed { i, symbol ->
            if (i == item.dotPosition) parts.add(".")
            parts.add(symbol.name)
        }
        if (item.dotPosition == body.size) parts.add(".")
        return "${item.production.head.name} -> ${parts.joinToString(" ")}"
    }

    private fun buildDot(title: String, automaton: SLR1Automata): String {
        val sb = StringBuilder()
        sb.appendLine("digraph $title {")
        sb.appendLine("  rankdir=LR;")
        sb.appendLine("  node [shape=box, style=rounded, fontname=\"Courier\"];")
        sb.appendLine()
        sb.appendLine("  start [shape=none, label=\"\"];")
        sb.appendLine("  start -> S${automaton.initialState.id};")
        sb.appendLine()

        for (state in automaton.states) {
            val label = buildString {
                append("State ${state.id}")
                for (item in state.items) {
                    append("\\n")
                    append(formatItem(item))
                }
            }
            val isAccepting = state.items.any {
                it.isComplete && it.production.head == automaton.augmentedGrammar.startSymbol
            }
            if (isAccepting) {
                sb.appendLine("  S${state.id} [label=\"$label\", peripheries=2];")
            } else {
                sb.appendLine("  S${state.id} [label=\"$label\"];")
            }
        }
        sb.appendLine()

        for ((transition, nextState) in automaton.transitions) {
            val (fromState, symbol) = transition
            sb.appendLine("  S$fromState -> S$nextState [label=\"${symbol.name}\"];")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    fun slr1ToDot(automaton: SLR1Automata): String = buildDot("SLR1", automaton)

    // recibe SLR1Automata porque LALR1AutomatonMerger.mergeFromSLR1 retorna SLR1Automata
    fun lalr1ToDot(automaton: SLR1Automata): String = buildDot("LALR1", automaton)

    fun renderToImage(dot: String, outputPath: String): Boolean {
        return try {
            val process = ProcessBuilder("dot", "-Tpng", "-o", outputPath)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            process.outputStream.use { it.write(dot.toByteArray()) }
            process.waitFor() == 0
        } catch (e: IOException) {
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}