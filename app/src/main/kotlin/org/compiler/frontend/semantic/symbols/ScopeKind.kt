package org.compiler.frontend.semantic.symbols

// El nombre del ambito de un foreach empieza asi: `foreach@12`. El StorageAllocator lo
// reconoce para agregarle al registro las dos locales ocultas del bucle.
const val FOREACH_SCOPE_PREFIX = "foreach@"

enum class ScopeKind {
    GLOBAL,
    CLASS,
    FUNCTION,
    BLOCK,
    LOOP
}
