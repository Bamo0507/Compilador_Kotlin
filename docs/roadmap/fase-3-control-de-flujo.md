# Fase 3: Control de flujo

**Objetivo de la fase:** traducir las condiciones como código de saltos con
cortocircuito, las sentencias de control con sus etiquetas, `break` y `continue`
hacia el bucle que los contiene, el `switch`, el ternario, los booleanos como valor y
el `try/catch`.

**Por qué va después de la Fase 2:** una condición es una comparación entre dos
expresiones, y esas expresiones ya se traducen con el GDA y el pool. Esta fase agrega
lo que cambia el orden de ejecución.

**Al terminar:** todo programa sin funciones, clases ni listas genera TAC completo.
Los `TODO` de control de flujo de la Fase 2 desaparecen.

**Teoría que la sostiene:** punto 6. Dragon Book 6.6 (código de saltos y caída), 6.7
(por qué aquí no hace falta backpatching) y 6.8 (`switch`).

---

## Ticket 3.1: Las condiciones como saltos

- **Estado**: completado
- **Depende de**: 2.3

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR: `generateCondition`)
- `frontend/intermediate/ExpressionDag.kt` (MODIFICAR: `Untranslated` pasa a `Subexpression`)
- `frontend/intermediate/models/Operators.kt` (MODIFICAR: `RelationalOperator.inverted`)
- `app/src/test/kotlin/org/compiler/TacGeneratorConditionTest.kt` (NUEVO)

### `generateCondition`

Recibe la condición y sus dos etiquetas. `null` significa **caer**: no saltar y seguir
con la instrucción siguiente (decisión 34, Dragon Book 6.6.5).

```kotlin
// Genera saltos a `trueLabel` si la condicion es verdadera y a `falseLabel` si es
// falsa. Una etiqueta null significa caer en la instruccion siguiente.
private fun generateCondition(condition: Expression, trueLabel: Label?, falseLabel: Label?)
```

| Condición | Verdadero y falso con etiqueta | Verdadero cae | Falso cae |
|---|---|---|---|
| `a relop b` | `if a relop b goto V`, `goto F` | `if a relop' b goto F` | `if a relop b goto V` |
| `B1 \|\| B2` | B1: (V, cae) y B2: (V, F) | B1: (Lnuevo, cae), B2: (cae, F), luego `Lnuevo:` | B1: (V, cae), B2: (V, cae) |
| `B1 && B2` | B1: (cae, F) y B2: (V, F) | B1: (cae, F), B2: (cae, F) | B1: (cae, Lnuevo), B2: (V, cae), luego `Lnuevo:` |
| `!B` | B con V y F intercambiadas | | |
| Variable o valor booleano | `if t goto V`, `goto F` | `ifFalse t goto F` | `if t goto V` |
| Constante `true` | `goto V` | nada | `goto V` |
| Constante `false` | `goto F` | `goto F` | nada |

`relop'` es la relación invertida: `<` pasa a `>=`, `==` a `!=`, y así. Vive en el
enum:

```kotlin
val inverted: RelationalOperator get() = when (this) {
    LESS -> GREATER_EQUAL
    LESS_EQUAL -> GREATER
    GREATER -> LESS_EQUAL
    GREATER_EQUAL -> LESS
    EQUAL -> NOT_EQUAL
    NOT_EQUAL -> EQUAL
}
```

Los operandos de una comparación se traducen con `generateExpression`, cada uno con su
propio GDA. La comparación misma no entra al GDA: es un salto, no un valor.

Las constantes plegadas son las que hacen que `while (true)` no genere ninguna
comparación: la condición cae al cuerpo, y el bucle solo sale con `break`.

### Los booleanos como valor

Solo `&&` y `||` necesitan saltos cuando se usan como valor. Una comparación sola usa
`Relational`, y `!` usa `Unary(NOT)`, que ya existen desde la Fase 2.

```
let b: boolean = x < y && z;

    <x < y && z: cae, L1>
    t1 = true
    goto L2
L1: t1 = false
L2:
    b = t1
```

`t1` se pide al pool **antes** de generar los saltos, para que los temporales que use
la condición queden por encima y no le roben el nombre.

### El ternario

```
let m: integer = a > b ? a : b;

    <a > b: cae, L1>
    t1 = a
    goto L2
L1: t1 = b
L2:
    m = t1
```

### Las subexpresiones del GDA

Un operando que no es una operación aritmética simple necesita su propia traducción:
con saltos (el ternario, `&&` y `||` como valor), con una llamada (`f(x)`, `new`) o con
acceso a memoria (`p.nombre`, `lista[i]`). Entra al GDA como una `Subexpression`: el GDA
la trata como una hoja que nunca se comparte, y el generador la traduce con su propia
función. La operación de arriba solo recibe el temporal donde quedó el resultado.

Reemplaza al nodo `Untranslated` de la Fase 2. Es una jerarquía sellada con un caso por
construcción, para que el `when` del generador los nombre y el compilador obligue a
traducir cada uno (decisión 36):

```kotlin
sealed interface Subexpression : DagNode {
    data class Ternary(val expression: TernaryOperation) : Subexpression
    data class LogicalValue(val expression: BinaryOperation) : Subexpression
    data class Call(val expression: FunctionCall) : Subexpression
    data class NewObject(val expression: ObjectCreation) : Subexpression
    data class NewList(val expression: ArrayLiteral) : Subexpression
    data class FieldAccess(val expression: PropertyAccess) : Subexpression
    data class ElementAccess(val expression: IndexAccess) : Subexpression
}
```

En esta fase se traducen `Ternary` y `LogicalValue`. Los otros cinco siguen siendo un
`TODO` hasta las fases de funciones y de objetos. `this` y la asignación a un campo o
elemento dentro de una expresión no son subexpresiones: el GDA los deja como `TODO`, y en
la fase de objetos `this` pasa a ser una hoja y la asignación una extensión de `Assign`.

Como nunca se comparte, una `Subexpression` siempre tiene un solo lector, y el temporal
que devuelve su traducción entra al pool con un solo uso.

**Cuándo se comparte (decisión 53).** El GDA solo reutiliza cálculos en expresiones
puras: variables, constantes y operaciones aritméticas, relacionales o de texto. Si la
expresión contiene una `Subexpression` o una asignación anidada, se construye sin
compartir. En `(a * b) + (c ? a * b : 0)`, la segunda multiplicación está en una rama que
puede no ejecutarse, y reutilizarla sería incorrecto. Reemplaza a la lista de casos de
`hasSideEffects`.

**El orden de lectura (decisión 54).** Una variable a la izquierda de una operación se
copia a un temporal si algún operando de su derecha contiene una asignación anidada o una
`Subexpression`. En `x + f()`, si `f` modifica `x`, el intérprete suma el valor de antes
de la llamada; sin la copia, el TAC leería `x` después:

```
t1 = x
t2 = call f, 0
t1 = t1 + t2
```

Extiende la regla que la Fase 2 ya aplica a `x + (x = 5)`.

### Aceptación

| Programa | TAC esperado |
|---|---|
| `if (a < b) print(1);` | `if a >= b goto L1`, `print_i 1`, `L1:` |
| `if (a < b && c > d) print(1);` | `if a >= b goto L1`, `if c <= d goto L1`, `print_i 1`, `L1:`. **`c > d` no se evalúa si `a < b` es falso** |
| `if (a < b \|\| c > d) print(1);` | `if a < b goto L2`, `if c <= d goto L1`, `L2:`, `print_i 1`, `L1:` |
| `if (!(a < b)) print(1);` | `if a < b goto L1`, `print_i 1`, `L1:` |
| `if (bandera) print(1);` | `ifFalse bandera goto L1`, `print_i 1`, `L1:` |
| `let b: boolean = x < y && z;` | la materialización con `true` y `false` |
| `let m: integer = a > b ? a : b;` | las dos ramas copian al mismo temporal |
| `a * b + (c ? a * b : 0)` | las dos `a * b` no se comparten |

**Al implementarlo:** los casos de aceptación con `if` se prueban en el ticket 3.2,
porque el `if` todavía no se traduce; aquí las condiciones se probaron a través del
ternario y de `&&` y `||` como valor, que pasan por la misma `generateCondition`. Para no
duplicar lógica, `ExpressionDag` expone `comparisonKind` y `relationalOf`, que usan tanto
el GDA como las comparaciones con saltos.

---

## Ticket 3.2: Las sentencias de control, `break` y `continue`

- **Estado**: pendiente
- **Depende de**: 3.1

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorStmtTest.kt` (NUEVO)

### Los esquemas, con caída

```
if (B) S                     if (B) S1 else S2
    <B: cae, Lfin>               <B: cae, L2>
    S                            S1
Lfin:                            goto Lfin
                             L2: S2
                             Lfin:

while (B) S                  do S while (B);
Linicio:                     Lcuerpo:
    <B: cae, Lfin>               S
    S                        Lcond:
    goto Linicio                 <B: Lcuerpo, cae>
Lfin:                        Lfin:

for (init; B; update) S
    init
Linicio:
    <B: cae, Lfin>           // sin condicion, no se genera nada: el for es infinito
    S
Lupdate:
    update
    goto Linicio
Lfin:
```

Las etiquetas se emiten siempre, aunque ningún `break` o `continue` las use. Quitar
las que nadie referencia sería otra pasada sobre el código, y una etiqueta sin uso no
cambia lo que el programa hace.

### La pila de bucles

```kotlin
// A donde saltan break y continue dentro de este bucle, y cuantos try habia abiertos
// al entrar: la diferencia con los de ahora es cuantos endtry hay que emitir.
private data class LoopLabels(
    val breakLabel: Label,
    val continueLabel: Label,
    val openTriesAtEntry: Int
)

private val loops = ArrayDeque<LoopLabels>()
```

| Bucle | `break` | `continue` |
|---|---|---|
| `while` | `Lfin` | `Linicio` |
| `do-while` | `Lfin` | `Lcond` |
| `for` | `Lfin` | `Lupdate` |

El `switch` no apila nada (decisión 5): un `break` dentro de un `switch` sale del
bucle que lo contiene. El `FlowAnalyzer` ya garantiza que todo `break` y `continue`
tiene un bucle, así que la pila nunca está vacía cuando se usa.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `while (i < 3) { i = i + 1; }` | el esquema con `goto Linicio` |
| `do { i = i + 1; } while (i < 3);` | la condición salta a `Lcuerpo` y no hay `goto` hacia atrás propio |
| `for (let i = 0; i < 3; i = i + 1) { if (i == 1) { continue; } }` | `continue` salta a `Lupdate` |
| `do { continue; } while (c);` | `continue` salta a `Lcond`, no a `Lcuerpo` |
| `while (true) { break; }` | ninguna comparación, y `break` salta a `Lfin` |
| Dos bucles anidados con `break` en el interno | salta al `Lfin` del interno |
| `break` dentro de un `switch` dentro de un `while` | salta al `Lfin` del `while` |

---

## Ticket 3.3: `switch`

- **Estado**: pendiente
- **Depende de**: 3.2

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorStmtTest.kt` (AMPLIAR)

### Qué se hace

Una cadena de comparaciones (decisión 35):

```
switch (x) { case 1: A  case 2: B  default: C }

    t1 = <x>                  // solo si x no es ya un Name o una Constant
    if t1 != 1 goto L2
    A
    goto Lfin
L2: if t1 != 2 goto L3
    B
    goto Lfin
L3: C
Lfin:
```

Cada comparación es `<sujeto == case: cae, Lsiguiente>`, así que reutiliza la
traducción de comparaciones del ticket 3.1, incluida la conversión a `float` si el
sujeto es entero y el `case` no.

**El sujeto vive todo el `switch`.** Se pide con tantos usos como `case` haya, y el pool
no lo entrega a nadie más hasta la última comparación, aunque los cuerpos de los
`case` usen sus propios temporales. La verificación de que no queden temporales vivos
pasa de *"al final de cada sentencia"* a *"al final de cada sentencia del nivel
superior"*: dentro de un `case`, el sujeto sigue vivo a propósito.

### Por qué

**Por qué no una tabla de saltos:** la tabla del Dragon Book (6.8) indexa por el valor
del `case`, y exige enteros constantes y cercanos. En Compiscript un `case` puede ser
un string o una variable. Con una cadena todos los casos se traducen igual.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `switch` con dos `case` y `default` | la cadena, y cada `case` termina con `goto Lfin` |
| Sin `default` | el último `case` que no coincide salta a `Lfin` |
| Sujeto `a + b` | se evalúa una vez, en un temporal que vive hasta la última comparación |
| `switch` con strings | comparaciones con `!=s` |

---

## Ticket 3.4: `try/catch`

- **Estado**: pendiente
- **Depende de**: 3.2

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorStmtTest.kt` (AMPLIAR)

### Qué se hace

```
try { A } catch (e) { B }

    try Lcatch, e        // si algo falla, el mensaje va a e y se salta a Lcatch
    A
    endtry
    goto Lfin
Lcatch:
    B
Lfin:
```

`TryBegin` lleva la variable del `catch` desde la Fase 1 (decisión 37). Así el TAC dice
explícitamente a dónde va el mensaje del `throw`, en vez de dejarlo implícito en la
semántica.

**El contador de `try` abiertos.** Sube al emitir `try` y baja al emitir `endtry`.
Dentro del `catch` ya bajó: el `throw` que llevó ahí quitó el manejador. Un `break` o
un `continue` emite un `endtry` por cada `try` abierto desde que se entró al bucle:

```
while (c) { try { break; } catch (e) { } }

Linicio:
    ifFalse c goto Lfin
    try L2, e
    endtry               // el break abandona el try
    goto Lfin
    endtry
    goto L3
L2:
L3:
    goto Linicio
Lfin:
```

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `try { print(1); } catch (e) { print(e); }` | el esquema, con `try L, e` |
| `try { let q = a / b; } catch (e) { print(e); }` | el `throw` del chequeo de división queda dentro del bloque protegido |
| `break` dentro de un `try` dentro de un bucle | un `endtry` antes del `goto Lfin` |
| `break` dentro de un `catch` dentro de un bucle | ningún `endtry`: el manejador ya no está |
| Dos `try` anidados y un `break` en el interno | dos `endtry` |

---

## Ticket 3.5: El documento, secciones de control de flujo

- **Estado**: pendiente
- **Depende de**: 3.4, 2.4

**Archivos:**

- `docs/lenguaje-intermedio.md` (AMPLIAR)

| Sección | Contenido |
|---|---|
| 11. Condiciones | Código de saltos, el reparto de etiquetas en `&&`, `\|\|` y `!`, la caída y la inversión de la relación |
| 12. Sentencias de control | Los esquemas de `if`, `while`, `do-while` y `for`, y la pila de bucles para `break` y `continue` |
| 13. `switch`, ternario y booleanos como valor | La cadena de comparaciones, la materialización y por qué esas expresiones no comparten nodos del GDA |
| 14. `try/catch` | El esquema, la variable del `catch` y el `endtry` de `break` y `continue` |
| Supuestos | Se agregan: invertir una relación supone que no hay `NaN`; las etiquetas se emiten aunque nadie salte a ellas |

### Aceptación

- Un ejemplo por construcción, todos salida real del generador.
- La sección 11 muestra el mismo `if` con y sin caída, y dice cuántas instrucciones
  ahorra.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 3.1 | Las condiciones con cortocircuito y caída, los booleanos como valor y el ternario |
| 3.2 | `if`, `while`, `do-while`, `for`, y `break` y `continue` con su pila de bucles |
| 3.3 | El `switch` como cadena de comparaciones |
| 3.4 | El `try/catch` y el `endtry` al abandonar un `try` |
| 3.5 | Las secciones 11 a 14 del documento |
