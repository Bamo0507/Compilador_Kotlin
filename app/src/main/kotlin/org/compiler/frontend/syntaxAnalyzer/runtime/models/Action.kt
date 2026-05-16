package org.compiler.frontend.syntaxAnalyzer.runtime.models

import org.compiler.frontend.syntaxAnalyzer.grammar.models.Production

sealed interface Action {
    data class Shift(val nextState: Int) : Action
    data class Reduce(val production: Production) : Action
    data object Accept : Action
}
