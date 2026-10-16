package org.compiler.frontend.semantic

import org.compiler.catalog.Column
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation

/**
 * Una columna encontrada, con el origen del FROM que la pone a la vista y a
 * cuantos ambitos de distancia estaba ese origen.
 *
 * El indice absoluto dentro de la fila es `source.index + posicion en su tabla`,
 * que es lo que hace que un JOIN cuadre sin llevar la cuenta aparte.
 */
data class ColumnMatch(val column: Column, val source: Symbol, val level: Int) {
    val index: Int get() = source.index + (source.table?.indexOf(column.name) ?: 0)

    // La ficha que queda pegada a la referencia. Lleva el indice ya absoluto y de
    // que tabla real viene, que es lo que la rejilla muestra en el encabezado.
    fun toSymbol(location: LexemeLocation) = Symbol(
        name = column.name,
        kind = DeclarationKind.COLUMN,
        type = column.type,
        location = location,
        scopeName = source.scopeName,
        sourceTable = source.sourceTable,
        index = index
    )
}

/**
 * Busca una columna entre los origenes del FROM, subiendo de ambito si no esta.
 *
 * Devuelve una lista y no un solo resultado porque un nombre sin calificar puede
 * estar en dos tablas del mismo FROM, y decidir que eso es ambiguo es de quien
 * llama, no de la busqueda.
 */
fun Scope.findColumn(qualifier: String?, name: String, level: Int = 0): List<ColumnMatch> {
    val visible = sources().filter { qualifier == null || it.name == qualifier }
    val here = visible.mapNotNull { source ->
        source.table?.column(name)?.let { ColumnMatch(it, source, level) }
    }
    if (here.isNotEmpty()) return here

    // Un calificador que existe en ESTE nivel manda: si la subconsulta dice
    // `FROM posts u`, su `u.id` es de posts y no se va a buscar al `u` de afuera.
    if (qualifier != null && visible.isNotEmpty()) return emptyList()

    return parent?.findColumn(qualifier, name, level + 1).orEmpty()
}

// El origen del FROM que lleva ese nombre, subiendo de ambito. Sirve para separar
// "ese alias no existe" de "ese alias no tiene esa columna".
fun Scope.findSource(name: String): Symbol? =
    sources().firstOrNull { it.name == name } ?: parent?.findSource(name)

/**
 * Los origenes del FROM de este ambito, en el orden en que se escribieron.
 *
 * El ambito global tambien guarda tablas, pero para mostrarlas en el arbol del
 * IDE, no para que una columna suelta se resuelva contra cualquier tabla de la
 * base que nadie puso en el FROM.
 */
fun Scope.sources(): List<Symbol> =
    if (kind == ScopeKind.GLOBAL) emptyList() else localSymbols().filter { it.table != null }
