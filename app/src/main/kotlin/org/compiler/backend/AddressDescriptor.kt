package org.compiler.backend

import org.compiler.backend.models.Register
import org.compiler.frontend.intermediate.models.Address

// Donde puede estar el valor actual de una variable.
sealed interface Location {
    // En su lugar de memoria: su casilla del registro de activacion o de la zona estatica.
    data object Memory : Location

    data class InRegister(val register: Register) : Location
}

/**
 * La libreta de las direcciones: para cada variable, en donde esta su valor actual
 * (Dragon Book 8.6.1). Puede estar en varios lugares a la vez: recien cargada, esta en
 * memoria y en un registro, y los dos tienen el mismo valor.
 *
 * Una variable que la libreta no menciona esta solo en memoria, que es como empieza todo
 * bloque basico.
 */
class AddressDescriptor {

    private val locations = mutableMapOf<Address, MutableSet<Location>>()

    fun locationsOf(variable: Address): Set<Location> =
        locations[variable]?.toSet() ?: setOf(Location.Memory)

    fun isInMemory(variable: Address): Boolean = Location.Memory in locationsOf(variable)

    fun registersOf(variable: Address): List<Register> =
        locationsOf(variable).filterIsInstance<Location.InRegister>().map { it.register }

    // Si el valor sobrevive aunque se pise el registro dado: esta en memoria o en otro.
    fun isElsewhere(variable: Address, register: Register): Boolean =
        locationsOf(variable).any { it != Location.InRegister(register) }

    // La variable esta solo en este registro: su valor en memoria quedo viejo.
    fun setOnlyIn(variable: Address, register: Register) {
        locations[variable] = mutableSetOf(Location.InRegister(register))
    }

    // La variable se escribio directo en memoria: los registros que la tenian quedaron
    // con un valor viejo.
    fun setOnlyInMemory(variable: Address) {
        locations[variable] = mutableSetOf(Location.Memory)
    }

    fun add(variable: Address, location: Location) {
        mutableLocationsOf(variable).add(location)
    }

    fun remove(variable: Address, location: Location) {
        mutableLocationsOf(variable).remove(location)
    }

    fun clear() {
        locations.clear()
    }

    private fun mutableLocationsOf(variable: Address): MutableSet<Location> =
        locations.getOrPut(variable) { mutableSetOf(Location.Memory) }
}
