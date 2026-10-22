package org.compiler.backend

import org.compiler.backend.models.ArmLine
import org.compiler.backend.models.AssemblyProgram
import org.compiler.backend.models.FloatRegister
import org.compiler.backend.models.Register
import org.compiler.backend.models.immediate
import org.compiler.backend.models.literal
import org.compiler.backend.models.memory
import org.compiler.frontend.intermediate.StorageLayout
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Allocate
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.IndexedLoad
import org.compiler.frontend.intermediate.models.IndexedStore
import org.compiler.frontend.intermediate.models.IndirectCall
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.ListPush
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Param
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.intermediate.models.VirtualTableDefinition

/**
 * Traduce el codigo de tres direcciones a ensamblador ARM de 32 bits.
 *
 * Solo lee el TAC y la memoria que ya asigno el StorageAllocator: nunca el AST. Una
 * instancia por compilacion, porque guarda las lineas y las etiquetas de los literales
 * mientras recorre el programa.
 *
 * El archivo sale en este orden: la configuracion, los datos (tablas de metodos,
 * globales y literales) y el codigo.
 */
class ArmGenerator(
    private val program: TacProgram,
    private val storageLayout: StorageLayout
) {
    private val lines = mutableListOf<ArmLine>()

    // Cada texto y cada flotante constante del programa, con su etiqueta en .rodata. Un
    // mismo valor que aparece dos veces comparte la etiqueta.
    private val stringLabels = linkedMapOf<String, String>()
    private val floatLabels = linkedMapOf<Double, String>()

    // El registro de activacion de cada funcion, ya con sus temporales.
    private val framesByFunction = program.activationRecords.associate { record ->
        record.function to Frame(record)
    }

    // La funcion que se esta traduciendo y la forma de su registro.
    private var currentFunction: FunctionLabel? = null
    private var currentFrame: Frame? = null

    fun generate(): AssemblyProgram {
        emitConfiguration()
        emitVirtualTables()
        emitStaticZone()
        emitConstants()
        emitCode()
        emitStackNote()
        return AssemblyProgram(lines.toList(), runtime)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  La configuracion y los datos
    // ══════════════════════════════════════════════════════════════════════

    // Sintaxis moderna, instrucciones de 32 bits y la unidad de punto flotante.
    private fun emitConfiguration() {
        directive("syntax", "unified")
        directive("arm")
        directive("fpu", "vfp")
    }

    //     .data
    //     .balign 4
    // cps_vtable_Perro:
    //     .word cps_Perro.hablar
    //     .word cps_Animal.comer
    //
    // Una entrada de 4 bytes por metodo, en el orden de la tabla: la casilla n esta en
    // el desplazamiento 4n, que es el que usa la llamada indirecta.
    private fun emitVirtualTables() {
        val tables = program.instructions.filterIsInstance<VirtualTableDefinition>()
        if (tables.isEmpty()) return

        directive("data")
        directive("balign", "4")
        tables.forEach { table ->
            label(ArmNames.virtualTable(table.className))
            table.methods.forEach { method -> directive("word", ArmNames.function(method)) }
        }
    }

    //     .bss
    //     .balign 8
    // cps_estatico:
    //     .space 8
    //
    // Las globales reciben su valor con instrucciones dentro de main, asi que la zona no
    // tiene valores iniciales: va en .bss, que llega en ceros. Se alinea a 8 porque una
    // global flotante necesita empezar en un multiplo de 8.
    private fun emitStaticZone() {
        if (storageLayout.staticSize == 0) return

        directive("bss")
        directive("balign", "8")
        label(ArmNames.STATIC_ZONE)
        directive("space", storageLayout.staticSize.toString())
    }

    //     .section .rodata
    // .Lstr_1:
    //     .asciz "hola"
    //     .balign 8
    // .Lfloat_1:
    //     .double 3.5
    //
    // Los textos y los flotantes constantes, en solo lectura. Se recogen recorriendo
    // todo el TAC, para darle a cada valor distinto una sola etiqueta.
    private fun emitConstants() {
        program.instructions.forEach { quadruple ->
            operandsOf(quadruple).filterIsInstance<Constant>().forEach { constant ->
                when (val value = constant.value) {
                    is String -> stringLabels.getOrPut(value) {
                        ArmNames.string(stringLabels.size + 1)
                    }
                    is Double -> floatLabels.getOrPut(value) {
                        ArmNames.float(floatLabels.size + 1)
                    }
                }
            }
        }
        if (stringLabels.isEmpty() && floatLabels.isEmpty()) return

        directive("section", ".rodata")
        stringLabels.forEach { (text, name) ->
            label(name)
            directive("asciz", quoted(text))
        }
        if (floatLabels.isNotEmpty()) directive("balign", "8")
        floatLabels.forEach { (value, name) ->
            label(name)
            emitDouble(value)
        }
    }

    // Infinito y NaN no tienen forma escrita en el ensamblador: van como sus 8 bytes, la
    // mitad baja primero. Pueden aparecer si el plegado calculo 1.0 / 0.0.
    private fun emitDouble(value: Double) {
        if (value.isFinite()) {
            directive("double", value.toString())
            return
        }
        val bits = value.toRawBits()
        directive("word", (bits and 0xFFFFFFFFL).toString(), (bits ushr 32).toString())
    }

    // Sin esta seccion el enlazador avisa que la pila podria ser ejecutable.
    private fun emitStackNote() {
        directive("section", ".note.GNU-stack", "\"\"", "%progbits")
    }

    // Las comillas, las barras y los saltos de linea se escapan como en C.
    private fun quoted(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ══════════════════════════════════════════════════════════════════════
    //  El codigo
    // ══════════════════════════════════════════════════════════════════════

    // Cada instruccion del TAC va precedida de un comentario con su texto, para leer el
    // .s junto al TAC. Las tablas de metodos ya se escribieron en .data.
    private fun emitCode() {
        directive("text")
        program.instructions.forEach { quadruple ->
            if (quadruple is VirtualTableDefinition) return@forEach
            comment(TacPrinter.line(quadruple).trim())
            translate(quadruple)
        }
    }

    private fun translate(quadruple: Quadruple) {
        when (quadruple) {
            is FunctionBegin -> emitPrologue(quadruple.function)
            is FunctionEnd -> emitEpilogue()
            is Print -> emitPrint(quadruple)
            else -> throw UntranslatedInstruction(quadruple)
        }
    }

    //     .global main
    // main:
    //     sub sp, sp, #12          solo main: nadie le reservo la parte fija
    //     add ip, sp, #12          el tope del registro, que dejo reservado quien llamo
    //     str fp, [ip, #-4]        enlace de control: el fp de quien llamo
    //     str lr, [ip, #-12]       direccion de retorno
    //     mov fp, ip               este es el registro actual
    //     sub sp, fp, #24          reserva locales y temporales
    private fun emitPrologue(function: FunctionLabel) {
        val frame = frameOf(function)
        currentFunction = function
        currentFrame = frame

        val name = ArmNames.function(function)
        if (isMain(function)) directive("global", name)
        label(name)

        if (isMain(function)) {
            arithmeticWithConstant("sub", Register.SP, Register.SP, frame.fixedPartSize)
        }
        arithmeticWithConstant("add", Register.IP, Register.SP, frame.fixedPartSize)
        instruction(
            "str", Register.FP.text, memory(Register.IP, frame.controlLinkOffset),
            comment = "enlace de control"
        )
        instruction(
            "str", Register.LR.text, memory(Register.IP, frame.returnAddressOffset),
            comment = "direccion de retorno"
        )
        instruction("mov", Register.FP.text, Register.IP.text)
        arithmeticWithConstant("sub", Register.SP, Register.FP, frame.size)
    }

    //     mov r0, #0               solo main: el codigo de salida
    //     ldr lr, [fp, #-12]       a donde se vuelve
    //     sub sp, fp, #12          la pila como la dejo quien llamo
    //     ldr fp, [fp, #-4]        el registro de quien llamo
    //     add sp, sp, #12          solo main: libera su parte fija
    //     bx lr                    vuelve
    private fun emitEpilogue() {
        val function = checkNotNull(currentFunction) { "end_func sin begin_func" }
        val frame = checkNotNull(currentFrame)

        if (isMain(function)) instruction("mov", Register.R0.text, immediate(0))
        instruction("ldr", Register.LR.text, memory(Register.FP, frame.returnAddressOffset))
        arithmeticWithConstant("sub", Register.SP, Register.FP, frame.fixedPartSize)
        instruction("ldr", Register.FP.text, memory(Register.FP, frame.controlLinkOffset))
        if (isMain(function)) {
            arithmeticWithConstant("add", Register.SP, Register.SP, frame.fixedPartSize)
        }
        instruction("bx", Register.LR.text)

        currentFunction = null
        currentFrame = null
    }

    // ══════════════════════════════════════════════════════════════════════
    //  print
    // ══════════════════════════════════════════════════════════════════════

    //     print_i 42       ->  mov r0, #42             bl cps_imprimir_entero
    //     print_s "hola"   ->  ldr r0, =.Lstr_1        bl cps_imprimir_string
    //     print_b true     ->  mov r0, #1              bl cps_imprimir_booleano
    //     print_f 3.5      ->  ldr ip, =.Lfloat_1
    //                          vldr d0, [ip]           bl cps_imprimir_flotante
    //
    // El valor va en r0, o en d0 si es flotante, que es donde lo espera la rutina. Por
    // ahora solo con constantes: cargar una variable es trabajo de getReg.
    private fun emitPrint(print: Print) {
        val constant = print.value as? Constant ?: throw UntranslatedInstruction(print)
        val routine = when (print.kind) {
            OperandKind.INTEGER -> {
                loadInteger(Register.R0, constant.value as Long)
                ArmNames.PRINT_INTEGER
            }
            OperandKind.BOOLEAN -> {
                loadInteger(Register.R0, if (constant.value == true) 1L else 0L)
                ArmNames.PRINT_BOOLEAN
            }
            OperandKind.STRING -> {
                val text = stringLabels.getValue(constant.value as String)
                instruction("ldr", Register.R0.text, literal(text))
                ArmNames.PRINT_STRING
            }
            OperandKind.FLOAT -> {
                loadFloat(FloatRegister.D0, constant.value as Double)
                ArmNames.PRINT_FLOAT
            }
            OperandKind.REFERENCE -> error("print de una referencia: el TypeChecker lo rechaza")
        }
        instruction("bl", routine)
    }

    // Un entero de 32 bits en un registro. Si cabe en la instruccion, `mov r0, #42`; si
    // su complemento cabe, `mvn r0, #6`, que deja -7; si no, desde el literal pool.
    private fun loadInteger(register: Register, value: Long) {
        val word = value.toInt()
        when {
            fitsImmediate(word) -> instruction("mov", register.text, immediate(word.toLong()))
            fitsImmediate(word.inv()) ->
                instruction("mvn", register.text, immediate(word.inv().toLong()))
            else -> instruction("ldr", register.text, literal(word.toString()))
        }
    }

    // Un flotante no cabe en ninguna instruccion: se lee de su etiqueta en .rodata.
    private fun loadFloat(register: FloatRegister, value: Double) {
        instruction("ldr", Register.IP.text, literal(floatLabels.getValue(value)))
        instruction("vldr", register.text, memory(Register.IP))
    }

    private fun frameOf(function: FunctionLabel): Frame =
        checkNotNull(framesByFunction[function]) { "'${function.name}' sin registro" }

    private fun isMain(function: FunctionLabel): Boolean =
        ArmNames.function(function) == ArmNames.ENTRY_POINT

    // `add rd, rn, #c`. Una constante solo cabe en la instruccion si es un numero de 8
    // bits rotado una cantidad par; si no, se carga antes en ip desde el literal pool.
    private fun arithmeticWithConstant(
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

    private fun fitsImmediate(value: Int): Boolean =
        (0 until 32 step 2).any { rotation ->
            (Integer.rotateLeft(value, rotation) and 0xFF.inv()) == 0
        }

    // ══════════════════════════════════════════════════════════════════════
    //  Ayudantes
    // ══════════════════════════════════════════════════════════════════════

    // Todas las direcciones que lee o escribe una instruccion.
    private fun operandsOf(quadruple: Quadruple): List<Address> = when (quadruple) {
        is Arithmetic -> listOf(quadruple.result, quadruple.left, quadruple.right)
        is Relational -> listOf(quadruple.result, quadruple.left, quadruple.right)
        is Concat -> listOf(quadruple.result, quadruple.left, quadruple.right)
        is Unary -> listOf(quadruple.result, quadruple.operand)
        is Copy -> listOf(quadruple.result, quadruple.source)
        is IfGoto -> listOf(quadruple.condition)
        is IfFalseGoto -> listOf(quadruple.condition)
        is IfRelationalGoto -> listOf(quadruple.left, quadruple.right)
        is Param -> listOf(quadruple.value)
        is Call -> listOfNotNull(quadruple.result)
        is Return -> listOfNotNull(quadruple.value)
        is IndexedLoad -> listOf(quadruple.result, quadruple.base, quadruple.offset)
        is IndexedStore -> listOf(quadruple.base, quadruple.offset, quadruple.value)
        is ListPush -> listOf(quadruple.list, quadruple.value)
        is Print -> listOf(quadruple.value)
        is TryBegin -> listOf(quadruple.exceptionVariable)
        is Throw -> listOf(quadruple.message)
        is Allocate -> listOf(quadruple.result, quadruple.size)
        is IndirectCall -> listOfNotNull(quadruple.result, quadruple.target)
        is LabelDefinition, is Goto, is FunctionBegin, is FunctionEnd, TryEnd,
        is VirtualTableDefinition -> emptyList()
    }

    private fun directive(name: String, vararg arguments: String) {
        lines += ArmLine.Directive(name, arguments.toList())
    }

    private fun label(name: String) {
        lines += ArmLine.LabelLine(name)
    }

    private fun instruction(mnemonic: String, vararg operands: String, comment: String? = null) {
        lines += ArmLine.Instruction(mnemonic, operands.toList(), comment)
    }

    private fun comment(text: String) {
        lines += ArmLine.Comment(text)
    }
}

// Las rutinas de apoyo, escritas a mano. Van al final de cada programa.
private val runtime: String by lazy {
    val resource = checkNotNull(ArmGenerator::class.java.getResource("/runtime/runtime.s")) {
        "No se encontro runtime.s entre los recursos"
    }
    resource.readText()
}

// Una instruccion del TAC que el backend todavia no sabe traducir.
class UntranslatedInstruction(quadruple: Quadruple) :
    RuntimeException("Todavía no se traduce: ${TacPrinter.line(quadruple).trim()}")
