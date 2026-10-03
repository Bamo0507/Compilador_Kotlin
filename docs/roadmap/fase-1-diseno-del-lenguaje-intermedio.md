# Fase 1: Diseño del lenguaje intermedio

**Objetivo de la fase:** definir el conjunto de instrucciones de tres direcciones como
modelo de Kotlin, su sintaxis textual, su vista de cuádruplo, y documentarlo. Es lo que
el generador de la Fase 2 va a producir y lo que la fase de assembler va a consumir.

**Por qué va antes que el generador:** es un cuello de botella deliberado, igual que
los modelos de la etapa anterior. Mientras el conjunto de instrucciones no esté
acordado, nadie puede traducir nada sin inventarse su propia forma de escribirlo. Y
vale 25 puntos por sí solo: *"definición y documentación del lenguaje intermedio, con
ejemplos y supuestos de traducción"*.

**Al terminar:** el modelo `Quadruple` con todas las familias que hoy se pueden
decidir, un printer que produce la sintaxis documentada, y `docs/lenguaje-intermedio.md`
con las secciones 2 a 7. **Cero traducción todavía**: eso es la Fase 2.

**Teoría que la sostiene:** puntos 3 y 4. Presentación 06, diapositivas 15 a 26;
Dragon Book 6.2.

**Lo que esta fase NO define, a propósito:** las instrucciones que dependen de puntos
de teoría todavía no estudiados. Entrar y salir de una función con su registro de
activación (punto 9), pedir memoria para `new` y las listas (puntos 10 y 11), y los
enlaces de acceso (punto 9). Cada una se agrega al modelo en su fase, con su sección
del documento.

---

## Ticket 1.1: Direcciones, etiquetas y cuádruplos

- **Estado**: completado
- **Depende de**: 0.2

**Archivos:**

- `frontend/intermediate/models/Address.kt` (NUEVO)
- `frontend/intermediate/models/Operators.kt` (NUEVO)
- `frontend/intermediate/models/Quadruple.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/QuadrupleTest.kt` (NUEVO)

### Las direcciones

Las tres clases de la diapositiva 15, más la etiqueta, que no es una dirección de un
valor sino de una instrucción:

```kotlin
// Lo que puede ir en un operando o en el resultado de un cuadruplo.
sealed interface Address

// Una variable del programa fuente. Guarda el Symbol y no el nombre: dos variables
// llamadas `x` en ambitos distintos son direcciones distintas.
data class Name(val symbol: Symbol) : Address

// Long, Double, String o Boolean; null para el literal `null`.
data class Constant(val value: Any?) : Address

// Inventado por el compilador. El indice lo asigna el TemporaryAllocator.
data class Temporary(val index: Int) : Address

// El destino de un salto. No es un Address: nunca se lee ni se escribe como valor.
data class Label(val index: Int)

// El punto de entrada de una funcion. Lleva nombre y no numero porque la llamada lo
// nombra: `call factorial, 1`.
data class FunctionLabel(val name: String)
```

**Por qué `Name` guarda el `Symbol`:** el AST ya resolvió cada identificador a su
`Symbol` (`Identifier.resolvedSymbol`). Guardarlo evita volver a resolver nombres, y
es lo que va a permitir, con la tabla de símbolos extendida, imprimir el
desplazamiento de la variable en su registro de activación en vez de su nombre.

### Los operadores, con su tipo

En assembler, sumar enteros y sumar flotantes son instrucciones distintas, así que el
TAC tiene que decir de qué tipo es cada operación (decisión 27). El operador y el tipo
de los operandos van en campos separados:

```kotlin
enum class ArithmeticOperator(val symbol: String) {
    ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/"), MODULO("%")
}

enum class RelationalOperator(val symbol: String) {
    LESS("<"), LESS_EQUAL("<="), GREATER(">"), GREATER_EQUAL(">="),
    EQUAL("=="), NOT_EQUAL("!=")
}

enum class TacUnaryOperator(val symbol: String) {
    NEGATE("-"), NOT("!"), INT_TO_FLOAT("inttofloat")
}

// De que tipo son los operandos. Decide que instruccion de maquina corresponde.
enum class OperandKind(val suffix: String) {
    INTEGER(""), FLOAT("f"), STRING("s"), BOOLEAN(""), REFERENCE("")
}
```

**Por qué enums propios y no el `BinaryOperator` del AST:** el del AST incluye `&&` y
`||`, que en el TAC no son operaciones sino saltos (punto 6). Con enums propios, un
cuádruplo con `&&` no se puede escribir. El unario lleva el prefijo `Tac` porque el
AST ya tiene un `UnaryOperator`, y dos enums con el mismo nombre en paquetes
distintos obligarían a importar con alias.

**Por qué `INT_TO_FLOAT` es un operador unario:** es una operación de un operando que
produce un valor, exactamente la forma `x = op y`. Es como lo trata el Dragon Book
(6.5.2).

### Las familias

Una `data class` por familia del punto 3 (decisión 25). Cada una tiene como máximo
cuatro campos con contenido, así que sigue siendo un cuádruplo:

```kotlin
sealed interface Quadruple

// x = y op z
data class Arithmetic(
    val result: Address, val left: Address, val operator: ArithmeticOperator,
    val right: Address, val kind: OperandKind
) : Quadruple

// x = y relop z: un booleano como valor, no como salto.
data class Relational(
    val result: Address, val left: Address, val operator: RelationalOperator,
    val right: Address, val kind: OperandKind
) : Quadruple

// x = y concat z. Aparte de Arithmetic porque no es aritmetica: en la maquina es una
// llamada a una rutina que reserva memoria para el string nuevo.
data class Concat(val result: Address, val left: Address, val right: Address) : Quadruple

// x = op y
data class Unary(
    val result: Address, val operator: TacUnaryOperator, val operand: Address,
    val kind: OperandKind
) : Quadruple

// x = y
data class Copy(val result: Address, val source: Address) : Quadruple

// L:
data class LabelDefinition(val label: Label) : Quadruple

// goto L
data class Goto(val label: Label) : Quadruple

// if x goto L    /    ifFalse x goto L
data class IfGoto(val condition: Address, val label: Label) : Quadruple
data class IfFalseGoto(val condition: Address, val label: Label) : Quadruple

// if x relop y goto L
data class IfRelationalGoto(
    val left: Address, val operator: RelationalOperator, val right: Address,
    val kind: OperandKind, val label: Label
) : Quadruple

// param x    /    call p, n    /    y = call p, n    /    return y
data class Param(val value: Address) : Quadruple
data class Call(val result: Address?, val function: FunctionLabel, val argumentCount: Int) : Quadruple
data class Return(val value: Address?) : Quadruple

// x = y[i]    /    x[i] = y.   El indice es un desplazamiento en bytes.
data class IndexedLoad(val result: Address, val base: Address, val offset: Address) : Quadruple
data class IndexedStore(val base: Address, val offset: Address, val value: Address) : Quadruple

// print x
data class Print(val value: Address, val kind: OperandKind) : Quadruple

// try L, e    /    endtry    /    throw x
// TryBegin lleva la variable del catch: el TAC dice a donde va el mensaje del throw.
data class TryBegin(val handler: Label, val exceptionVariable: Address) : Quadruple
data object TryEnd : Quadruple
data class Throw(val message: Address) : Quadruple
```

**Por qué `Call` apunta a un `FunctionLabel`:** una función es, para el TAC, la
etiqueta donde empieza su código. Es un tipo aparte de `Label` porque se nombra y no
se numera, y porque no se puede saltar a una función con `goto`. Cómo se forma ese
nombre (funciones anidadas, métodos de clases distintas con el mismo nombre) se decide
en la Fase 4.

**Por qué `TryEnd` es `data object`:** no lleva datos. Es la única instrucción así.

### Aceptación

- Un `when` sobre `Quadruple` sin rama `else` compila solo si cubre las 19 familias.
- No se puede construir un cuádruplo aritmético con `&&`: no existe en el enum.
- Dos `Name` de dos `Symbol` distintos con el mismo nombre no son iguales.
  *Test explícito.*
- `./gradlew test` en verde.

---

## Ticket 1.2: La sintaxis textual y la vista de cuádruplo

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `frontend/intermediate/TacPrinter.kt` (NUEVO)
- `frontend/intermediate/models/QuadrupleRow.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/TacPrinterTest.kt` (NUEVO)

### La sintaxis

Una línea por cuádruplo. Las etiquetas van pegadas al margen y el resto con sangría,
como en la diapositiva 22:

| Familia | Sintaxis | Ejemplo |
|---|---|---|
| `Arithmetic` | `x = y op<tipo> z` | `t1 = a + b`, `t2 = t1 *f 2.5` |
| `Relational` | `x = y relop<tipo> z` | `t1 = a < b`, `t2 = s ==s "hola"` |
| `Concat` | `x = y concat z` | `t1 = "Hola " concat nombre` |
| `Unary` | `x = op<tipo> y` | `t1 = - a`, `t2 = -f x`, `t3 = ! b`, `t4 = inttofloat i` |
| `Copy` | `x = y` | `contador = t1` |
| `LabelDefinition` | `L:` | `L1:` |
| `Goto` | `goto L` | `goto L1` |
| `IfGoto` / `IfFalseGoto` | `if x goto L` / `ifFalse x goto L` | `ifFalse t1 goto L2` |
| `IfRelationalGoto` | `if x relop<tipo> y goto L` | `if i < n goto L1` |
| `Param` | `param x` | `param t1` |
| `Call` | `call L, n` / `x = call L, n` | `t2 = call factorial, 1` |
| `Return` | `return` / `return x` | `return t3` |
| `IndexedLoad` / `IndexedStore` | `x = y[i]` / `x[i] = y` | `t3 = lista[t2]`, `lista[t2] = t1` |
| `Print` | `print_<tipo> x` | `print_i t1`, `print_s "fin"` |
| `TryBegin` / `TryEnd` / `Throw` | `try L, e` / `endtry` / `throw x` | `try L4, e`, `throw "División entre cero"` |

**El sufijo de tipo:** `f` para `float` y `s` para `string`; los enteros, booleanos y
referencias van sin sufijo. Así el caso más común, el entero, se lee igual que en las
diapositivas. `print` usa un sufijo con nombre (`_i`, `_f`, `_s`, `_b`) porque cambia
la llamada al sistema que hará la fase de assembler, y un `print` sin sufijo sería
ambiguo.

**Las constantes:** los números tal cual; los strings entre comillas dobles; `true`,
`false` y `null` como palabras.

**Los nombres:** por ahora, el nombre del `Symbol`. Ver *"Lo que queda abierto"*.

### La vista de cuádruplo

La tabla de cuatro columnas de la diapositiva 25, para la GUI y la documentación:

```kotlin
data class QuadrupleRow(
    val operator: String,
    val argument1: String,
    val argument2: String,
    val result: String
)

fun Quadruple.toRow(): QuadrupleRow = when (this) { ... }
```

Ejemplos de cómo cae cada familia en las columnas:

| Cuádruplo | operador | arg1 | arg2 | resultado |
|---|---|---|---|---|
| `t2 = b * t1` | `*` | `b` | `t1` | `t2` |
| `t1 = - c` | `-` | `c` | | `t1` |
| `a = t5` | `=` | `t5` | | `a` |
| `ifFalse t1 goto L2` | `ifFalse` | `t1` | | `L2` |
| `t3 = lista[t2]` | `=[]` | `lista` | `t2` | `t3` |
| `param t1` | `param` | `t1` | | |

**Por qué el printer está aparte del modelo:** `Quadruple` es datos; cómo se escribe es
una vista. Es la misma regla que separa `Type` de `TypeRules`.

### Aceptación

- Un test por familia que fija su línea de texto exacta.
- El programa de la diapositiva 24, armado a mano como lista de cuádruplos, imprime
  exactamente su TAC, y su `toRow()` reproduce la tabla de la diapositiva 25.
- `toRow()` no tiene rama `else`.

---

## Ticket 1.3: El documento del lenguaje intermedio, secciones 2 a 7

- **Estado**: pendiente
- **Depende de**: 1.2, 0.5

**Archivos:**

- `docs/lenguaje-intermedio.md` (AMPLIAR)

Cada sección cita el punto de teoría, la diapositiva o la sección del Dragon Book en
que se apoya, y la decisión del README que la cierra.

| Sección | Contenido |
|---|---|
| 2. Direcciones | Las tres clases y la etiqueta. Por qué `Name` guarda el símbolo |
| 3. Instrucciones | La tabla de la sintaxis del ticket 1.2, con una frase por familia sobre qué hace en ejecución |
| 4. Tipos en las operaciones | El sufijo de tipo, `inttofloat` y `concat`. El ejemplo `1 + 2.5` |
| 5. Etiquetas y saltos | Etiquetas simbólicas sin backpatching, con el razonamiento de la decisión 24 y el ejemplo `if` contra `do-while` |
| 6. Errores en ejecución | `try`, `endtry` y `throw`: la semántica de manejador, por qué el `throw` encuentra el manejador aunque esté varias llamadas abajo, y qué se deja a la fase de assembler |
| 7. Representación interna | Cuádruplos y no tripletas (decisión 25), con la tabla de la diapositiva 25 generada por `toRow()` |
| Supuestos | Una lista que crece con cada fase. Arranca con: un entero es de 32 bits; no hay recursión ilimitada; una instrucción tiene como máximo un operador |

### Aceptación

- Cada familia del modelo aparece en la sección 3 con un ejemplo.
- Los ejemplos del documento son salida real del `TacPrinter`, no texto escrito a mano.
- No hay ninguna instrucción en el documento que no exista en `Quadruple.kt`.

---

## Lo que queda abierto

- **Variables con el mismo nombre en ámbitos distintos.** En el texto, `x` del bloque y
  `x` de afuera se imprimen igual aunque sean dos `Name` distintos. La regla ya está
  decidida (decisión 41: `x` y `x@3`, solo cuando hay ambigüedad) y se implementa en la
  Fase 4, junto con la tabla de símbolos extendida. Hasta entonces, los ejemplos del
  documento evitan el shadowing.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 1.1 | `Address`, `Label`, los operadores con tipo y las 19 familias de `Quadruple` |
| 1.2 | La sintaxis textual y la tabla de cuádruplo |
| 1.3 | Las secciones 2 a 7 del documento, con ejemplos generados |
