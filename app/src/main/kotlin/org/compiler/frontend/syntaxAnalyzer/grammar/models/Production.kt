package org.compiler.frontend.syntaxAnalyzer.grammar.models

// id identifies this production by number (e.g. "reduce by production 5") so that
// LR items and parse traces can reference it without comparing full symbol lists.
//
// precedenceLabel is the %prec override: when present, this production takes the
// precedence LEVEL of the label instead of the one of its own operator. It is what lets
// unary minus bind tighter than multiplication even though the token OP_MINUS is declared
// at the additive level (for subtraction). The label is a pseudo-token such as UMINUS: it
// never appears in any production body and the lexer never emits it.
data class Production(
    val id: Int,
    val head: Symbol.NonTerminal,
    val body: List<Symbol>,
    val precedenceLabel: Symbol.Terminal? = null
)
