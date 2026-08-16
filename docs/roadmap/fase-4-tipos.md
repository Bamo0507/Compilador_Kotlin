# Fase 4 — Pasada 2: verificación de tipos

**Objetivo de la fase:** recorrer el AST una segunda vez, ahora **entrando a los
cuerpos**, verificando que cada operación tenga sentido y **decorando** cada
expresión con su tipo y, cuando se puede, con su valor.

**Es el corazón del proyecto.** De los 60 puntos de "Analizador Sintáctico y
Semántico", la mayoría se juegan aquí.

**Al terminar:** cada nodo `Expression` del AST tiene su `type` puesto, las expresiones
constantes tienen su `constantValue` calculado, y los errores de tipo están
reportados con línea, columna, tipo esperado y tipo encontrado.

**Estimación:** cuatro o cinco sesiones. Es la fase más larga.

---

## La forma del recorrido: S-atribuida, evaluable en un solo pase

El verificador es una clase con **una función por construcción del lenguaje**. Cada
función de expresión **devuelve** el tipo de esa expresión:

```kotlin
class TypeChecker(
    private val globalScope: Scope,
    private val diagnostics: Diagnostics
) {
    // El cursor del recorrido, igual que en la Fase 3.
    private var currentScope: Scope = globalScope

    // El tipo de retorno de la funcion en la que estoy, o null si no estoy en una.
    // Existe porque el Scope NO guarda el tipo de retorno de su funcion: ese dato
    // vive en el Symbol de la funcion, que esta en el ambito PADRE.
    private var currentReturnType: Type? = null

    private val typeResolver = TypeResolver(globalScope, diagnostics)

    // La jerarquia de clases sale del arbol de ambitos, que es el unico que la
    // conoce. Ver ticket 4.1.
    private val typeRules = TypeRules { className ->
        globalScope.lookupLocal(className)?.memberScope?.superclass?.name
    }

    private fun checkExpression(expr: Expression): TypedValue = when (expr) {
        is Literal        -> checkLiteral(expr)
        is Identifier     -> checkIdentifier(expr)
        is BinaryOperation         -> checkBinaryOperation(expr)
        is UnaryOperation          -> checkUnaryOperation(expr)
        is TernaryOperation        -> checkTernaryOperation(expr)
        is FunctionCall           -> checkFunctionCall(expr)
        is IndexAccess          -> checkIndexAccess(expr)
        is PropertyAccess -> checkPropertyAccess(expr)
        is ObjectCreation            -> checkObjectCreation(expr)
        is ThisReference           -> checkThisReference(expr)
        is ArrayLiteral   -> checkArrayLiteral(expr)
        is AssignmentExpression     -> checkAssignmentExpression(expr)
    }
}
```

### La correspondencia con la teoría, que hay que poder explicar

| Concepto del curso | En el código |
|---|---|
| Atributo **sintetizado** (sube) | el **valor de retorno** de `checkX` |
| Atributo **heredado** (baja) | el **parámetro**, o el campo `currentScope` |
| **Regla** semántica (ecuación pura) | el `return` que calcula el tipo |
| **Acción** semántica (con efecto; la posición importa) | `declare(...)`, `report(...)`, abrir y cerrar ámbito |
| Recorrido DFS en postorden | la recursión `checkExpression(hijo)` antes del `return` |
| Pila de tablas de símbolos | el call stack de Kotlin + el cursor `currentScope` |
| Grafo de dependencias / orden topológico | **lo garantiza el postorden: no se construye en código** |

### ¿Qué clase de SDD es? La respuesta que hay que poder dar

**El sistema de tipos es una SDD S-atribuida.** Todo tipo es sintetizado: el tipo de
`a + b` depende **solo** de los tipos de `a` y `b`, que son sus hijos. Por eso es
evaluable en **un solo recorrido en postorden**.

El ámbito **no es un atributo, es un efecto**. Si se pasara como parámetro, sería un
atributo heredado y el conjunto sería L-atribuido. Manteniéndolo como campo mutable
del verificador (`currentScope`), con entrada y salida en las funciones que abren
ámbito, sigue siendo una **SDD S-atribuida con acciones semánticas**, que es la
formulación estándar.

Se elige el campo mutable, no el parámetro, por tres razones: mantiene la SDD
S-atribuida (más limpio de explicar), evita ensuciar la firma de las ~30 funciones,
y es lo que hace todo el mundo.

**Y por eso no hay que construir el grafo de dependencias de atributos**: el
postorden del recorrido ya es un orden topológico válido, porque cada nodo solo
depende de sus hijos.

### `TypedValue`: el tipo y, si se puede, el valor

```kotlin
// Lo que devuelve cada función de expresión.
//
// El tipo SIEMPRE viene. El valor solo cuando se puede calcular en tiempo de
// compilación: para `3 + 5` es 8, para `x + 5` es null.
data class TypedValue(
    val type: Type,
    val constant: Any? = null
) {
    val isConstant: Boolean get() = constant != null
}
```

**Esto es lo que da tres cosas en un solo recorrido:**

1. **Verificación de tipos** (el requisito principal).
2. **Plegado de constantes**: `print(3 + 5)` imprime `8` sin necesitar intérprete.
3. **El parche de la condición constante** para el análisis de flujo (Fase 5):
   `while (true)` y `while (1 == 1)` se detectan porque su condición tiene
   `constant == true`.

---

## Ticket 4.1 — `TypeRules` y el documento de reglas de inferencia

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `frontend/semantic/TypeRules.kt` (NUEVO)
- `docs/reglas-de-tipos.md` (NUEVO)
- `app/src/test/kotlin/org/compiler/TypeRulesTest.kt` (NUEVO)

**Qué es esto, en simple:** un archivo con **una función por regla de tipo**, sin
saber nada del AST ni de los ámbitos. Recibe tipos, devuelve tipos. Es la tabla de
verdad del lenguaje, aislada para poder leerla y probarla sola.

### La regla de inferencia y su función son el mismo texto en dos formatos

El catedrático pidió aplicar la notación de Cardelli. La correspondencia es exacta:

```
Γ ⊢ M : integer      Γ ⊢ N : integer
────────────────────────────────────
        Γ ⊢ (M − N) : integer
```

- Lo de **arriba de la línea** son las **premisas**: en el código, las llamadas
  recursivas a los hijos.
- Lo de **abajo** es la **conclusión**: en el código, el `return`.
- **Γ** (el contexto) es `currentScope`.

Cada función de `TypeRules.kt` lleva su regla en el comentario de encima. **El
documento y el código son el mismo texto en dos formatos**, y por eso no se pueden
desincronizar sin que se note.

### Los ayudantes de compatibilidad

```kotlin
// Lo mínimo que TypeRules necesita saber del mundo exterior: la jerarquía de clases,
// que hace falta para el subtipado. Se inyecta para que TypeRules no dependa de
// Scope y se pueda probar sin construir ámbitos.
fun interface ClassHierarchy {
    fun superclassOf(className: String): String?
}
```

```kotlin
// Las reglas de tipo del lenguaje. No conoce el AST ni los ámbitos:
// recibe tipos y devuelve tipos. Aislado para poder leerlo y probarlo solo.
//
// Es una CLASE y no un `object` porque el subtipado necesita la jerarquía inyectada,
// y un `object` no tiene constructor. Las alternativas eran pasar la jerarquía como
// parámetro en los ~15 sitios que llaman a isAssignable, o guardarla en un `var` del
// object — que violaría el principio 6 (nada de `object` con estado mutable).
class TypeRules(private val hierarchy: ClassHierarchy) {

    fun isNumeric(type: Type): Boolean =
        type == IntegerType || type == FloatType

    // El tipo más ancho de dos numéricos. integer + float = float.
    //
    // PRECONDICION: los dos son numéricos. Sin el require, widen(string, boolean)
    // devolvería IntegerType en silencio.
    fun widen(left: Type, right: Type): Type {
        require(isNumeric(left) && isNumeric(right)) {
            "widen solo aplica a tipos numéricos, recibió '${left.name}' y '${right.name}'"
        }
        return if (left == FloatType || right == FloatType) FloatType else IntegerType
    }

    // ¿Se puede guardar un valor de tipo `source` en algo declarado `target`?
    //
    // Es la regla más usada del compilador: asignaciones, argumentos de llamada,
    // valor de retorno, e inicializadores usan todos esta función.
    fun isAssignable(target: Type, source: Type): Boolean = when {
        // Un error ya reportado se acepta en silencio: corta cascadas.
        target == ErrorType || source == ErrorType -> true

        target == source -> true

        // Ensanchamiento: un integer cabe en un float. Al revés NO,
        // porque habría pérdida de precisión y el lenguaje no tiene casts.
        target == FloatType && source == IntegerType -> true

        // null solo cabe en clases y arreglos, no en los tipos simples.
        source == NullType && (target is ClassType || target is ArrayType) -> true

        // Un arreglo VACIO llega como ArrayType(NullType) y encaja con cualquier
        // arreglo: es el contexto el que dice de qué es.
        //   let notas: integer[] = [];
        source is ArrayType && source.element == NullType && target is ArrayType -> true

        // Subtipado: un Perro cabe donde se pide un Animal.
        target is ClassType && source is ClassType -> isSubclassOf(source, target)

        // Los arreglos NO son covariantes: ver la decisión abajo.
        else -> false
    }

    // El tipo común de dos ramas. Lo usan el ternario, el literal de arreglo y la
    // igualdad. Devuelve null si no hay tipo común: eso es un error.
    fun unify(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == right -> left
        isNumeric(left) && isNumeric(right) -> widen(left, right)
        left == NullType && (right is ClassType || right is ArrayType) -> right
        right == NullType && (left is ClassType || left is ArrayType) -> left
        left is ClassType && right is ClassType -> commonAncestor(left, right)

        // Recursivo en arreglos: el tipo común de integer[] y float[] es float[].
        // Sin esta rama, [[1, 2], [3.5, 4.0]] daría error sin razón.
        left is ArrayType && right is ArrayType ->
            unify(left.element, right.element)?.let { ArrayType(it) }

        else -> null
    }
}
```

`isSubclassOf` y `commonAncestor` son privadas y caminan la cadena de `hierarchy`.

Y el `TypeChecker` la construye desde el árbol de ámbitos, que es el único que
conoce la herencia:

```kotlin
private val typeRules = TypeRules { className ->
    globalScope.lookupLocal(className)?.memberScope?.superclass?.name
}
```

En los tests, una jerarquía falsa de una línea:

```kotlin
val rules = TypeRules { if (it == "Perro") "Animal" else null }
```

### Decisión: los arreglos no son covariantes

```cps
let a: float = 1;              // OK: ensanchamiento
let b: float[] = [1, 2];       // ERROR: `[1, 2]` es integer[], no float[]
```

Se siente inconsistente, pero **rechazarlo es lo correcto**. Si se permitiera, esto
sería legal:

```cps
let enteros: integer[] = [1, 2];
let vista: float[] = enteros;      // si los arreglos fueran covariantes
vista[0] = 1.5;                    // y ahora enteros[0] contiene un float
```

Es el agujero de los arreglos covariantes de Java, que ahí explota en ejecución con
`ArrayStoreException`. Se deja estricto: el arreglo se escribe con el tipo que se
quiere, `[1.0, 2.0]`.

### Las reglas de operadores

```kotlin
// ── Regla A1/A2/A3: aritmética ──────────────────────────────────────────
//
//   Γ ⊢ M : integer      Γ ⊢ N : integer      op ∈ {+ − * / %}
//   ──────────────────────────────────────────────────────────
//                  Γ ⊢ M op N : integer
//
//   Γ ⊢ M : τ₁   Γ ⊢ N : τ₂   τ₁,τ₂ ∈ {integer,float}   τ₁=float ∨ τ₂=float
//   op ∈ {+ − * /}
//   ────────────────────────────────────────────────────────────────────
//                        Γ ⊢ M op N : float
//
//   A3 (decisión documentada): % NO aplica a float. Solo integer.
fun arithmetic(op: BinaryOperator, left: Type, right: Type): Type? = when {
    left == ErrorType || right == ErrorType -> ErrorType
    op == BinaryOperator.MODULO ->
        if (left == IntegerType && right == IntegerType) IntegerType else null
    isNumeric(left) && isNumeric(right) -> widen(left, right)
    else -> null
}

// ── Regla C1: concatenación ─────────────────────────────────────────────
//
//   Γ ⊢ M : string      Γ ⊢ N : string
//   ──────────────────────────────────
//         Γ ⊢ M + N : string
//
// Solo el operador +, y solo si AMBOS lados son string. Se decide que
// "texto" + 5 sea ERROR y no concatenación implícita: el lenguaje no tiene
// conversión automática a string, y aceptarla escondería errores de tipo.
fun concatenation(op: BinaryOperator, left: Type, right: Type): Type? =
    if (op == BinaryOperator.ADD && left == StringType && right == StringType) StringType
    else null

// ── Regla L1: lógicos ───────────────────────────────────────────────────
//
//   Γ ⊢ M : boolean     Γ ⊢ N : boolean     op ∈ {&& ||}
//   ────────────────────────────────────────────────────
//               Γ ⊢ M op N : boolean
fun logical(left: Type, right: Type): Type? = when {
    left == ErrorType || right == ErrorType -> ErrorType
    left == BooleanType && right == BooleanType -> BooleanType
    else -> null
}

// ── Regla R1: relacionales ──────────────────────────────────────────────
//
//   Γ ⊢ M : τ₁   Γ ⊢ N : τ₂   (τ₁,τ₂ numéricos) ∨ (τ₁=τ₂=string)
//   op ∈ {< <= > >=}
//   ────────────────────────────────────────────────────────────
//                     Γ ⊢ M op N : boolean
fun relational(left: Type, right: Type): Type? = when {
    left == ErrorType || right == ErrorType -> ErrorType
    isNumeric(left) && isNumeric(right) -> BooleanType
    left == StringType && right == StringType -> BooleanType
    else -> null
}

// ── Regla E1: igualdad ──────────────────────────────────────────────────
//
//   Γ ⊢ M : τ₁    Γ ⊢ N : τ₂    comparables(τ₁, τ₂)
//   ───────────────────────────────────────────────
//              Γ ⊢ M == N : boolean
//
// Es más permisiva que la relacional: además de numéricos y strings, admite
// boolean con boolean, y null con cualquier clase o arreglo.
fun equality(left: Type, right: Type): Type? = when {
    left == ErrorType || right == ErrorType -> ErrorType
    unify(left, right) != null -> BooleanType
    else -> null
}

// ── Regla L2/N1: unarios ────────────────────────────────────────────────
//
//   Γ ⊢ M : boolean          Γ ⊢ M : τ    τ ∈ {integer, float}
//   ────────────────         ─────────────────────────────────
//   Γ ⊢ !M : boolean                  Γ ⊢ −M : τ
fun unary(op: UnaryOperator, operand: Type): Type? = when {
    operand == ErrorType -> ErrorType
    op == UnaryOperator.NOT    -> if (operand == BooleanType) BooleanType else null
    op == UnaryOperator.NEGATE -> if (isNumeric(operand)) operand else null
    else -> null
}
```

**Por qué todas devuelven `Type?` y no reportan errores:** `TypeRules` no conoce
`Diagnostics` ni las ubicaciones. Devuelve `null` cuando la combinación es inválida,
y el `TypeChecker` —que sí tiene el nodo con su `location`— arma el mensaje. Así las
reglas se prueban con tipos puros, sin construir un AST.

### `docs/reglas-de-tipos.md`

Una tabla con **una fila por regla**, tres columnas:

| Regla (notación de Cardelli) | Función | Test |
|---|---|---|
| A1 — aritmética entre enteros | `TypeRules.arithmetic` | `arithmetic entre integers da integer` |
| A2 — aritmética con ensanchamiento | `TypeRules.arithmetic` | `integer con float da float` |
| A3 — módulo solo entre enteros | `TypeRules.arithmetic` | `modulo con float es error` |
| C1 — concatenación de strings | `TypeRules.concatenation` | ... |
| L1 — operadores lógicos | `TypeRules.logical` | ... |
| ... | ... | ... |

Este es **el documento que se abre en la defensa**. Hace que la trazabilidad desde
la teoría hasta el test sea inmediata, y es exactamente lo que pidió el catedrático
al mencionar a Cardelli.

### Aceptación

Los tests se construyen con una jerarquía falsa de una línea, sin ámbitos:

```kotlin
val rules = TypeRules { if (it == "Perro") "Animal" else null }
```

- Una función por regla, cada una con su regla de inferencia en el comentario.
- `arithmetic(ADD, IntegerType, IntegerType)` devuelve `IntegerType`.
- `arithmetic(ADD, IntegerType, FloatType)` devuelve `FloatType`.
- `arithmetic(MODULO, FloatType, IntegerType)` devuelve `null`.
- `concatenation(ADD, StringType, IntegerType)` devuelve `null`.
- `isAssignable(FloatType, IntegerType)` es `true`;
  `isAssignable(IntegerType, FloatType)` es `false`.
- `isAssignable(ClassType("Animal"), ClassType("Perro"))` es `true` cuando `Perro`
  hereda de `Animal`; al revés es `false`.
- `isAssignable(cualquierCosa, ErrorType)` es `true` (corta cascadas).
- **`isAssignable(ArrayType(IntegerType), ArrayType(NullType))` es `true`**: es el
  caso de `let notas: integer[] = [];`, un arreglo vacío que toma su tipo del
  contexto.
- **`isAssignable(ArrayType(FloatType), ArrayType(IntegerType))` es `false`**: los
  arreglos no son covariantes, y hay un test que lo fija a propósito.
- `unify(IntegerType, FloatType)` devuelve `FloatType`.
- `unify(StringType, BooleanType)` devuelve `null`.
- **`unify(ArrayType(IntegerType), ArrayType(FloatType))` devuelve
  `ArrayType(FloatType)`**: es el caso de `[[1, 2], [3.5, 4.0]]`, y prueba que
  `unify` recursa en arreglos.
- **`widen(StringType, BooleanType)` lanza `IllegalArgumentException`**, no devuelve
  `IntegerType`: la precondición está verificada.
- `TypeRules` no es un `object`: dos instancias con jerarquías distintas dan
  respuestas distintas a `isAssignable(ClassType("Animal"), ClassType("Perro"))`.
- `docs/reglas-de-tipos.md` tiene una fila por función pública de `TypeRules`, y
  cada fila nombra un test que existe.

### Respaldo

Dragon Book §6.3 y §6.5. Cardelli, *Type Systems*: notación, reglas de tipo y
derivaciones. Notas de clase sobre reglas de inferencia.

---

## Ticket 4.2 — `TypeChecker`: literales, nombres y operadores

- **Estado**: pendiente
- **Depende de**: 3.2, 4.1

**Archivos:**

- `frontend/semantic/TypeChecker.kt` (NUEVO — parte de expresiones básicas)
- `app/src/test/kotlin/org/compiler/TypeCheckerExprTest.kt` (NUEVO)

### Los literales, y la validación de rango que el lexer no puede hacer

```kotlin
private fun checkLiteral(expr: Literal): TypedValue {
    // El valor de un literal SIEMPRE se conoce en compilación: es la base del
    // plegado de constantes.
    val value = expr.value

    // El AstBuilder guardó los enteros como Long para no perder información.
    // Aquí se valida el rango: 99999999999 es sintácticamente válido pero no
    // cabe en un integer. El lexer entrega la FORMA; esta fase decide el VALOR.
    if (expr.literalType == IntegerType && value is Long) {
        if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
            report(expr, "El literal entero '$value' no cabe en un integer")
            return decorate(expr, TypedValue(ErrorType))
        }
    }

    return decorate(expr, TypedValue(expr.literalType, value))
}

// Escribe el tipo y el valor en el nodo del AST, y devuelve el TypedValue.
// "Decorar el árbol" es exactamente esto, y pasa por aquí una sola vez.
private fun decorate(expr: Expression, value: TypedValue): TypedValue {
    expr.type = value.type
    expr.constantValue = value.constant
    return value
}
```

### Los nombres

```kotlin
private fun checkIdentifier(expr: Identifier): TypedValue {
    val symbol = currentScope.lookup(expr.name)

    if (symbol == null) {
        report(expr, "La variable '${expr.name}' no está declarada")
        return decorate(expr, TypedValue(ErrorType))
    }

    // Se guarda el simbolo resuelto en el nodo. Es la unica vez que se resuelve este
    // nombre en todo el compilador: las fases 5 y 6, la GUI y la generacion de
    // codigo lo leen de aqui. Ver ticket 1.4.
    expr.resolvedSymbol = symbol

    // Los contadores de vivacidad se llevan AQUI y solo aqui. La Fase 5 ya no
    // recorre el AST — formatea lo que esta en el Symbol.
    symbol.useCount += 1
    symbol.lastUseLine = expr.location.line

    // Captura: el uso esta dentro de una funcion MAS anidada que la declaracion, y
    // la declaracion no es global (un global no se captura, vive todo el programa).
    //
    // Se calcula aqui porque `currentScope` esta a mano; sin esto haria falta un
    // segundo recorrido con su propio cursor de ambito, y mantener los dos cursores
    // sincronizados era una fuente de bugs silenciosos.
    if (currentScope.functionDepth() > symbol.declarationFunctionDepth &&
        symbol.declarationFunctionDepth > 0
    ) {
        symbol.usedInNestedFunction = true
    }

    if (!symbol.initialized && symbol.kind != DeclarationKind.FUNCTION) {
        report(expr, "La variable '${expr.name}' se usa antes de tener un valor")
    }

    // El valor solo se propaga desde CONSTANTES. Una `const` no se puede reasignar
    // —el verificador lo reporta como error—, asi que su valor es el mismo en todo
    // el programa y plegarlo nunca puede mentir.
    //
    // Para una `let` NO se propaga, aunque su valor inicial se conozca: bastaria un
    // `if (cond) { x = 10; }` para que el plegado diera una respuesta que depende de
    // cond. El valor de una variable mutable lo da el interprete (Fase 6), que si
    // ejecuta el programa en vez de recorrer el texto.
    val constant = if (symbol.kind == DeclarationKind.CONSTANT) symbol.constantValue
                   else null

    return decorate(expr, TypedValue(symbol.type, constant))
}
```

**El `constantValue` de una `const`** lo escribe `checkVariableDeclaration` cuando el
inicializador es constante, y exige un campo más en `Symbol`:

```kotlin
// En Symbol, junto a los campos de análisis:
//
// El valor de una CONSTANTE, si se conoce en compilación. Solo se llena para
// kind == CONSTANT: una variable mutable no tiene un valor único.
var constantValue: Any? = null
```

Con eso desaparece la limitación que la Fase 5 tenía documentada:

```cps
const SIEMPRE: boolean = true;

function f(): integer {
  while (SIEMPRE) { return 1; }
  // sin propagacion: "hay caminos que no retornan"  <- falso positivo
  // con propagacion: SIEMPRE se pliega a true, el bucle es infinito, correcto
}
```

### Los operadores binarios: la regla, el error y el plegado

```kotlin
private fun checkBinaryOperation(expr: BinaryOperation): TypedValue {
    // PREMISAS: los dos hijos, en postorden.
    val left = checkExpression(expr.left)
    val right = checkExpression(expr.right)

    // La regla que aplica depende del GRUPO del operador. Cuatro grupos, cuatro
    // reglas. El `when` es exhaustivo porque OperatorGroup es un enum.
    val resultType: Type? = when (expr.operator.group) {
        OperatorGroup.ARITHMETIC ->
            // El + es el único sobrecargado: numérico o concatenación.
            typeRules.arithmetic(expr.operator, left.type, right.type)
                ?: typeRules.concatenation(expr.operator, left.type, right.type)
        OperatorGroup.LOGICAL    -> typeRules.logical(left.type, right.type)
        OperatorGroup.RELATIONAL -> typeRules.relational(left.type, right.type)
        OperatorGroup.EQUALITY   -> typeRules.equality(left.type, right.type)
    }

    if (resultType == null) {
        report(expr,
            "El operador '${expr.operator.symbol}' no se puede aplicar a " +
            "'${left.type.name}' y '${right.type.name}'"
        )
        return decorate(expr, TypedValue(ErrorType))
    }

    // Division o modulo entre CERO con divisor constante: es error de compilacion.
    if (isDivisionByZero(expr.operator, right)) {
        report(expr.right,
            "No se puede dividir entre cero"
        )
        return decorate(expr, TypedValue(ErrorType))
    }

    // CONCLUSIÓN: el tipo, más el valor si ambos operandos son constantes.
    return decorate(expr, TypedValue(
        type = resultType,
        constant = foldBinaryOperation(expr.operator, left, right, resultType)
    ))
}

// ¿El divisor es una constante que vale cero?
//
// Solo se puede afirmar cuando el divisor es CONSTANTE. Con `a / b` donde b es una
// variable es imposible saberlo estaticamente, y ese caso va al chequeo dinamico del
// interprete (Fase 6).
//
// Esa division del trabajo ES la propiedad REALIZABLE del sistema de tipos, con las
// dos mitades implementadas: lo verificable en compilacion se verifica aqui, y lo
// que no, en ejecucion.
private fun isDivisionByZero(op: BinaryOperator, right: TypedValue): Boolean {
    if (op != BinaryOperator.DIVIDE && op != BinaryOperator.MODULO) return false

    return when (val divisor = right.constant) {
        is Long   -> divisor == 0L
        is Double -> divisor == 0.0
        else      -> false          // no es constante: no se puede afirmar nada
    }
}
```

**Nota sobre el falso positivo aceptado:** `if (false) { print(1 / 0); }` se reporta
como error aunque nunca se ejecute, porque el verificador recorre el cuerpo del `if`
sin evaluar la condición. Es el mismo comportamiento de `javac`, y es información
útil: código inalcanzable que además reventaría.

### El plegado de constantes

```kotlin
// Calcula el valor de la operación si AMBOS operandos son constantes.
// Devuelve null si alguno no lo es: entonces el valor solo se sabe en ejecución.
private fun foldBinaryOperation(
    op: BinaryOperator,
    left: TypedValue,
    right: TypedValue,
    resultType: Type
): Any? {
    if (!left.isConstant || !right.isConstant) return null
    if (resultType == ErrorType) return null

    return when (op) {
        // ── Aritmeticos ──────────────────────────────────────────────
        // El + es el unico que puede dar string: es la concatenacion.
        BinaryOperator.ADD -> when {
            resultType == StringType -> "${left.constant}${right.constant}"
            resultType == FloatType  -> asDouble(left) + asDouble(right)
            else                     -> asLong(left) + asLong(right)
        }
        BinaryOperator.SUBTRACT ->
            if (resultType == FloatType) asDouble(left) - asDouble(right)
            else asLong(left) - asLong(right)

        BinaryOperator.MULTIPLY ->
            if (resultType == FloatType) asDouble(left) * asDouble(right)
            else asLong(left) * asLong(right)

        // Aqui ya es seguro dividir: si el divisor fuera una constante cero,
        // checkBinaryOperation reporto el error y no llego a plegar.
        BinaryOperator.DIVIDE ->
            if (resultType == FloatType) asDouble(left) / asDouble(right)
            else asLong(left) / asLong(right)

        // El modulo solo aplica a enteros (regla A3), asi que no hay caso float.
        BinaryOperator.MODULO -> asLong(left) % asLong(right)

        // ── Igualdad ─────────────────────────────────────────────────
        BinaryOperator.EQUAL     -> areConstantsEqual(left, right)
        BinaryOperator.NOT_EQUAL -> !areConstantsEqual(left, right)

        // ── Relacionales ─────────────────────────────────────────────
        // Se comparan como Double cuando son numericos, y con compareTo cuando son
        // strings. TypeRules.relational ya garantizo que son uno de los dos casos.
        BinaryOperator.LESS          -> compareConstants(left, right) < 0
        BinaryOperator.LESS_EQUAL    -> compareConstants(left, right) <= 0
        BinaryOperator.GREATER       -> compareConstants(left, right) > 0
        BinaryOperator.GREATER_EQUAL -> compareConstants(left, right) >= 0

        // ── Logicos ──────────────────────────────────────────────────
        BinaryOperator.AND -> asBoolean(left) && asBoolean(right)
        BinaryOperator.OR  -> asBoolean(left) || asBoolean(right)
    }
}
```

**El `when` no tiene `else`**, y eso es a propósito: `BinaryOperator` es un enum, así
que si se agrega un operador y se olvida su plegado, **Kotlin no compila**. Es el
principio 5 del proyecto trabajando.

### Los ayudantes de conversión, y la trampa que resuelven

```kotlin
// El AstBuilder guarda los enteros como Long y los flotantes como Double. Cuando la
// operacion mezcla los dos, el resultado es float y hay que convertir el Long:
//
//   1 + 2.5   ->  left.constant es 1L, right.constant es 2.5
//
// Sin la conversion, `left.constant as Double` lanza ClassCastException sobre el 1L.
// Es el bug que aparece con el primer `integer + float` del proyecto.
private fun asDouble(value: TypedValue): Double = when (val constant = value.constant) {
    is Double -> constant
    is Long   -> constant.toDouble()
    else      -> error("Se esperaba un numero constante, no '$constant'")
}

private fun asLong(value: TypedValue): Long = when (val constant = value.constant) {
    is Long -> constant
    else    -> error("Se esperaba un entero constante, no '$constant'")
}

private fun asBoolean(value: TypedValue): Boolean = when (val constant = value.constant) {
    is Boolean -> constant
    else       -> error("Se esperaba un booleano constante, no '$constant'")
}

// El == de Kotlin dice que 1L != 1.0, porque son tipos distintos. Pero en Compiscript
// `1 == 1.0` debe ser TRUE: la regla E1 los considera comparables.
//
// Si los dos son numericos se comparan como Double; si no, con el == normal (cubre
// string con string, boolean con boolean, y null con null).
private fun areConstantsEqual(left: TypedValue, right: TypedValue): Boolean =
    if (isNumericConstant(left) && isNumericConstant(right)) {
        asDouble(left) == asDouble(right)
    } else {
        left.constant == right.constant
    }

// Devuelve <0, 0 o >0, como compareTo. Solo se llama cuando TypeRules.relational
// dijo que la comparacion aplica, o sea con dos numericos o dos strings.
private fun compareConstants(left: TypedValue, right: TypedValue): Int =
    if (isNumericConstant(left) && isNumericConstant(right)) {
        asDouble(left).compareTo(asDouble(right))
    } else {
        (left.constant as String).compareTo(right.constant as String)
    }

private fun isNumericConstant(value: TypedValue): Boolean =
    value.constant is Long || value.constant is Double
```

**Los `error(...)` de los tres primeros son a propósito.** No son errores del usuario:
si `asLong` recibe un string, es un **bug del compilador** — significa que
`TypeRules` aprobó una combinación que no debía. Un `error(...)` lo hace ruidoso en
vez de silencioso, y ese es el criterio del proyecto: las excepciones son para bugs
propios, `Diagnostics` es para errores del usuario.

### El ayudante que reporta

```kotlin
// Arma el SemanticError con la ubicacion del nodo y lo manda a Diagnostics.
//
// Toma un Node y no una posicion para que el llamador nunca tenga que sacar la
// ubicacion a mano: `report(expr.right, ...)` apunta al operando, `report(expr, ...)`
// a la expresion completa. Elegir bien el nodo es lo que hace que el subrayado del
// IDE caiga donde debe.
private fun report(node: Node, message: String) {
    diagnostics.report(CompilerError.SemanticError(node.location, message))
}
```

### El unario

```kotlin
// ── Regla L2/N1 ─────────────────────────────────────────────────────
//   Γ ⊢ M : boolean          Γ ⊢ M : τ    τ ∈ {integer, float}
//   ────────────────         ─────────────────────────────────
//   Γ ⊢ !M : boolean                  Γ ⊢ −M : τ
private fun checkUnaryOperation(expr: UnaryOperation): TypedValue {
    val operand = checkExpression(expr.operand)

    val resultType = typeRules.unary(expr.operator, operand.type)

    if (resultType == null) {
        report(expr,
            "El operador '${expr.operator.symbol}' no se puede aplicar a " +
            "'${operand.type.name}'"
        )
        return decorate(expr, TypedValue(ErrorType))
    }

    val folded = when {
        !operand.isConstant -> null
        expr.operator == UnaryOperator.NOT    -> !asBoolean(operand)
        operand.constant is Double            -> -asDouble(operand)
        else                                  -> -asLong(operand)
    }

    return decorate(expr, TypedValue(resultType, folded))
}
```

**Por qué el plegado vale la pena aunque no lo pidan:**

1. `print(3 + 5)` imprime `8` sin necesitar el intérprete.
2. Resuelve el falso positivo de `while (1 == 1)` en el análisis de flujo (Fase 5):
   la condición se pliega a `true` y el bucle se reconoce como infinito.
3. Permite detectar `lista[-1]` y la división entre cero constante en compilación
   (propiedad **realizable**).

Y sale casi gratis, porque el recorrido que lo calcula es el mismo que ya verifica
los tipos.

### El ternario, con la unificación de ramas

```kotlin
// ── Regla T1 ────────────────────────────────────────────────────────
//   Γ ⊢ C : boolean   Γ ⊢ M : τ₁   Γ ⊢ N : τ₂   unify(τ₁,τ₂) = τ
//   ──────────────────────────────────────────────────────────────
//                    Γ ⊢ C ? M : N  :  τ
private fun checkTernaryOperation(expr: TernaryOperation): TypedValue {
    val condition = checkExpression(expr.condition)
    val ifTrue = checkExpression(expr.ifTrue)
    val ifFalse = checkExpression(expr.ifFalse)

    if (condition.type != BooleanType && condition.type != ErrorType) {
        report(expr.condition,
            "La condición del operador ternario debe ser boolean, " +
            "no '${condition.type.name}'")
    }

    val resultType = typeRules.unify(ifTrue.type, ifFalse.type)
    if (resultType == null) {
        report(expr,
            "Las dos ramas del ternario tienen tipos incompatibles: " +
            "'${ifTrue.type.name}' y '${ifFalse.type.name}'")
        return decorate(expr, TypedValue(ErrorType))
    }

    // Si la condición es constante, se conoce qué rama se toma.
    val folded = when (condition.constant) {
        true  -> ifTrue.constant
        false -> ifFalse.constant
        else  -> null
    }

    return decorate(expr, TypedValue(resultType, folded))
}
```

### El literal de arreglo

```kotlin
// ── Regla AL1 ───────────────────────────────────────────────────────
//   Γ ⊢ Eᵢ : τᵢ  ∀i     unify(τ₁..τₙ) = τ
//   ──────────────────────────────────────
//         Γ ⊢ [E₁,...,Eₙ] : τ[]
private fun checkArrayLiteral(expr: ArrayLiteral): TypedValue {
    if (expr.elements.isEmpty()) {
        // Un arreglo vacío no dice de qué es. Se marca con un tipo especial que
        // encaja con cualquier arreglo al asignarse; si no hay contexto que lo
        // determine, es error.
        return decorate(expr, TypedValue(ArrayType(NullType)))
    }

    val elementTypes = expr.elements.map { checkExpression(it).type }

    // Unificar de a pares, de izquierda a derecha.
    val unified = elementTypes.reduce { accumulated, next ->
        typeRules.unify(accumulated, next) ?: run {
            report(expr,
                "Los elementos de la lista tienen tipos incompatibles: " +
                "'${accumulated.name}' y '${next.name}'")
            ErrorType
        }
    }

    return decorate(expr, TypedValue(ArrayType(unified)))
}
```

### Aceptación

Un caso válido y uno inválido por cada regla:

| Entrada | Resultado esperado |
|---|---|
| `1 + 2` | tipo `integer`, valor `3` |
| `1 + 2.5` | tipo `float`, valor `3.5` |
| `10 - 3 - 2` | tipo `integer`, valor `5` (verifica el plegado a la izquierda de la Fase 2) |
| `5 % 2` | tipo `integer`, valor `1` |
| `5.0 % 2` | **error**: el módulo solo aplica a enteros |
| `"a" + "b"` | tipo `string`, valor `"ab"` |
| `"a" + 1` | **error**: `+` no aplica a `string` e `integer` |
| `true && false` | tipo `boolean`, valor `false` |
| `1 && true` | **error** |
| `1 < 2` | tipo `boolean`, valor `true` |
| `true < false` | **error** |
| `1 == 1.0` | tipo `boolean` |
| `1 == "a"` | **error** |
| `!true` | tipo `boolean`, valor `false` |
| `-5` | tipo `integer`, valor `-5` |
| `-2.5` | tipo `float`, valor `-2.5` |
| `!5` | **error** |
| `10 / 0` | **error**: no se puede dividir entre cero |
| `10 % 0` | **error**: el módulo entre cero falla igual |
| `10 / x` con `x: integer` | **válido**: el divisor no es constante, el chequeo va al intérprete |
| `1 == 1.0` | tipo `boolean`, valor **`true`**. *El `==` de Kotlin diría `false` porque `1L != 1.0`* |
| `"a" < "b"` | tipo `boolean`, valor `true` |
| `2 < 1.5` | tipo `boolean`, valor `false` |
| `true ? 1 : 2` | tipo `integer`, valor `1` |
| `1 ? 1 : 2` | **error**: la condición no es boolean |
| `true ? 1 : "a"` | **error**: ramas incompatibles |
| `[1, 2, 3]` | tipo `integer[]` |
| `[1, "a"]` | **error**: elementos incompatibles |
| `x` sin declarar | **error**: variable no declarada |
| `99999999999` | **error**: no cabe en un integer |

Y el que prueba que las cascadas se cortan:

- **`(1 + "a") * 2` produce EXACTAMENTE UN error**, no dos.

Y estos dos, que son estructurales:

- Un `when (op)` sobre `BinaryOperator` en `foldBinaryOperation` **sin rama `else`**
  compila: los trece operadores tienen su plegado.
- Después de verificar una expresión, `expr.type != null` siempre.

### Respaldo

Dragon Book §6.5.1 y §6.5.2. Enunciado, *"Sistema de Tipos"*.

---

## Ticket 4.3 — `TypeChecker`: llamadas, clases, `this` y arreglos

- **Estado**: pendiente
- **Depende de**: 4.2

**Archivos:**

- `frontend/semantic/TypeChecker.kt` (continuar)
- `app/src/test/kotlin/org/compiler/TypeCheckerCallsTest.kt` (NUEVO)

### Llamadas: función suelta y método, un solo camino

Recordar de la Fase 2: `perro.hablar()` produce
`FunctionCall(callee = PropertyAccess(perro, "hablar"), arguments = [])`. **La llamada a
método no es un nodo aparte**, y eso resulta una ventaja: `checkFunctionCall` no
distingue casos. Verifica el callee con `checkExpression`, y si es un método,
`checkPropertyAccess` ya devolvió su `FunctionType`.

```kotlin
// ── Regla F1 ────────────────────────────────────────────────────────
//   Γ ⊢ f : (τ₁,...,τₙ) → τ    Γ ⊢ Aᵢ : σᵢ    asignable(τᵢ, σᵢ) ∀i
//   ──────────────────────────────────────────────────────────────
//                    Γ ⊢ f(A₁,...,Aₙ) : τ
private fun checkFunctionCall(expr: FunctionCall): TypedValue {
    val calleeType = checkExpression(expr.callee).type

    // Corta la cascada: si el callee ya falló, no reportar de nuevo.
    if (calleeType == ErrorType) {
        expr.arguments.forEach { checkExpression(it) }
        return decorate(expr, TypedValue(ErrorType))
    }

    // "verificación de sentido semántico en expresiones (no multiplicar
    // funciones)" del enunciado: solo se puede llamar a algo que ES una función.
    if (calleeType !is FunctionType) {
        report(expr, "'${describeCallee(expr.callee)}' no es una función")
        expr.arguments.forEach { checkExpression(it) }
        return decorate(expr, TypedValue(ErrorType))
    }

    checkArguments(expr, calleeType.parameters, expr.arguments)
    return decorate(expr, TypedValue(calleeType.returns))
}

// El nombre legible de lo que se intento llamar, para el mensaje de error.
//
// Existe porque `expr.callee` es un nodo, no un texto, y el usuario necesita leer
// QUE cosa no era una funcion:
//   f * 2      ->  "f"
//   perro.x()  ->  "perro.x"
//   5()        ->  "la expresion"
private fun describeCallee(callee: Expression): String = when (callee) {
    is Identifier     -> callee.name
    is PropertyAccess -> "${describeCallee(callee.target)}.${callee.propertyName}"
    is ThisReference  -> "this"
    else              -> "la expresión"
}

// La validación de argumentos se comparte entre las llamadas y `new`.
private fun checkArguments(
    node: Expression,
    expected: List<Type>,
    arguments: List<Expression>
) {
    val actual = arguments.map { checkExpression(it).type }

    if (actual.size != expected.size) {
        report(node,
            "Se esperaban ${expected.size} argumentos y se recibieron ${actual.size}")
        return
    }

    // Coincidencia POSICIONAL, como pide el enunciado.
    expected.zip(actual).forEachIndexed { index, (expectedType, actualType) ->
        if (!typeRules.isAssignable(expectedType, actualType)) {
            report(arguments[index],
                "El argumento ${index + 1} debe ser '${expectedType.name}', " +
                "no '${actualType.name}'")
        }
    }
}
```

### Acceso a propiedad, con herencia

```kotlin
// ── Regla P1 ────────────────────────────────────────────────────────
//   Γ ⊢ M : ClassType(C)    miembro x : τ ∈ ámbito(C) ∪ ámbito(super(C))
//   ────────────────────────────────────────────────────────────────────
//                            Γ ⊢ M.x : τ
private fun checkPropertyAccess(expr: PropertyAccess): TypedValue {
    val targetType = checkExpression(expr.target).type

    if (targetType == ErrorType) return decorate(expr, TypedValue(ErrorType))

    if (targetType !is ClassType) {
        report(expr,
            "No se puede acceder a '.${expr.propertyName}' sobre " +
            "'${targetType.name}': no es un objeto")
        return decorate(expr, TypedValue(ErrorType))
    }

    val classScope = classScopeOf(targetType.className)
    // lookupMember busca en la clase y sus superclases, pero NO sale al ámbito
    // exterior: un campo heredado sí, una variable global no.
    val member = classScope?.lookupMember(expr.propertyName)

    if (member == null) {
        report(expr,
            "La clase '${targetType.className}' no tiene un miembro " +
            "llamado '${expr.propertyName}'")
        return decorate(expr, TypedValue(ErrorType))
    }

    // Mismo criterio que en checkIdentifier: se guarda el miembro resuelto y se
    // llevan los contadores aqui, una sola vez.
    expr.resolvedMember = member
    member.useCount += 1
    member.lastUseLine = expr.location.line

    return decorate(expr, TypedValue(member.type))
}

// El ambito de miembros de una clase, a partir de su nombre.
//
// Es el salto que la decision 2 del ticket 1.1 obliga a dar: ClassType guarda SOLO
// el nombre para no crear un ciclo Type -> Scope -> Symbol -> Type, asi que llegar a
// los miembros cuesta pasar por el Symbol de la clase.
//
// lookupLocal y no lookup: las clases viven solo en el ambito global.
private fun classScopeOf(className: String): Scope? =
    globalScope.lookupLocal(className)?.memberScope
```

### La sobrescritura de métodos: el agujero que hay que cerrar

Sin esta verificación, lo siguiente **pasa sin error**:

```cps
class Animal {
  function hablar(): string { return "ruido"; }
}
class Perro : Animal {
  function hablar(): integer { return 5; }    // sobrescribe con OTRA firma
}
```

`Scope.declare` solo mira `symbols[name]` del **nivel local**, así que declarar
`hablar` en `Perro` no choca con el de `Animal`: la sobrescritura se permite con
**cualquier** firma. Y eso rompe el subtipado:

```cps
let a: Animal = new Perro();
let s: string = a.hablar();     // el verificador dice string (consulta Animal)
                                // en ejecucion devuelve 5
```

**El verificador aprueba y el intérprete devuelve otro tipo.** Es el único agujero de
sanidad que quedaba en el sistema de tipos.

```kotlin
// La firma de un metodo que sobrescribe debe ser IGUAL a la de la superclase.
//
// Comparar los FunctionType con == alcanza, porque los data class comparan por
// estructura: es la decision 9 del README.
//
// Lo llama checkFunctionDeclaration (ticket 4.4) cuando currentScope es una clase, y
// NO la Fase 3: ahi la superclase puede no tener sus miembros registrados todavia si
// se declara mas abajo en el archivo, y la verificacion se saltaria en silencio.
private fun checkOverride(decl: FunctionDeclaration, classScope: Scope) {
    val inherited = classScope.superclass?.lookupMember(decl.name) ?: return
    val ownType = classScope.lookupLocal(decl.name)?.type ?: return

    if (ownType != inherited.type) {
        report(decl,
            "El método '${decl.name}' sobrescribe el de la superclase con otra " +
            "firma: se esperaba '${inherited.type.name}' y es '${ownType.name}'"
        )
    }
}
```

### `new` y el constructor

```kotlin
// ── Regla N1 ────────────────────────────────────────────────────────
//   clase C ∈ Γ   constructor(C) : (τ₁,...,τₙ) → void   asignable(τᵢ, σᵢ)
//   ─────────────────────────────────────────────────────────────────────
//               Γ ⊢ new C(A₁,...,Aₙ) : ClassType(C)
private fun checkObjectCreation(expr: ObjectCreation): TypedValue {
    // lookupLocal y no lookup: las clases viven solo en el ambito global.
    val classSymbol = globalScope.lookupLocal(expr.className)

    if (classSymbol == null || classSymbol.kind != DeclarationKind.CLASS) {
        report(expr, "La clase '${expr.className}' no está declarada")
        expr.arguments.forEach { checkExpression(it) }
        return decorate(expr, TypedValue(ErrorType))
    }

    val classScope = classSymbol.memberScope!!

    // El constructor se reconoce POR NOMBRE: la gramatica no tiene sintaxis propia
    // para el, es una `function` que se llama "constructor". La constante vive en
    // Symbol.kt, compartida con la Fase 3 (ticket 3.2).
    //
    // lookupMember y no lookupLocal: el constructor SI se hereda cuando la clase no
    // declara uno propio.
    //
    // Es al reves que en Java, y por una razon concreta: Compiscript no tiene `super`,
    // asi que una subclase sin constructor propio no tendria NINGUNA forma de
    // inicializar los campos heredados. Es lo que hace funcionar el ejemplo de
    // Especificaciones.md:
    //
    //   class Perro : Animal { function hablar(): string { ... } }
    //   let perro: Perro = new Perro("Toby");     <- usa el constructor de Animal
    val constructor = classScope.lookupMember(CONSTRUCTOR_NAME)
    val expectedParameters = (constructor?.type as? FunctionType)?.parameters
        ?: emptyList()   // ni propio ni heredado: constructor implicito de cero parametros

    checkArguments(expr, expectedParameters, expr.arguments)

    return decorate(expr, TypedValue(ClassType(expr.className)))
}
```

### `this`

```kotlin
// ── Regla TH1 ───────────────────────────────────────────────────────
//   dentro de la clase C
//   ─────────────────────────
//   Γ ⊢ this : ClassType(C)
private fun checkThisReference(expr: ThisReference): TypedValue {
    val classScope = currentScope.enclosingClass()

    if (classScope == null) {
        report(expr, "'this' solo se puede usar dentro de una clase")
        return decorate(expr, TypedValue(ErrorType))
    }

    return decorate(expr, TypedValue(ClassType(classScope.name)))
}
```

### Indexación, con la validación de índice constante

```kotlin
// ── Regla IX1 ───────────────────────────────────────────────────────
//   Γ ⊢ A : τ[]    Γ ⊢ I : integer
//   ──────────────────────────────
//            Γ ⊢ A[I] : τ
private fun checkIndexAccess(expr: IndexAccess): TypedValue {
    val targetType = checkExpression(expr.target).type
    val index = checkExpression(expr.index)

    if (targetType == ErrorType) return decorate(expr, TypedValue(ErrorType))

    if (targetType !is ArrayType) {
        report(expr, "No se puede indexar sobre '${targetType.name}': no es una lista")
        return decorate(expr, TypedValue(ErrorType))
    }

    if (index.type != IntegerType && index.type != ErrorType) {
        report(expr.index,
            "El índice debe ser integer, no '${index.type.name}'")
    }

    // Propiedad REALIZABLE del sistema de tipos:
    // lo que se puede verificar estáticamente, se verifica aquí.
    // Un índice negativo constante es error de compilación.
    // Un índice variable NO se puede verificar: va al chequeo dinámico
    // del intérprete (Fase 6).
    val constantIndex = index.constant
    if (constantIndex is Long && constantIndex < 0) {
        report(expr.index, "El índice no puede ser negativo: $constantIndex")
    }

    return decorate(expr, TypedValue(targetType.element))
}
```

### Aceptación

| Entrada | Resultado esperado |
|---|---|
| `function f(a: integer): integer {...}` y `f(1)` | tipo `integer` |
| `f(1, 2)` | **error**: se esperaba 1 argumento, se recibieron 2 |
| `f("a")` | **error**: el argumento 1 debe ser integer |
| `f(1) * 2` con `f` devolviendo integer | tipo `integer` |
| `f * 2` (la función, no la llamada) | **error**: `f` no es un valor multiplicable |
| `let x: integer = 1; x();` | **error**: `x` no es una función |
| `perro.nombre` con el campo declarado en `Perro` | el tipo del campo |
| `perro.nombre` con el campo heredado de `Animal` | el tipo del campo. **Test obligatorio** |
| `perro.noExiste` | **error**: la clase no tiene ese miembro |
| `perro.hablar()` | el tipo de retorno del método |
| `(5).campo` | **error**: `integer` no es un objeto |
| `new Perro("Toby")` con constructor de 1 string | tipo `Perro` |
| `new Perro()` con constructor de 1 parámetro | **error**: se esperaba 1 argumento |
| `new Animal()` sin constructor declarado | tipo `Animal`, sin error |
| `class Perro : Animal { }` sin constructor propio, y `new Perro("Toby")` | **válido**: hereda el de `Animal`. *Test que sostiene el programa de la demo* |
| `class Perro : Animal { }` y `new Perro()` con `Animal` pidiendo un string | **error**: se esperaba 1 argumento |
| `new NoExiste()` | **error**: clase no declarada |
| `this` dentro de un método | el tipo de la clase |
| `this` fuera de una clase | **error** |
| `nombre` sin `this.` dentro de un método | el tipo del campo: la cadena de ámbitos lo encuentra |
| `f * 2` con `f` una función | **error**: *"'f' no es una función"*… mejor dicho, no es un valor multiplicable. El mensaje nombra `f` gracias a `describeCallee` |
| `class Perro : Animal` con `hablar(): integer` sobre `hablar(): string` | **error**: sobrescribe con otra firma. **Test del agujero de sanidad** |
| `class Perro : Animal` con `hablar(): string` igual que el padre | válido |
| `class Perro : Animal` con un método nuevo que el padre no tiene | válido |
| `class A { function constructor(): integer { } }` | **error**: el constructor no declara tipo de retorno |
| `lista[0]` con `lista: integer[]` | tipo `integer` |
| `lista["a"]` | **error**: el índice debe ser integer |
| `lista[-1]` | **error**: índice negativo |
| `x[0]` con `x: integer` | **error**: no es una lista |
| `matriz[0][1]` con `matriz: integer[][]` | tipo `integer` |

### Respaldo

Enunciado: *"Funciones y Procedimientos"*, *"Clases y Objetos"*, *"Listas y
Estructuras de Datos"*. Dragon Book §6.5.

---

## Ticket 4.4 — `TypeChecker`: sentencias y ámbitos

- **Estado**: pendiente
- **Depende de**: 4.3

**Archivos:**

- `frontend/semantic/TypeChecker.kt` (continuar)
- `app/src/test/kotlin/org/compiler/TypeCheckerStmtTest.kt` (NUEVO)

**Qué se hace:** las funciones de sentencia. Son las que **abren y cierran los
ámbitos**, y las que verifican las reglas de control de flujo.

### El punto de entrada y el despachador

```kotlin
// El punto de entrada de la Pasada 2. Lo llama el pipeline (ticket 7.1).
fun check(program: Program) {
    program.statements.forEach { checkStatement(it) }
}

// El despachador. El `when` es exhaustivo porque Statement es sealed: si se agrega
// una sentencia nueva y se olvida su funcion, Kotlin no compila.
private fun checkStatement(stmt: Statement) = when (stmt) {
    is VariableDeclaration  -> checkVariableDeclaration(stmt)
    is FunctionDeclaration  -> checkFunctionDeclaration(stmt)
    is ClassDeclaration     -> checkClassDeclaration(stmt)
    is Assignment           -> checkAssignment(stmt)
    is ExpressionStatement  -> checkExpressionStatement(stmt)
    is Print                -> checkPrint(stmt)
    is Block                -> checkBlock(stmt)
    is If                   -> checkIfStatement(stmt)
    is While                -> checkWhileStatement(stmt)
    is DoWhile              -> checkDoWhileStatement(stmt)
    is For                  -> checkForStatement(stmt)
    is ForEach              -> checkForEach(stmt)
    is Switch               -> checkSwitch(stmt)
    is TryCatch             -> checkTryCatch(stmt)
    is Return               -> checkReturn(stmt)

    // break y continue no tienen NADA que verificar aqui: su unica regla es de
    // ubicacion ("solo dentro de un bucle"), y eso lo valida el FlowAnalyzer de la
    // Fase 5. Estan en el `when` para que sea exhaustivo.
    is Break, is Continue   -> Unit
}
```

### Las dos sentencias triviales

```kotlin
// print acepta CUALQUIER tipo: solo hay que verificar la expresion para decorarla.
// No hay regla de tipo que imponer.
private fun checkPrint(stmt: Print) {
    checkExpression(stmt.expr)
}

// Una expresion usada como sentencia: se descarta su valor. Verificarla igual es lo
// que hace que `f(1);` valide sus argumentos.
private fun checkExpressionStatement(stmt: ExpressionStatement) {
    checkExpression(stmt.expr)
}
```

### Los seis nodos que abren ámbito

Regla: **un nodo abre ámbito si y solo si puede declarar nombres que mueren con él.**

| Abre ámbito | Qué declara | `ScopeKind` |
|---|---|---|
| `Block` | variables locales | `BLOCK` |
| `If` | las de cada rama | `BLOCK` |
| `While` / `DoWhile` / `For` | lo que declare el inicializador | `LOOP` |
| `ForEach` | la variable del ciclo | `LOOP` |
| `Switch` | las de cada `case` y del `default` | `BLOCK` |
| `TryCatch` | el parámetro del `catch` | `BLOCK` |
| `FunctionDeclaration` | sus parámetros | `FUNCTION` |
| `ClassDeclaration` | ya lo abrió la Pasada 1; aquí solo **se entra** | `CLASS` |

Todos los demás pasan el mismo ámbito hacia abajo.

```kotlin
// La función ES el ámbito: se entra al principio y se sale al final.
//
// El nombre lo pasa QUIEN abre el ámbito, y nombra la CONSTRUCCIÓN, no "block":
// "if@4", "for@3", "case@10". Ver la tabla de nombres en el ticket 1.3.
// Este `checkBlock` es solo para el `{ }` suelto; if, while, for, etc. abren el
// suyo con su propio nombre (ver más abajo).
private fun checkBlock(block: Block) {
    withScope(ScopeKind.BLOCK, "block@${block.location.line}") {
        block.statements.forEach { checkStatement(it) }
    }
}

// El ayudante que todas las construcciones usan: entra, ejecuta, y sale
// pase lo que pase.
private inline fun withScope(kind: ScopeKind, name: String, body: () -> Unit) {
    currentScope = currentScope.openChild(kind, name)
    body()
    currentScope = currentScope.parent!!
}
```

Y cada construcción abre el suyo con su propio nombre:

```kotlin
private fun checkIfStatement(stmt: IfStatement) {
    requireBooleanCondition(stmt.condition, "if")

    withScope(ScopeKind.BLOCK, "if@${stmt.location.line}") {
        stmt.thenBranch.statements.forEach { checkStatement(it) }
    }

    stmt.elseBranch?.let { elseBranch ->
        withScope(ScopeKind.BLOCK, "else@${elseBranch.location.line}") {
            elseBranch.statements.forEach { checkStatement(it) }
        }
    }
}

// Los bucles abren LOOP, no BLOCK: es lo que permite validar break y continue.
//
// OJO: NO se puede delegar en checkBlock, aunque la gramática diga que el cuerpo
// es un `block`. Si lo hiciera, el ámbito quedaría marcado como BLOCK en vez de
// LOOP, y el árbol de ámbitos de la GUI mostraría "block@7" donde debe decir
// "while@7".
private fun checkWhileStatement(stmt: WhileStatement) {
    requireBooleanCondition(stmt.condition, "while")
    withScope(ScopeKind.LOOP, "while@${stmt.location.line}") {
        stmt.body.statements.forEach { checkStatement(it) }
    }
}

private fun checkForStatement(stmt: ForStatement) {
    // El inicializador declara DENTRO del ámbito del for: `for (let i = 0; ...)`
    // deja `i` visible solo en el ciclo.
    withScope(ScopeKind.LOOP, "for@${stmt.location.line}") {
        stmt.initializer?.let { checkStatement(it) }
        stmt.condition?.let { requireBooleanCondition(it, "for") }
        stmt.update?.let { checkExpression(it) }
        stmt.body.statements.forEach { checkStatement(it) }
    }
}

// El do-while ejecuta su cuerpo antes de evaluar la condicion, pero para el
// VERIFICADOR el orden no importa: los dos son el mismo ambito y la misma regla.
// El orden si le importa a la Fase 5, que usa "el cuerpo corre al menos una vez"
// para decidir si garantiza retorno.
private fun checkDoWhileStatement(stmt: DoWhile) {
    withScope(ScopeKind.LOOP, "do@${stmt.location.line}") {
        stmt.body.statements.forEach { checkStatement(it) }
    }
    requireBooleanCondition(stmt.condition, "do-while")
}
```

### `try/catch`: el parámetro es de tipo `string`

```kotlin
private fun checkTryCatch(stmt: TryCatch) {
    withScope(ScopeKind.BLOCK, "try@${stmt.location.line}") {
        stmt.tryBlock.statements.forEach { checkStatement(it) }
    }

    withScope(ScopeKind.BLOCK, "catch@${stmt.catchBlock.location.line}") {
        // El parametro del catch es siempre `string`: la gramatica no deja anotarlo
        //   tryCatchStatement: 'try' block 'catch' '(' Identifier ')' block
        // y el interprete le liga el MENSAJE del error:
        //   catchEnvironment.define(nombre, StringValue(error.message))
        //
        // Es lo que hace que el unico ejemplo de try/catch del enunciado funcione:
        //   catch (err) { print("Error atrapado: " + err); }   <- string + string
        currentScope.declareOrReport(Symbol(
            name = stmt.catchParameterName,
            kind = DeclarationKind.VARIABLE,
            type = StringType,
            location = stmt.location,
            scopeName = currentScope.name,
            offset = 0,
            initialized = true
        ))

        stmt.catchBlock.statements.forEach { checkStatement(it) }
    }
}
```

### `class`: se RECUPERA el ámbito, no se abre

```kotlin
private fun checkClassDeclaration(decl: ClassDeclaration) {
    // Se recupera el ambito que creo la Pasada 1. Usar withScope(CLASS, decl.name)
    // crearia un SEGUNDO ambito para la misma clase: la GUI mostraria dos nodos
    // "Animal" y los metodos se declararian en el vacio.
    val classScope = classScopeOf(decl.name) ?: return

    val previousScope = currentScope
    currentScope = classScope

    decl.members.forEach { member ->
        when (member) {
            // Los campos YA los declaro la Pasada 1. Aqui solo se verifica su
            // inicializador: la Pasada 1 no evalua expresiones, asi que nunca lo
            // comparo contra el tipo anotado.
            is VariableDeclaration -> checkFieldInitializer(member)
            is FunctionDeclaration -> checkFunctionDeclaration(member)
            else -> Unit
        }
    }

    currentScope = previousScope
}

// Verifica el inicializador de un campo SIN volver a declararlo.
//
// Sin esta funcion, `class A { let x: integer = "hola"; }` pasaria sin error.
private fun checkFieldInitializer(decl: VariableDeclaration) {
    val initializer = decl.initializer?.let { checkExpression(it) } ?: return
    val declaredType = currentScope.lookupLocal(decl.name)?.type ?: return

    if (!typeRules.isAssignable(declaredType, initializer.type)) {
        report(decl,
            "No se puede asignar '${initializer.type.name}' al campo " +
            "'${decl.name}', declarado como '${declaredType.name}'")
    }
}
```

### `declareOrReport`: la misma función que la Pasada 1

Las cuatro declaraciones de esta fase —variables, parámetros, la variable del
`foreach` y el parámetro del `catch`— pasan por la **misma** función de extensión que
usa el `DeclarationCollector` (ticket 3.2):

```kotlin
currentScope.declareOrReport(symbol, diagnostics)
```

Vive en `frontend/semantic/ScopeDeclaration.kt` y no duplicada en cada fase, porque el
mensaje *"'x' ya fue declarado en este ámbito (línea N)"* tiene que ser idéntico en
las dos.

### Declaración de variable: la inferencia y la regla de `const`

```kotlin
private fun checkVariableDeclaration(decl: VariableDeclaration) {
    val declaredType = typeResolver.resolve(decl.declaredType)
    val initializer = decl.initializer?.let { checkExpression(it) }

    // Regla del enunciado: "inicialización obligatoria de constantes".
    if (decl.isConstant && initializer == null) {
        report(decl, "La constante '${decl.name}' debe inicializarse en su declaración")
    }

    val finalType = when {
        // Tipo anotado: se verifica que el inicializador encaje.
        declaredType != null -> {
            if (initializer != null &&
                !typeRules.isAssignable(declaredType, initializer.type)
            ) {
                report(decl,
                    "No se puede asignar '${initializer.type.name}' a " +
                    "'${decl.name}', declarada como '${declaredType.name}'")
            }
            declaredType
        }

        // Sin tipo anotado: se INFIERE del inicializador.
        initializer != null -> initializer.type

        // Ni tipo ni inicializador: no hay forma de saber qué es.
        else -> {
            report(decl,
                "'${decl.name}' necesita un tipo anotado o un valor inicial")
            ErrorType
        }
    }

    currentScope.declareOrReport(Symbol(
        name = decl.name,
        kind = if (decl.isConstant) DeclarationKind.CONSTANT else DeclarationKind.VARIABLE,
        type = finalType,
        location = decl.location,
        scopeName = currentScope.name,
        offset = 0,
        initialized = initializer != null,

        // Solo para constantes. Una `let` no guarda su valor: ver el comentario de
        // checkIdentifier sobre por que propagarlo podria mentir.
        constantValue = if (decl.isConstant) initializer?.constant else null
    ))
}
```

### Asignación: el lvalue y la constante

Una asignación llega al verificador por **dos nodos distintos**, y los dos aplican las
mismas tres reglas:

| Nodo | De dónde viene |
|---|---|
| `Assignment` | sentencia: `x = 5;`, `obj.prop = 5;`, `lista[0] = 5;` |
| `AssignmentExpression` | anidada: `let y = (x = 5);`, `if (x = 1) { }` |

La Fase 2 normaliza las dos formas de **sentencia** a un solo `Assignment` (ticket
2.3), pero `AssignmentExpression` sigue existiendo para el caso anidado. Así que las
reglas van en **un ayudante compartido** y las dos funciones lo llaman:

```kotlin
// Las tres reglas de toda asignacion, en un solo lugar.
//
// Existe porque una asignacion llega por dos nodos: Assignment (sentencia) y
// AssignmentExpression (anidada). Copiar las reglas en las dos funciones seria
// pedir que se desincronicen.
//
// Devuelve el tipo del DESTINO, que es lo que necesita el llamador de expresion:
// el valor de `x = 5` es 5, y su tipo es el de x.
private fun checkAssignmentRules(
    node: Node,               // para la ubicacion del error
    target: Expression,
    value: Expression
): TypedValue {
    val targetValue = checkExpression(target)
    val valueValue = checkExpression(value)

    // Solo tres formas de expresion pueden estar del lado izquierdo.
    if (!isLValue(target)) {
        report(node, "El lado izquierdo de una asignación debe ser una variable, " +
                     "un campo o un elemento de lista")
        return TypedValue(ErrorType)
    }

    // Regla del enunciado: una constante no se puede reasignar.
    val symbol = symbolOf(target)
    if (symbol?.kind == DeclarationKind.CONSTANT) {
        report(node, "No se puede reasignar la constante '${symbol.name}'")
        return TypedValue(ErrorType)
    }

    if (!typeRules.isAssignable(targetValue.type, valueValue.type)) {
        report(node,
            "No se puede asignar '${valueValue.type.name}' a " +
            "'${targetValue.type.name}'")
        return TypedValue(ErrorType)
    }

    // A partir de aqui la variable tiene un valor.
    symbol?.initialized = true

    return TypedValue(targetValue.type)
}

private fun isLValue(expr: Expression): Boolean =
    expr is Identifier || expr is PropertyAccess || expr is IndexAccess

// El Symbol al que apunta un lvalue, para poder verificar si es constante.
//
// Es trivial porque la Fase 4 ya guardo el simbolo resuelto en el nodo (ticket 1.4).
// Sin eso habria que volver a buscar el nombre en el arbol de ambitos, con el riesgo
// de encontrar otro por un cursor desincronizado.
private fun symbolOf(expr: Expression): Symbol? = when (expr) {
    is Identifier     -> expr.resolvedSymbol
    is PropertyAccess -> expr.resolvedMember

    // Asignar a lista[0] NO reasigna `lista`: modifica su contenido. Devolver el
    // simbolo del arreglo haria que `const lista = [1,2]; lista[0] = 5;` diera error,
    // y mutar el contenido de una constante es legal — lo que no se puede reasignar
    // es la constante misma.
    is IndexAccess    -> null

    else -> null
}

// La sentencia descarta el tipo: una sentencia no produce valor.
private fun checkAssignment(stmt: Assignment) {
    checkAssignmentRules(stmt, stmt.target, stmt.value)
}

// La expresion lo devuelve y decora el nodo.
//
// El tipo de `x = 5` es el tipo de x, no de 5. Es lo que hace que `if (x = 1)` sea
// ERROR: devuelve integer, no boolean. Misma regla que en Java.
private fun checkAssignmentExpression(expr: AssignmentExpression): TypedValue =
    decorate(expr, checkAssignmentRules(expr, expr.target, expr.value))
```

### Funciones: parámetros, ámbito propio y tipo de retorno

```kotlin
private fun checkFunctionDeclaration(decl: FunctionDeclaration) {
    // Si esta funcion es un metodo de una clase que hereda, su firma tiene que
    // coincidir con la del padre. Ver checkOverride en el ticket 4.3.
    if (currentScope.kind == ScopeKind.CLASS) {
        checkOverride(decl, currentScope)
    }

    val symbol = currentScope.lookup(decl.name)
    val functionType = symbol?.type as? FunctionType

    val functionScope = currentScope.openChild(ScopeKind.FUNCTION, decl.name)
    val previousScope = currentScope
    val previousReturnType = currentReturnType

    currentScope = functionScope
    currentReturnType = functionType?.returns ?: VoidType

    // Los parámetros se declaran en el ámbito de la función, ya inicializados.
    decl.parameters.forEachIndexed { index, parameter ->
        currentScope.declareOrReport(Symbol(
            name = parameter.name,
            kind = DeclarationKind.PARAMETER,
            type = functionType?.parameters?.getOrNull(index) ?: ErrorType,
            location = parameter.location,
            scopeName = functionScope.name,
            offset = index,
            initialized = true
        ))
    }

    // El cuerpo se recorre SIN abrir otro ámbito: los parámetros y las locales
    // del primer nivel comparten el ámbito de la función, que es la semántica
    // esperada (un parámetro y una local con el mismo nombre son un choque).
    decl.body.statements.forEach { checkStatement(it) }

    currentScope = previousScope
    currentReturnType = previousReturnType
}

private fun checkReturn(stmt: Return) {
    // La UBICACIÓN de `return` (dentro o fuera de una función) la valida el
    // FlowAnalyzer en la Fase 5. Aquí solo se verifica el TIPO.
    val expected = currentReturnType ?: return
    val actual = stmt.value?.let { checkExpression(it).type } ?: VoidType

    if (!typeRules.isAssignable(expected, actual)) {
        report(stmt,
            "La función debe devolver '${expected.name}', no '${actual.name}'")
    }
}
```

**Por qué el cuerpo no abre otro ámbito:** si `checkFunctionDeclaration` abriera el ámbito
de la función y después `checkBlock` abriera uno para el cuerpo, entonces
`function f(x: integer) { let x: string; }` sería legal (la local taparía al
parámetro). En Compiscript, como en la mayoría de los lenguajes, eso debe ser un
choque de nombres. Por eso el cuerpo se recorre directamente.

### Control de flujo: la condición debe ser booleana

El ayudante que usan las cuatro construcciones con condición (`checkIfStatement`,
`checkWhileStatement`, `checkForStatement` y `checkDoWhileStatement`, mostradas
arriba):

```kotlin
private fun requireBooleanCondition(condition: Expression, construct: String) {
    val type = checkExpression(condition).type
    if (type != BooleanType && type != ErrorType) {
        report(condition,
            "La condición de '$construct' debe ser boolean, no '${type.name}'")
    }
}
```

**Esta es la regla que hace que `if (x = 1)` sea error en Compiscript**, igual que
en Java: la asignación devuelve el tipo de la variable, no `boolean`.

### El `switch`: comparable, no booleano

```kotlin
// El enunciado dice que la condición del switch debe ser boolean, pero su propio
// ejemplo hace `switch (x) { case 1: }` con x entero.
//
// La lectura correcta es la del catedrático: TODA condición de control de flujo se
// resuelve como operación booleana, y en un switch lo que ocurre internamente es
// `x == 1`, una COMPARACIÓN que produce boolean. La regla real es que el sujeto y
// los case sean COMPARABLES entre sí.
private fun checkSwitch(stmt: Switch) {
    val subject = checkExpression(stmt.subject)

    stmt.cases.forEach { case ->
        val caseType = checkExpression(case.value).type

        if (typeRules.equality(subject.type, caseType) == null) {
            report(case.value,
                "El case de tipo '${caseType.name}' no se puede comparar con " +
                "el switch de tipo '${subject.type.name}'")
        }

        withScope(ScopeKind.BLOCK, "case@${case.location.line}") {
            case.body.forEach { checkStatement(it) }
        }
    }

    stmt.defaultBody?.let { body ->
        withScope(ScopeKind.BLOCK, "default@${stmt.location.line}") {
            body.forEach { checkStatement(it) }
        }
    }
}
```

### `foreach`: el único punto de inferencia real

```kotlin
private fun checkForEach(stmt: ForEach) {
    val iterableType = checkExpression(stmt.iterable).type

    // El tipo de la variable del ciclo NO se declara: se INFIERE del tipo de
    // elemento del arreglo. Es el único lugar del lenguaje donde hay inferencia
    // que no viene de un inicializador.
    val elementType = when {
        iterableType is ArrayType -> iterableType.element
        iterableType == ErrorType -> ErrorType
        else -> {
            report(stmt.iterable,
                "'foreach' solo recorre listas, y '${iterableType.name}' no lo es")
            ErrorType
        }
    }

    withScope(ScopeKind.LOOP, "foreach@${stmt.location.line}") {
        currentScope.declareOrReport(Symbol(
            name = stmt.variableName,
            kind = DeclarationKind.VARIABLE,
            type = elementType,
            location = stmt.location,
            scopeName = currentScope.name,
            offset = 0,
            initialized = true
        ))
        stmt.body.statements.forEach { checkStatement(it) }
    }
}
```

Los bucles `while`, `do-while` y `for` abren su ámbito con `ScopeKind.LOOP`, que es
lo que permite validar `break` y `continue` en la Fase 5.

### Aceptación

| Entrada | Resultado esperado |
|---|---|
| `let x: integer = 1;` | válido |
| `let x: integer = "a";` | **error**: no se puede asignar string a integer |
| `let x: float = 1;` | válido (ensanchamiento) |
| `let x: integer = 1.5;` | **error**: no hay estrechamiento |
| `let x = 5;` | válido, tipo inferido `integer` |
| `let x;` | **error**: necesita tipo o valor inicial |
| `const PI: integer;` | **error**: la constante debe inicializarse |
| `const PI = 314; PI = 3;` | **error**: no se puede reasignar una constante |
| `5 = x;` | **error**: el lado izquierdo no es asignable |
| `if (true) {}` | válido |
| `if (1) {}` | **error**: la condición debe ser boolean |
| `if (x = 1) {}` | **error**: la asignación no produce boolean |
| `while ("a") {}` | **error** |
| `switch (x) { case 1: }` con `x: integer` | válido |
| `switch (x) { case "a": }` con `x: integer` | **error**: case no comparable |
| `foreach (n in [1,2,3]) { print(n); }` | válido, `n` inferido `integer` |
| `foreach (n in 5) {}` | **error**: 5 no es una lista |
| `function f(): integer { return "a"; }` | **error**: debe devolver integer |
| `function f(): integer { return 1; }` | válido |
| `function f() { print("hola"); }` | válido: sin anotar es void (decisión 15) |
| `function f() { return; }` | válido: `return;` pelado en una void es salida temprana |
| `function f() { return 1; }` | **error**: debe devolver void, no integer. *No se infiere del cuerpo.* |
| `function f(x: integer) { let x: string; }` | **error**: `x` ya declarado |
| Un bloque anidado que tapa una variable exterior | válido (shadowing) |
| Recursión: `function fact(n: integer): integer { return n * fact(n-1); }` | válido |
| `class A { let x: integer = "hola"; }` | **error**: el inicializador del campo no encaja. *Test de `checkFieldInitializer`* |
| `try { } catch (err) { print("E: " + err); }` | válido: `err` es `string` |
| `try { } catch (err) { let y: integer = err; }` | **error**: `err` es `string` |
| `do { } while (1);` | **error**: la condición debe ser boolean |
| `const lista = [1,2]; lista[0] = 5;` | **válido**: mutar el contenido no es reasignar la constante |
| `const lista = [1,2]; lista = [3];` | **error**: eso sí es reasignar |
| `print(perro);` | válido: `print` acepta cualquier tipo |

Y estos dos, que son estructurales:

- Después de verificar, **todo nodo `Expression` del AST tiene `type != null`.**
- El árbol de ámbitos tiene un `Scope` por cada bloque, función, clase y bucle del
  programa, todos enumerables desde `globalScope.children`.
- **Una clase produce UN solo `Scope`, no dos.** Es el test de que
  `checkClassDeclaration` recupera el ámbito de la Pasada 1 en vez de abrir otro.
- Un `when (stmt)` sobre `Statement` en `checkStatement` **sin rama `else`** compila:
  las 17 sentencias tienen su función.

### Respaldo

Enunciado: *"Manejo de Ámbito"*, *"Control de Flujo"*, *"Sistema de Tipos"*. Dragon
Book §6.5.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 4.1 | Las reglas de tipo aisladas, con su documento en notación de Cardelli |
| 4.2 | Literales, nombres, operadores, ternario y listas, con plegado de constantes |
| 4.3 | Llamadas, métodos, `new`, `this`, propiedades e indexación |
| 4.4 | Sentencias, ámbitos, control de flujo y declaraciones locales |

**Al terminar:** el AST está **decorado** (cada expresión con su tipo y, cuando se
puede, su valor), y todos los errores de tipo están reportados con ubicación y
mensaje explicativo. Con esto y la Fase 3 se cubre la mayor parte de los 85 puntos
de semántica y tabla de símbolos.
