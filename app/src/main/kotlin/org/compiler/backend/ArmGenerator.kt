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
import org.compiler.frontend.intermediate.models.Allocate
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
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
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.ListPush
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Param
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
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
    private val calls = CallingSequence(emitter, framesByFunction)

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
        memory.currentFunction = begin.function
        calls.prologue(begin.function)

        // La vivacidad se resuelve sobre el grafo de la funcion entera antes de empezar:
        // lo que hay que guardar al final de un bloque depende de por donde puede seguir
        // el flujo, y eso no se ve mirando una instruccion a la vez.
        val blocks = basicBlocksOf(body)
        val liveness = BlockLiveness(blocks)

        blocks.forEachIndexed { number, block ->
            val liveAtExit = liveness.liveAtExitOf(number)
            registers.startBlock(NextUseAnalysis(block, liveAtExit), liveAtExit)

            block.instructions.forEachIndexed { position, quadruple ->
                emitter.comment(TacPrinter.line(quadruple).trim())
                registers.startInstruction(position, usesOf(quadruple), definitionOf(quadruple))
                val following = block.instructions.subList(position + 1, block.instructions.size)
                translate(quadruple, begin.function, following)
            }
            if (!endsBlock(block.instructions.last())) registers.flush()
        }

        emitter.comment("end_func ${begin.function.name}")
        emitter.label(ArmNames.functionExit(begin.function))
        calls.epilogue(begin.function)
        memory.currentFunction = null
    }

    // La funcion llega hasta aqui porque las etiquetas del TAC llevan su nombre: el L3
    // de una funcion y el L3 de otra son etiquetas distintas en el mismo archivo.
    // `following` son las instrucciones que quedan del bloque: el primer `param` las
    // mira para saber a quien se esta llamando.
    private fun translate(
        quadruple: Quadruple,
        function: FunctionLabel,
        following: List<Quadruple>
    ) {
        when (quadruple) {
            is Arithmetic -> emitArithmetic(quadruple)
            is Relational -> emitRelational(quadruple)
            is Unary -> emitUnary(quadruple)
            is Copy -> emitCopy(quadruple)
            is Print -> emitPrint(quadruple)
            is LabelDefinition -> emitter.label(ArmNames.label(function, quadruple.label))
            is Goto -> emitGoto(quadruple, function)
            is IfGoto ->
                emitTruthJump(quadruple.condition, Condition.NOT_EQUAL, quadruple.label, function)
            is IfFalseGoto ->
                emitTruthJump(quadruple.condition, Condition.EQUAL, quadruple.label, function)
            is IfRelationalGoto -> emitRelationalJump(quadruple, function)
            is Param -> emitParam(quadruple, following)
            is Call -> emitCall(quadruple)
            is IndirectCall -> emitIndirectCall(quadruple)
            is Return -> emitReturn(quadruple, function)
            is Allocate -> emitAllocate(quadruple)
            is IndexedLoad -> emitIndexedLoad(quadruple)
            is IndexedStore -> emitIndexedStore(quadruple)
            is ListPush -> emitListPush(quadruple)
            is Concat -> emitConcat(quadruple)
            is Throw -> emitThrow(quadruple)
            else -> throw UntranslatedInstruction(quadruple)
        }
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
        val operands = emitComparison(relational.left, relational.right, relational.kind)
        writeBoolean(Condition.of(relational.operator, relational.kind), operands)
    }

    private fun emitComparison(left: Address, right: Address, kind: OperandKind): List<Register> =
        when (kind) {
            OperandKind.FLOAT -> {
                memory.loadFloatOperand(left, FloatRegister.D1)
                memory.loadFloatOperand(right, FloatRegister.D2)
                emitFloatCompare()
                emptyList()
            }
            OperandKind.STRING -> {
                val first = registerOf(left, scratch = Register.R2)
                val second = registerOf(right, scratch = Register.R3)
                moveArguments(first, second)
                emitter.instruction("bl", ArmNames.COMPARE_STRINGS)
                emitter.instruction("cmp", Register.R0.text, immediate(0))
                listOf(first, second)
            }
            else -> {
                val first = registerOf(left, scratch = Register.R2)
                val second = secondOperandOf(right)
                emitter.instruction("cmp", first.text, second.text)
                listOfNotNull(first, second.register)
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
                storeFloatResult(unary.result)
            }
            TacUnaryOperator.NEGATE -> {
                if (unary.kind == OperandKind.FLOAT) {
                    memory.loadFloatOperand(unary.operand, FloatRegister.D1)
                    emitter.instruction("vneg.f64", FloatRegister.D0.text, FloatRegister.D1.text)
                    storeFloatResult(unary.result)
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
            storeFloatResult(copy.result)
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
    //  Control de flujo
    // ══════════════════════════════════════════════════════════════════════

    //     goto L3      ->   el vaciado del bloque   b .Lfactorial_3
    private fun emitGoto(goto: Goto, function: FunctionLabel) {
        registers.flush()
        emitter.instruction("b", ArmNames.label(function, goto.label))
    }

    // Un booleano del TAC es 1 o 0, asi que "es verdadero" es "no es cero". Es la misma
    // comparacion para los dos, y solo cambia el sufijo del salto.
    private fun emitTruthJump(
        condition: Address,
        whenToJump: Condition,
        label: Label,
        function: FunctionLabel
    ) {
        val value = registerOf(condition, scratch = Register.R2)
        emitter.instruction("cmp", value.text, immediate(0))
        branch(whenToJump, label, function)
    }

    // El TAC ya trae la relacion que hace falta: el generador de TAC convirtio cada
    // condicion en un salto, invirtiendola cuando el salto se da si la condicion NO se
    // cumple. Aqui no se vuelve a invertir nada.
    private fun emitRelationalJump(goto: IfRelationalGoto, function: FunctionLabel) {
        emitComparison(goto.left, goto.right, goto.kind)
        branch(Condition.of(goto.operator, goto.kind), goto.label, function)
    }

    private fun branch(condition: Condition, label: Label, function: FunctionLabel) {
        registers.flush()
        emitter.instruction("b${condition.suffix}", ArmNames.label(function, label))
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Llamadas
    // ══════════════════════════════════════════════════════════════════════

    // La llamada que se esta armando: el registro nuevo ya reservado, y por que
    // parametro va. Vive entre el primer `param` y su `call`, que en el TAC son
    // consecutivos porque los argumentos se evaluan antes.
    private class PendingCall(val frame: Frame) {
        var nextParameter = 0
    }

    private var pendingCall: PendingCall? = null

    //     param t2   ->   str Rt2, [sp, #16]
    //
    // La casilla es la del parametro dentro del registro NUEVO, que todavia no tiene fp:
    // se cuenta desde sp, que es donde empieza la parte fija recien reservada.
    private fun emitParam(param: Param, following: List<Quadruple>) {
        val pending = pendingCall ?: openCall(calleeOf(following))
        val index = pending.nextParameter++
        val slot = calls.parameterSlot(pending.frame, index)

        if (param.kind == OperandKind.FLOAT) {
            memory.loadFloatOperand(param.value, FloatRegister.D0)
            emitter.instruction("vstr", FloatRegister.D0.text, slot)
            return
        }
        val value = registerOf(param.value, scratch = Register.R2)
        val mnemonic = if (pending.frame.parameterSize(index) == 1) "strb" else "str"
        emitter.instruction(mnemonic, value.text, slot)
    }

    //     t2 = call factorial, 1   ->   bl cps_factorial   add sp, sp, #24   mov Rt2, r0
    private fun emitCall(call: Call) {
        // Una funcion sin argumentos no tuvo ningun param que reservara su registro.
        val pending = pendingCall ?: openCall(call.function)
        pendingCall = null

        // ^h dice cuantos enlaces sube quien llama para llegar al registro de la funcion
        // que contiene a la llamada. Con ^0 es su propio fp.
        call.accessHops?.let { calls.storeAccessLink(pending.frame, memory.accessLink(it)) }

        // Lo ultimo antes del salto. Una funcion de Compiscript usa los mismos registros
        // que reparte getReg y no los devuelve intactos, asi que lo que quede en uno se
        // pierde: al llegar al bl no puede quedar nada. Las rutinas del runtime si los
        // conservan, y por eso print no vacia. Si el resultado es un temporal, su valor de
        // ahora no se guarda: la llamada lo va a sobrescribir, y la funcion llamada no
        // puede leerlo. Una variable del programa si se guarda, porque la funcion podria.
        registers.flush(overwritten = call.result as? Temporary)
        calls.call(call.function)
        finishCall(pending, call.result, call.resultKind)
    }

    //     t1 = call t1, 1   ->   la misma secuencia, con blx Rt1 en vez de bl
    //
    // La direccion del metodo sale de la tabla del objeto en ejecucion, asi que el salto
    // es a un registro. La parte fija se arma con el registro del metodo que resolvio el
    // verificador: todas las versiones de un metodo tienen la misma firma, y sus
    // registros de activacion empiezan igual.
    private fun emitIndirectCall(call: IndirectCall) {
        val pending = pendingCall ?: openCall(call.method)
        pendingCall = null

        // El registro sigue teniendo la direccion despues del vaciado, que solo guarda.
        val target = registers.registerFor(call.target)
        registers.flush(overwritten = call.result as? Temporary)
        calls.callAddress(target)
        finishCall(pending, call.result, call.resultKind)
    }

    // Lo que sigue al salto en las dos llamadas: liberar la parte fija y tomar el
    // resultado, que vuelve en r0 o en d0.
    private fun finishCall(pending: PendingCall, result: Address?, resultKind: OperandKind?) {
        calls.closeFrame(pending.frame)
        if (result != null) {
            if (resultKind == OperandKind.FLOAT) storeFloatResult(result)
            else takeResult(emptyList())
        }

        // Una llamada cierra el bloque, asi que el siguiente va a buscar el resultado en
        // memoria: hay que dejarlo ahi antes de salir.
        registers.flush()
    }

    //     return t1   ->   mov r0, Rt1   vaciado   b .Lfactorial_fin
    //
    // El valor vuelve en r0, o en d0 si es flotante. La casilla "valor devuelto" del
    // registro de activacion no se usa: ese registro ya se libero cuando quien llama lee
    // el resultado. El vaciado va antes del salto por lo mismo que en los saltos, y
    // ademas porque una global que solo vivia en un registro se perderia al volver.
    private fun emitReturn(instruction: Return, function: FunctionLabel) {
        val value = instruction.value
        when {
            value == null -> Unit
            instruction.kind == OperandKind.FLOAT ->
                memory.loadFloatOperand(value, FloatRegister.D0)
            isVariable(value) ->
                emitter.instruction("mov", Register.R0.text, registers.registerFor(value).text)
            else -> loadConstant(Register.R0, value)
        }
        registers.flush()
        emitter.instruction("b", ArmNames.functionExit(function))
    }

    // `sub sp, sp, #24`: la parte fija del registro nuevo.
    private fun openCall(callee: FunctionLabel): PendingCall =
        PendingCall(calls.openFrame(callee)).also { pendingCall = it }

    // A quien se esta llamando. Entre el primer param y su call el TAC no tiene otra
    // cosa, asi que la busqueda es lineal y corta.
    private fun calleeOf(following: List<Quadruple>): FunctionLabel =
        when (val call = following.firstOrNull { it is Call || it is IndirectCall }) {
            is Call -> call.function
            is IndirectCall -> call.method
            else -> error("Un param sin su call")
        }

    // ══════════════════════════════════════════════════════════════════════
    //  El monticulo: objetos, listas y strings
    // ══════════════════════════════════════════════════════════════════════

    //     t1 = alloc 12   ->   mov r0, #12   bl cps_reservar   mov Rt1, r0
    //
    // cps_reservar devuelve el bloque en ceros, o termina el programa si no hay memoria.
    // Conserva r4 a r11, como las rutinas de print, asi que no hace falta vaciar.
    private fun emitAllocate(allocate: Allocate) {
        val size = loadArgument(Register.R0, allocate.size)
        emitter.instruction("bl", ArmNames.ALLOCATE)
        takeResult(listOfNotNull(size))
    }

    //     t1 = perro[4]            ->   ldr Rt1, [Rperro, #4]
    //     t1 = lista[t2]           ->   ldr Rt1, [Rlista, Rt2]
    //     t1 = obj[8] (flotante)   ->   vldr d0, [Robj, #8]   vstr d0, [t1]
    private fun emitIndexedLoad(load: IndexedLoad) {
        val address = heapAddressOf(load.base, load.offset, load.kind)
        if (load.kind == OperandKind.FLOAT) {
            emitter.instruction("vldr", FloatRegister.D0.text, address.text)
            storeFloatResult(load.result)
            return
        }
        val result = registers.registerForResult(address.registers)
        emitter.instruction(loadMnemonicOf(load.kind), result.text, address.text)
        registers.recordResult(result)
    }

    //     perro[4] = t1            ->   str Rt1, [Rperro, #4]
    //     t1[0] = vtable.Perro     ->   ldr r1, =cps_vtable_Perro   str r1, [Rt1]
    //     t2[0] = 1.5              ->   vldr d0, [.Lfloat_1]   vstr d0, [Rt2]
    //
    // El flotante se carga antes que la direccion, porque cargarlo puede usar ip, y la
    // direccion de un flotante con desplazamiento variable tambien.
    private fun emitIndexedStore(store: IndexedStore) {
        if (store.kind == OperandKind.FLOAT) {
            memory.loadFloatOperand(store.value, FloatRegister.D0)
            val address = heapAddressOf(store.base, store.offset, store.kind)
            emitter.instruction("vstr", FloatRegister.D0.text, address.text)
            return
        }
        val address = heapAddressOf(store.base, store.offset, store.kind)
        val value = registerOf(store.value, scratch = Register.R1)
        emitter.instruction(storeMnemonicOf(store.kind), value.text, address.text)
    }

    //     push_i lista, x   ->   mov r0, Rlista   mov r1, #4   bl cps_lista_agregar
    //                            str Rx, [r0]
    //
    // La rutina no escribe el valor: devuelve en r0 la casilla nueva, y el valor se guarda
    // ahi como en cualquier otra casilla del monticulo. Asi una sola rutina sirve para
    // todos los tipos.
    private fun emitListPush(push: ListPush) {
        loadArgument(Register.R0, push.list)
        emitter.loadInteger(Register.R1, elementSizeOf(push.kind).toLong())
        emitter.instruction("bl", ArmNames.LIST_APPEND)

        val slot = memory(Register.R0)
        if (push.kind == OperandKind.FLOAT) {
            memory.loadFloatOperand(push.value, FloatRegister.D0)
            emitter.instruction("vstr", FloatRegister.D0.text, slot)
            return
        }
        val value = registerOf(push.value, scratch = Register.R1)
        emitter.instruction(storeMnemonicOf(push.kind), value.text, slot)
    }

    //     x = a concat b   ->   mov r0, Ra   mov r1, Rb   bl cps_concatenar   mov Rx, r0
    private fun emitConcat(concat: Concat) {
        val left = loadArgument(Register.R0, concat.left)
        val right = loadArgument(Register.R1, concat.right)
        emitter.instruction("bl", ArmNames.CONCATENATE)
        takeResult(listOfNotNull(left, right))
    }

    // Una direccion del monticulo, ya escrita como la pide la instruccion, y los
    // registros de getReg que usa.
    private data class HeapAddress(val text: String, val registers: List<Register>)

    //     [Rb, #8]   un desplazamiento constante que cabe en la instruccion
    //     [Rb, Ro]   uno variable
    //     [ip]       un flotante con desplazamiento variable: vldr y vstr no aceptan un
    //                registro como desplazamiento, asi que primero se suma en ip
    private fun heapAddressOf(base: Address, offset: Address, kind: OperandKind): HeapAddress {
        val baseRegister = registerOf(base, scratch = Register.R2)
        val isFloat = kind == OperandKind.FLOAT

        val constant = (offset as? Constant)?.let { integerValueOf(it) }
        if (constant != null && fitsOffset(constant, isFloat)) {
            return HeapAddress(memory(baseRegister, constant.toInt()), listOf(baseRegister))
        }

        val offsetRegister = registerOf(offset, scratch = Register.R3)
        val used = listOf(baseRegister, offsetRegister)
        if (!isFloat) return HeapAddress(memory(baseRegister, offsetRegister), used)
        emitter.instruction("add", Register.IP.text, baseRegister.text, offsetRegister.text)
        return HeapAddress(memory(Register.IP), used)
    }

    // ldr y str alcanzan 4095 bytes desde la base; vldr y vstr, 1020 y en multiplos de 4.
    private fun fitsOffset(offset: Long, isFloat: Boolean): Boolean =
        if (isFloat) offset in -1020L..1020L && offset % 4 == 0L else offset in -4095L..4095L

    // Un booleano mide un byte en un objeto o en una lista: se mueve con ldrb y strb.
    private fun loadMnemonicOf(kind: OperandKind): String =
        if (kind == OperandKind.BOOLEAN) "ldrb" else "ldr"

    private fun storeMnemonicOf(kind: OperandKind): String =
        if (kind == OperandKind.BOOLEAN) "strb" else "str"

    // Cuanto mide un elemento de una lista: lo mismo que su casilla en un registro de
    // activacion.
    private fun elementSizeOf(kind: OperandKind): Int = when (kind) {
        OperandKind.FLOAT -> 8
        OperandKind.BOOLEAN -> 1
        OperandKind.INTEGER, OperandKind.STRING, OperandKind.REFERENCE -> 4
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Errores
    // ══════════════════════════════════════════════════════════════════════

    //     throw "mensaje"   ->   ldr r0, =.Lstr_3   bl cps_lanzar
    //
    // Por ahora no hay try que lo atrape: cps_lanzar imprime el mensaje y termina con
    // codigo 1. Igual se vacia antes, porque throw cierra el bloque.
    private fun emitThrow(instruction: Throw) {
        loadArgument(Register.R0, instruction.message)
        registers.flush()
        emitter.instruction("bl", ArmNames.THROW)
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
        storeFloatResult(arithmetic.result)
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
        if (print.kind == OperandKind.FLOAT) {
            memory.loadFloatOperand(print.value, FloatRegister.D0)
        } else {
            loadArgument(Register.R0, print.value)
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

    // Un operando en el registro de argumento de una rutina, r0 o r1. Devuelve el registro
    // de getReg de donde salio, si era una variable, para que el resultado pueda usarlo.
    // getReg nunca usa r0 a r3, asi que cargar el segundo argumento no pisa el primero.
    private fun loadArgument(argument: Register, operand: Address): Register? {
        if (!isVariable(operand)) {
            loadConstant(argument, operand)
            return null
        }
        val register = registers.registerFor(operand)
        emitter.instruction("mov", argument.text, register.text)
        return register
    }

    // El resultado de una rutina o de una llamada llega en r0, y pasa al registro que le
    // da getReg.
    private fun takeResult(operandRegisters: List<Register>) {
        val result = registers.registerForResult(operandRegisters)
        emitter.instruction("mov", result.text, Register.R0.text)
        registers.recordResult(result)
    }

    // Un flotante no pasa por getReg: se guarda directo en su casilla, y la libreta tiene
    // que saberlo.
    private fun storeFloatResult(result: Address) {
        memory.storeFloat(FloatRegister.D0, result)
        registers.recordResultInMemory()
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
