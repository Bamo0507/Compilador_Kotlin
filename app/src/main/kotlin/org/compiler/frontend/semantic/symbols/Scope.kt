package org.compiler.frontend.semantic.symbols

import org.compiler.frontend.intermediate.models.ClassLayout

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

    // Solo para kind == CLASS: como queda un objeto de esta clase en memoria y su
    // tabla de metodos. La pone el StorageAllocator, despues del analisis semantico; la
    // lee la tabla de simbolos del IDE.
    var classLayout: ClassLayout? = null

    fun attachSuperclass(scope: Scope) {
        require(superclass == null) { "La superclase de '$name' ya fue asignada" }
        superclass = scope
    }

    // Linked para conservar el orden de declaracion: importa para la GUI y para los
    // desplazamientos en memoria.
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
     * Falla solo si el nombre ya existe en ESTE nivel. Tapar un nombre de un ambito
     * exterior es legal; redeclararlo en el mismo ambito no.
     */
    fun declare(symbol: Symbol): DeclareResult {
        val previous = symbols[symbol.name]
        if (previous != null) return DeclareResult.AlreadyDeclared(previous)

        // El ambito completa los dos datos que solo el conoce, para que ningun
        // llamador pueda equivocarse en ellos.
        symbols[symbol.name] = symbol.copy(
            isMember = kind == ScopeKind.CLASS,
            declarationFunctionDepth = functionDepth()
        )
        return DeclareResult.Ok
    }

    /**
     * El lookup general: para resolver un nombre suelto como `x` o `saludar`.
     *
     * Busca en este nivel y luego en los ambitos que lo contienen. Gana la primera
     * coincidencia, que es la regla del ambito mas anidado.
     */
    fun lookup(name: String): Symbol? {
        // Los miembros de una clase solo se alcanzan con `this.`, como en TypeScript:
        // el nombre suelto sigue de largo hacia el ambito que contiene a la clase.
        if (kind == ScopeKind.CLASS) return parent?.lookup(name)
        return symbols[name] ?: parent?.lookup(name)
    }

    /**
     * Solo ESTE nivel, sin recorrer ninguna cadena.
     *
     * Para detectar redeclaracion, y para encontrar la firma de una funcion recien
     * declarada en el ambito actual.
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
