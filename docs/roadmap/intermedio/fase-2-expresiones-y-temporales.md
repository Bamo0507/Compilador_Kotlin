# Fase 2: Expresiones y temporales

**Objetivo de la fase:** el generador de TAC existe, corre como etapa del pipeline, y
traduce expresiones, declaraciones, asignaciones a variables y `print`, pasando por un
GDA por expresión y reciclando temporales con un pool con conteo de usos.

**Por qué este alcance y no más:** las expresiones son la base de todo lo demás. Una
condición de `while`, un argumento de llamada o un índice de lista son expresiones, y
todas pasan por el mismo GDA y el mismo pool. Lo que necesita saltos (`&&`, `||`, el
ternario y el control de flujo) es del punto 6; las llamadas, del punto 9; los
objetos y las listas, del punto 10.

**Al terminar:** `print(a + a * (b - c) + (b - c) * d)` genera TAC con dos
temporales donde la diapositiva 19 usa cinco, y una división entre una variable
emite su chequeo.

**Teoría que la sostiene:** puntos 2 y 5. Presentación 06, diapositivas 10 a 14 y 19;
Dragon Book 6.1, 6.4 y 6.5.

---

## Ticket 2.1: `TemporaryAllocator`, el pool con conteo de usos

- **Estado**: completado
- **Depende de**: 1.1

**Archivos:**

- `frontend/intermediate/TemporaryAllocator.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/TemporaryAllocatorTest.kt` (NUEVO)

### Qué se hace

```kotlin
/**
 * Entrega temporales y los recicla cuando su ultimo lector los consume.
 *
 * Un contador alcanza mientras los temporales mueren en orden de pila, que es lo que
 * pasa en un arbol. En un GDA un nodo compartido tiene varios lectores y puede morir
 * despues de temporales mas nuevos, asi que hace falta saber cuantos lectores le
 * quedan a cada uno.
 */
class TemporaryAllocator {

    // Los indices libres, de menor a mayor: se reutiliza siempre el mas bajo.
    private val free = sortedSetOf<Int>()

    private var nextIndex = 1

    private val remainingUses = mutableMapOf<Int, Int>()

    // `uses` es cuantas veces se va a leer. En un arbol siempre es 1; en el GDA, la
    // cantidad de aristas que llegan al nodo.
    fun newTemp(uses: Int): Temporary {
        val index = free.pollFirst() ?: nextIndex++
        remainingUses[index] = uses
        return Temporary(index)
    }

    // Se llama cada vez que una instruccion lee el operando, ANTES de pedir el
    // temporal de su resultado: asi el resultado puede reutilizar el nombre.
    fun consume(address: Address) {
        if (address !is Temporary) return
        val left = remainingUses.getValue(address.index) - 1
        if (left == 0) {
            remainingUses.remove(address.index)
            free.add(address.index)
        } else {
            remainingUses[address.index] = left
        }
    }

    // Cuantos temporales distintos hicieron falta: la medida que el enunciado pide
    // minimizar.
    val temporaryCount: Int get() = nextIndex - 1

    // Al terminar una sentencia no debe quedar ninguno vivo.
    val hasLiveTemporaries: Boolean get() = remainingUses.isNotEmpty()
}
```

### Por qué

**Por qué un pool y no el contador del punto 5:** con un GDA, el contador genera código
que pisa valores vivos (decisión 31). En un árbol puro, donde todo temporal tiene
`uses = 1`, el pool entrega exactamente los mismos nombres que el contador, así que no
se pierde nada de la explicación clásica: el contador es el caso particular.

**Por qué el índice libre más bajo:** hace el resultado determinista, que es lo que
permite fijar el TAC exacto en un test, y reproduce el `t1 = t1 * t2` del ejemplo del
punto 5.

### Aceptación

| Secuencia | Resultado esperado |
|---|---|
| `(a + b) * (c + d)` con `uses = 1` en todos | usa `t1` y `t2`; el resultado reutiliza `t1` |
| `a + b * c - d` con `uses = 1` | usa solo `t1` |
| Un temporal con `uses = 2` consumido una vez | sigue vivo: el siguiente `newTemp` no lo entrega |
| El mismo, consumido dos veces | queda libre |
| Liberar `t1` antes que `t2` y pedir uno nuevo | entrega `t1` |
| `consume` sobre un `Name` o un `Constant` | no hace nada |
| El caso del bug del punto 5: un resultado vivo mientras se calculan dos temporales más | ningún temporal vivo se vuelve a entregar |

---

## Ticket 2.2: `ExpressionDag`, el GDA de una expresión

- **Estado**: completado
- **Depende de**: 1.1

**Archivos:**

- `frontend/intermediate/ExpressionDag.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/ExpressionDagTest.kt` (NUEVO)

### Qué se hace

Convierte una `Expression` del AST, ya decorada con tipos, en un GDA construido con el
método del número de valor (diapositiva 14). Los nodos viven en una lista y su índice
es su número de valor; una tabla hash con la llave del nodo contesta *"¿ya existe?"*.

```kotlin
// El numero de valor de un nodo es su indice en `nodes`.
sealed interface DagNode {
    // Una variable o una constante: no genera instruccion.
    data class Leaf(val address: Address) : DagNode

    // Una operacion. `operands` son numeros de valor de otros nodos.
    data class Operation(val operation: DagOperation, val operands: List<Int>) : DagNode
}

// Lo que distingue a dos operaciones. Es la parte "op" de la llave <op, izq, der>.
sealed interface DagOperation {
    data class Arithmetic(val operator: ArithmeticOperator, val kind: OperandKind) : DagOperation
    data class Relational(val operator: RelationalOperator, val kind: OperandKind) : DagOperation
    data class Unary(val operator: TacUnaryOperator, val kind: OperandKind) : DagOperation
    data object Concat : DagOperation
}

class ExpressionDag private constructor(
    val nodes: List<DagNode>,
    val root: Int,

    // Cuantas aristas llegan a cada nodo: los `uses` del TemporaryAllocator.
    val parentCount: List<Int>
) {
    companion object {
        fun build(expression: Expression): ExpressionDag { ... }
    }
}
```

**La construcción, nodo por nodo:**

1. Si la expresión tiene `constantValue` (la plegó el `TypeChecker`), es una hoja
   `Constant`, sin importar cuántos nodos tenía debajo.
2. Un `Literal` es una hoja `Constant`; un `Identifier`, una hoja `Name` con su
   `resolvedSymbol`.
3. Una operación construye primero sus hijos, de abajo hacia arriba, y después busca
   su llave `(operación, números de los hijos)` en la tabla. Si existe, devuelve ese
   número; si no, agrega el nodo.
4. **Las conversiones implícitas son nodos.** Con `x: integer`, `x + 2.5` produce
   `Unary(INT_TO_FLOAT)` sobre `x` y después `Arithmetic(ADD, FLOAT)`. Al ser
   nodos, también se comparten: `x + 2.5` dos veces convierte `x` una sola vez.
5. El tipo de cada operación sale del AST decorado: `expr.type == FloatType` da
   `OperandKind.FLOAT`, una suma de strings da `Concat`, y una comparación toma el tipo
   de sus operandos ya ensanchados.

**Cuándo NO se comparte (decisión 30):** si la expresión contiene una llamada a
función o una asignación anidada, se construye con la tabla hash desactivada. Cada
nodo es nuevo, y el GDA queda igual al árbol.

```kotlin
// a * b + f() + a * b: f puede modificar a o b, y las dos a * b no son el mismo valor.
// (x = 5) + x: la x de la derecha no es la x de antes de la asignacion.
```

Las hojas se comparten igual en los dos modos, porque no generan instrucción: leer `a`
dos veces es leer la misma dirección.

### Por qué

**Por qué se construye desde el AST y no sobre el TAC:** es lo que muestran las
diapositivas 12 y 13: la misma definición dirigida por la sintaxis que construye el
árbol, con un `new` que reutiliza. Un GDA sobre el TAC por bloque básico (Dragon Book
8.5) es una optimización aparte, con invalidación en cada asignación, y encaja mejor en
la fase de assembler.

**Por qué apagar la tabla y no buscar un criterio más fino:** saber qué nodos
dependen de una variable que una llamada pudo cambiar es análisis de efectos. Apagar
la tabla para toda la expresión es una regla de una línea, y una expresión con
llamadas rara vez repite subexpresiones.

### Aceptación

| Expresión | Resultado esperado |
|---|---|
| `a + a * (b - c) + (b - c) * d` | 9 nodos; `b - c` con `parentCount = 2`; `a` con `parentCount = 2`. *Es la diapositiva 13* |
| `(x + y) * (x + y)` | el nodo `x + y` con `parentCount = 2`, las dos aristas desde el mismo padre |
| `x + 2.5` con `x: integer` | un nodo `INT_TO_FLOAT` entre `x` y la suma |
| `3 + 5` | una sola hoja `Constant(8)` |
| `a * b + f() + a * b` | las dos `a * b` son nodos distintos |
| `(x = 5) + x` | sin compartir ninguna operación |

---

## Ticket 2.3: `TacGenerator` y la etapa G del pipeline

- **Estado**: completado
- **Depende de**: 1.2, 2.1, 2.2

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (NUEVO)
- `frontend/intermediate/models/TacProgram.kt` (NUEVO)
- `runtime/CompilerPipeline.kt` (MODIFICAR: etapa G)
- `runtime/models/CompilationResult.kt` (MODIFICAR: campo `tac`)
- `app/src/test/kotlin/org/compiler/TacGeneratorExprTest.kt` (NUEVO)

### El resultado

```kotlin
data class TacProgram(
    val instructions: List<Quadruple>,

    // Cuantos temporales distintos hicieron falta.
    val temporaryCount: Int
)
```

### La etapa G

```kotlin
// Etapa G: codigo intermedio, solo si no quedo ningun error. Corre aparte de la
// ejecucion: una no depende de la otra.
val tac = if (!diagnostics.hasErrors) TacGenerator().generate(ast) else null
```

### El generador

Una función por construcción, igual que el `TypeChecker` y el `Interpreter`. Las
sentencias no devuelven nada; las expresiones devuelven la `Address` donde quedó su
valor.

```kotlin
class TacGenerator {
    private val instructions = mutableListOf<Quadruple>()
    private val temporaries = TemporaryAllocator()
    private var nextLabel = 1

    fun generate(program: Program): TacProgram

    private fun generateStatement(stmt: Statement)
    private fun generateExpression(expr: Expression): Address
}
```

**Las sentencias de esta fase:**

| Sentencia | Traducción |
|---|---|
| `let x: integer = e;` y `const` | el código de `e`, luego `x = <dirección de e>` |
| `let x: integer;` | `x = 0`: el valor por defecto de su tipo, el mismo que usa el intérprete |
| `x = e;` | el código de `e`, luego `x = <dirección>` |
| `print(e);` | el código de `e`, luego `print_<tipo> <dirección>` |
| `e;` | el código de `e`, y su dirección se consume |
| `{ ... }` | las sentencias de adentro, en orden. El bloque no genera nada propio: las variables ya son `Name` con su `Symbol` |

**Las expresiones** se traducen a través del GDA: `ExpressionDag.build(expr)`, y
después se emite el GDA en postorden desde la raíz. Cada nodo `Operation` se emite una
sola vez y se recuerda su dirección. Cada vez que un padre lee la dirección de un hijo,
se llama a `consume`, y el temporal de cada nodo se pide con
`uses = parentCount[nodo]`.

Una asignación anidada, `x = (y = 5)`, emite `y = 5` y devuelve `Name(y)`: el valor de
`y = 5` es lo que quedó en `y`.

**El chequeo de división entre cero (decisión 29)**, al emitir un `/` o un `%` cuyo
divisor no es una constante distinta de cero:

```
    if t2 != 0 goto L3
    throw "División entre cero (línea 7)"
L3:
    t3 = t1 / t2
```

Va en línea, junto a la división, para que el mensaje lleve la línea del fuente. Si el
divisor es una constante distinta de cero no se emite: el `TypeChecker` ya rechazó el
cero constante.

**Lo que esta fase todavía no traduce:** `&&`, `||` y el ternario (punto 6), el
control de flujo (punto 6), las funciones y las llamadas (punto 9), y los objetos, las
listas, `this` y la asignación a campos o elementos (punto 10). Esas ramas del `when`
son `TODO("<construcción>")`. El pipeline atrapa el `NotImplementedError` y deja
`tac = null`, para que el IDE no se caiga con un programa que todavía no se puede
traducir. Esas ramas desaparecen con sus fases, y la última fase verifica que no
quede ningún `TODO`.

### Aceptación

| Programa | TAC esperado |
|---|---|
| `print(3 + 5);` | `print_i 8` |
| `let x: integer = a + b * c - d;` | `t1 = b * c`, `t1 = a + t1`, `t1 = t1 - d`, `x = t1`. Un temporal |
| `let r: integer = a + a * (b - c) + (b - c) * d;` | `t1 = b - c`, `t2 = a * t1`, `t2 = a + t2`, `t1 = t1 * d`, `t1 = t2 + t1`, `r = t1`. **Dos temporales donde la diapositiva 19 usa cinco** |
| `let f: float = x + 2.5;` con `x: integer` | `t1 = inttofloat x`, `t1 = t1 +f 2.5`, `f = t1` |
| `let s: string = "a" + nombre;` | `t1 = "a" concat nombre`, `s = t1` |
| `let b: boolean = x < y;` | `t1 = x < y`, `b = t1` |
| `let q: integer = a / b;` | el chequeo con `if b != 0 goto`, el `throw` y la división |
| `let q: integer = a / 2;` | sin chequeo |
| Un programa con un `while` | `tac == null`, sin excepción hacia el IDE |
| Un programa con errores semánticos | `tac == null` |

- Al terminar cada sentencia, `hasLiveTemporaries` es falso. *Se verifica en todos
  los tests.*

---

## Ticket 2.4: El documento, secciones 8 a 10

- **Estado**: completado
- **Depende de**: 2.3, 1.3

**Archivos:**

- `docs/lenguaje-intermedio.md` (AMPLIAR)

| Sección | Contenido |
|---|---|
| 8. Temporales | El contador y su invariante, por qué el GDA lo rompe, y el pool con conteo de usos como generalización. El ejemplo del bug de pedir antes de liberar |
| 9. GDA | El número de valor, cuándo se comparte y cuándo no, y la diferencia con un GDA por bloque básico |
| 10. Expresiones y sentencias simples | La tabla de traducción del ticket 2.3, el ejemplo de la diapositiva 19 contra el nuestro, las conversiones implícitas y el chequeo de división |
| Supuestos | Se agregan: una expresión con llamadas o asignaciones anidadas no comparte subexpresiones; una variable sin inicializar arranca en el cero de su tipo |

### Aceptación

- Los ejemplos son salida real del generador.
- La sección 8 muestra el mismo ejemplo con el contador y con el pool, y en un árbol
  dan el mismo resultado.

---

## Decisiones de esta fase

- **Se usa el plegado de constantes del `TypeChecker` (decisión 32).** `print(3 + 5)`
  genera `print_i 8`. El costo es que los ejemplos con literales no muestran la
  traducción, así que el documento usa variables.
- **Se conserva la copia final (decisión 33).** `x = a + b` genera `t1 = a + b` y
  `x = t1`, como la diapositiva 24 (`a = t5`). Escribir directo `x = a + b` ahorraría
  una instrucción, pero es una optimización que la teoría no muestra.

---

## Lo que cambió al implementar

La fase se implementó como estaba planeada, con cuatro diferencias:

- **El chequeo de división solo se emite para enteros (decisión 51).** En flotantes,
  dividir entre cero da infinito, que es un valor legítimo, y es lo mismo que hace el
  intérprete.
- **Las asignaciones anidadas tienen su propio nodo en el GDA, `Assign` (decisión 52).**
  Nunca se comparte, y su valor es la variable asignada. Al implementarlo apareció un caso
  que el plan no vio: en `x + (x = 5)`, la `x` de la izquierda es una hoja que no genera
  instrucción, así que se leería después de la asignación. El generador la copia antes a
  un temporal, y el resultado coincide con el intérprete.
- **Lo que todavía no se traduce entra al GDA como un nodo `Untranslated`**, que el
  generador convierte en un `TODO`. Cumple el papel que el ticket 3.1 le asigna al nodo
  `Opaque`. En la Fase 3 se convierte en la jerarquía `Subexpression` (decisión 36).
- **`VariableDeclaration` guarda su `Symbol`**, que lo llena el `TypeChecker` al
  declararla. Así el generador escribe en la variable sin volver a buscar su nombre, igual
  que `Identifier` guarda su `resolvedSymbol`. Los campos de clase no lo reciben, porque
  los declara la Pasada 1: la fase de objetos los busca en el ámbito de la clase.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 2.1 | El pool de temporales con conteo de usos |
| 2.2 | El GDA de una expresión, con el número de valor y las conversiones como nodos |
| 2.3 | El generador como etapa G, con expresiones, sentencias simples y el chequeo de división |
| 2.4 | Las secciones 8 a 10 del documento |
