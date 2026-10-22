package org.compiler.backend

import org.compiler.frontend.intermediate.StorageAllocator
import org.compiler.frontend.intermediate.models.ActivationRecordLayout

/**
 * Un registro de activacion visto desde la maquina.
 *
 * El StorageAllocator da a cada casilla un desplazamiento que crece desde 0, pero la pila
 * de ARM crece hacia abajo. Se reconcilian con un espejo: fp apunta al tope del registro,
 * y la casilla con desplazamiento d y tamaño s vive en fp - d - s. Asi ningun numero del
 * StorageAllocator cambia.
 *
 * Con el registro de factorial, que mide 40:
 *   n                     desplazamiento 0, tamaño 4   ->  [fp, #-4]
 *   enlace de control     desplazamiento 8, tamaño 4   ->  [fp, #-12]
 *   direccion de retorno  desplazamiento 16, tamaño 4  ->  [fp, #-20]
 */
class Frame(private val record: ActivationRecordLayout) {

    // Lo que mide el registro completo, con locales y temporales.
    val size: Int get() = record.size

    // La parte fija: de la casilla 0 hasta el final de la direccion de retorno. La
    // reserva quien llama; el resto, la funcion misma.
    val fixedPartSize: Int
        get() = slot(StorageAllocator.RETURN_ADDRESS_SLOT).let { it.offset + it.size }

    // Donde se guarda el fp de quien llama.
    val controlLinkOffset: Int get() = fpOffsetOf(StorageAllocator.CONTROL_LINK_SLOT)

    // Donde se guarda la direccion del registro de la funcion que contiene a esta.
    val accessLinkOffset: Int get() = fpOffsetOf(StorageAllocator.ACCESS_LINK_SLOT)

    // Donde se guarda lr, la direccion a la que se vuelve.
    val returnAddressOffset: Int get() = fpOffsetOf(StorageAllocator.RETURN_ADDRESS_SLOT)

    // La casilla del temporal tN, al final del registro: `t2` -> [fp, #-40].
    fun temporaryOffset(index: Int): Int = fpOffsetOf("t$index")

    // El desplazamiento desde fp de una casilla del registro: siempre negativo.
    fun fpOffsetOf(offset: Int, size: Int): Int = -(offset + size)

    private fun fpOffsetOf(slotName: String): Int =
        slot(slotName).let { fpOffsetOf(it.offset, it.size) }

    private fun slot(name: String) = checkNotNull(record.fields.firstOrNull { it.name == name }) {
        "El registro de '${record.function.name}' no tiene la casilla '$name'"
    }
}
