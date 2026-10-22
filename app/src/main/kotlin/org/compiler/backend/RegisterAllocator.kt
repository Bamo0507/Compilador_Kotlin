package org.compiler.backend

import org.compiler.backend.models.Register
import org.compiler.frontend.intermediate.models.Address

/**
 * La funcion getReg del generador simple (Dragon Book 8.6.3), con sus dos libretas.
 *
 * Funciona como un getOrPut de Kotlin: si la variable ya esta en un registro, lo
 * devuelve; si no, consigue uno, la carga, lo anota y lo devuelve. La diferencia es que
 * conseguir un registro puede costar instrucciones: un ldr para cargar, y a veces un str
 * para no perder lo que el registro tenia.
 *
 * Reparte solo los registros que la funcion llamada debe conservar, asi que un valor que
 * vive en ellos sobrevive a las llamadas al runtime.
 */
class RegisterAllocator(
    private val memory: MemoryAccess,
    registers: List<Register> = Register.VALUE_REGISTERS
) {
    private val registerDescriptor = RegisterDescriptor(registers)
    private val addressDescriptor = AddressDescriptor()

    private var nextUse: NextUseAnalysis? = null
    private var liveAtExit: (Address) -> Boolean = { true }

    // La instruccion que se esta traduciendo: su posicion en el bloque, lo que lee y lo
    // que escribe.
    private var index = 0
    private var operands: List<Address> = emptyList()
    private var result: Address? = null

    // Los registros ya elegidos para la instruccion actual. No se pueden vaciar para
    // conseguir otro: en `x = y + z`, buscar el de z no puede pisar el de y.
    private val reserved = mutableSetOf<Register>()

    // Cada bloque empieza con las libretas en blanco: todo esta solo en memoria.
    fun startBlock(analysis: NextUseAnalysis, liveAtExit: (Address) -> Boolean) {
        registerDescriptor.clear()
        addressDescriptor.clear()
        nextUse = analysis
        this.liveAtExit = liveAtExit
    }

    fun startInstruction(index: Int, operands: List<Address>, result: Address?) {
        this.index = index
        this.operands = operands
        this.result = result
        reserved.clear()
    }

    // ── getReg para un operando ────────────────────────────────────────────

    /**
     * Un registro con el valor del operando y, en este orden:
     *   1. el que ya lo tiene, sin instrucciones
     *   2. uno vacio, con un ldr
     *   3. uno cuyas variables estan todas a salvo, con un ldr
     *   4. el que pida menos guardados: un str por cada variable que solo vivia ahi, y
     *      despues el ldr
     */
    fun registerFor(operand: Address): Register {
        addressDescriptor.registersOf(operand).firstOrNull()?.let { return reserve(it) }

        val register = freeRegister()
        memory.load(register, operand)
        registerDescriptor.holdOnly(register, operand)
        addressDescriptor.add(operand, Location.InRegister(register))
        return reserve(register)
    }

    // ── getReg para el resultado ───────────────────────────────────────────

    /**
     * El registro donde se calcula el resultado x, en este orden:
     *   1. uno que tenga solo a x: su valor viejo ya no sirve
     *   2. el de un operando cuyo valor se puede perder: esta en otro lado o ya no se
     *      usa, y no se vuelve a leer en el bloque
     *   3. los pasos 2 a 4 de los operandos
     * Despues de calcular hay que anotarlo con recordResult.
     */
    fun registerForResult(operandRegisters: List<Register>): Register {
        val target = checkNotNull(result) { "La instruccion no tiene resultado" }

        registerDescriptor.registers.firstOrNull { register ->
            registerDescriptor.variablesIn(register) == setOf(target)
        }?.let { return reserve(it) }

        // Un operando constante vive en un registro de paso, que getReg no reparte.
        operandRegisters.filter { it in registerDescriptor.registers }.firstOrNull { register ->
            registerDescriptor.variablesIn(register).all { canLoseForResult(it, register) }
        }?.let { return reserve(it) }

        return reserve(freeRegister())
    }

    // x se calculo en el registro: el registro tiene solo a x, x esta solo ahi, y las
    // variables que el registro tenia antes ya no estan en el.
    fun recordResult(register: Register) {
        val target = checkNotNull(result)
        registerDescriptor.variablesIn(register).forEach {
            addressDescriptor.remove(it, Location.InRegister(register))
        }
        registerDescriptor.removeEverywhere(target)
        registerDescriptor.holdOnly(register, target)
        addressDescriptor.setOnlyIn(target, register)
    }

    // `x = y` no genera instruccion: x se anota en el registro de y, y su valor queda
    // solo ahi.
    fun recordCopy(sourceRegister: Register) {
        val target = checkNotNull(result)
        registerDescriptor.removeEverywhere(target)
        registerDescriptor.add(sourceRegister, target)
        addressDescriptor.setOnlyIn(target, sourceRegister)
    }

    // ── El final del bloque ────────────────────────────────────────────────

    // Un str por cada variable viva cuyo valor solo esta en un registro. Despues las
    // libretas quedan en blanco: lo que venga despues encuentra todo en memoria.
    fun flush() {
        registerDescriptor.registers.forEach { register ->
            registerDescriptor.variablesIn(register).forEach { variable ->
                if (!addressDescriptor.isInMemory(variable) && liveAtExit(variable)) {
                    memory.store(register, variable)
                    addressDescriptor.add(variable, Location.Memory)
                }
            }
        }
        registerDescriptor.clear()
        addressDescriptor.clear()
    }

    // ── Elegir un registro ─────────────────────────────────────────────────

    // Los pasos 2 a 4: uno vacio, uno seguro o el que pida menos guardados.
    private fun freeRegister(): Register {
        val candidates = registerDescriptor.registers.filter { it !in reserved }

        candidates.firstOrNull { registerDescriptor.isEmpty(it) }?.let { return it }

        candidates
            .filter { register -> unsafeVariablesIn(register).isEmpty() }
            .maxByOrNull { farthestNextUseIn(it) }
            ?.let { return evict(it) }

        val victim = candidates.minBy { unsafeVariablesIn(it).size }
        unsafeVariablesIn(victim).forEach { variable ->
            memory.store(victim, variable)
            addressDescriptor.add(variable, Location.Memory)
        }
        return evict(victim)
    }

    // Las variables del registro que se perderian si se pisa: su valor solo esta ahi y
    // se vuelve a necesitar, despues o en esta misma instruccion, y no es el resultado
    // que esta por escribirse.
    private fun unsafeVariablesIn(register: Register): List<Address> =
        registerDescriptor.variablesIn(register).filterNot { variable ->
            val readNow = variable in operands
            addressDescriptor.isElsewhere(variable, register) ||
                (variable == result && !readNow) ||
                (!usageOf(variable).live && !readNow)
        }

    // Para el resultado, ademas de estar a salvo, la variable no debe volver a leerse en
    // el bloque: si no, habria que cargarla otra vez.
    private fun canLoseForResult(variable: Address, register: Register): Boolean {
        if (variable == result) return true
        val usage = usageOf(variable)
        if (!usage.live) return true
        return addressDescriptor.isElsewhere(variable, register) && usage.nextUse == null
    }

    // Entre los registros seguros, conviene vaciar el que tiene variables que se leen mas
    // tarde, o que no se vuelven a leer en el bloque.
    private fun farthestNextUseIn(register: Register): Int =
        registerDescriptor.variablesIn(register).minOfOrNull { variable ->
            usageOf(variable).nextUse ?: Int.MAX_VALUE
        } ?: Int.MAX_VALUE

    // El registro se vacia: sus variables dejan de estar en el.
    private fun evict(register: Register): Register {
        registerDescriptor.variablesIn(register).forEach {
            addressDescriptor.remove(it, Location.InRegister(register))
        }
        return register
    }

    private fun usageOf(variable: Address): Usage =
        checkNotNull(nextUse) { "getReg fuera de un bloque" }.usageAfter(index, variable)

    private fun reserve(register: Register): Register {
        reserved += register
        return register
    }
}
