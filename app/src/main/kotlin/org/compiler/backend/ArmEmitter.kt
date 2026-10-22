package org.compiler.backend

import org.compiler.backend.models.ArmLine
import org.compiler.backend.models.Register
import org.compiler.backend.models.immediate
import org.compiler.backend.models.literal

/**
 * Donde se van escribiendo las lineas del .s. Lo comparten el generador, getReg y el
 * acceso a memoria, para que las tres partes escriban en el mismo archivo y en orden.
 */
class ArmEmitter {

    private val emitted = mutableListOf<ArmLine>()

    val lines: List<ArmLine> get() = emitted.toList()

    fun directive(name: String, vararg arguments: String) {
        emitted += ArmLine.Directive(name, arguments.toList())
    }

    fun label(name: String) {
        emitted += ArmLine.LabelLine(name)
    }

    fun instruction(mnemonic: String, vararg operands: String, comment: String? = null) {
        emitted += ArmLine.Instruction(mnemonic, operands.toList(), comment)
    }

    fun comment(text: String) {
        emitted += ArmLine.Comment(text)
    }

    // `add rd, rn, #c`. Una constante solo cabe en la instruccion si es un numero de 8
    // bits rotado una cantidad par; si no, se carga antes en ip desde el literal pool.
    fun arithmeticWithConstant(
        mnemonic: String,
        destination: Register,
        source: Register,
        constant: Int
    ) {
        if (fitsImmediate(constant)) {
            instruction(mnemonic, destination.text, source.text, immediate(constant.toLong()))
            return
        }
        instruction("ldr", Register.IP.text, literal(constant.toString()))
        instruction(mnemonic, destination.text, source.text, Register.IP.text)
    }

    // Un entero de 32 bits en un registro. Si cabe en la instruccion, `mov r0, #42`; si
    // su complemento cabe, `mvn r0, #6`, que deja -7; si no, desde el literal pool.
    fun loadInteger(register: Register, value: Long) {
        val word = value.toInt()
        when {
            fitsImmediate(word) -> instruction("mov", register.text, immediate(word.toLong()))
            fitsImmediate(word.inv()) ->
                instruction("mvn", register.text, immediate(word.inv().toLong()))
            else -> instruction("ldr", register.text, literal(word.toString()))
        }
    }

    companion object {
        // Si el numero cabe como constante dentro de una instruccion de ARM.
        fun fitsImmediate(value: Int): Boolean =
            (0 until 32 step 2).any { rotation ->
                (Integer.rotateLeft(value, rotation) and 0xFF.inv()) == 0
            }
    }
}
