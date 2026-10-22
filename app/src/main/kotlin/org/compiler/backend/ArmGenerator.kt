package org.compiler.backend

import org.compiler.backend.models.AssemblyProgram
import org.compiler.backend.models.Condition
import org.compiler.backend.models.FloatRegister
import org.compiler.backend.models.Register
import org.compiler.backend.models.immediate
import org.compiler.backend.models.literal
import org.compiler.backend.models.memory
import org.compiler.frontend.intermediate.StorageLayout
import org.compiler.frontend.intermediate.TacPrinter
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.intermediate.models.VirtualTableAddress
import org.compiler.frontend.intermediate.models.VirtualTableDefinition

/**
 * Traduce el codigo de tres direcciones a ensamblador ARM de 32 bits.
 *
 * Solo lee el TAC y la memoria que ya asigno el StorageAllocator: nunca el AST. Una
 * instancia por compilacion, porque guarda las lineas y las etiquetas de los literales
 * mientras recorre el programa.
 *
 * El archivo sale en este orden: la configuracion, los datos (tablas de metodos,
 * globales y literales) y el codigo. El codigo se traduce funcion por funcion, y cada
 * funcion bloque basico por bloque basico: dentro de un bloque, getReg decide en que
 * registro vive cada valor.
 */
class ArmGenerator(
    private val program: TacProgram,
    private val storageLayout: StorageLayout,

    // Los registros que reparte getReg. Las pruebas usan menos para reproducir los
    // ejemplos del libro, que suponen una maquina de tres registros.
    valueRegisters: List<Register> = Register.VALUE_REGISTERS
) {
    private val emitter = ArmEmitter()

    // Cada texto y cada flotante constante del programa, con su etiqueta en .rodata. Un
    // mismo valor que aparece dos veces comparte la etiqueta.
    private val stringLabels = linkedMapOf<String, String>()
    private val floatLabels = linkedMapOf<Double, String>()

    // El registro de activacion de cada funcion, ya con sus temporales.
    private val framesByFunction = program.activationRecords.associate { record ->
        record.function to Frame(record)
    }

    private val memory = MemoryAccess(emitter, storageLayout, framesByFunction)
    private val registers = RegisterAllocator(memory, valueRegisters)

    fun generate(): AssemblyProgram {
        emitConfiguration()
        emitVirtualTables()
        emitStaticZone()
        emitConstants()
        emitCode()
        emitStackNote()
        return AssemblyProgram(emitter.lines, runtime)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  La configuracion y los datos
    // ══════════════════════════════════════════════════════════════════════

    // Sintaxis moderna, instrucciones de 32 bits y la unidad de punto flotante.
    private fun emitConfiguration() {
        emitter.directive("syntax", "unified")
        emitter.directive("arm")
        emitter.directive("fpu", "vfp")
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

        emitter.directive("data")
        emitter.directive("balign", "4")
        tables.forEach { table ->
            emitter.label(ArmNames.virtualTable(table.className))
            table.methods.forEach { method ->
                emitter.directive("word", ArmNames.function(method))
            }
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

        emitter.directive("bss")
        emitter.directive("balign", "8")
        emitter.label(ArmNames.STATIC_ZONE)
        emitter.directive("space", storageLayout.staticSize.toString())
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
            addressesOf(quadruple).filterIsInstance<Constant>().forEach { constant ->
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

        emitter.directive("section", ".rodata")
        stringLabels.forEach { (text, name) ->
            emitter.label(name)
            emitter.directive("asciz", quoted(text))
        }
        if (floatLabels.isNotEmpty()) emitter.directive("balign", "8")
        floatLabels.forEach { (value, name) ->
            emitter.label(name)
            emitDouble(value)
        }
    }

    // Infinito y NaN no tienen forma escrita en el ensamblador: van como sus 8 bytes, la
    // mitad baja primero. Pueden aparecer si el plegado calculo 1.0 / 0.0.
    private fun emitDouble(value: Double) {
        if (value.isFinite()) {
            emitter.directive("double", value.toString())
            return
        }
        val bits = value.toRawBits()
        emitter.directive(
            "word", (bits and 0xFFFFFFFFL).toString(), (bits ushr 32).toString()
        )
    }

    // Sin esta seccion el enlazador avisa que la pila podria ser ejecutable.
    private fun emitStackNote() {
        emitter.directive("section", ".note.GNU-stack", "\"\"", "%progbits")
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
    //  Las funciones
    // ══════════════════════════════════════════════════════════════════════

    // Cada funcion es lo que va de su begin_func a su end_func. Las tablas de metodos ya
    // se escribieron en .data.
    private fun emitCode() {
        emitter.directive("text")
        val instructions = program.instructions.filterNot { it is VirtualTableDefinition }

        var index = 0
        while (index < instructions.size) {
            val begin = instructions[index] as? FunctionBegin
                ?: error("Codigo fuera de una funcion: ${TacPrinter.line(instructions[index])}")
            val endIndex = (index + 1 until instructions.size).first { position ->
                (instructions[position] as? FunctionEnd)?.function == begin.function
            }
            emitFunction(begin, instructions.subList(index + 1, endIndex))
            index = endIndex + 1
        }
    }

    // Cada instruccion del TAC va precedida de un comentario con su texto, para leer el
    // .s junto al TAC. Al final de cada bloque se guarda en memoria lo que solo estaba en
    // un registro y se va a necesitar despues.
    private fun emitFunction(begin: FunctionBegin, body: List<Quadruple>) {
        emitter.comment(TacPrinter.line(begin).trim())
        emitPrologue(begin.function)

        basicBlocksOf(body).forEach { block ->
            val blockEnd = block.start + block.instructions.size
            val liveAtExit = NextUseAnalysis.liveAtExitOf(body, blockEnd)
            registers.startBlock(NextUseAnalysis(block, liveAtExit), liveAtExit)

            block.instructions.forEachIndexed { position, quadruple ->
                emitter.comment(TacPrinter.line(quadruple).trim())
                registers.startInstruction(position, usesOf(quadruple), definitionOf(quadruple))
                translate(quadruple)
            }
            if (!endsBlock(block.instructions.last())) registers.flush()
        }

        emitter.comment("end_func ${begin.function.name}")
        emitEpilogue(begin.function)
    }

    private fun translate(quadruple: Quadruple) {
        when (quadruple) {
            is Arithmetic -> emitArithmetic(quadruple)
            is Relational -> emitRelational(quadruple)
            is Unary -> emitUnary(quadruple)
            is Copy -> emitCopy(quadruple)
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
        memory.currentFunction = function

        val name = ArmNames.function(function)
        if (isMain(function)) emitter.directive("global", name)
        emitter.label(name)

        if (isMain(function)) {
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
    //     ldr lr, [fp, #-12]       a donde se vuelve
    //     sub sp, fp, #12          la pila como la dejo quien llamo
    //     ldr fp, [fp, #-4]        el registro de quien llamo
    //     add sp, sp, #12          solo main: libera su parte fija
    //     bx lr                    vuelve
    private fun emitEpilogue(function: FunctionLabel) {
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
        }
        emitter.instruction("bx", Register.LR.text)
        memory.currentFunction = null
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Enteros, booleanos y referencias
    // ══════════════════════════════════════════════════════════════════════

    //     x = a + b    ->   add Rx, Ra, Rb
    //     x = a + 5    ->   add Rx, Ra, #5
    //     x = a * b    ->   mul Rx, Ra, Rb
    //     x = a / b    ->   mov r0, Ra   mov r1, Rb   bl __aeabi_idiv   mov Rx, r0
    //     x = a % b    ->   igual, con __aeabi_idivmod, y el residuo sale en r1
    private fun emitArithmetic(arithmetic: Arithmetic) {
        if (arithmetic.kind == OperandKind.FLOAT) return emitFloatArithmetic(arithmetic)

        val left = registerOf(arithmetic.left, scratch = Register.R2)
        when (arithmetic.operator) {
            ArithmeticOperator.ADD, ArithmeticOperator.SUBTRACT -> {
                val right = secondOperandOf(arithmetic.right)
                val mnemonic = if (arithmetic.operator == ArithmeticOperator.ADD) "add" else "sub"
                val result = registers.registerForResult(listOfNotNull(left, right.register))
                emitter.instruction(mnemonic, result.text, left.text, right.text)
                registers.recordResult(result)
            }
            ArithmeticOperator.MULTIPLY -> {
                val right = registerOf(arithmetic.right, scratch = Register.R3)
                val result = registers.registerForResult(listOf(left, right))
                emitter.instruction("mul", result.text, left.text, right.text)
                registers.recordResult(result)
            }
            ArithmeticOperator.DIVIDE, ArithmeticOperator.MODULO -> {
                val right = registerOf(arithmetic.right, scratch = Register.R3)
                emitDivision(arithmetic.operator, left, right)
            }
        }
    }

    // La Raspberry Pi 1 no tiene instruccion de division: la hace una rutina de libgcc,
    // que recibe el dividendo en r0 y el divisor en r1. Solo toca r0 a r3 y r12, asi que
    // los valores de getReg sobreviven. El chequeo de division entre cero ya va antes, en
    // el TAC.
    private fun emitDivision(operator: ArithmeticOperator, left: Register, right: Register) {
        moveArguments(left, right)
        val isDivision = operator == ArithmeticOperator.DIVIDE
        emitter.instruction("bl", if (isDivision) "__aeabi_idiv" else "__aeabi_idivmod")

        val result = registers.registerForResult(listOf(left, right))
        val answer = if (isDivision) Register.R0 else Register.R1
        emitter.instruction("mov", result.text, answer.text)
        registers.recordResult(result)
    }

    //     x = a < b    ->   cmp Ra, Rb   mov Rx, #0   movlt Rx, #1
    //
    // Es el slt de MIPS: cmp deja la respuesta en las banderas, y solo una de las dos
    // escrituras cuenta. mov sin s no toca las banderas, asi que el orden es seguro aunque
    // Rx sea el registro de un operando.
    private fun emitRelational(relational: Relational) {
        val condition = Condition.of(relational.operator, relational.kind)
        when (relational.kind) {
            OperandKind.FLOAT -> {
                memory.loadFloatOperand(relational.left, FloatRegister.D1)
                memory.loadFloatOperand(relational.right, FloatRegister.D2)
                emitFloatCompare()
                writeBoolean(condition, emptyList())
            }
            OperandKind.STRING -> {
                val left = registerOf(relational.left, scratch = Register.R2)
                val right = registerOf(relational.right, scratch = Register.R3)
                moveArguments(left, right)
                emitter.instruction("bl", ArmNames.COMPARE_STRINGS)
                emitter.instruction("cmp", Register.R0.text, immediate(0))
                writeBoolean(condition, listOf(left, right))
            }
            else -> {
                val left = registerOf(relational.left, scratch = Register.R2)
                val right = secondOperandOf(relational.right)
                emitter.instruction("cmp", left.text, right.text)
                writeBoolean(condition, listOfNotNull(left, right.register))
            }
        }
    }

    // Los dos operandos de una rutina, en r0 y r1. Vienen de registros de getReg o de
    // r2 y r3, asi que copiarlos en este orden no pisa ninguno.
    private fun moveArguments(first: Register, second: Register) {
        emitter.instruction("mov", Register.R0.text, first.text)
        emitter.instruction("mov", Register.R1.text, second.text)
    }

    // Despues de un cmp: 1 si se cumple la condicion, 0 si no.
    private fun writeBoolean(condition: Condition, operandRegisters: List<Register>) {
        val result = registers.registerForResult(operandRegisters)
        emitter.instruction("mov", result.text, immediate(0))
        emitter.instruction("mov${condition.suffix}", result.text, immediate(1))
        registers.recordResult(result)
    }

    //     x = - a          ->   rsb Rx, Ra, #0      0 - a
    //     x = ! b          ->   eor Rx, Rb, #1      cambia 0 por 1 y 1 por 0
    //     x = inttofloat i ->   vmov s0, Ri   vcvt.f64.s32 d0, s0   vstr d0, [x]
    private fun emitUnary(unary: Unary) {
        when (unary.operator) {
            TacUnaryOperator.INT_TO_FLOAT -> {
                val operand = registerOf(unary.operand, scratch = Register.R2)
                emitter.instruction("vmov", "s0", operand.text)
                emitter.instruction("vcvt.f64.s32", FloatRegister.D0.text, "s0")
                memory.storeFloat(FloatRegister.D0, unary.result)
            }
            TacUnaryOperator.NEGATE -> {
                if (unary.kind == OperandKind.FLOAT) {
                    memory.loadFloatOperand(unary.operand, FloatRegister.D1)
                    emitter.instruction("vneg.f64", FloatRegister.D0.text, FloatRegister.D1.text)
                    memory.storeFloat(FloatRegister.D0, unary.result)
                    return
                }
                emitIntegerUnary("rsb", unary.operand)
            }
            TacUnaryOperator.NOT -> emitIntegerUnary("eor", unary.operand)
        }
    }

    // rsb Rx, Ra, #0 o eor Rx, Rb, #1: las dos llevan el mismo segundo operando.
    private fun emitIntegerUnary(mnemonic: String, operand: Address) {
        val source = registerOf(operand, scratch = Register.R2)
        val result = registers.registerForResult(listOf(source))
        val constant = if (mnemonic == "eor") 1L else 0L
        emitter.instruction(mnemonic, result.text, source.text, immediate(constant))
        registers.recordResult(result)
    }

    //     x = y        ->   ninguna instruccion: x se anota en el registro de y
    //     x = 5        ->   mov Rx, #5
    //     x = 3.5      ->   vldr d0, [.Lfloat_1]   vstr d0, [x]
    private fun emitCopy(copy: Copy) {
        if (copy.kind == OperandKind.FLOAT) {
            memory.loadFloatOperand(copy.source, FloatRegister.D0)
            memory.storeFloat(FloatRegister.D0, copy.result)
            return
        }
        if (isVariable(copy.source)) {
            registers.recordCopy(registers.registerFor(copy.source))
            return
        }
        val result = registers.registerForResult(emptyList())
        loadConstant(result, copy.source)
        registers.recordResult(result)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Flotantes
    // ══════════════════════════════════════════════════════════════════════

    //     x = a +f b   ->   vldr d1, [a]   vldr d2, [b]   vadd.f64 d0, d1, d2   vstr d0, [x]
    //
    // Los flotantes no pasan por getReg: cada instruccion carga sus operandos y guarda el
    // resultado en memoria.
    private fun emitFloatArithmetic(arithmetic: Arithmetic) {
        val mnemonic = when (arithmetic.operator) {
            ArithmeticOperator.ADD -> "vadd.f64"
            ArithmeticOperator.SUBTRACT -> "vsub.f64"
            ArithmeticOperator.MULTIPLY -> "vmul.f64"
            ArithmeticOperator.DIVIDE -> "vdiv.f64"
            ArithmeticOperator.MODULO -> throw UntranslatedInstruction(arithmetic)
        }
        memory.loadFloatOperand(arithmetic.left, FloatRegister.D1)
        memory.loadFloatOperand(arithmetic.right, FloatRegister.D2)
        emitter.instruction(
            mnemonic, FloatRegister.D0.text, FloatRegister.D1.text, FloatRegister.D2.text
        )
        memory.storeFloat(FloatRegister.D0, arithmetic.result)
    }

    // vcmp compara en la unidad de punto flotante, y vmrs copia el resultado a las
    // banderas principales, que son las que leen los sufijos.
    private fun emitFloatCompare() {
        emitter.instruction("vcmp.f64", FloatRegister.D1.text, FloatRegister.D2.text)
        emitter.instruction("vmrs", "APSR_nzcv", "fpscr")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  print
    // ══════════════════════════════════════════════════════════════════════

    //     print_i x        ->   mov r0, Rx              bl cps_imprimir_entero
    //     print_i 42       ->   mov r0, #42             bl cps_imprimir_entero
    //     print_s "hola"   ->   ldr r0, =.Lstr_1        bl cps_imprimir_string
    //     print_f x        ->   vldr d0, [x]            bl cps_imprimir_flotante
    //
    // El valor va en r0, o en d0 si es flotante, que es donde lo espera la rutina. Las
    // rutinas conservan r4 a r11, asi que lo que guarda getReg sobrevive.
    private fun emitPrint(print: Print) {
        val routine = when (print.kind) {
            OperandKind.INTEGER -> ArmNames.PRINT_INTEGER
            OperandKind.BOOLEAN -> ArmNames.PRINT_BOOLEAN
            OperandKind.STRING -> ArmNames.PRINT_STRING
            OperandKind.FLOAT -> ArmNames.PRINT_FLOAT
            OperandKind.REFERENCE -> error("print de una referencia: el TypeChecker lo rechaza")
        }
        when {
            print.kind == OperandKind.FLOAT ->
                memory.loadFloatOperand(print.value, FloatRegister.D0)
            isVariable(print.value) -> {
                val value = registers.registerFor(print.value)
                emitter.instruction("mov", Register.R0.text, value.text)
            }
            else -> loadConstant(Register.R0, print.value)
        }
        emitter.instruction("bl", routine)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Operandos
    // ══════════════════════════════════════════════════════════════════════

    // Un operando en un registro: el que le da getReg si es una variable, o el registro de
    // paso indicado si es una constante. r2 y r3 no los reparte getReg.
    private fun registerOf(operand: Address, scratch: Register): Register {
        if (isVariable(operand)) return registers.registerFor(operand)
        loadConstant(scratch, operand)
        return scratch
    }

    // El segundo operando de add, sub o cmp: una constante que cabe va dentro de la
    // instruccion, y entonces no ocupa registro.
    private data class SecondOperand(val text: String, val register: Register?)

    private fun secondOperandOf(operand: Address): SecondOperand {
        val value = (operand as? Constant)?.let { integerValueOf(it) }
        if (value != null && ArmEmitter.fitsImmediate(value.toInt())) {
            return SecondOperand(immediate(value), register = null)
        }
        val register = registerOf(operand, scratch = Register.R3)
        return SecondOperand(register.text, register)
    }

    // Una constante entera, booleana, nula, de texto o la direccion de una tabla de
    // metodos, en un registro.
    private fun loadConstant(register: Register, address: Address) {
        when (address) {
            is VirtualTableAddress ->
                emitter.instruction(
                    "ldr", register.text, literal(ArmNames.virtualTable(address.className))
                )
            is Constant -> {
                val text = address.value as? String
                if (text != null) {
                    emitter.instruction("ldr", register.text, literal(stringLabels.getValue(text)))
                } else {
                    emitter.loadInteger(register, checkNotNull(integerValueOf(address)))
                }
            }
            else -> error("${TacPrinter.address(address)} no es una constante")
        }
    }

    // true es 1, false y null son 0.
    private fun integerValueOf(constant: Constant): Long? = when (val value = constant.value) {
        is Long -> value
        is Boolean -> if (value) 1L else 0L
        null -> 0L
        else -> null
    }

    // Un flotante en un registro de punto flotante: de su casilla si es una variable, o de
    // su etiqueta en .rodata si es una constante.
    private fun MemoryAccess.loadFloatOperand(operand: Address, register: FloatRegister) {
        val value = (operand as? Constant)?.value
        if (value == null) {
            loadFloat(register, operand)
            return
        }
        emitter.instruction("ldr", Register.IP.text, literal(floatLabels.getValue(value as Double)))
        emitter.instruction("vldr", register.text, memory(Register.IP))
    }

    private fun frameOf(function: FunctionLabel): Frame =
        checkNotNull(framesByFunction[function]) { "'${function.name}' sin registro" }

    private fun isMain(function: FunctionLabel): Boolean =
        ArmNames.function(function) == ArmNames.ENTRY_POINT
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
