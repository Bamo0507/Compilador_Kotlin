package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol

sealed interface Action {
    data class Shift(val nextState: Int) : Action
    data class Reduce(val production: Production) : Action
    data object Accept : Action
    data class Match(val terminal: Symbol.Terminal) : Action
    data class Expand(val production: Production) : Action
}
