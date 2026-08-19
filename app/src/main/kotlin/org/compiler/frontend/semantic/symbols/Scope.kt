package org.compiler.frontend.semantic.symbols

/**
 * Un ambito: los nombres visibles en un lugar del programa.
 *
 * Es una clase normal y no un data class porque un ambito tiene IDENTIDAD: dos
 * ambitos con los mismos simbolos siguen siendo dos ambitos distintos.
 *
 * No se destruye al salir de el. El arbol completo sobrevive toda la compilacion,
 * porque la GUI tiene que mostrarlo y la herencia necesita ambitos ya cerrados.
 */
class Scope(
    val kind: ScopeKind,
    val name: String,

    // El ambito que me contiene.
    val parent: Scope?
) {

    // La clase de la que heredo. Es var porque de que hereda una clase se sabe
    // despues de crear su ambito.
    var superclass: Scope? = null
        private set

    fun attachSuperclass(scope: Scope) {
        require(superclass == null) { "La superclase de '$name' ya fue asignada" }
        superclass = scope
    }

    // Linked para conservar el orden de declaracion: importa para la GUI y para los
    // offsets.
    private val symbols = linkedMapOf<String, Symbol>()

    private val childScopes = mutableListOf<Scope>()

    // Vista de solo lectura. Nadie de afuera agrega hijos.
    val children: List<Scope> get() = childScopes

    private var nextOffset = 0

    fun openChild(kind: ScopeKind, name: String): Scope {
        val child = Scope(kind, name, parent = this)
        childScopes.add(child)
        return child
    }

    /**
     * Falla solo si el nombre ya existe en ESTE nivel. Tapar un nombre de un ambito
     * exterior es legal; redeclararlo en el mismo ambito no.
     */
    fun declare(symbol: Symbol): DeclareResult {
        val previous = symbols[symbol.name]
        if (previous != null) return DeclareResult.AlreadyDeclared(previous)

        // El ambito completa los tres datos que solo el conoce, para que ningun
        // llamador pueda equivocarse en ellos.
        symbols[symbol.name] = symbol.copy(
            offset = nextOffset,
            isMember = kind == ScopeKind.CLASS,
            declarationFunctionDepth = functionDepth()
        )
        nextOffset += 1
        return DeclareResult.Ok
    }

    /**
     * El lookup general: para resolver un nombre suelto como `x` o `saludar`.
     *
     * Busca en este nivel, luego en la cadena de superclases, y por ultimo en la de
     * ambitos que lo contienen. Gana la primera coincidencia, que es la regla del
     * ambito mas anidado.
     */
    fun lookup(name: String): Symbol? {
        val local = symbols[name]
        if (local != null) return local

        val inherited = superclass?.lookupMember(name)
        if (inherited != null) return inherited

        return parent?.lookup(name)
    }

    /**
     * Solo ESTE nivel, sin recorrer ninguna cadena.
     *
     * Para detectar redeclaracion, y para buscar el `constructor` de una clase.
     */
    fun lookupLocal(name: String): Symbol? = symbols[name]

    /**
     * Este nivel y la cadena de superclases, sin mirar los ambitos que lo contienen.
     *
     * Es lo que necesita `perro.nombre`: un campo heredado de Animal si, una variable
     * global llamada `nombre` no.
     */
    fun lookupMember(name: String): Symbol? =
        symbols[name] ?: superclass?.lookupMember(name)

    // La clase mas cercana que me contiene. Lo usa `this`.
    fun enclosingClass(): Scope? =
        if (kind == ScopeKind.CLASS) this else parent?.enclosingClass()

    // Cuantas funciones hay en la cadena entre aqui y la raiz. Clases, bloques y
    // bucles no cuentan.
    fun functionDepth(): Int =
        (if (kind == ScopeKind.FUNCTION) 1 else 0) + (parent?.functionDepth() ?: 0)

    // Los simbolos de este nivel, en orden de declaracion.
    fun localSymbols(): List<Symbol> = symbols.values.toList()
}

/**
 * Es un sealed interface y no un Boolean porque cuando falla se necesita el simbolo
 * anterior, para poder decir en que linea estaba.
 */
sealed interface DeclareResult {
    data object Ok : DeclareResult
    data class AlreadyDeclared(val previous: Symbol) : DeclareResult
}
