package org.compiler.frontend.semantic

import org.compiler.frontend.semantic.models.GarbageCollectorReport
import org.compiler.frontend.semantic.models.SymbolLiveness
import org.compiler.frontend.semantic.symbols.Scope

/**
 * Arma el reporte de vivacidad recorriendo el ARBOL DE AMBITOS.
 *
 * No recorre el AST ni lleva cursor: la Fase 4 ya dejo los contadores en cada Symbol
 * mientras verificaba. Aqui solo se leen y se agrupan.
 *
 * No produce errores: una variable declarada y nunca usada no lo es. Esto va a una
 * vista propia de la GUI, no a la lista de errores.
 */
class LivenessReportBuilder {

    fun build(globalScope: Scope): GarbageCollectorReport =
        GarbageCollectorReport(entriesByScope = collectScope(globalScope, linkedMapOf()))

    // Linked para que los ambitos salgan en el orden en que se declararon, que es el
    // orden del codigo fuente.
    private fun collectScope(
        scope: Scope,
        accumulated: LinkedHashMap<String, List<SymbolLiveness>>
    ): Map<String, List<SymbolLiveness>> {
        accumulated[scope.name] = scope.localSymbols().map { symbol ->
            SymbolLiveness(
                symbol = symbol,
                scopeName = scope.name,
                declaredAtLine = symbol.location.line,
                lastUseLine = symbol.lastUseLine,
                useCount = symbol.useCount,
                usedInNestedFunction = symbol.usedInNestedFunction
            )
        }

        scope.children.forEach { collectScope(it, accumulated) }
        return accumulated
    }
}
