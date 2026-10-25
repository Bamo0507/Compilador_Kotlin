package org.compiler.backend

import org.compiler.frontend.intermediate.StorageAllocator
import org.compiler.frontend.intermediate.models.ActivationRecordField
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

    /**
     * La parte fija: de la casilla 0 hasta el final de la direccion de retorno. La
     * reserva quien llama; el resto, la funcion misma.
     *
     * Redondeada hacia arriba a 8, como el registro completo (decision 42). Sin eso, una
     * funcion con parte fija de 36 dejaria sp en un multiplo de 4 durante toda su
     * ejecucion, y libc exige que sp este alineado a 8 en cada llamada. Los
     * desplazamientos de las casillas no cambian: se cuentan desde fp, que sigue siendo
     * el tope del registro, y el relleno queda debajo.
     */
    val fixedPartSize: Int
        get() = slot(StorageAllocator.RETURN_ADDRESS_SLOT)
            .let { align(it.offset + it.size) }

    private fun align(size: Int): Int = (size + 7) / 8 * 8

    // Donde se guarda el fp de quien llama.
    val controlLinkOffset: Int get() = fpOffsetOf(StorageAllocator.CONTROL_LINK_SLOT)

    // Donde se guarda la direccion del registro de la funcion que contiene a esta.
    val accessLinkOffset: Int get() = fpOffsetOf(StorageAllocator.ACCESS_LINK_SLOT)

    // Donde se guarda lr, la direccion a la que se vuelve.
    val returnAddressOffset: Int get() = fpOffsetOf(StorageAllocator.RETURN_ADDRESS_SLOT)

    // La casilla del temporal tN, al final del registro: `t2` -> [fp, #-40].
    fun temporaryOffset(index: Int): Int = fpOffsetOf("t$index")

    // ── Visto desde quien llama ────────────────────────────────────────────

    // Quien llama reserva la parte fija y escribe ahi los argumentos, pero todavia no
    // tiene fp: el registro nuevo todavia no es el actual. Como su tope va a quedar en
    // sp + parte fija, la casilla de desplazamiento d y tamaño s cae en sp + F - d - s.

    // La casilla del parametro numero index. Los parametros son los primeros campos del
    // registro, en el orden en que se declararon.
    fun parameterSpOffset(index: Int): Int = spOffsetOf(record.fields[index])

    // Cuanto mide esa casilla. Decide la instruccion: un booleano ocupa un byte, y
    // escribirle cuatro pisaria la casilla del parametro anterior.
    fun parameterSize(index: Int): Int = record.fields[index].size

    // La casilla donde quien llama deja el enlace de acceso del registro nuevo.
    val accessLinkSpOffset: Int get() = spOffsetOf(slot(StorageAllocator.ACCESS_LINK_SLOT))

    private fun spOffsetOf(field: ActivationRecordField): Int =
        fixedPartSize - field.offset - field.size

    // El desplazamiento desde fp de una casilla del registro: siempre negativo.
    fun fpOffsetOf(offset: Int, size: Int): Int = -(offset + size)

    private fun fpOffsetOf(slotName: String): Int =
        slot(slotName).let { fpOffsetOf(it.offset, it.size) }

    private fun slot(name: String) = checkNotNull(record.fields.firstOrNull { it.name == name }) {
        "El registro de '${record.function.name}' no tiene la casilla '$name'"
    }
}
