// El modelo del ensamblador y su impresor: las formas de operando, las cuatro clases de
// linea, y un programa minimo que se imprime y, si hay Docker, se ejecuta.
package org.compiler

import org.compiler.backend.ArmPrinter
import org.compiler.backend.models.ArmLine
import org.compiler.backend.models.AssemblyProgram
import org.compiler.backend.models.Condition
import org.compiler.backend.models.FloatRegister
import org.compiler.backend.models.Register
import org.compiler.backend.models.immediate
import org.compiler.backend.models.literal
import org.compiler.backend.models.memory
import org.compiler.backend.models.registerList
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class ArmPrinterTest {

    // ── Los operandos ──────────────────────────────────────────────────────

    @Test
    fun `cada forma de operando se escribe como en el ensamblador`() {
        assertEquals("#5", immediate(5))
        assertEquals("#-8", immediate(-8))
        assertEquals("[fp, #-8]", memory(Register.FP, -8))
        assertEquals("[r4]", memory(Register.R4))
        assertEquals("[r4, r5]", memory(Register.R4, Register.R5))
        assertEquals("=cps_estatico", literal("cps_estatico"))
        assertEquals("{fp, lr}", registerList(Register.FP, Register.LR))
    }

    // ── Las lineas ─────────────────────────────────────────────────────────

    // La etiqueta va al margen; lo demas, con sangria.
    @Test
    fun `cada clase de linea tiene su forma`() {
        assertEquals("main:", ArmPrinter.line(ArmLine.LabelLine("main")))
        assertEquals("    .text", ArmPrinter.line(ArmLine.Directive("text")))
        assertEquals(
            "    .asciz \"hola\"",
            ArmPrinter.line(ArmLine.Directive("asciz", listOf("\"hola\"")))
        )
        assertEquals(
            "    add r4, r5, #5",
            ArmPrinter.line(ArmLine.Instruction("add", listOf("r4", "r5", immediate(5))))
        )
        assertEquals("    bx lr", ArmPrinter.line(ArmLine.Instruction("bx", listOf("lr"))))
        assertEquals(
            "    ldr r4, [fp, #-8]  @ x",
            ArmPrinter.line(
                ArmLine.Instruction("ldr", listOf("r4", memory(Register.FP, -8)), comment = "x")
            )
        )
        assertEquals(
            "    @ begin_func factorial, 40",
            ArmPrinter.line(ArmLine.Comment("begin_func factorial, 40"))
        )
    }

    // ── Las condiciones ────────────────────────────────────────────────────

    // Solo menor y menor o igual cambian cuando se comparan flotantes.
    @Test
    fun `la condicion de una relacion depende del tipo`() {
        val integer = OperandKind.INTEGER
        val float = OperandKind.FLOAT

        assertEquals(Condition.LESS, Condition.of(RelationalOperator.LESS, integer))
        assertEquals(Condition.MINUS, Condition.of(RelationalOperator.LESS, float))
        assertEquals(Condition.LESS_EQUAL, Condition.of(RelationalOperator.LESS_EQUAL, integer))
        assertEquals(Condition.LOWER_SAME, Condition.of(RelationalOperator.LESS_EQUAL, float))
        assertEquals(Condition.GREATER, Condition.of(RelationalOperator.GREATER, float))
        assertEquals(Condition.NOT_EQUAL, Condition.of(RelationalOperator.NOT_EQUAL, integer))
    }

    // ── Un programa completo ───────────────────────────────────────────────

    // El mismo programa de scripts/hola.s, armado con el modelo: imprime un entero y un
    // flotante con printf y termina con codigo 0.
    private val minimalProgram = AssemblyProgram(
        listOf(
            ArmLine.Directive("syntax", listOf("unified")),
            ArmLine.Directive("arm"),
            ArmLine.Directive("fpu", listOf("vfp")),
            ArmLine.Directive("section", listOf(".rodata")),
            ArmLine.LabelLine("formato"),
            ArmLine.Directive("asciz", listOf("\"int=%d double=%f\\n\"")),
            ArmLine.Directive("balign", listOf("8")),
            ArmLine.LabelLine("tres_y_medio"),
            ArmLine.Directive("double", listOf("3.5")),
            ArmLine.Directive("text"),
            ArmLine.Directive("global", listOf("main")),
            ArmLine.LabelLine("main"),
            instruction("push", registerList(Register.FP, Register.LR)),
            instruction("mov", Register.FP.text, Register.SP.text),
            instruction("ldr", Register.R0.text, literal("formato")),
            instruction("mov", Register.R1.text, immediate(42)),
            instruction("ldr", Register.IP.text, literal("tres_y_medio")),
            instruction("vldr", FloatRegister.D0.text, memory(Register.IP)),
            instruction("vmov", Register.R2.text, Register.R3.text, FloatRegister.D0.text),
            instruction("bl", "printf"),
            instruction("mov", Register.R0.text, immediate(0)),
            instruction("pop", registerList(Register.FP, Register.PC)),
            ArmLine.Directive("section", listOf(".note.GNU-stack", "\"\"", "%progbits"))
        )
    )

    private fun instruction(mnemonic: String, vararg operands: String) =
        ArmLine.Instruction(mnemonic, operands.toList())

    @Test
    fun `el programa minimo se imprime como el archivo de ensamblador`() {
        val expected = """
            |    .syntax unified
            |    .arm
            |    .fpu vfp
            |    .section .rodata
            |formato:
            |    .asciz "int=%d double=%f\n"
            |    .balign 8
            |tres_y_medio:
            |    .double 3.5
            |    .text
            |    .global main
            |main:
            |    push {fp, lr}
            |    mov fp, sp
            |    ldr r0, =formato
            |    mov r1, #42
            |    ldr ip, =tres_y_medio
            |    vldr d0, [ip]
            |    vmov r2, r3, d0
            |    bl printf
            |    mov r0, #0
            |    pop {fp, pc}
            |    .section .note.GNU-stack, "", %progbits
            |""".trimMargin()

        assertEquals(expected, ArmPrinter.print(minimalProgram))
    }

    // Ensambla y corre el programa en el contenedor ARM. Sin Docker o sin la imagen, se
    // salta: el resto de la bateria no depende de el.
    @Test
    fun `el programa minimo corre en el contenedor ARM`() {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")

        val execution = ArmContainer.run("minimo", ArmPrinter.print(minimalProgram))

        assertEquals(0, execution.exitCode, execution.output)
        assertEquals("int=42 double=3.500000\n", execution.output)
    }

    // El runtime es texto a mano: va despues de lo generado, y se puede dejar afuera.
    @Test
    fun `el runtime va al final y se puede omitir`() {
        val program = AssemblyProgram(listOf(ArmLine.Directive("text")), runtime = "@ runtime\n")

        assertEquals("    .text\n\n@ runtime\n", ArmPrinter.print(program))
        assertEquals("    .text\n", ArmPrinter.print(program, includeRuntime = false))
    }
}
