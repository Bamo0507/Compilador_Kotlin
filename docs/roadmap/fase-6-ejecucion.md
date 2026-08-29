# Fase 6 — Ejecución

**Objetivo de la fase:** ejecutar el programa. `print(3 + 5)` imprime `8`, una
función recursiva calcula el factorial, y un objeto guarda y devuelve sus campos.

**Por qué va al final:** el intérprete corre sobre el AST **ya validado**. Si se
ejecuta antes de verificar, se ejecuta código con errores de tipo y se obtiene
basura en vez de un mensaje útil. Y el intérprete puede asumir que todo está bien
tipado, lo que lo hace mucho más corto: no revalida nada.

**Nota de alcance:** esto **no está en la rúbrica escrita** (IDE 15 / Sintáctico y
Semántico 60 / Tabla de Símbolos 25), pero el catedrático lo pidió explícitamente:
espera el árbol sintáctico, el árbol validado semánticamente, **y el resultado de
ejecutar**. Se hace, pero después de que todo lo que sí puntúa esté terminado.

**Estimación:** dos o tres sesiones.

---

## Lo que ya se obtuvo sin intérprete

Antes de escribir una línea de esta fase, el plegado de constantes de la Fase 4 ya
resuelve una parte de la demostración:

```cps
print(3 + 5);        // el TypeChecker ya calculó 8
print(2 * (4 + 1));  // ya calculó 10
```

El intérprete agrega lo que el plegado no puede: variables que cambian, bucles,
llamadas a función, objetos.

Y hay un pago concreto: `evaluate` arranca con un atajo sobre lo que la Fase 4 ya
resolvió, así que `print(3 + 5)` **no ejecuta ninguna suma** — el `8` ya estaba en el
nodo. Ver "Los dos despachadores" en el ticket 6.2.

---

## Ticket 6.1 — Valores en ejecución y entorno

- **Estado**: pendiente
- **Depende de**: 5.1

**Archivos:**

- `interpreter/RuntimeValue.kt` (NUEVO)
- `interpreter/Environment.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/EnvironmentTest.kt` (NUEVO)

### `RuntimeValue`: los valores que existen mientras el programa corre

**No confundir `Type` con `RuntimeValue`.** Son dos cosas distintas y es la
confusión más fácil de esta fase:

| | `Type` (Fase 1) | `RuntimeValue` (esta fase) |
|---|---|---|
| Qué es | *"esta variable es un entero"* | *"esta variable vale 42"* |
| Cuándo existe | en compilación | en ejecución |
| Ejemplo | `IntegerType` | `IntValue(42)` |

```kotlin
// Un valor concreto durante la ejecución.
sealed interface RuntimeValue {
    // Cómo se imprime con print(). Es la única forma en que el usuario los ve.
    fun display(): String
}

data class IntValue(val value: Long) : RuntimeValue {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : RuntimeValue {
    override fun display() = value.toString()
}

data class StringValue(val value: String) : RuntimeValue {
    override fun display() = value
}

data class BoolValue(val value: Boolean) : RuntimeValue {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : RuntimeValue {
    override fun display() = "null"
}

// Una lista. Es MUTABLE porque lista[0] = 5 debe modificarla en su lugar.
//
// Es `class` y no `data class` a proposito: dos arreglos distintos con el mismo
// contenido NO son el mismo arreglo. Con `data class`, `[1,2] == [1,2]` daria true, y
// en la mayoria de los lenguajes eso es false.
class ArrayValue(val elements: MutableList<RuntimeValue>) : RuntimeValue {
    override fun display() = elements.joinToString(", ", "[", "]") { it.display() }
}

// Una instancia de clase: sus campos, por nombre.
//
// TAMPOCO es `data class`, y aqui la razon es mas fuerte. Con data class:
//
//   let a: Perro = new Perro("Toby");
//   let b: Perro = new Perro("Toby");
//   print(a == b);      // daria TRUE, porque compara className y fields
//
// Son dos objetos distintos. Un objeto tiene IDENTIDAD, igual que un Scope, y por eso
// las dos son `class`: el equals por referencia es el correcto.
class ObjectValue(
    val className: String,
    val fields: MutableMap<String, RuntimeValue>
) : RuntimeValue {
    override fun display() = "$className@${hashCode()}"
}

// Una función como valor.
//
// El campo `closure` es la pieza clave de esta fase: guarda el ENTORNO donde la
// función fue DEFINIDA, no donde se llama. Es lo que hace que una función anidada
// pueda seguir viendo las variables de la función que la creó, aunque esa ya haya
// terminado. Es la contraparte en ejecución del análisis de capturas de la Fase 5.
// Misma razon: dos funciones no se comparan por valor.
class FunctionValue(
    val declaration: FunctionDeclaration,
    val closure: Environment,
    val boundThis: ObjectValue? = null    // no-null si es un método
) : RuntimeValue {
    override fun display() = "<function ${declaration.name}>"
}
```

### `Environment`: los ámbitos, pero con valores

`Scope` guarda **qué tipo tiene** cada nombre. `Environment` guarda **qué vale**.
Misma forma de árbol, contenido distinto.

```kotlin
// Los valores visibles en un punto de la ejecución.
//
// Es el gemelo en ejecución de Scope: misma estructura de padre e hijos, pero
// guarda valores en vez de tipos.
class Environment(private val parent: Environment? = null) {

    private val values = mutableMapOf<String, RuntimeValue>()

    fun define(name: String, value: RuntimeValue) {
        values[name] = value
    }

    // Busca en este nivel y sube. Igual que Scope.lookup.
    fun get(name: String): RuntimeValue? =
        values[name] ?: parent?.get(name)

    // Asigna a la variable EXISTENTE, buscando hacia arriba.
    //
    // Distinguir esto de `define` es lo que hace que las asignaciones dentro de un
    // bloque modifiquen la variable de afuera en vez de crear una nueva:
    //   let x = 1;
    //   { x = 2; }     <- modifica la x de afuera, no crea otra
    //   print(x);      <- imprime 2
    fun assign(name: String, value: RuntimeValue): Boolean = when {
        values.containsKey(name) -> { values[name] = value; true }
        parent != null           -> parent.assign(name, value)
        else                     -> false
    }

    fun child(): Environment = Environment(parent = this)
}
```

### Aceptación

- `IntValue(42).display()` es `"42"`.
- `ArrayValue([1,2,3]).display()` es `"[1, 2, 3]"`.
- `BoolValue(true).display()` es `"true"`.
- **Dos `ObjectValue` con los mismos campos NO son iguales**, y dos `ArrayValue` con
  el mismo contenido tampoco. *Test de que no son `data class`.*
- `IntValue(42) == IntValue(42)` **sí** es `true`: los primitivos sí se comparan por
  valor, y son `data class`.
- Un `Environment` hijo ve las variables del padre.
- `assign` en un hijo modifica la variable del padre si existe ahí.
- `define` en un hijo crea una variable nueva que **tapa** la del padre, sin
  modificarla.
- `assign` a un nombre que no existe en ningún nivel devuelve `false`.

---

## Ticket 6.2 — `Interpreter`

- **Estado**: pendiente
- **Depende de**: 6.1

**Archivos:**

- `interpreter/Interpreter.kt` (NUEVO)
- `interpreter/ControlFlowSignals.kt` (NUEVO)
- `interpreter/RuntimeError.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/InterpreterTest.kt` (NUEVO)

**Qué es esto, en simple:** la misma forma que el `TypeChecker` —una función por
construcción del lenguaje— pero en vez de devolver un tipo, devuelve un **valor**.

```kotlin
class Interpreter {
    private val output = mutableListOf<String>()
    private var environment = Environment()

    // Las declaraciones de clase, por nombre. Se registran ANTES de ejecutar, para
    // que `new Perro()` funcione aunque la clase este declarada mas abajo.
    private val classDeclarations = mutableMapOf<String, ClassDeclaration>()

    // Contador de profundidad de llamada. Ver "El limite de recursion" abajo.
    private var callDepth = 0

    fun run(program: Program): ExecutionResult {
        // Ronda previa: registrar clases y funciones del nivel superior. Es el
        // equivalente en ejecucion de la Pasada 1, y por la misma razon: habilita
        // llamar a algo declarado mas abajo.
        program.statements.forEach { statement ->
            when (statement) {
                is ClassDeclaration    -> classDeclarations[statement.name] = statement
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
            // Se devuelve la salida producida HASTA el error, no una lista vacia: el
            // usuario necesita ver hasta donde llego su programa.
            ExecutionResult(output, runtimeError = error)
        }
    }
}
```

### Los dos despachadores

```kotlin
private fun evaluate(expr: Expression): RuntimeValue {
    // ATAJO: si la Fase 4 ya calculo el valor, no hay nada que ejecutar.
    //
    //   print(3 + 5);   ->  el 8 ya esta en el nodo, no se suma nada aqui
    //   print(2 * (4 + 1));  ->  el 10 tampoco
    //
    // Es el pago de haber decorado el arbol, y hace que la mitad de la demo del
    // enunciado no toque una sola linea de aritmetica.
    expr.constantValue?.let { return toRuntimeValue(it) }

    return when (expr) {
        is Literal              -> evaluateLiteral(expr)
        is ArrayLiteral         -> evaluateArrayLiteral(expr)
        is Identifier           -> evaluateIdentifier(expr)
        is ThisReference        -> evaluateThis(expr)
        is UnaryOperation       -> evaluateUnary(expr)
        is BinaryOperation      -> evaluateBinary(expr)
        is TernaryOperation     -> evaluateTernary(expr)
        is AssignmentExpression -> evaluateAssignmentExpression(expr)
        is FunctionCall         -> evaluateFunctionCall(expr)
        is IndexAccess          -> evaluateIndexAccess(expr)
        is PropertyAccess       -> evaluatePropertyAccess(expr)
        is ObjectCreation       -> evaluateObjectCreation(expr)
    }
}

private fun execute(stmt: Statement) = when (stmt) {
    is VariableDeclaration  -> executeVariableDeclaration(stmt)
    is FunctionDeclaration  -> executeFunctionDeclaration(stmt)
    is Assignment           -> executeAssignment(stmt)
    is ExpressionStatement  -> { evaluate(stmt.expr); Unit }
    is Print                -> executePrint(stmt)
    is Block                -> executeBlock(stmt, environment.child())
    is If                   -> executeIf(stmt)
    is While                -> executeWhile(stmt)
    is DoWhile              -> executeDoWhile(stmt)
    is For                  -> executeFor(stmt)
    is ForEach              -> executeForEach(stmt)
    is Switch               -> executeSwitch(stmt)
    is TryCatch             -> executeTryCatch(stmt)
    is Return               -> throw ReturnSignal(stmt.value?.let { evaluate(it) } ?: NullValue)
    is Break                -> throw BreakSignal()
    is Continue             -> throw ContinueSignal()

    // Las clases ya se registraron en run(). Anidadas en un bloque no se soportan:
    // es la misma limitacion documentada en la Pasada 1.
    is ClassDeclaration     -> Unit
}
```

### El intérprete confía en los tipos decorados

Esto es lo que hace que `evaluateBinary` sea corto: **no inspecciona los valores para
decidir qué operación hacer**, mira el tipo que el `TypeChecker` ya escribió.

```kotlin
private fun evaluateBinary(expr: BinaryOperation): RuntimeValue {
    // Cortocircuito de && y ||: el lado derecho NO se evalua si el izquierdo decide.
    // Es la unica razon por la que estos dos se tratan antes que el resto.
    if (expr.operator == BinaryOperator.AND) {
        return BoolValue(asBoolean(evaluate(expr.left)) && asBoolean(evaluate(expr.right)))
    }
    if (expr.operator == BinaryOperator.OR) {
        return BoolValue(asBoolean(evaluate(expr.left)) || asBoolean(evaluate(expr.right)))
    }

    val left = evaluate(expr.left)
    val right = evaluate(expr.right)

    // `expr.type` lo puso la Fase 4. El interprete no vuelve a decidir si esto es
    // aritmetica de enteros, de flotantes o concatenacion: ya esta decidido.
    return when (expr.operator.group) {
        OperatorGroup.ARITHMETIC -> arithmeticResult(expr, left, right)
        OperatorGroup.RELATIONAL -> BoolValue(compareValues(left, right).let { comparison ->
            when (expr.operator) {
                BinaryOperator.LESS          -> comparison < 0
                BinaryOperator.LESS_EQUAL    -> comparison <= 0
                BinaryOperator.GREATER       -> comparison > 0
                else                         -> comparison >= 0
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
    StringType  -> StringValue(asString(left) + asString(right))
    FloatType   -> FloatValue(applyFloat(expr.operator, asDouble(left), asDouble(right)))
    else        -> IntValue(applyLong(expr.operator, asLong(left), asLong(right), expr))
}
```

**`applyLong` recibe el `expr`** porque es donde se verifica la división entre cero
dinámica: la Fase 4 solo pudo rechazarla cuando el divisor era constante.

```kotlin
private fun applyLong(
    op: BinaryOperator,
    left: Long,
    right: Long,
    expr: BinaryOperation
): Long = when (op) {
    BinaryOperator.ADD      -> left + right
    BinaryOperator.SUBTRACT -> left - right
    BinaryOperator.MULTIPLY -> left * right
    BinaryOperator.DIVIDE, BinaryOperator.MODULO -> {
        // Chequeo DINAMICO: el divisor puede ser una variable, y ahi la Fase 4 no
        // pudo decidir. Es la propiedad REALIZABLE con las dos mitades puestas.
        if (right == 0L) {
            throw RuntimeError(expr.location, "División entre cero")
        }
        if (op == BinaryOperator.DIVIDE) left / right else left % right
    }
    else -> error("'$op' no es un operador aritmético")
}
```

### `break`, `continue` y `return` como señales

Un intérprete de árbol tiene un problema: cuando se ejecuta un `return` en el fondo
de cinco bloques anidados, hay que salir de las cinco llamadas de Kotlin de golpe.
La técnica estándar y más simple es usar excepciones como **señales**:

```kotlin
// Señales de control de flujo. NO son errores: son la forma estándar de sacar el
// control de varios niveles de recursión de una sola vez.
//
// Heredan de RuntimeException pero sin stack trace (el `false` del último
// parámetro), porque capturar la traza sería costoso y aquí no sirve para nada.
sealed class ControlFlowSignal : RuntimeException(null, null, false, false)

class BreakSignal : ControlFlowSignal()
class ContinueSignal : ControlFlowSignal()
class ReturnSignal(val value: RuntimeValue) : ControlFlowSignal()
```

```kotlin
private fun executeWhile(stmt: While) {
    while (asBoolean(evaluate(stmt.condition))) {
        try {
            executeBlock(stmt.body, environment.child())
        } catch (signal: BreakSignal) {
            break            // sale del bucle de Kotlin
        } catch (signal: ContinueSignal) {
            continue         // sigue con la próxima vuelta
        }
        // Un ReturnSignal NO se captura aquí: sube hasta la llamada a función.
    }
}
```

**Por qué excepciones y no un valor de retorno:** si cada `execute` devolviera un
`enum { NORMAL, BREAK, CONTINUE, RETURN }`, cada llamada tendría que revisar el
resultado y propagarlo, en las treinta funciones. Con señales, solo los tres lugares
que las capturan tienen código extra. Es más simple de leer y más difícil de
equivocar.

### Errores en ejecución: la propiedad *realizable*

```kotlin
// Un error que solo se puede detectar ejecutando.
//
// Esta clase es la materialización de la propiedad REALIZABLE del sistema de tipos:
// "lo que no se puede verificar estáticamente debe compararse dinámicamente al
// momento de la ejecución".
//
// El TypeChecker verifica lo que puede (lista[-1] con índice constante, tipos de
// todas las operaciones); lo que depende de valores que solo existen al correr
// (lista[i], división entre cero) se verifica aquí.
class RuntimeError(
    val location: LexemeLocation,
    override val message: String
) : RuntimeException(message)
```

Los dos casos concretos en Compiscript:

```kotlin
private fun evaluateIndexAccess(expr: IndexAccess): RuntimeValue {
    val array = evaluate(expr.target) as ArrayValue
    val index = (evaluate(expr.index) as IntValue).value.toInt()

    // Chequeo DINÁMICO: el TypeChecker no pudo hacerlo porque el índice puede ser
    // una variable. Es la propiedad realizable en acción.
    if (index < 0 || index >= array.elements.size) {
        throw RuntimeError(expr.location,
            "Índice fuera de rango: $index (la lista tiene ${array.elements.size} elementos)")
    }

    return array.elements[index]
}
```

Y estos errores son los que atrapa el `try/catch` del propio lenguaje, que es
exactamente el ejemplo de `Especificaciones.md`:

```cps
try {
  let peligro = lista[100];
} catch (err) {
  print("Error atrapado: " + err);
}
```

```kotlin
private fun executeTryCatch(stmt: TryCatch) {
    try {
        executeBlock(stmt.tryBlock, environment.child())
    } catch (error: RuntimeError) {
        val catchEnvironment = environment.child()
        // El parámetro del catch recibe el mensaje del error como string.
        catchEnvironment.define(stmt.catchParameterName, StringValue(error.message))
        executeBlock(stmt.catchBlock, catchEnvironment)
    }
    // Las ControlFlowSignal NO se capturan: un `return` dentro de un try debe
    // seguir saliendo de la función, no ser tratado como una excepción del usuario.
}
```

Ese último comentario es importante: `ControlFlowSignal` y `RuntimeError` son ambas
`RuntimeException`, así que un `catch (e: RuntimeException)` descuidado atraparía los
`return`. Hay que capturar el tipo exacto.

### Llamadas a función y closures

```kotlin
private fun evaluateFunctionCall(expr: FunctionCall): RuntimeValue {
    val callee = evaluate(expr.callee) as FunctionValue
    val arguments = expr.arguments.map { evaluate(it) }
    return invoke(callee, arguments, expr.location)
}

// Ejecutar una funcion ya resuelta. Lo comparten las llamadas normales y la
// invocacion de metodos desde `new`.
private fun invoke(
    callee: FunctionValue,
    arguments: List<RuntimeValue>,
    location: LexemeLocation
): RuntimeValue {
    // El limite de recursion: sin esto, `function f(): integer { return f(); }`
    // tumba la ventana con un StackOverflowError, que ningun catch del usuario
    // puede atrapar. Convertirlo en RuntimeError lo hace atrapable y deja el IDE
    // vivo. Es la misma defensa que el `catch (Throwable)` de AppState.
    if (callDepth >= MAX_CALL_DEPTH) {
        throw RuntimeError(location,
            "Recursión demasiado profunda: más de $MAX_CALL_DEPTH llamadas anidadas")
    }

    // El entorno de la llamada cuelga del entorno donde la función fue DEFINIDA
    // (su closure), NO de donde se la está llamando. Esto es lo que hace que las
    // funciones anidadas vean las variables de la función que las creó.
    val callEnvironment = callee.closure.child()

    // `this` disponible si es un método.
    callee.boundThis?.let { callEnvironment.define("this", it) }

    // Los parámetros se ligan por POSICIÓN, como validó el TypeChecker.
    callee.declaration.parameters.forEachIndexed { index, parameter ->
        callEnvironment.define(parameter.name, arguments[index])
    }

    callDepth += 1
    try {
        return try {
            executeBlock(callee.declaration.body, callEnvironment)
            NullValue                         // llegó al final sin return
        } catch (signal: ReturnSignal) {
            signal.value                      // hubo return
        }
    } finally {
        // En el `finally` para que una excepcion no deje el contador inflado: si no,
        // un try/catch alrededor de una recursion profunda dejaria el interprete
        // creyendo que sigue anidado.
        callDepth -= 1
    }
}

private companion object {
    private const val MAX_CALL_DEPTH = 1000
}
```

**`callee.closure.child()` y no `environment.child()`** es la línea que hace que los
closures funcionen. Si colgara del entorno de la llamada, la función vería las
variables de quien la llama en vez de las de donde fue definida — que es el
comportamiento equivocado y el bug clásico de los intérpretes de árbol.

### Objetos, `new` y el despacho de métodos

```kotlin
private fun evaluateObjectCreation(expr: ObjectCreation): RuntimeValue {
    val declaration = classDeclarations[expr.className]!!
    val instance = ObjectValue(expr.className, mutableMapOf())

    // Los campos, desde la superclase hacia abajo: asi una subclase que redefina un
    // campo con inicializador gana sobre el del padre.
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
//
// Recorre de ARRIBA hacia abajo (primero la superclase) para que, si una subclase
// declara un campo con el mismo nombre, su inicializador sobreescriba al del padre.
private fun initializeFields(declaration: ClassDeclaration, instance: ObjectValue) {
    declaration.superclassName
        ?.let { classDeclarations[it] }
        ?.let { initializeFields(it, instance) }

    declaration.members.filterIsInstance<VariableDeclaration>().forEach { field ->
        instance.fields[field.name] =
            field.initializer?.let { evaluate(it) } ?: defaultValueFor(field)
    }
}
```

### El valor por defecto de un campo sin inicializar

```cps
class Animal { let nombre: string; }    // legal: la gramatica no exige inicializador
let a: Animal = new Animal();
print(a.nombre);                        // ¿que imprime?
```

**Decisión: un cero por tipo, no `null`.**

```kotlin
// El valor con el que arranca un campo sin inicializador.
//
// Se elige un CERO POR TIPO y no NullValue para todo, por una razon concreta: `null`
// solo es asignable a clases y arreglos (regla de isAssignable), asi que un
// `let n: integer;` con NullValue tendria en ejecucion un valor que su propio tipo
// rechaza. `print(a.nombre)` imprimiria "null" sobre algo declarado `string`.
//
// El campo declaredType nunca es null aqui: la Pasada 1 exige tipo anotado en los
// campos (ticket 3.2).
private fun defaultValueFor(field: VariableDeclaration): RuntimeValue =
    when (field.declaredType?.name) {
        "integer" -> IntValue(0)
        "float"   -> FloatValue(0.0)
        "string"  -> StringValue("")
        "boolean" -> BoolValue(false)

        // Clases y arreglos si arrancan en null: son referencias, y `null` es un
        // valor legitimo de su tipo.
        else      -> NullValue
    }
```

### El despacho de métodos: por qué `Perro` gana sobre `Animal`

Es lo que hace que la demo funcione:

```cps
let perro: Perro = new Perro("Toby");
print(perro.hablar());      // "Toby ladra.", NO "Toby hace ruido."
```

La clave: la búsqueda arranca de `ObjectValue.className` —la clase **real** del
objeto— y **no** del tipo declarado de la variable.

```kotlin
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

    // Un metodo: se busca desde la clase REAL del objeto hacia arriba, y se liga a
    // esta instancia. `boundThis` es lo que hace que `this.nombre` funcione adentro.
    //
    // Arrancar de target.className y no del tipo declarado de la variable es TODO el
    // mecanismo de sobrescritura: `perro` puede estar declarado como Animal, pero su
    // ObjectValue dice "Perro", asi que gana el hablar() de Perro.
    val method = findMethod(target.className, expr.propertyName)
        ?: throw RuntimeError(expr.location,
            "'${target.className}' no tiene un miembro llamado '${expr.propertyName}'")

    return FunctionValue(method, globalEnvironment, boundThis = target)
}

// Busca un metodo subiendo la cadena de herencia. Es el mismo recorrido que hace
// Scope.lookupMember en compilacion.
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
```

### Las sentencias

```kotlin
// El entorno raiz, donde viven las globales y las funciones del nivel superior.
// Se guarda aparte porque los metodos lo usan como closure: un metodo no captura el
// entorno de la llamada, solo ve sus parametros, `this` y las globales.
private val globalEnvironment = Environment()

// Un bloque abre un entorno hijo, y lo restaura al salir.
// Mismo patron que withScope en la Fase 4: el call stack de Kotlin es la pila.
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
    // define y no assign: es una declaracion, crea un nombre NUEVO en este entorno.
    // Sin inicializador arranca en el cero de su tipo, igual que un campo.
    environment.define(
        stmt.name,
        stmt.initializer?.let { evaluate(it) } ?: defaultValueFor(stmt)
    )
}

// Una funcion se vuelve un valor al declararse, y captura el entorno ACTUAL como su
// closure. Eso es lo que permite que una funcion anidada vea las locales de la de
// afuera.
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

private fun executeDoWhile(stmt: DoWhile) {
    do {
        if (!runLoopBody(stmt.body)) break
    } while (asBoolean(evaluate(stmt.condition)))
}

private fun executeFor(stmt: For) {
    // El inicializador declara en un entorno propio del bucle: `for (let i = 0; ...)`
    // deja `i` visible solo adentro.
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
    val iterable = evaluate(stmt.iterable) as ArrayValue

    // Se copia la lista antes de recorrer: si el cuerpo modificara el arreglo, el
    // recorrido no deberia cambiar a mitad de camino.
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

// SIN fall-through: el primer case que coincide ejecuta su cuerpo y el switch
// termina. Es la decision 5 del README, y es la razon de los `return` de abajo.
private fun executeSwitch(stmt: Switch) {
    val subject = evaluate(stmt.subject)

    stmt.cases.forEach { case ->
        if (valuesAreEqual(subject, evaluate(case.value))) {
            val caseEnvironment = environment.child()
            val previous = environment
            environment = caseEnvironment
            try {
                case.body.forEach { execute(it) }
            } finally {
                environment = previous
            }
            return
        }
    }

    stmt.defaultBody?.let { body ->
        val defaultEnvironment = environment.child()
        val previous = environment
        environment = defaultEnvironment
        try {
            body.forEach { execute(it) }
        } finally {
            environment = previous
        }
    }
}

// Ejecuta el cuerpo de un bucle UNA vez. Devuelve false si hay que salir del bucle.
//
// Existe para no repetir el mismo try/catch en los cuatro bucles. Un ReturnSignal NO
// se captura aqui: tiene que seguir subiendo hasta la llamada a la funcion.
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
```

### La asignación: tres destinos posibles

```kotlin
private fun executeAssignment(stmt: Assignment) {
    assignTo(stmt.target, evaluate(stmt.value))
}

// La misma operacion la necesita AssignmentExpression cuando se usa anidada.
private fun evaluateAssignmentExpression(expr: AssignmentExpression): RuntimeValue {
    val value = evaluate(expr.value)
    assignTo(expr.target, value)
    return value            // el valor de `x = 5` es 5
}

private fun assignTo(target: Expression, value: RuntimeValue) = when (target) {
    is Identifier -> {
        // assign y no define: modifica la variable EXISTENTE subiendo hasta
        // encontrarla. define crearia una nueva que tapa la de afuera, y entonces
        // `{ x = 2; }` no cambiaria la x del bloque exterior.
        if (!environment.assign(target.name, value)) {
            throw RuntimeError(target.location,
                "La variable '${target.name}' no existe en tiempo de ejecución")
        }
    }

    is PropertyAccess -> {
        val instance = evaluate(target.target) as ObjectValue
        instance.fields[target.propertyName] = value
    }

    is IndexAccess -> {
        val array = evaluate(target.target) as ArrayValue
        array.elements[boundsCheckedIndex(array, target)] = value
    }

    // El TypeChecker ya rechazo cualquier otro lvalue (isLValue del ticket 4.4), asi
    // que llegar aqui seria un bug del compilador, no del usuario.
    else -> error("Destino de asignación no válido: ${target::class.simpleName}")
}
```

### Las expresiones restantes

```kotlin
private fun evaluateLiteral(expr: Literal): RuntimeValue = toRuntimeValue(expr.value)

private fun evaluateArrayLiteral(expr: ArrayLiteral): RuntimeValue =
    ArrayValue(expr.elements.map { evaluate(it) }.toMutableList())

private fun evaluateIdentifier(expr: Identifier): RuntimeValue =
    environment.get(expr.name)
        ?: throw RuntimeError(expr.location,
            "La variable '${expr.name}' no tiene valor en tiempo de ejecución")

// `this` es un nombre normal en el entorno: lo definio `invoke` a partir de boundThis.
private fun evaluateThis(expr: ThisReference): RuntimeValue =
    environment.get("this")
        ?: throw RuntimeError(expr.location, "'this' no está disponible aquí")

private fun evaluateUnary(expr: UnaryOperation): RuntimeValue {
    val operand = evaluate(expr.operand)
    return when (expr.operator) {
        UnaryOperator.NOT    -> BoolValue(!asBoolean(operand))
        UnaryOperator.NEGATE ->
            if (expr.type == FloatType) FloatValue(-asDouble(operand))
            else IntValue(-asLong(operand))
    }
}

// La rama que no se toma NO se evalua: es lo mismo que el cortocircuito de && y ||.
private fun evaluateTernary(expr: TernaryOperation): RuntimeValue =
    if (asBoolean(evaluate(expr.condition))) evaluate(expr.ifTrue)
    else evaluate(expr.ifFalse)

private fun evaluateIndexAccess(expr: IndexAccess): RuntimeValue {
    val array = evaluate(expr.target) as ArrayValue
    return array.elements[boundsCheckedIndex(array, expr)]
}

// El chequeo de rango, compartido por la lectura y la escritura.
//
// Es DINAMICO porque el indice puede ser una variable: la Fase 4 solo pudo rechazar
// los indices negativos constantes. La propiedad realizable, otra vez.
private fun boundsCheckedIndex(array: ArrayValue, expr: IndexAccess): Int {
    val index = asLong(evaluate(expr.index)).toInt()

    if (index < 0 || index >= array.elements.size) {
        throw RuntimeError(expr.location,
            "Índice fuera de rango: $index (la lista tiene ${array.elements.size} " +
            "elementos)")
    }

    return index
}
```

### Los ayudantes de conversión

```kotlin
// Un valor plegado por la Fase 4 (o el `value` de un Literal) a RuntimeValue.
private fun toRuntimeValue(constant: Any?): RuntimeValue = when (constant) {
    null       -> NullValue
    is Long    -> IntValue(constant)
    is Double  -> FloatValue(constant)
    is String  -> StringValue(constant)
    is Boolean -> BoolValue(constant)
    else       -> error("Constante de tipo inesperado: $constant")
}

// Los cuatro `as*` desempaquetan. Los `error(...)` son para BUGS DEL COMPILADOR: si
// llega el valor equivocado, significa que el TypeChecker aprobo algo que no debia.
// Un error del usuario nunca pasa por aqui.
private fun asBoolean(value: RuntimeValue): Boolean =
    (value as? BoolValue)?.value ?: error("Se esperaba un boolean, no $value")

private fun asLong(value: RuntimeValue): Long =
    (value as? IntValue)?.value ?: error("Se esperaba un integer, no $value")

private fun asDouble(value: RuntimeValue): Double = when (value) {
    is FloatValue -> value.value
    is IntValue   -> value.value.toDouble()      // ensanchamiento: 1 + 2.5
    else          -> error("Se esperaba un número, no $value")
}

private fun asString(value: RuntimeValue): String =
    (value as? StringValue)?.value ?: error("Se esperaba un string, no $value")

// La igualdad de Compiscript, no la de Kotlin.
//
// Dos razones para no usar == directo:
//   1. IntValue(1) != FloatValue(1.0) para Kotlin, pero `1 == 1.0` debe ser true;
//   2. ObjectValue y ArrayValue son `class`, asi que su == ya es por identidad, que
//      es lo correcto — y hay que dejarlo pasar tal cual.
private fun valuesAreEqual(left: RuntimeValue, right: RuntimeValue): Boolean =
    if (isNumeric(left) && isNumeric(right)) asDouble(left) == asDouble(right)
    else left == right

private fun compareValues(left: RuntimeValue, right: RuntimeValue): Int =
    if (isNumeric(left) && isNumeric(right)) asDouble(left).compareTo(asDouble(right))
    else asString(left).compareTo(asString(right))

private fun isNumeric(value: RuntimeValue): Boolean =
    value is IntValue || value is FloatValue

private fun applyFloat(op: BinaryOperator, left: Double, right: Double): Double =
    when (op) {
        BinaryOperator.ADD      -> left + right
        BinaryOperator.SUBTRACT -> left - right
        BinaryOperator.MULTIPLY -> left * right
        BinaryOperator.DIVIDE   -> left / right   // la division entre 0.0 la valida
                                                  // applyLong para enteros; en float
                                                  // el chequeo va en arithmeticResult
        else -> error("'$op' no aplica a flotantes")   // MODULO es solo integer (A3)
    }
```

### El resultado de ejecutar

```kotlin
data class ExecutionResult(
    val output: List<String>,          // lo que imprimió print(), en orden
    val runtimeError: RuntimeError?    // null si terminó bien
)
```

**Por qué la salida es una lista y no `println`:** tiene que aparecer en la consola
del IDE, no en la terminal. Además, así es testeable: un test compara la lista contra
lo esperado.

### Aceptación

**Expresiones y variables:**

| Programa | Salida esperada |
|---|---|
| `print(3 + 5);` | `8` |
| `print(2 * (4 + 1));` | `10` |
| `print(10 - 3 - 2);` | `5`. *Verifica el plegado a la izquierda de la Fase 2.* |
| `print("Hola " + "mundo");` | `Hola mundo` |
| `print(3.5 + 1);` | `4.5` |
| `print(true && false);` | `false` |
| `let x = 1; x = x + 1; print(x);` | `2` |
| `let x = 1; { x = 2; } print(x);` | `2`. *Verifica `assign` vs `define`.* |
| `let x = 1; { let x = 2; } print(x);` | `1`. *Verifica el shadowing.* |

**Control de flujo:**

| Programa | Salida esperada |
|---|---|
| `let i = 0; while (i < 3) { print(i); i = i + 1; }` | `0`, `1`, `2` |
| `foreach (n in [1,2,3]) { print(n); }` | `1`, `2`, `3` |
| `foreach (n in [1,2,3]) { if (n == 2) { continue; } print(n); }` | `1`, `3` |
| `for (let i = 0; i < 5; i = i + 1) { if (i == 2) { break; } print(i); }` | `0`, `1` |
| `switch (2) { case 1: print("a"); case 2: print("b"); }` | `b`. *Sin fall-through.* |
| `switch (9) { case 1: print("a"); }` | nada, sin error |

**Funciones y recursión:**

| Programa | Salida esperada |
|---|---|
| `function suma(a: integer, b: integer): integer { return a + b; } print(suma(2,3));` | `5` |
| El `factorial` de `Especificaciones.md` con `factorial(5)` | `120` |
| El `crearContador` de `Especificaciones.md` | corre sin error |
| Una función anidada que usa una local de la de afuera | ve el valor correcto. *Test de closure.* |
| `function f(): integer { return f(); }` llamada | error *"recursión demasiado profunda"*, la app no muere |
| Una recursión profunda atrapada con `try/catch` y luego otra llamada normal | funciona: el `finally` deja `callDepth` en su valor correcto |
| `let i = 0; for (let j = 0; j < 3; j = j + 1) { if (j == 1) { continue; } i = i + 1; }` | `i` queda en `2`: el `continue` **sí** ejecuta la actualización |

**Clases:**

| Programa | Salida esperada |
|---|---|
| El `Animal` de `Especificaciones.md` con `new Animal("Rex")` y `hablar()` | `Rex hace ruido.` |
| El `Perro : Animal` con `hablar()` sobrescrito | `Toby ladra.`. **Test del despacho por la clase real del objeto** |
| `let a: Animal = new Perro("Toby"); print(a.hablar());` | `Toby ladra.` — la variable es `Animal` y gana `Perro`. *El test que prueba que el despacho no usa el tipo declarado* |
| `this.nombre` dentro de un método | devuelve el campo |
| `class A { let n: integer; }` y `print(new A().n)` | `0`, no `null`. *Test del valor por defecto por tipo* |
| `class A { let s: string; }` y `print(new A().s)` | cadena vacía |
| `class A { let otro: A; }` y `print(new A().otro)` | `null`: las clases sí arrancan en null |
| `new Perro("Toby") == new Perro("Toby")` | `false`. **Test de que `ObjectValue` no es `data class`** |
| `[1,2] == [1,2]` | `false`: `ArrayValue` tampoco |
| `1 == 1.0` | `true`: los numéricos se comparan como `Double` |

**Errores en ejecución:**

| Programa | Resultado esperado |
|---|---|
| `let lista = [1,2,3]; print(lista[10]);` | `RuntimeError` con *"índice fuera de rango"* y la línea |
| `let d: integer = 0; print(1 / d);` | `RuntimeError` con *"división entre cero"*. **No se usa `1 / 0` literal**: eso es error de compilación (ticket 4.2), así que nunca llega aquí |
| El `try/catch` de `Especificaciones.md` | imprime el mensaje del catch y **no** aborta |
| `return` dentro de un `try` | sale de la función; **no** lo captura el catch |

### Respaldo

Instrucción del catedrático: *"ya deberíamos poder ejecutar código normal"*.
`Especificaciones.md`, todos los ejemplos. Propiedad **realizable** del sistema de
tipos: los chequeos que no se pueden hacer estáticamente se hacen aquí.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 6.1 | Los valores en ejecución y el árbol de entornos |
| 6.2 | El intérprete completo, con closures, clases, control de flujo y errores dinámicos |

**Al terminar:** un `.cps` válido se ejecuta y su salida aparece en la consola del
IDE. Y los chequeos que el sistema de tipos no podía hacer estáticamente se hacen
aquí — que es la propiedad **realizable**, cerrada de punta a punta.
