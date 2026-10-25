package org.compiler.backend

import org.compiler.backend.models.Register
import org.compiler.backend.models.immediate
import org.compiler.backend.models.memory
import org.compiler.backend.models.registerList
import org.compiler.frontend.intermediate.models.FunctionLabel

class CallingSequence(
    private val emitter: ArmEmitter,
    private val frames: Map<FunctionLabel, Frame>
) {

    // ── La funcion llamada ─────────────────────────────────────────────────

    //     add ip, sp, #24          el tope del registro, que la llamada dejo reservado
    //     str fp, [ip, #-12]       enlace de control: el fp de quien llama
    //     str lr, [ip, #-20]       direccion de retorno
    //     mov fp, ip               este es el registro actual
    //     sub sp, fp, #40          reserva locales y temporales
    //
    // fp se escribe hasta el final porque hasta ahi hace falta el de quien llama.
    fun prologue(function: FunctionLabel) {
        val frame = frameOf(function)

        val name = ArmNames.function(function)
        if (isMain(function)) emitter.directive("global", name)
        emitter.label(name)

        // main lo llama libc, que no reservo nada: se reserva su propia parte fija. Antes
        // guarda los registros de getReg, porque libc espera encontrarlos intactos al
        // volver. Son seis, asi que sp sigue alineado a 8.
        if (isMain(function)) {
            emitter.instruction("push", preservedByMain)
            emitter.arithmeticWithConstant("sub", Register.SP, Register.SP, frame.fixedPartSize)
        }
        emitter.arithmeticWithConstant("add", Register.IP, Register.SP, frame.fixedPartSize)
        emitter.instruction(
            "str", Register.FP.text, memory(Register.IP, frame.controlLinkOffset),
            comment = "enlace de control"
        )
        emitter.instruction(
            "str", Register.LR.text, memory(Register.IP, frame.returnAddressOffset),
            comment = "direccion de retorno"
        )
        emitter.instruction("mov", Register.FP.text, Register.IP.text)
        emitter.arithmeticWithConstant("sub", Register.SP, Register.FP, frame.size)
    }

    //     mov r0, #0               solo main: el codigo de salida
    //     ldr lr, [fp, #-20]       a donde se vuelve
    //     sub sp, fp, #24          la pila como la dejo quien llama
    //     ldr fp, [fp, #-12]       el registro de quien llama
    //     bx lr                    vuelve
    //
    // lr y fp se leen mientras fp todavia apunta a este registro, y por eso se recuperan
    // antes de moverlo.
    fun epilogue(function: FunctionLabel) {
        val frame = frameOf(function)

        if (isMain(function)) emitter.instruction("mov", Register.R0.text, immediate(0))
        emitter.instruction(
            "ldr", Register.LR.text, memory(Register.FP, frame.returnAddressOffset)
        )
        emitter.arithmeticWithConstant("sub", Register.SP, Register.FP, frame.fixedPartSize)
        emitter.instruction(
            "ldr", Register.FP.text, memory(Register.FP, frame.controlLinkOffset)
        )
        if (isMain(function)) {
            emitter.arithmeticWithConstant("add", Register.SP, Register.SP, frame.fixedPartSize)
            emitter.instruction("pop", preservedByMain)
        }
        emitter.instruction("bx", Register.LR.text)
    }

    // ── Quien llama ────────────────────────────────────────────────────────

    // `sub sp, sp, #24`: la parte fija del registro nuevo, donde van los argumentos y el
    // enlace de acceso. Lo que la funcion necesita de ahi en adelante lo reserva ella.
    fun openFrame(callee: FunctionLabel): Frame {
        val frame = frameOf(callee)
        emitter.arithmeticWithConstant("sub", Register.SP, Register.SP, frame.fixedPartSize)
        return frame
    }

    // `[sp, #16]`: la casilla del argumento numero index dentro del registro nuevo.
    fun parameterSlot(frame: Frame, index: Int): String =
        memory(Register.SP, frame.parameterSpOffset(index))

    // `str fp, [sp, #4]`: el registro de la funcion que contiene a la llamada.
    fun storeAccessLink(frame: Frame, base: Register) {
        emitter.instruction(
            "str", base.text, memory(Register.SP, frame.accessLinkSpOffset),
            comment = "enlace de acceso"
        )
    }

    fun call(callee: FunctionLabel) {
        emitter.instruction("bl", ArmNames.function(callee))
    }

    // `blx r4`: llama a la direccion que esta en el registro, que salio de la tabla de
    // metodos del objeto.
    fun callAddress(target: Register) {
        emitter.instruction("blx", target.text)
    }

    // `add sp, sp, #24`: el registro nuevo ya murio.
    fun closeFrame(frame: Frame) {
        emitter.arithmeticWithConstant("add", Register.SP, Register.SP, frame.fixedPartSize)
    }

    private val preservedByMain = registerList(*Register.VALUE_REGISTERS.toTypedArray())

    private fun frameOf(function: FunctionLabel): Frame =
        checkNotNull(frames[function]) { "'${function.name}' sin registro" }

    private fun isMain(function: FunctionLabel): Boolean =
        ArmNames.function(function) == ArmNames.ENTRY_POINT
}
