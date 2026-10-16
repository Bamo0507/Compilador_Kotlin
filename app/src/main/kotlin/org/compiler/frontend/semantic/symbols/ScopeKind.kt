package org.compiler.frontend.semantic.symbols

/**
 * Las dos clases de ambito que existen.
 *
 * GLOBAL es uno solo y contiene las tablas del catalogo. QUERY es uno por cada
 * SELECT, anidados igual que el SQL: el de una subconsulta tiene como padre el de
 * la consulta que la contiene.
 */
enum class ScopeKind {
    GLOBAL,
    QUERY
}
