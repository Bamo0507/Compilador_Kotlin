package org.compiler.backend

import org.compiler.backend.models.FloatRegister
import org.compiler.backend.models.Register
import org.compiler.backend.models.literal
import org.compiler.backend.models.memory
import org.compiler.frontend.intermediate.StorageAllocator
import org.compiler.frontend.intermediate.StorageLayout
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.StorageLocation

/**
 * Donde vive en memoria cada variable del TAC, y las instrucciones que la leen y la
 * escriben.
 *
 *   local o parametro, desplazamiento d, tamaño s   [fp, #-(d+s)]
 *   temporal tN                                     su casilla al final del registro
 *   global, desplazamiento d                        ldr ip, =cps_estatico   y   [ip, #d]
 *   cuenta^2                                        seguir dos enlaces de acceso desde fp
 *
 * El tamaño elige la instruccion: ldr y str para 4 bytes, ldrb y strb para un booleano,
 * vldr y vstr para un flotante. Un temporal entero o booleano se guarda siempre con sus
 * 4 bytes: su casilla mide 8, asi que no hace falta distinguirlos.
 */
class MemoryAccess(
    private val emitter: ArmEmitter,
    private val storageLayout: StorageLayout,
    private val frames: Map<FunctionLabel, Frame>
) {
    // La funcion que se esta traduciendo: sus locales y temporales estan en su registro.
    var currentFunction: FunctionLabel? = null

    // El ambito de cada funcion, para encontrar la que la contiene al seguir un enlace de
    // acceso.
    private val scopesByFunction: Map<FunctionLabel, Scope> =
        storageLayout.functions.entries.associate { (scope, record) -> record.function to scope }

    // Una direccion de memoria: un registro base mas un desplazamiento.
    private data class Slot(val base: Register, val offset: Int)

    fun load(register: Register, variable: Address) {
        val mnemonic = if (isByte(variable)) "ldrb" else "ldr"
        val slot = slotOf(variable)
        emitter.instruction(
            mnemonic, register.text, memory(slot.base, slot.offset), comment = nameOf(variable)
        )
    }

    fun store(register: Register, variable: Address) {
        val mnemonic = if (isByte(variable)) "strb" else "str"
        val slot = slotOf(variable)
        emitter.instruction(
            mnemonic, register.text, memory(slot.base, slot.offset), comment = nameOf(variable)
        )
    }

    fun loadFloat(register: FloatRegister, variable: Address) {
        emitter.instruction(
            "vldr", register.text, floatOperandOf(variable), comment = nameOf(variable)
        )
    }

    fun storeFloat(register: FloatRegister, variable: Address) {
        emitter.instruction(
            "vstr", register.text, floatOperandOf(variable), comment = nameOf(variable)
        )
    }

    // vldr y vstr solo alcanzan 1020 bytes desde la base. Mas lejos, la direccion
    // completa se calcula antes en ip.
    private fun floatOperandOf(variable: Address): String {
        val slot = slotOf(variable)
        if (slot.offset in -1020..1020) return memory(slot.base, slot.offset)
        emitter.arithmeticWithConstant("add", Register.IP, slot.base, slot.offset)
        return memory(Register.IP)
    }

    // Si hace falta, deja en ip la base: la zona estatica o el registro de otra funcion.
    private fun slotOf(variable: Address): Slot = when (variable) {
        is Temporary -> Slot(Register.FP, currentFrame().temporaryOffset(variable.index))
        is Name -> slotOf(variable)
        else -> error("${TacPrinter.address(variable)} no vive en memoria")
    }

    private fun slotOf(name: Name): Slot {
        val size = StorageAllocator.sizeOf(name.symbol.type)
        return when (val storage = name.symbol.storage) {
            is StorageLocation.Static -> {
                emitter.instruction("ldr", Register.IP.text, literal(ArmNames.STATIC_ZONE))
                Slot(Register.IP, storage.offset)
            }
            is StorageLocation.Frame -> {
                val base = accessLink(name.hops)
                Slot(base, -(storage.offset + size))
            }
            is StorageLocation.Field, null -> error("'${name.symbol.name}' no es una variable")
        }
    }

    // `cuenta^2`: cada salto lee el enlace de acceso del registro donde se esta, que
    // apunta al registro de la funcion que lo contiene. Al final, ip apunta al registro
    // donde vive la variable. Con cero saltos es el registro actual, o sea fp.
    fun accessLink(hops: Int): Register {
        var function = checkNotNull(currentFunction)
        var base = Register.FP
        repeat(hops) {
            val frame = frameOf(function)
            emitter.instruction(
                "ldr", Register.IP.text, memory(base, frame.accessLinkOffset),
                comment = "enlace de acceso"
            )
            base = Register.IP
            function = enclosingFunctionOf(function)
        }
        return base
    }

    private fun enclosingFunctionOf(function: FunctionLabel): FunctionLabel {
        val scope = checkNotNull(scopesByFunction[function]) { "'${function.name}' sin ambito" }
        val enclosing = generateSequence(scope.parent) { it.parent }
            .first { it.kind == ScopeKind.FUNCTION }
        return storageLayout.functions.getValue(enclosing).function
    }

    private fun frameOf(function: FunctionLabel): Frame =
        checkNotNull(frames[function]) { "'${function.name}' sin registro" }

    private fun currentFrame(): Frame = frameOf(checkNotNull(currentFunction))

    private fun isByte(variable: Address): Boolean =
        variable is Name && variable.symbol.type == BooleanType

    private fun nameOf(variable: Address): String = TacPrinter.address(variable)
}
