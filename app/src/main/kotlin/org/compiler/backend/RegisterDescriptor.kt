package org.compiler.backend

import org.compiler.backend.models.Register
import org.compiler.frontend.intermediate.models.Address

/**
 * La libreta de los registros: para cada registro, que variables tienen su valor actual
 * en el (Dragon Book 8.6.1). Un registro puede tener varias a la vez: despues de
 * `x = y`, el registro de y tiene tambien a x.
 *
 * Arranca vacia en cada bloque basico.
 */
class RegisterDescriptor(registers: List<Register>) {

    private val contents: Map<Register, MutableSet<Address>> =
        registers.associateWith { mutableSetOf() }

    val registers: Set<Register> get() = contents.keys

    fun variablesIn(register: Register): Set<Address> = contents.getValue(register).toSet()

    fun isEmpty(register: Register): Boolean = contents.getValue(register).isEmpty()

    // El registro queda con una sola variable: lo que tenia antes se pierde.
    fun holdOnly(register: Register, variable: Address) {
        contents.getValue(register).apply {
            clear()
            add(variable)
        }
    }

    fun add(register: Register, variable: Address) {
        contents.getValue(register).add(variable)
    }

    // La variable deja de estar en todos los registros.
    fun removeEverywhere(variable: Address) {
        contents.values.forEach { it.remove(variable) }
    }

    fun clear() {
        contents.values.forEach { it.clear() }
    }
}
