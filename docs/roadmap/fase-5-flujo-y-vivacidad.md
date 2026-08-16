# Fase 5 — Flujo y vivacidad

**Objetivo de la fase:** dos cosas cortas, y solo la primera recorre el AST.

**5.1 — `FlowAnalyzer`** valida las reglas que dependen de **dónde** está una
sentencia: `return` dentro de una función, `break` dentro de un bucle, código
inalcanzable, y retorno en todos los caminos.

**5.2 — `LivenessReportBuilder`** arma el reporte que un **recolector de basura**
necesitaría. **No recorre el AST**: la Fase 4 ya dejó los contadores en cada
`Symbol`, así que esto solo recorre el árbol de ámbitos y formatea.

**Por qué el flujo va aparte del `TypeChecker`:** es una pregunta de otra naturaleza.
El verificador de tipos pregunta *"¿esta operación tiene sentido?"*; el `FlowAnalyzer`
pregunta *"¿esta sentencia está en un lugar donde se puede ejecutar?"*. Mezclarlas
haría que `TypeChecker` llevara dos contadores más y sería más difícil de leer.

**Estimación:** una o dos sesiones. Son recorridos cortos con lógica clara.

---

## Ticket 5.1 — `FlowAnalyzer`

- **Estado**: pendiente
- **Depende de**: 4.4

**Archivos:**

- `frontend/semantic/FlowAnalyzer.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/FlowAnalyzerTest.kt` (NUEVO)

**Qué es esto, en simple:** revisa tres cosas que dependen de la **posición** de una
sentencia en el programa:

1. `break` y `continue` solo dentro de bucles.
2. `return` solo dentro de funciones.
3. Código muerto: instrucciones después de `return`, `break` o `continue`.

Y una cuarta que es consecuencia natural de la tercera: si una función declara tipo
de retorno, **todos los caminos** deben devolver algo.

### Cómo lleva la cuenta de dónde está

No necesita los ámbitos: le bastan dos contadores.

```kotlin
// Valida las reglas que dependen de DÓNDE está una sentencia.
//
// Lleva contadores propios en vez de consultar el árbol de ámbitos porque son dos
// enteros y hacen el código trivial de leer.
class FlowAnalyzer(private val diagnostics: Diagnostics) {

    private var loopDepth = 0        // cuántos bucles hay encima
    private var functionDepth = 0    // cuántas funciones hay encima

    fun analyze(program: Program) {
        analyzeStatements(program.statements)
    }
}
```

### La detección de código muerto va en CADA lista de sentencias

```kotlin
// Recorre una lista y ademas busca codigo muerto EN ESA LISTA.
//
// Todo lugar del AST que tenga una lista de sentencias pasa por aqui: el programa,
// los bloques, los cuerpos de funcion, los cuerpos de `case`. Una version anterior
// del plan solo lo llamaba sobre program.statements, y entonces esto no se
// detectaba:
//
//   function f(): integer { return 1; print("nunca"); }
//
// que es justo el caso que el enunciado nombra.
private fun analyzeStatements(statements: List<Statement>) {
    statements.forEach { analyzeStatement(it) }
    checkUnreachable(statements)
}

private fun analyzeBlock(block: Block) = analyzeStatements(block.statements)

// Un switch NO sube loopDepth: no es un bucle. Por eso un `break` dentro de un
// switch que no esta en un bucle sigue dando error, que es lo correcto.
// Ver "break dentro de un switch" abajo.
private fun analyzeSwitch(stmt: Switch) {
    stmt.cases.forEach { analyzeStatements(it.body) }
    stmt.defaultBody?.let { analyzeStatements(it) }
}
```

### Las tres reglas de ubicación

```kotlin
private fun analyzeStatement(stmt: Statement) {
    when (stmt) {
        is Break -> if (loopDepth == 0) {
            report(stmt, "'break' solo se puede usar dentro de un bucle")
        }

        is Continue -> if (loopDepth == 0) {
            report(stmt, "'continue' solo se puede usar dentro de un bucle")
        }

        is Return -> if (functionDepth == 0) {
            report(stmt, "'return' solo se puede usar dentro de una función")
        }

        is While    -> withinLoop { analyzeBlock(stmt.body) }
        is DoWhile  -> withinLoop { analyzeBlock(stmt.body) }
        is For      -> withinLoop { analyzeBlock(stmt.body) }
        is ForEach  -> withinLoop { analyzeBlock(stmt.body) }

        is FunctionDeclaration -> analyzeFunction(stmt)

        is If -> {
            analyzeBlock(stmt.thenBranch)
            stmt.elseBranch?.let { analyzeBlock(it) }
        }

        is Block -> analyzeBlock(stmt)

        is Switch -> analyzeSwitch(stmt)

        is TryCatch -> {
            analyzeBlock(stmt.tryBlock)
            analyzeBlock(stmt.catchBlock)
        }

        is ClassDeclaration -> stmt.members.forEach { analyzeStatement(it) }

        // Las demás no afectan el flujo.
        else -> Unit
    }
}

private inline fun withinLoop(body: () -> Unit) {
    loopDepth += 1
    body()
    loopDepth -= 1
}
```

### Un detalle que hay que hacer bien: la función corta la cuenta de bucles

```kotlin
private fun analyzeFunction(decl: FunctionDeclaration) {
    val previousLoopDepth = loopDepth

    functionDepth += 1
    // Una función anidada dentro de un bucle NO hereda ese bucle: un `break`
    // dentro de la función no puede salir del bucle de afuera.
    //
    //   while (x) { function f() { break; } }   <- ERROR, y hay que atraparlo
    loopDepth = 0

    analyzeBlock(decl.body)
    checkAllPathsReturn(decl)

    loopDepth = previousLoopDepth
    functionDepth -= 1
}
```

Sin ese `loopDepth = 0`, el `break` dentro de la función anidada pasaría sin error.
Es el caso que se olvida siempre.

### Código muerto

```kotlin
// Una sentencia después de return / break / continue nunca se ejecuta.
// El enunciado lo pide como regla: "detección de código muerto".
private fun checkUnreachable(statements: List<Statement>) {
    val terminatorIndex = statements.indexOfFirst { isTerminator(it) }

    if (terminatorIndex >= 0 && terminatorIndex < statements.size - 1) {
        val firstDead = statements[terminatorIndex + 1]
        val terminator = statements[terminatorIndex]

        report(firstDead,
            "Código inalcanzable: nunca se ejecuta porque " +
            "'${describeTerminator(terminator)}' de la línea " +
            "${terminator.location.line} corta el flujo"
        )
    }
}

private fun isTerminator(stmt: Statement): Boolean =
    stmt is Return || stmt is Break || stmt is Continue

// El nombre de la palabra clave que corto el flujo, para el mensaje.
private fun describeTerminator(stmt: Statement): String = when (stmt) {
    is Return   -> "return"
    is Break    -> "break"
    is Continue -> "continue"
    else        -> "el salto"
}

// Un delegado de una linea. Se repite en TypeChecker y aqui, y esta bien asi: es una
// linea, y mantenerlo local deja los ~20 sitios de llamada cortos.
private fun report(node: Node, message: String) {
    diagnostics.report(CompilerError.SemanticError(node.location, message))
}
```

**Se reporta solo la primera sentencia muerta, no todas.** Si después de un `return`
hay diez sentencias, diez errores no aportan más información que uno. El mensaje
nombra dónde está el corte, que es lo que el usuario necesita para arreglarlo. Es la
propiedad **transparente** aplicada a un caso concreto.

### ¿Retorna en todos los caminos?

```kotlin
// Si una función declara tipo de retorno, TODOS sus caminos deben devolver algo.
private fun checkAllPathsReturn(decl: FunctionDeclaration) {
    // Sin tipo declarado, la función es void: no tiene que retornar (decision 15).
    if (decl.returnType == null) return

    if (!alwaysReturns(decl.body.statements)) {
        report(decl,
            "La función '${decl.name}' declara devolver " +
            "'${decl.returnType.name}' pero hay caminos que no retornan")
        //                        ^^^^ .name y no .baseName: para integer[] hay que
        //                        imprimir "integer[]", no "integer".
    }
}

// ¿Esta lista de sentencias garantiza un return en TODOS sus caminos?
private fun alwaysReturns(statements: List<Statement>): Boolean =
    statements.any { alwaysReturns(it) }

private fun alwaysReturns(stmt: Statement): Boolean = when (stmt) {
    is Return -> true

    // Solo si AMBAS ramas retornan. Un if sin else no garantiza nada:
    // si la condición es falsa, no pasa por ninguna rama.
    is If -> stmt.elseBranch != null &&
             alwaysReturns(stmt.thenBranch.statements) &&
             alwaysReturns(stmt.elseBranch.statements)

    is Block -> alwaysReturns(stmt.statements)

    // do-while ejecuta su cuerpo AL MENOS una vez, así que si el cuerpo retorna,
    // la función retorna.
    is DoWhile -> alwaysReturns(stmt.body.statements)

    // while y for podrían no ejecutarse nunca... SALVO que su condición sea
    // constantemente verdadera. Ver el parche de abajo.
    is While -> isAlwaysTrue(stmt.condition) && !escapesWithBreak(stmt.body)
    is For   -> (stmt.condition == null || isAlwaysTrue(stmt.condition)) &&
                !escapesWithBreak(stmt.body)

    // Un switch garantiza retorno solo si tiene default Y todos los casos retornan.
    // Sin default, un valor que no coincida con ningún case pasa de largo.
    is Switch -> stmt.defaultBody != null &&
                 stmt.cases.all { alwaysReturns(it.body) } &&
                 alwaysReturns(stmt.defaultBody)

    else -> false
}

// ¿Este cuerpo tiene un `break` que salga de ESTE bucle?
//
// Hace falta porque un bucle infinito solo garantiza retorno si nadie se escapa:
//   while (true) { return 1; }            <- garantiza
//   while (true) { if (x) { break; } }    <- NO garantiza
private fun escapesWithBreak(block: Block): Boolean =
    block.statements.any { containsOwnBreak(it) }

private fun containsOwnBreak(stmt: Statement): Boolean = when (stmt) {
    is Break -> true

    is Block    -> escapesWithBreak(stmt)
    is If       -> escapesWithBreak(stmt.thenBranch) ||
                   stmt.elseBranch?.let { escapesWithBreak(it) } == true
    is TryCatch -> escapesWithBreak(stmt.tryBlock) || escapesWithBreak(stmt.catchBlock)
    is Switch   -> stmt.cases.any { case -> case.body.any { containsOwnBreak(it) } } ||
                   stmt.defaultBody?.any { containsOwnBreak(it) } == true

    // Los bucles ANIDADOS y las funciones anidadas NO cuentan: un break ahi dentro
    // sale del bucle interno, no de este.
    //
    //   while (true) {
    //     while (y) { break; }   <- sale del while interno
    //     return 1;              <- asi que el externo SI garantiza retorno
    //   }
    is While, is DoWhile, is For, is ForEach, is FunctionDeclaration -> false

    else -> false
}
```

### El parche de la condición constante, y por qué el plegado de la Fase 4 lo regala

```kotlin
// ¿La condición es constantemente verdadera?
//
// Sin este parche, `while (true) { return 1; }` daría un FALSO POSITIVO: el
// análisis diría "hay caminos que no retornan" sobre código perfectamente legal.
//
// Y como la Fase 4 pliega constantes, esto funciona no solo con el literal `true`
// sino tambien con `1 == 1` y `2 > 1`, que se pliegan a `true` durante la
// verificacion de tipos; y con una `const`, cuyo valor la Fase 4 propaga.
private fun isAlwaysTrue(condition: Expression): Boolean =
    condition.constantValue == true
```

En el plan del proyecto anterior, `while (1 == 1)` estaba anotado como **limitación
conocida**: daba un falso positivo porque no había plegado de constantes. Aquí no
existe esa limitación, porque el `TypeChecker` ya calculó el valor.

Y tampoco existe con una constante con nombre, porque la Fase 4 propaga el valor de
las `const` (ticket 4.2):

```cps
const SIEMPRE: boolean = true;

function f(): integer {
  while (SIEMPRE) { return 1; }   // reconocido como bucle infinito, sin falso positivo
}
```

**Limitación que sí queda:** lo mismo con una variable **mutable** no se detecta.

```cps
let siempre: boolean = true;
while (siempre) { return 1; }     // falso positivo: "hay caminos que no retornan"
```

Y es a propósito: el valor de una `let` puede cambiar en un `if` o en un bucle, así
que propagarlo daría respuestas que dependen de caminos que el verificador no
simula. Resolverlo bien exige análisis de flujo de datos completo. La solución para
el usuario es escribir `const`, que es lo correcto de todos modos para una condición
que no cambia.

### Aceptación

**Ubicación de saltos:**

| Entrada | Resultado |
|---|---|
| `while (x) { break; }` | válido |
| `break;` en el nivel superior | **error** |
| `function f() { break; }` | **error** |
| `while (x) { function f() { break; } }` | **error**. *Test obligatorio: es el que se olvida.* |
| `function f() { return 1; }` | válido |
| `return 1;` fuera de una función | **error** |
| `switch (x) { case 1: break; }` fuera de un bucle | **error**: `break` solo en bucles (decisión 5) |
| `while (y) { switch (x) { case 1: break; } }` | válido, y **rompe el `while`**, no el `switch`. Ver la nota abajo |

### `break` dentro de un `switch`: rompe el bucle, no el switch

```cps
while (x) {
  switch (y) {
    case 1: break;      // rompe el WHILE
  }
}
```

En C y en Java rompería el `switch`. En Compiscript no existe el `break` de `switch`
—no hay fall-through, cada `case` termina solo (decisión 5)—, así que el único
significado posible es salir del bucle.

Y `analyzeSwitch` **no** sube `loopDepth`, así que el mismo `break` fuera de un bucle
sigue dando error.

**Código muerto:**

| Entrada | Resultado |
|---|---|
| `return 1; print("a");` | **error** en el `print`, nombrando la línea del `return` |
| `function f() { return 1; print("a"); }` | **error**. *Test de que la detección entra a los cuerpos, no solo al nivel superior* |
| `while (x) { break; print("a"); }` | **error** en el `print` |
| `switch (x) { case 1: continue; print("a"); }` dentro de un bucle | **error** en el `print` |
| Diez sentencias después de un `return` | **un solo** error, no diez |

**Retorno en todos los caminos:**

| Entrada | Resultado |
|---|---|
| `function f(): integer { return 1; }` | válido |
| `function f(): integer { if (x) { return 1; } }` | **error**: falta el camino del else |
| `function f(): integer { if (x) { return 1; } else { return 2; } }` | válido |
| `function f(): integer { while (true) { return 1; } }` | válido. *Test del parche.* |
| `function f(): integer { while (1 == 1) { return 1; } }` | válido. *Test del plegado.* |
| `const SIEMPRE = true;` y `function f(): integer { while (SIEMPRE) { return 1; } }` | válido. *Test de la propagación de constantes.* |
| `let siempre = true;` y `function f(): integer { while (siempre) { return 1; } }` | **error**. *Limitación documentada: las variables mutables no se propagan.* |
| `function f(): integer { do { return 1; } while (x); }` | válido |
| `function f(): integer { while (x) { return 1; } }` | **error**: el bucle puede no ejecutarse |
| `function f(): integer { while (true) { if (x) { break; } } }` | **error**: el `break` escapa. *Test de `escapesWithBreak`* |
| `function f(): integer { while (true) { while (y) { break; } return 1; } }` | válido: ese `break` sale del bucle **interno** |
| `function f(): integer[] { }` | **error**, y el mensaje dice `'integer[]'`, no `'integer'` |
| `function f() { }` sin tipo de retorno | válido |

### Respaldo

Enunciado: *"break y continue solo permitidos dentro de bucles"*, *"return solo
permitido dentro del cuerpo de una función"*, *"detección de código muerto
(instrucciones después de return, break, etc.)"*, *"validación del tipo de retorno
respecto al tipo declarado"*.

---

## Ticket 5.2 — `LivenessReportBuilder`: el reporte para el recolector de basura

- **Estado**: pendiente
- **Depende de**: 4.4

**Archivos:**

- `frontend/semantic/LivenessReportBuilder.kt` (NUEVO)
- `frontend/semantic/models/GcReport.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/LivenessReportTest.kt` (NUEVO)

### Qué se está pidiendo realmente

La instrucción del catedrático fue:

> *"Dejar un tipo de meta que me diga cuándo algo ya no se va a utilizar en todo mi
> árbol, para que el garbage collector limpie lo que tengo en mis tablas."*

Traducción concreta: **no se implementa un recolector de basura.** Se produce la
**información que un recolector necesitaría**. Eso es análisis de vivacidad
(*liveness*) simplificado, y ya tiene sus campos reservados en `Symbol` desde la
Fase 1:

```kotlin
var useCount: Int = 0
var lastUseLine: Int? = null
var usedInNestedFunction: Boolean = false
```

### Alcance real de `usedInNestedFunction`: qué afirma y qué no

En lenguajes donde las funciones son valores (JavaScript, Kotlin), una función
anidada puede **devolverse** y sobrevivir a la que la creó. Ahí la captura obliga a
mover la variable de la pila al montón, y el montón es lo que un recolector
administra.

**En Compiscript eso no puede pasar.** Mira el ejemplo de `Especificaciones.md`:

```cps
function crearContador(): integer {
  function siguiente(): integer {
    return 1;
  }
  return siguiente();     // llama a siguiente, NO la devuelve
}
```

`siguiente` vive en el ámbito de `crearContador` y no hay forma de sacarla:

- `return siguiente;` parsea, pero el tipo de retorno solo admite primitivo,
  arreglo o clase — no hay sintaxis para declarar que se devuelve una función.
- Guardarla en una global o en un campo tiene el mismo problema: no existe
  anotación de tipo que la reciba.

Consecuencia honesta, y hay que tenerla clara para la defensa:

| | JavaScript / Kotlin | Compiscript |
|---|---|---|
| ¿La función anidada sobrevive a la de afuera? | sí | **no** |
| ¿La captura obliga a usar el montón? | sí | **no**, la pila alcanza |

**`usedInNestedFunction` no cambia dónde se guarda nada.** Es información correcta
sobre la forma del programa —esa dependencia entre funciones existe— y es lo que el
catedrático pidió reportar. Queda en la misma categoría que `offset`: el dato que
una fase posterior, o un lenguaje con valores de función, necesitaría.

Por eso el campo se llama `usedInNestedFunction` y no `escapesScope`: nombra **lo
que se mide**, no una consecuencia de vida que este lenguaje no tiene.

### Este ticket NO recorre el AST

Una versión anterior del plan tenía un `LivenessAnalyzer` que recorría el AST con su
propio cursor de ámbito:

```kotlin
// LO QUE SE DESCARTO
class LivenessAnalyzer(private val globalScope: Scope) {
    private var currentScope = globalScope          // <-- OTRO cursor de ambito

    private fun analyzeIdentifier(expr: Identifier) {
        val symbol = currentScope.lookup(expr.name) ?: return
        symbol.useCount += 1                         // <-- la Fase 4 YA lo conto
        ...
    }
}
```

Tenía **dos problemas**, y los dos eran de fondo:

**1. Doble conteo.** La Fase 4 ya incrementa `useCount` en `checkIdentifier` y en
`checkPropertyAccess`. Contar otra vez dejaba `useCount` al doble, y el reporte del
recolector decía el doble de usos que hay.

**2. Dos cursores de ámbito que hay que mantener sincronizados a mano.** Para que
`currentScope.lookup(name)` diera el mismo símbolo que dio la Fase 4, esta clase tenía
que abrir y cerrar **exactamente los mismos ámbitos, con los mismos nombres y en el
mismo orden**. Si el `TypeChecker` abría `if@4` y esto abría `block@4`, los `lookup`
divergían y el reporte salía mal **en silencio**.

**La Fase 4 ya tiene todo.** `checkIdentifier` conoce `currentScope`, así que lleva los
tres datos ahí mismo:

```kotlin
// En checkIdentifier (ticket 4.2):
expr.resolvedSymbol = symbol
symbol.useCount += 1
symbol.lastUseLine = expr.location.line

if (currentScope.functionDepth() > symbol.declarationFunctionDepth &&
    symbol.declarationFunctionDepth > 0
) {
    symbol.usedInNestedFunction = true
}
```

La captura se detecta comparando `currentScope.functionDepth()` contra
`Symbol.declarationFunctionDepth`, que `Scope.declare` puso al declarar (ticket 1.2).
Las dos condiciones son las mismas de antes:

1. el uso está en una función **más anidada** que la declaración;
2. la declaración no es global (`depth > 0`) — un global vive todo el programa, no hay
   nada que capturar.

### Lo que este ticket sí hace: formatear

```kotlin
// Arma el reporte de vivacidad recorriendo el ARBOL DE AMBITOS.
//
// No recorre el AST y no lleva cursor de ámbito: la Fase 4 ya dejó los contadores en
// cada Symbol. Aquí solo se leen y se agrupan para la GUI.
class LivenessReportBuilder {

    fun build(globalScope: Scope): GcReport =
        GcReport(entriesByScope = collectScope(globalScope, mutableMapOf()))

    private fun collectScope(
        scope: Scope,
        accumulated: MutableMap<String, List<SymbolLiveness>>
    ): Map<String, List<SymbolLiveness>> {
        accumulated[scope.name] = scope.localSymbols().map { symbol ->
            SymbolLiveness(
                symbol = symbol,
                scopeName = scope.name,
                declaredAtLine = symbol.location.line,
                lastUseLine = symbol.lastUseLine,
                useCount = symbol.useCount,
                usedInNestedFunction = symbol.usedInNestedFunction
            )
        }

        scope.children.forEach { collectScope(it, accumulated) }
        return accumulated
    }
}
```

Diez líneas de lógica, y **cero riesgo de desincronización** porque no hay nada que
sincronizar.

### El reporte

```kotlin
// Lo que un recolector de basura necesitaría saber sobre un símbolo.
data class SymbolLiveness(
    val symbol: Symbol,
    val scopeName: String,
    val declaredAtLine: Int,
    val lastUseLine: Int?,           // null = nunca se usa
    val useCount: Int,
    val usedInNestedFunction: Boolean
) {
    // Nunca se usó: su memoria nunca hizo falta.
    val neverUsed: Boolean get() = useCount == 0

    // NO hay una propiedad `canBeFreedOnScopeExit`. Sería mentira: en Compiscript
    // TODO se puede liberar al cerrar su ámbito, porque una función anidada no
    // puede sobrevivir a la de afuera. `usedInNestedFunction` se reporta como
    // observación, no como impedimento.
}

// El reporte completo, agrupado por ámbito. Lo muestra la GUI.
data class GcReport(
    val entriesByScope: Map<String, List<SymbolLiveness>>
) {
    val neverUsed: List<SymbolLiveness>
        get() = entriesByScope.values.flatten().filter { it.neverUsed }

    val symbolsUsedInNestedFunctions: List<SymbolLiveness>
        get() = entriesByScope.values.flatten().filter { it.usedInNestedFunction }
}
```

### Esto NO produce errores

Recordar la decisión 10 del README: **no hay warnings en este proyecto.** Una
variable declarada y no usada **no se reporta como error**, porque no lo es y no se
pide.

La información de vivacidad va a **una vista propia de la GUI** (Fase 7), no a la
lista de errores. Son dos cosas distintas:

| | Va a la lista de errores | Va al reporte de vivacidad |
|---|---|---|
| Variable no declarada | Sí (error) | — |
| Código muerto después de `return` | Sí (error) | — |
| Variable declarada y nunca usada | **No** | Sí |
| Variable capturada por un closure | **No** | Sí |
| Última línea de uso de cada símbolo | **No** | Sí |

### Aceptación

| Programa | Resultado esperado |
|---|---|
| `let x = 1; print(x);` | `x` con `useCount = 1`, `lastUseLine = 2` |
| `let x = 1;` sin usarlo | `x` con `useCount = 0`, aparece en `neverUsed` |
| `let x = 1; print(x); print(x);` | `useCount = 2`, `lastUseLine` es la de la última |
| `print(x)` una sola vez | `useCount = 1`, **no 2**. *Test que atrapa el doble conteo:* la Fase 4 cuenta y la Fase 5 solo formatea |
| Una función anidada que usa una local de la función de afuera | esa local con `usedInNestedFunction = true`. **Test central del ticket.** |
| Una función anidada que usa una variable **global** | la global con `usedInNestedFunction = false` (los globales no se capturan) |
| Una función anidada que usa solo sus propias locales | ninguna captura |
| Un uso dentro de un `if` dentro de la misma función | `usedInNestedFunction = false`: los bloques no cuentan en `functionDepth()` |
| Un programa con clases, bucles y funciones anidadas | `entriesByScope` tiene una entrada por cada ámbito del programa |

Y este, que es el que cierra el argumento:

- En el programa de `crearContador` de `Especificaciones.md`, extendido para que
  `siguiente` use una local de `crearContador`, el reporte muestra esa local como
  capturada.

### Respaldo

Instrucción del catedrático sobre la conexión con el recolector de basura. Notas de
clase: *"posición de memoria"*, *"pointers"*, *"funciones como parámetro
(lambdas)"*. Enunciado: *"soporte para funciones anidadas y closures, capturando
variables del entorno de definición"*.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 5.1 | `break`/`continue`/`return` en su lugar, código muerto, retorno en todos los caminos |
| 5.2 | El reporte de vivacidad para el GC, armado sobre el árbol de ámbitos |

**Al terminar:** el análisis semántico está completo. Todo lo que el enunciado pide
verificar está verificado, y la información extra que pidió el catedrático (la del
recolector de basura) está disponible para mostrarse.
