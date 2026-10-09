package org.compiler.frontend.intermediate

import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.ClassLayout
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.CONSTRUCTOR_NAME
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FOREACH_SCOPE_PREFIX
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
import org.compiler.models.LexemeLocation

// Donde quedo todo: la zona estatica, el main implicito y el registro de cada funcion,
// indexado por su ambito.
data class StorageLayout(
    val staticSize: Int,
    val main: ActivationRecordLayout,
    val functions: Map<Scope, ActivationRecordLayout>,

    // El ambito de cada clase por su nombre, en orden de declaracion. Su forma en
    // memoria esta en `Scope.classLayout`.
    val classes: Map<String, Scope> = emptyMap(),

    // El registro de la rutina `$init` de cada clase, por el nombre de la clase.
    val initializers: Map<String, ActivationRecordLayout> = emptyMap(),

    // El parametro escondido `this` de cada metodo, constructor y `$init`, por la
    // etiqueta de su funcion.
    val thisParameters: Map<FunctionLabel, Symbol> = emptyMap(),

    // Las dos locales ocultas de cada foreach, por el ambito del bucle.
    val forEachLocals: Map<Scope, ForEachLocals> = emptyMap()
)

// `$lista` guarda la lista que se recorre, evaluada una sola vez, y `$i` el indice.
// No pueden ser temporales: viven todo el bucle, y el pool recicla los temporales al
// terminar cada sentencia.
data class ForEachLocals(val list: Symbol, val index: Symbol)

/**
 * Le da a cada simbolo su lugar en memoria y arma el registro de activacion de cada
 * funcion. Corre despues del analisis semantico, sobre el arbol de ambitos completo.
 *
 * Un bloque, un if o un bucle abren un ambito pero no un registro: sus variables viven
 * en el registro de la funcion que los contiene. Solo las funciones crean registros.
 */
class StorageAllocator {

    // Lo que se descubre al armar los registros: los `this` y las locales ocultas.
    private val thisParameters = linkedMapOf<FunctionLabel, Symbol>()
    private val forEachLocals = linkedMapOf<Scope, ForEachLocals>()

    fun allocate(globalScope: Scope): StorageLayout {
        thisParameters.clear()
        forEachLocals.clear()

        val staticSize = allocateGlobals(globalScope)

        // Las clases antes que las funciones: un metodo necesita saber de que clase es
        // su `this`, y el generador, la forma de cada objeto.
        val classes = linkedMapOf<String, Scope>()
        globalScope.children.filter { it.kind == ScopeKind.CLASS }.forEach { classScope ->
            layoutOf(classScope)
            classes[classScope.name] = classScope
        }
        val initializers = linkedMapOf<String, ActivationRecordLayout>()
        classes.values.forEach { initializers[it.name] = allocateInitializer(it) }

        val main = allocateMain(globalScope)

        val functions = linkedMapOf<Scope, ActivationRecordLayout>()
        functionScopesOf(globalScope).forEach { functions[it] = allocateFunction(it) }

        assignTacNames(globalScope, functions.keys)
        return StorageLayout(
            staticSize, main, functions, classes, initializers,
            thisParameters.toMap(), forEachLocals.toMap()
        )
    }

    // ── Las clases: la forma del objeto y su tabla de metodos ──────────────

    /**
     * La casilla 0 es la tabla de metodos, en todas las clases. Despues van los campos
     * heredados, en el desplazamiento que tenian en la superclase, y despues los
     * propios con la alineacion natural. El tamaño se redondea a la alineacion mas
     * grande de sus campos, como un struct de C.
     *
     * En la tabla, un metodo heredado conserva la posicion que tenia en la del padre,
     * uno que sobrescribe reemplaza esa entrada, y uno nuevo va al final.
     */
    private fun layoutOf(classScope: Scope): ClassLayout {
        classScope.classLayout?.let { return it }
        val parent = classScope.superclass?.let { layoutOf(it) }

        val fields = parent?.fields?.toMutableList()
            ?: mutableListOf(ActivationRecordField(VIRTUAL_TABLE_SLOT, 0, POINTER_SIZE))
        var offset = parent?.size ?: POINTER_SIZE

        classScope.localSymbols().filter { it.isField() }.forEach { field ->
            val size = sizeOf(field.type)
            offset = align(offset, size)
            field.storage = StorageLocation.Field(offset)
            fields += ActivationRecordField(field.name, offset, size)
            offset += size
        }

        val methods = parent?.methods?.toMutableList() ?: mutableListOf()
        classScope.localSymbols()
            .filter { it.kind == DeclarationKind.FUNCTION && it.name != CONSTRUCTOR_NAME }
            .forEach { method ->
                val label = methodLabel(classScope, method.name)
                val inherited = methods.indexOfFirst { it.name.substringAfterLast('.') == method.name }
                if (inherited >= 0) methods[inherited] = label else methods += label
            }

        val alignment = maxOf(POINTER_SIZE, fields.maxOf { it.size })
        val layout = ClassLayout(classScope.name, align(offset, alignment), methods, fields)
        classScope.classLayout = layout
        return layout
    }

    private fun methodLabel(classScope: Scope, methodName: String): FunctionLabel =
        classScope.children
            .firstOrNull { it.kind == ScopeKind.FUNCTION && it.name == methodName }
            ?.let { labelOf(it) }
            ?: FunctionLabel("${classScope.name}.$methodName")

    // `Perro.$init`: solo recibe `this`, y no tiene locales. Sus temporales los agrega
    // el generador, como en cualquier funcion.
    private fun allocateInitializer(classScope: Scope): ActivationRecordLayout {
        val label = FunctionLabel("${classScope.name}.$INIT_NAME")
        val record = RecordBuilder()
        record.place(thisParameterOf(classScope, label, INIT_NAME, classScope.functionDepth() + 1, null))
        record.placeLinks()
        return record.build(label)
    }

    // `this` es un parametro mas, el primero: desplazamiento 0. Es un Symbol sintetico
    // que no se declara en ningun ambito, porque nadie lo busca por nombre: el AST ya
    // dice donde aparece `this`.
    private fun thisParameterOf(
        classScope: Scope,
        label: FunctionLabel,
        scopeName: String,
        functionDepth: Int,
        location: LexemeLocation?
    ): Symbol {
        val symbol = Symbol(
            name = THIS_NAME,
            kind = DeclarationKind.PARAMETER,
            type = ClassType(classScope.name),
            location = location ?: LexemeLocation(0, 0),
            scopeName = scopeName,
            declarationFunctionDepth = functionDepth,
            initialized = true
        )
        thisParameters[label] = symbol
        return symbol
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
        val label = labelOf(functionScope)

        // Un metodo o un constructor recibe el objeto como primer parametro escondido.
        val classScope = functionScope.parent?.takeIf { it.kind == ScopeKind.CLASS }
        if (classScope != null) {
            val location = classScope.lookupLocal(functionScope.name)?.location
            record.place(
                thisParameterOf(classScope, label, functionScope.name, functionScope.functionDepth(), location)
            )
        }

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

        return record.build(label)
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
        hiddenLocalsOf(scope) +
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

    private fun hiddenLocalsOf(scope: Scope): List<Symbol> =
        forEachLocals[scope]?.let { listOf(it.list, it.index) } ?: emptyList()

    // ── El registro, campo por campo ───────────────────────────────────────

    private inner class RecordBuilder {
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
            if (scope.kind == ScopeKind.LOOP && scope.name.startsWith(FOREACH_SCOPE_PREFIX)) {
                placeForEachLocals(scope)
            }
            scope.localSymbols().filter { it.occupiesMemory() }.forEach { place(it) }
            scope.children
                .filter { it.kind == ScopeKind.BLOCK || it.kind == ScopeKind.LOOP }
                .forEach { placeLocals(it) }
        }

        // `$lista` y `$i`, antes de la variable del bucle. Llevan la ubicacion de esa
        // variable, que es la del foreach: si una funcion tiene dos foreach, el segundo
        // se imprime `$i@7`, con la misma regla que dos variables con el mismo nombre.
        private fun placeForEachLocals(scope: Scope) {
            val variable = scope.localSymbols().firstOrNull() ?: return
            fun hidden(name: String, type: Type) = Symbol(
                name = name,
                kind = DeclarationKind.VARIABLE,
                type = type,
                location = variable.location,
                scopeName = scope.name,
                declarationFunctionDepth = variable.declarationFunctionDepth,
                initialized = true
            )

            val locals = ForEachLocals(
                list = hidden(HIDDEN_LIST_NAME, ArrayType(variable.type)),
                index = hidden(HIDDEN_INDEX_NAME, IntegerType)
            )
            place(locals.list)
            place(locals.index)
            forEachLocals[scope] = locals
        }

        fun build(label: FunctionLabel): ActivationRecordLayout =
            ActivationRecordLayout(label, fields.toList(), align(offset, STACK_ALIGNMENT))
    }

    companion object {
        const val MAIN_LABEL = "\$main"
        const val INIT_NAME = "\$init"
        const val THIS_NAME = "this"
        const val HIDDEN_LIST_NAME = "\$lista"
        const val HIDDEN_INDEX_NAME = "\$i"

        // El nombre de la casilla 0 de todo objeto.
        const val VIRTUAL_TABLE_SLOT = "tabla de métodos"

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
        private fun Symbol.isField(): Boolean =
            (kind == DeclarationKind.VARIABLE || kind == DeclarationKind.CONSTANT) && isMember

        private fun Symbol.occupiesMemory(): Boolean =
            (kind == DeclarationKind.VARIABLE || kind == DeclarationKind.CONSTANT ||
                kind == DeclarationKind.PARAMETER) && !isMember
    }
}
