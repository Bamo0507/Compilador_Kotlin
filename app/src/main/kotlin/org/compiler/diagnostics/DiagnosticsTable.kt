package org.compiler.diagnostics

//RECORDAR LO DE RECUPERAR ERRORES, PENSAR CON LOS DEL PARSER, SE PODRIA HACER SI SE ASUME
// QUE SE ENCUENTRA LA CATEGORIA QUE "FALTABA" SEGUN LAS REGLAS DE LA GRAMATICA, QUE SIGA LEYENDO,
object DiagnosticsTable {
    private val errors = mutableListOf<CompilerError>()

    fun report(error: CompilerError) = errors.add(error)
    fun lexerErrors(): List<CompilerError.LexerError> = errors.filterIsInstance<CompilerError.LexerError>()
    fun parserErrors(): List<CompilerError.ParserError> = errors.filterIsInstance<CompilerError.ParserError>()

    fun all(): List<CompilerError> = errors.toList()
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun clear() = errors.clear()
}
