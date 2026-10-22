package org.compiler.backend

import org.compiler.backend.models.ArmLine
import org.compiler.backend.models.AssemblyProgram

/**
 * El texto del archivo .s. Es el unico lugar que decide como se ve el ensamblador.
 *
 * Las etiquetas van pegadas al margen y todo lo demas con sangria, igual que en el TAC:
 * asi los destinos de los saltos y los inicios de funcion se encuentran de un vistazo.
 */
object ArmPrinter {

    private const val INDENT = "    "

    // El archivo termina con un salto de linea: sin el, el ensamblador avisa que la
    // ultima linea quedo incompleta. Sin el runtime, el texto es solo lo generado.
    fun print(program: AssemblyProgram, includeRuntime: Boolean = true): String {
        val generated = program.lines.joinToString("\n", postfix = "\n") { line(it) }
        if (!includeRuntime || program.runtime.isEmpty()) return generated
        return generated + "\n" + program.runtime.trimEnd() + "\n"
    }

    fun line(line: ArmLine): String = when (line) {
        is ArmLine.LabelLine -> "${line.name}:"
        is ArmLine.Directive -> INDENT + directive(line)
        is ArmLine.Instruction -> INDENT + instruction(line)
        is ArmLine.Comment -> "$INDENT@ ${line.text}"
    }

    // `.asciz "hola"`: el nombre con su punto, y los argumentos separados por coma.
    private fun directive(directive: ArmLine.Directive): String {
        val name = ".${directive.name}"
        if (directive.arguments.isEmpty()) return name
        return "$name ${directive.arguments.joinToString(", ")}"
    }

    // `ldr r4, [fp, #-8]  @ x`: el mnemonico, los operandos separados por coma y, si la
    // instruccion lo trae, su comentario al final.
    private fun instruction(instruction: ArmLine.Instruction): String {
        val mnemonic = instruction.mnemonic
        val operands = instruction.operands.joinToString(", ")
        val text = if (operands.isEmpty()) mnemonic else "$mnemonic $operands"
        return instruction.comment?.let { "$text  @ $it" } ?: text
    }
}
