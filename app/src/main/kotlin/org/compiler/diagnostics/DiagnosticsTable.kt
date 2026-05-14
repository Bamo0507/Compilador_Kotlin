package org.compiler.diagnostics

object DiagnosticsTable {
    private val errors = mutableListOf<CompilerError>()

    fun report(error: CompilerError) = errors.add(error)
    fun lexerErrors(): List<CompilerError.LexerError> = errors.filterIsInstance<CompilerError.LexerError>()
    fun parserErrors(): List<CompilerError.ParserError> = errors.filterIsInstance<CompilerError.ParserError>()
    fun all(): List<CompilerError> = errors.toList()
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun clear() = errors.clear()
}
