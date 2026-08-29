package org.compiler.frontend.semantic.models

import org.compiler.frontend.semantic.symbols.Symbol

// Lo que un recolector de basura necesitaria saber de un simbolo.
data class SymbolLiveness(
    val symbol: Symbol,
    val scopeName: String,
    val declaredAtLine: Int,

    // null = nunca se usa.
    val lastUseLine: Int?,

    val useCount: Int,
    val usedInNestedFunction: Boolean
) {
    // Su memoria nunca hizo falta.
    val neverUsed: Boolean get() = useCount == 0
}

/**
 * El reporte completo, agrupado por ambito. Lo muestra la GUI.
 *
 * No hay una propiedad `liberable`: en Compiscript la respuesta seria siempre si,
 * porque una funcion anidada no puede sobrevivir a la de afuera. usedInNestedFunction
 * se reporta como observacion, no como impedimento.
 */
data class GarbageCollectorReport(
    val entriesByScope: Map<String, List<SymbolLiveness>>
) {
    val neverUsed: List<SymbolLiveness>
        get() = entriesByScope.values.flatten().filter { it.neverUsed }

    val usedInNestedFunctions: List<SymbolLiveness>
        get() = entriesByScope.values.flatten().filter { it.usedInNestedFunction }
}
