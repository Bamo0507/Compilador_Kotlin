package org.compiler.frontend.intermediate

import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.NullType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.StorageLocation
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.frontend.semantic.symbols.VoidType

// Donde quedo todo: la zona estatica, el main implicito y el registro de cada funcion,
// indexado por su ambito.
data class StorageLayout(
    val staticSize: Int,
    val main: ActivationRecordLayout,
    val functions: Map<Scope, ActivationRecordLayout>
)

/**
 * Le da a cada simbolo su lugar en memoria y arma el registro de activacion de cada
 * funcion. Corre despues del analisis semantico, sobre el arbol de ambitos completo.
 *
 * Un bloque, un if o un bucle abren un ambito pero no un registro: sus variables viven
 * en el registro de la funcion que los contiene. Solo las funciones crean registros.
 */
class StorageAllocator {

    fun allocate(globalScope: Scope): StorageLayout {
        val staticSize = allocateGlobals(globalScope)
        val main = allocateMain(globalScope)

        val functions = linkedMapOf<Scope, ActivationRecordLayout>()
        functionScopesOf(globalScope).forEach { functions[it] = allocateFunction(it) }

        assignTacNames(globalScope, functions.keys)
        return StorageLayout(staticSize, main, functions)
    }

    // ── Las globales: datos estaticos ──────────────────────────────────────

    private fun allocateGlobals(globalScope: Scope): Int {
        var offset = 0
        globalScope.localSymbols().filter { it.occupiesMemory() }.forEach { symbol ->
            val size = sizeOf(symbol.type)
            offset = align(offset, size)
            symbol.storage = StorageLocation.Static(offset)
            offset += size
        }
        return offset
    }

    // ── El main implicito ──────────────────────────────────────────────────

    // El codigo del nivel superior es una funcion mas: sus temporales y las locales de
    // sus bloques viven en su propio registro.
    private fun allocateMain(globalScope: Scope): ActivationRecordLayout {
        val record = RecordBuilder()
        record.placeLinks()
        globalScope.children
            .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
            .forEach { record.placeLocals(it) }
        return record.build(FunctionLabel(MAIN_LABEL))
    }

    // ── Las funciones ──────────────────────────────────────────────────────

    // En el orden de la diapositiva 21: parametros, valor devuelto, enlaces, direccion
    // de retorno y locales. Los temporales los agrega el generador, que es quien sabe
    // cuantos hicieron falta.
    private fun allocateFunction(functionScope: Scope): ActivationRecordLayout {
        val record = RecordBuilder()

        val (parameters, locals) = functionScope.localSymbols()
            .filter { it.occupiesMemory() }
            .partition { it.kind == DeclarationKind.PARAMETER }

        parameters.forEach { record.place(it) }

        val returnSize = sizeOf(returnTypeOf(functionScope))
        if (returnSize > 0) record.place("valor devuelto", returnSize)

        record.placeLinks()
        locals.forEach { record.place(it) }
        functionScope.children
            .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
            .forEach { record.placeLocals(it) }

        return record.build(labelOf(functionScope))
    }

    // La firma vive en el ambito que contiene a la funcion, no en el suyo.
    private fun returnTypeOf(functionScope: Scope): Type {
        val signature = functionScope.parent?.lookupLocal(functionScope.name)?.type
        return (signature as? FunctionType)?.returns ?: VoidType
    }

    // `factorial`; `externa.sumar` para una anidada; `Perro.hablar` para un metodo.
    private fun labelOf(functionScope: Scope): FunctionLabel {
        val path = generateSequence(functionScope) { it.parent }
            .filter { it.kind == ScopeKind.FUNCTION || it.kind == ScopeKind.CLASS }
            .map { it.name }
            .toList()
            .reversed()
        return FunctionLabel(path.joinToString("."))
    }

    private fun functionScopesOf(scope: Scope): List<Scope> =
        scope.children.flatMap { child ->
            val own = if (child.kind == ScopeKind.FUNCTION) listOf(child) else emptyList()
            own + functionScopesOf(child)
        }

    // ── Los nombres en el TAC ──────────────────────────────────────────────

    /**
     * Dos variables con el mismo nombre se imprimirian igual en el TAC. Chocan si estan
     * en el mismo registro, o si una es global y la otra local: el que lee el TAC no
     * podria saber cual es. La primera en declararse queda con su nombre, y las demas
     * llevan la linea de su declaracion.
     *
     * Dos locales de funciones distintas no chocan: el `n` de factorial y el de
     * fibonacci viven en registros distintos. Tampoco una local de una funcion
     * anidada con la de su padre: los saltos del enlace de acceso ya las distinguen.
     */
    private fun assignTacNames(globalScope: Scope, functionScopes: Collection<Scope>) {
        val globals = globalScope.localSymbols().filter { it.occupiesMemory() }
        val mainLocals = globalScope.children
            .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
            .flatMap { symbolsOfRecord(it) }

        // Las globales van primero: si una local se llama igual, la global conserva el
        // nombre y la local lleva el sufijo.
        resolveCollisions(globals + mainLocals)
        functionScopes.forEach { resolveCollisions(globals + symbolsOfRecord(it)) }
    }

    // Todas las variables que viven en el registro de este ambito: las suyas y las de
    // sus bloques, sin entrar a funciones anidadas.
    private fun symbolsOfRecord(scope: Scope): List<Symbol> =
        scope.localSymbols().filter { it.occupiesMemory() } +
            scope.children
                .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
                .flatMap { symbolsOfRecord(it) }

    private fun resolveCollisions(symbols: List<Symbol>) {
        symbols.groupBy { it.name }.values
            .filter { it.size > 1 }
            .forEach { sameName ->
                val lines = sameName.groupingBy { it.location.line }.eachCount()
                sameName.drop(1).forEach { symbol ->
                    val location = symbol.location
                    val sharesLine = (lines[location.line] ?: 0) > 1
                    symbol.tacName = if (sharesLine) {
                        "${symbol.name}@${location.line}:${location.position}"
                    } else {
                        "${symbol.name}@${location.line}"
                    }
                }
            }
    }

    // ── El registro, campo por campo ───────────────────────────────────────

    private class RecordBuilder {
        private var offset = 0
        private val fields = mutableListOf<ActivationRecordField>()

        fun place(name: String, size: Int): Int {
            offset = align(offset, size)
            val placedAt = offset
            fields += ActivationRecordField(name, placedAt, size)
            offset += size
            return placedAt
        }

        fun place(symbol: Symbol) {
            symbol.storage = StorageLocation.Frame(place(symbol.name, sizeOf(symbol.type)))
        }

        // El enlace de acceso va en todos los registros, aunque solo lo usen las
        // funciones anidadas: una sola forma de registro por 4 bytes. Del estado de la
        // maquina solo se reserva la direccion de retorno; que registros salvar lo
        // decide la fase de assembler.
        fun placeLinks() {
            place("enlace de control", POINTER_SIZE)
            place("enlace de acceso", POINTER_SIZE)
            place("dirección de retorno", POINTER_SIZE)
        }

        // Un bloque no tiene registro propio: sus variables entran al de la funcion.
        // Los bloques hermanos no comparten espacio, aunque nunca vivan a la vez.
        fun placeLocals(scope: Scope) {
            scope.localSymbols().filter { it.occupiesMemory() }.forEach { place(it) }
            scope.children
                .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
                .forEach { placeLocals(it) }
        }

        fun build(label: FunctionLabel): ActivationRecordLayout =
            ActivationRecordLayout(label, fields.toList(), align(offset, STACK_ALIGNMENT))
    }

    companion object {
        const val MAIN_LABEL = "\$main"

        // ARM de 32 bits: una direccion mide 4 bytes y la pila se alinea a 8.
        const val POINTER_SIZE = 4
        const val STACK_ALIGNMENT = 8

        // Un temporal puede guardar un entero y despues un float, porque el pool recicla
        // nombres sin mirar el tipo: su espacio alcanza para el mas grande.
        const val TEMPORARY_SIZE = 8

        fun sizeOf(type: Type): Int = when (type) {
            IntegerType -> 4
            FloatType -> 8
            BooleanType -> 1
            VoidType -> 0

            // Objetos, listas, strings y null son direcciones.
            StringType, NullType, is ArrayType, is ClassType, is FunctionType -> POINTER_SIZE

            ErrorType -> error("Un tipo con error no llega a la asignacion de memoria")
        }

        // Alineacion natural: cada valor empieza en un multiplo de su tamaño.
        fun align(offset: Int, size: Int): Int =
            if (size <= 1) offset else (offset + size - 1) / size * size

        // Las funciones y las clases son codigo, no datos; los campos viven dentro del
        // objeto, en el monticulo.
        private fun Symbol.occupiesMemory(): Boolean =
            (kind == DeclarationKind.VARIABLE || kind == DeclarationKind.CONSTANT ||
                kind == DeclarationKind.PARAMETER) && !isMember
    }
}
