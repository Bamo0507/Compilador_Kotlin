package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.semantic.symbols.DeclareResult
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation

// Declarar y reportar si el nombre ya existia es la misma operacion en las dos pasadas,
// asi que vive una sola vez. Son funciones de extension y no metodos de Scope porque
// Scope es un modelo de datos y no debe conocer Diagnostics.

fun Scope.declareOrReport(symbol: Symbol, diagnostics: Diagnostics) {
    when (val result = declare(symbol)) {
        is DeclareResult.Ok -> Unit
        is DeclareResult.AlreadyDeclared ->
            diagnostics.reportAlreadyDeclared(symbol.name, symbol.location, result.previous)
    }
}

// Aparte porque registerClassNames lo necesita SIN declarar: ahi el nombre repetido se
// detecta antes de intentar, para no dejar un ambito huerfano colgado.
fun Diagnostics.reportAlreadyDeclared(
    name: String,
    location: LexemeLocation,
    previous: Symbol
) {
    report(
        CompilerError.SemanticError(
            location = location,
            message = "'$name' ya fue declarado en este ámbito " +
                "(línea ${previous.location.line})"
        )
    )
}
