package org.compiler.interpreter

import org.compiler.frontend.ast.models.*
import org.compiler.frontend.semantic.symbols.CONSTRUCTOR_NAME
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.models.LexemeLocation

// El resultado de ejecutar un programa.

data class ExecutionResult(
    val output: List<String>, // lo que imprimió print(), en orden
    val runtimeError: RuntimeError? // null si terminó bien
)

/**
 * Ejecuta el AST ya validado.
 *
 * La misma forma que el TypeChecker —una funcion por construccion del lenguaje— pero
 * en vez de devolver un tipo, devuelve un VALOR. Corre despues de las fases 3-5, asi
 * que puede asumir que todo esta bien tipado: no revalida nada.
 */
class Interpreter {

    private val output = mutableListOf<String>()
    private val globalEnvironment = Environment()
    private var environment = globalEnvironment
    private val classDeclarations = mutableMapOf<String, ClassDeclaration>()
    private var callDepth = 0

    fun run(program: Program): ExecutionResult {
        program.statements.forEach { statement ->
            when (statement) {
                is ClassDeclaration -> classDeclarations[statement.name] = statement
                is FunctionDeclaration -> executeFunctionDeclaration(statement)
                else -> Unit
            }
        }

        return try {
            program.statements
                .filter { it !is ClassDeclaration && it !is FunctionDeclaration }
                .forEach { execute(it) }
            ExecutionResult(output, runtimeError = null)
        } catch (error: RuntimeError) {
            ExecutionResult(output, runtimeError = error)
        }
    }

    // ===================================================
    //  Los dos despachadores
    // ===================================================

    private fun evaluate(expr: Expression): RuntimeValue {
        expr.constantValue?.let { return toRuntimeValue(it) }

        return when (expr) {
            is Literal -> evaluateLiteral(expr)
            is ArrayLiteral -> evaluateArrayLiteral(expr)
            is Identifier -> evaluateIdentifier(expr)
            is ThisReference -> evaluateThis(expr)
            is UnaryOperation -> evaluateUnary(expr)
            is BinaryOperation -> evaluateBinary(expr)
            is TernaryOperation -> evaluateTernary(expr)
            is AssignmentExpression -> evaluateAssignmentExpression(expr)
            is FunctionCall -> evaluateFunctionCall(expr)
            is IndexAccess -> evaluateIndexAccess(expr)
            is PropertyAccess -> evaluatePropertyAccess(expr)
            is ObjectCreation -> evaluateObjectCreation(expr)
        }
    }

    private fun execute(stmt: Statement): Unit = when (stmt) {
        is VariableDeclaration -> executeVariableDeclaration(stmt)
        is FunctionDeclaration -> executeFunctionDeclaration(stmt)
        is Assignment -> executeAssignment(stmt)
        is ExpressionStatement -> { evaluate(stmt.expr); Unit }
        is Print -> executePrint(stmt)
        is Block -> executeBlock(stmt, environment.child())
        is If -> executeIf(stmt)
        is While -> executeWhile(stmt)
        is DoWhile -> executeDoWhile(stmt)
        is For -> executeFor(stmt)
        is ForEach -> executeForEach(stmt)
        is Switch -> executeSwitch(stmt)
        is TryCatch -> executeTryCatch(stmt)
        is Return -> throw ReturnSignal(stmt.value?.let { evaluate(it) } ?: NullValue)
        is Break -> throw BreakSignal()
        is Continue -> throw ContinueSignal()

        // Las clases ya se registraron en run(). Anidadas en un bloque no se
        // soportan: es la misma limitacion documentada en la Pasada 1.
        is ClassDeclaration -> Unit
    }

    // ===================================================
    //  Operadores: el interprete confia en los tipos decorados
    // ===================================================

    private fun evaluateBinary(expr: BinaryOperation): RuntimeValue {
        // Cortocircuito de && y ||: el lado derecho NO se evalua si el izquierdo decide.
        if (expr.operator == BinaryOperator.AND) {
            return BoolValue(asBoolean(evaluate(expr.left)) && asBoolean(evaluate(expr.right)))
        }
        if (expr.operator == BinaryOperator.OR) {
            return BoolValue(asBoolean(evaluate(expr.left)) || asBoolean(evaluate(expr.right)))
        }

        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.group) {
            OperatorGroup.ARITHMETIC -> arithmeticResult(expr, left, right)
            OperatorGroup.RELATIONAL -> BoolValue(compareValues(left, right).let { comparison ->
                when (expr.operator) {
                    BinaryOperator.LESS -> comparison < 0
                    BinaryOperator.LESS_EQUAL -> comparison <= 0
                    BinaryOperator.GREATER -> comparison > 0
                    else -> comparison >= 0
                }
            })
            OperatorGroup.EQUALITY -> BoolValue(
                if (expr.operator == BinaryOperator.EQUAL) valuesAreEqual(left, right)
                else !valuesAreEqual(left, right)
            )
            OperatorGroup.LOGICAL -> error("&& y || ya se manejaron arriba")
        }
    }

    private fun arithmeticResult(
        expr: BinaryOperation,
        left: RuntimeValue,
        right: RuntimeValue
    ): RuntimeValue = when (expr.type) {
        StringType -> StringValue(asString(left) + asString(right))
        FloatType -> FloatValue(applyFloat(expr.operator, asDouble(left), asDouble(right)))
        else -> IntValue(applyLong(expr.operator, asLong(left), asLong(right), expr))
    }

    private fun applyLong(
        op: BinaryOperator,
        left: Long,
        right: Long,
        expr: BinaryOperation
    ): Long = when (op) {
        BinaryOperator.ADD -> left + right
        BinaryOperator.SUBTRACT -> left - right
        BinaryOperator.MULTIPLY -> left * right
        BinaryOperator.DIVIDE, BinaryOperator.MODULO -> {
            // Chequeo DINAMICO: el divisor puede ser una variable.
            if (right == 0L) {
                throw RuntimeError(expr.location, "División entre cero")
            }
            if (op == BinaryOperator.DIVIDE) left / right else left % right
        }
        else -> error("'$op' no es un operador aritmético")
    }

    private fun applyFloat(op: BinaryOperator, left: Double, right: Double): Double =
        when (op) {
            BinaryOperator.ADD -> left + right
            BinaryOperator.SUBTRACT -> left - right
            BinaryOperator.MULTIPLY -> left * right
            BinaryOperator.DIVIDE -> left / right
            else -> error("'$op' no aplica a flotantes") // MODULO es solo integer (A3)
        }

    private fun evaluateUnary(expr: UnaryOperation): RuntimeValue {
        val operand = evaluate(expr.operand)
        return when (expr.operator) {
            UnaryOperator.NOT -> BoolValue(!asBoolean(operand))
            UnaryOperator.NEGATE ->
                if (expr.type == FloatType) FloatValue(-asDouble(operand))
                else IntValue(-asLong(operand))
        }
    }

    // La rama que no se toma NO se evalua: es lo mismo que el cortocircuito de &&.
    private fun evaluateTernary(expr: TernaryOperation): RuntimeValue =
        if (asBoolean(evaluate(expr.condition))) evaluate(expr.ifTrue)
        else evaluate(expr.ifFalse)

    // ===================================================
    //  Expresiones simples
    // ===================================================

    private fun evaluateLiteral(expr: Literal): RuntimeValue = toRuntimeValue(expr.value)

    private fun evaluateArrayLiteral(expr: ArrayLiteral): RuntimeValue =
        ArrayValue(expr.elements.map { evaluate(it) }.toMutableList())

    private fun evaluateIdentifier(expr: Identifier): RuntimeValue =
        environment.get(expr.name)
            ?: throw RuntimeError(expr.location,
                "La variable '${expr.name}' no tiene valor en tiempo de ejecución")

    // `this` es un nombre normal en el entorno: lo definio `invoke` desde boundThis.
    private fun evaluateThis(expr: ThisReference): RuntimeValue =
        environment.get("this")
            ?: throw RuntimeError(expr.location, "'this' no está disponible aquí")

    private fun evaluateIndexAccess(expr: IndexAccess): RuntimeValue {
        val array = asArray(evaluate(expr.target), expr.location)
        return array.elements[boundsCheckedIndex(array, expr)]
    }

    // El chequeo de rango, compartido por la lectura y la escritura.
    private fun boundsCheckedIndex(array: ArrayValue, expr: IndexAccess): Int {
        val index = asLong(evaluate(expr.index)).toInt()

        if (index < 0 || index >= array.elements.size) {
            throw RuntimeError(expr.location,
                "Índice fuera de rango: $index (la lista tiene ${array.elements.size} " +
                "elementos)")
        }

        return index
    }

    // ===================================================
    //  Llamadas a funcion y closures
    // ===================================================

    private fun evaluateFunctionCall(expr: FunctionCall): RuntimeValue {
        val callee = asFunction(evaluate(expr.callee), expr.location)
        val arguments = expr.arguments.map { evaluate(it) }
        return invoke(callee, arguments, expr.location)
    }

    // Ejecutar una funcion ya resuelta. Lo comparten las llamadas normales y la
    // invocacion del constructor desde `new`.
    private fun invoke(
        callee: FunctionValue,
        arguments: List<RuntimeValue>,
        location: LexemeLocation
    ): RuntimeValue {
        // El limite de recursion: sin esto, `function f(): integer { return f(); }`
        // tumba la ventana con un StackOverflowError, que ningun catch del usuario
        // puede atrapar.
        if (callDepth >= MAX_CALL_DEPTH) {
            throw RuntimeError(location,
                "Recursión demasiado profunda: más de $MAX_CALL_DEPTH llamadas anidadas")
        }

        val callEnvironment = callee.closure.child()

        // `this` disponible si es un metodo.
        callee.boundThis?.let { callEnvironment.define("this", it) }

        // Los parametros se ligan por POSICION, como validó el TypeChecker.
        callee.declaration.parameters.forEachIndexed { index, parameter ->
            callEnvironment.define(parameter.name, arguments[index])
        }

        callDepth += 1
        try {
            return try {
                executeBlock(callee.declaration.body, callEnvironment)
                NullValue // llegó al final sin return
            } catch (signal: ReturnSignal) {
                signal.value // hubo return
            }
        } finally {
            // En el finally para que una excepcion no deje el contador inflado: si
            // no, un try/catch alrededor de una recursion profunda dejaria el
            // interprete creyendo que sigue anidado.
            callDepth -= 1
        }
    }

    // ===================================================
    //  Objetos, `new` y el despacho de metodos
    // ===================================================

    private fun evaluateObjectCreation(expr: ObjectCreation): RuntimeValue {
        // La Fase 3 ya valido que la clase existe, pero solo se registran las del
        // nivel superior: una anidada en un bloque llegaria aqui sin declaracion.
        val declaration = classDeclarations[expr.className]
            ?: throw RuntimeError(expr.location,
                "La clase '${expr.className}' no está disponible en tiempo de ejecución")
        val instance = ObjectValue(expr.className, mutableMapOf())

        // Los campos, desde la superclase hacia abajo.
        initializeFields(declaration, instance)

        // El constructor, propio o HEREDADO: findMethod sube la cadena de herencia.
        // El TypeChecker ya valido la cantidad y el tipo de argumentos.
        findMethod(expr.className, CONSTRUCTOR_NAME)?.let { constructor ->
            invoke(
                FunctionValue(constructor, globalEnvironment, boundThis = instance),
                expr.arguments.map { evaluate(it) },
                expr.location
            )
        }

        return instance
    }

    // Los campos de la clase y de sus superclases, con su valor inicial.
    private fun initializeFields(declaration: ClassDeclaration, instance: ObjectValue) {
        declaration.superclassName
            ?.let { classDeclarations[it] }
            ?.let { initializeFields(it, instance) }

        declaration.members.filterIsInstance<VariableDeclaration>().forEach { field ->
            instance.fields[field.name] =
                field.initializer?.let { evaluate(it) } ?: defaultValueFor(field)
        }
    }

    // El valor con el que arranca una variable o campo sin inicializador.
    private fun defaultValueFor(field: VariableDeclaration): RuntimeValue =
        when (field.declaredType?.name) {
            "integer" -> IntValue(0)
            "float" -> FloatValue(0.0)
            "string" -> StringValue("")
            "boolean" -> BoolValue(false)

            // Clases y arreglos SI arrancan en null: son referencias, y `null` es
            // un valor legitimo de su tipo.
            else -> NullValue
        }

    // Un acceso a propiedad devuelve un CAMPO o un METODO ligado.
    private fun evaluatePropertyAccess(expr: PropertyAccess): RuntimeValue {
        val target = evaluate(expr.target)

        if (target !is ObjectValue) {
            throw RuntimeError(expr.location,
                "No se puede acceder a '.${expr.propertyName}' sobre un valor que no es " +
                "un objeto")
        }

        // Un campo: esta en el mapa de la instancia.
        target.fields[expr.propertyName]?.let { return it }

        // Un metodo: se busca desde la clase REAL del objeto hacia arriba, y se
        // liga a esta instancia. `boundThis` hace que `this.nombre` funcione adentro.
        val method = findMethod(target.className, expr.propertyName)
            ?: throw RuntimeError(expr.location,
                "'${target.className}' no tiene un miembro llamado '${expr.propertyName}'")

        return FunctionValue(method, globalEnvironment, boundThis = target)
    }

    // Busca un metodo subiendo la cadena de herencia. Es el mismo recorrido que
    // hace Scope.lookupMember en compilacion.
    private fun findMethod(className: String, methodName: String): FunctionDeclaration? {
        val declaration = classDeclarations[className] ?: return null

        findMethodIn(declaration, methodName)?.let { return it }

        return declaration.superclassName?.let { findMethod(it, methodName) }
    }

    // Solo en ESTA clase, sin subir. Es el paso base de findMethod.
    private fun findMethodIn(
        declaration: ClassDeclaration,
        methodName: String
    ): FunctionDeclaration? =
        declaration.members
            .filterIsInstance<FunctionDeclaration>()
            .firstOrNull { it.name == methodName }

    // ==================================================
    //  La asignacion: tres destinos posibles
    // ==================================================

    private fun executeAssignment(stmt: Assignment) {
        assignTo(stmt.target, evaluate(stmt.value))
    }

    // La misma operacion la necesita AssignmentExpression cuando se usa anidada.
    private fun evaluateAssignmentExpression(expr: AssignmentExpression): RuntimeValue {
        val value = evaluate(expr.value)
        assignTo(expr.target, value)
        return value // el valor de `x = 5` es 5
    }

    // Cuerpo de BLOQUE y no `= when (...)`: como expresion, cada rama tendria que
    // ser una expresion, y el `if` sin `else` de la primera rama no lo es.
    private fun assignTo(target: Expression, value: RuntimeValue) {
        when (target) {
            is Identifier -> {
                if (!environment.assign(target.name, value)) {
                    throw RuntimeError(target.location,
                        "La variable '${target.name}' no existe en tiempo de ejecución")
                }
            }

            is PropertyAccess -> {
                val instance = asObject(evaluate(target.target), target.location)
                instance.fields[target.propertyName] = value
            }

            is IndexAccess -> {
                val array = asArray(evaluate(target.target), target.location)
                array.elements[boundsCheckedIndex(array, target)] = value
            }

            else -> error("Destino de asignación no válido: ${target::class.simpleName}")
        }
    }

    // ==================================================
    //  Sentencias
    // ==================================================

    // Un bloque abre un entorno hijo, y lo restaura al salir.
    private fun executeBlock(block: Block, blockEnvironment: Environment) {
        val previous = environment
        environment = blockEnvironment
        try {
            block.statements.forEach { execute(it) }
        } finally {
            // finally para que un ReturnSignal no deje el entorno equivocado puesto.
            environment = previous
        }
    }

    private fun executeVariableDeclaration(stmt: VariableDeclaration) {
        // define y no assign: es una declaracion, crea un nombre NUEVO en este
        // entorno. Sin inicializador arranca en el cero de su tipo, como un campo.
        environment.define(
            stmt.name,
            stmt.initializer?.let { evaluate(it) } ?: defaultValueFor(stmt)
        )
    }

    private fun executeFunctionDeclaration(stmt: FunctionDeclaration) {
        environment.define(stmt.name, FunctionValue(stmt, environment))
    }

    private fun executePrint(stmt: Print) {
        output.add(evaluate(stmt.expr).display())
    }

    private fun executeIf(stmt: If) {
        if (asBoolean(evaluate(stmt.condition))) {
            executeBlock(stmt.thenBranch, environment.child())
        } else {
            stmt.elseBranch?.let { executeBlock(it, environment.child()) }
        }
    }

    private fun executeWhile(stmt: While) {
        while (asBoolean(evaluate(stmt.condition))) {
            if (!runLoopBody(stmt.body)) break
        }
    }

    private fun executeDoWhile(stmt: DoWhile) {
        do {
            if (!runLoopBody(stmt.body)) break
        } while (asBoolean(evaluate(stmt.condition)))
    }

    private fun executeFor(stmt: For) {
        // El inicializador declara en un entorno propio del bucle
        val loopEnvironment = environment.child()
        val previous = environment
        environment = loopEnvironment

        try {
            stmt.initializer?.let { execute(it) }

            // Sin condicion, el bucle es infinito: `for (;;)`.
            while (stmt.condition == null || asBoolean(evaluate(stmt.condition))) {
                if (!runLoopBody(stmt.body)) break

                // La actualizacion corre TAMBIEN despues de un `continue`: por eso
                // runLoopBody devuelve true en ese caso.
                stmt.update?.let { evaluate(it) }
            }
        } finally {
            environment = previous
        }
    }

    private fun executeForEach(stmt: ForEach) {
        val iterable = asArray(evaluate(stmt.iterable), stmt.location)

        iterable.elements.toList().forEach { element ->
            val iterationEnvironment = environment.child()
            iterationEnvironment.define(stmt.variableName, element)

            val previous = environment
            environment = iterationEnvironment
            try {
                if (!runLoopBody(stmt.body)) return
            } finally {
                environment = previous
            }
        }
    }

    private fun executeSwitch(stmt: Switch) {
        val subject = evaluate(stmt.subject)

        stmt.cases.forEach { case ->
            if (valuesAreEqual(subject, evaluate(case.value))) {
                executeStatements(case.body)
                return
            }
        }

        stmt.defaultBody?.let { executeStatements(it) }
    }

    // El cuerpo de un case o default: una lista de sentencias en un entorno hijo.
    // Un case no tiene llaves en la gramatica, asi que no es un Block.
    private fun executeStatements(statements: List<Statement>) {
        val caseEnvironment = environment.child()
        val previous = environment
        environment = caseEnvironment
        try {
            statements.forEach { execute(it) }
        } finally {
            environment = previous
        }
    }

    private fun executeTryCatch(stmt: TryCatch) {
        try {
            executeBlock(stmt.tryBlock, environment.child())
        } catch (error: RuntimeError) {
            val catchEnvironment = environment.child()
            catchEnvironment.define(stmt.catchParameterName, StringValue(error.message))
            executeBlock(stmt.catchBlock, catchEnvironment)
        }
    }

    // Ejecuta el cuerpo de un bucle UNA vez. Devuelve false si hay que salir.
    private fun runLoopBody(body: Block): Boolean {
        try {
            executeBlock(body, environment.child())
        } catch (signal: BreakSignal) {
            return false
        } catch (signal: ContinueSignal) {
            // Esta vuelta termina; el bucle continua.
        }
        return true
    }

    // ==================================================
    //  Ayudantes de conversion
    // =================================================

    // Un valor plegado por la Fase 4 (o el `value` de un Literal) a RuntimeValue.
    private fun toRuntimeValue(constant: Any?): RuntimeValue = when (constant) {
        null -> NullValue
        is Long -> IntValue(constant)
        is Double -> FloatValue(constant)
        is String -> StringValue(constant)
        is Boolean -> BoolValue(constant)
        else -> error("Constante de tipo inesperado: $constant")
    }

    private fun asBoolean(value: RuntimeValue): Boolean =
        (value as? BoolValue)?.value ?: error("Se esperaba un boolean, no $value")

    private fun asLong(value: RuntimeValue): Long =
        (value as? IntValue)?.value ?: error("Se esperaba un integer, no $value")

    private fun asDouble(value: RuntimeValue): Double = when (value) {
        is FloatValue -> value.value
        is IntValue -> value.value.toDouble() // ensanchamiento: 1 + 2.5
        else -> error("Se esperaba un número, no $value")
    }

    // Las tres de abajo NO son conversiones: son chequeos dinamicos.
    //
    // El TypeChecker garantiza el TIPO, no que la referencia tenga algo adentro:
    // `let a: integer[] = null;` es legal. Sin esto, indexar ese nulo lanzaria una
    // ClassCastException, que ni el try/catch del usuario ni run() atrapan.
    private fun asArray(value: RuntimeValue, location: LexemeLocation): ArrayValue =
        value as? ArrayValue
            ?: throw RuntimeError(location, "Se esperaba una lista, no '${value.display()}'")

    private fun asObject(value: RuntimeValue, location: LexemeLocation): ObjectValue =
        value as? ObjectValue
            ?: throw RuntimeError(location, "Se esperaba un objeto, no '${value.display()}'")

    private fun asFunction(value: RuntimeValue, location: LexemeLocation): FunctionValue =
        value as? FunctionValue
            ?: throw RuntimeError(location, "Se esperaba una función, no '${value.display()}'")

    private fun asString(value: RuntimeValue): String =
        (value as? StringValue)?.value ?: error("Se esperaba un string, no $value")

    // La igualdad de Compiscript
    private fun valuesAreEqual(left: RuntimeValue, right: RuntimeValue): Boolean =
        if (isNumeric(left) && isNumeric(right)) asDouble(left) == asDouble(right)
        else left == right

    private fun compareValues(left: RuntimeValue, right: RuntimeValue): Int =
        if (isNumeric(left) && isNumeric(right)) asDouble(left).compareTo(asDouble(right))
        else asString(left).compareTo(asString(right))

    private fun isNumeric(value: RuntimeValue): Boolean =
        value is IntValue || value is FloatValue

    private companion object {
        private const val MAX_CALL_DEPTH = 1000
    }
}
