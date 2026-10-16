package org.compiler.frontend.semantic.symbols

/**
 * Un ambito: los nombres visibles en un punto del script.
 *
 * Es una clase normal y no un data class porque un ambito tiene IDENTIDAD: dos
 * ambitos con los mismos simbolos siguen siendo dos ambitos distintos.
 *
 * No se destruye al salir de el. El arbol completo sobrevive toda la corrida,
 * porque la GUI tiene que mostrarlo.
 */
class Scope(
    val kind: ScopeKind,
    val name: String,

    // El ambito que me contiene. Para una subconsulta es el de la consulta de
    // afuera, y por eso resolver un nombre externo es subir por aqui.
    val parent: Scope?
) {

    // Linked para conservar el orden de declaracion: es el orden de las columnas
    // en la fila, y el que muestra la GUI.
    private val symbols = linkedMapOf<String, Symbol>()

    private val childScopes = mutableListOf<Scope>()

    // Vista de solo lectura. Nadie de afuera agrega hijos.
    val children: List<Scope> get() = childScopes

    fun openChild(kind: ScopeKind, name: String): Scope {
        val child = Scope(kind, name, parent = this)
        childScopes.add(child)
        return child
    }

    /**
     * Falla solo si el nombre ya existe en ESTE nivel.
     *
     * Tapar un nombre de un ambito exterior es legal: una subconsulta puede usar
     * el alias `u` aunque la consulta de afuera ya lo tenga. Repetirlo en el mismo
     * FROM no lo es.
     */
    fun declare(symbol: Symbol): DeclareResult {
        val previous = symbols[symbol.name]
        if (previous != null) return DeclareResult.AlreadyDeclared(previous)

        symbols[symbol.name] = symbol
        return DeclareResult.Ok
    }

    /**
     * Busca en este nivel y luego sube por la cadena de padres. Gana la primera
     * coincidencia, que es la regla del ambito mas anidado.
     */
    fun lookup(name: String): Symbol? = symbols[name] ?: parent?.lookup(name)

    /**
     * Lo mismo que lookup, pero diciendo CUANTOS niveles subio.
     *
     * Ese numero es el `level` que la fase 4 pega en cada ColumnReference: 0 es
     * esta consulta, 1 la de afuera. El motor lo necesita para saber de que fila
     * de la pila sacar el valor, y sale gratis de la misma busqueda.
     */
    fun resolve(name: String): Resolution? {
        val local = symbols[name]
        if (local != null) return Resolution(local, level = 0)

        val outer = parent?.resolve(name) ?: return null
        return Resolution(outer.symbol, level = outer.level + 1)
    }

    // Solo ESTE nivel, sin subir. Para detectar un alias repetido en el mismo FROM.
    fun lookupLocal(name: String): Symbol? = symbols[name]

    // Los simbolos de este nivel, en orden de declaracion.
    fun localSymbols(): List<Symbol> = symbols.values.toList()
}

// Un simbolo encontrado, y a cuantos ambitos de distancia estaba.
data class Resolution(val symbol: Symbol, val level: Int)

/**
 * Es un sealed interface y no un Boolean porque cuando falla se necesita el
 * simbolo anterior, para poder decir en que linea estaba.
 */
sealed interface DeclareResult {
    data object Ok : DeclareResult
    data class AlreadyDeclared(val previous: Symbol) : DeclareResult
}
