# Fase 1: Cambios al lenguaje

**Objetivo de la fase:** que Compiscript tenga listas que crecen, como un ArrayList, y
que el TAC ya traiga su forma final en memoria antes de empezar a traducir a ARM.
Además, que el intérprete imprima los flotantes con el formato que va a usar el ARM.

**Por qué va antes del backend:** el backend traduce el TAC (decisión 60). Si las
listas cambiaran de forma a mitad del backend, habría que rehacer la traducción de
cada acceso. Y el intérprete es el oráculo de la batería de punta a punta: su salida
tiene que poder coincidir con la del ARM.

**Al terminar:** `lista.push(x)` y `lista.length` funcionan en el semántico, el
intérprete y el TAC; las listas son una ficha y un contenido; los flotantes se
imprimen con seis decimales sin ceros sobrantes; todos los `.tac` dorados están
regenerados y revisados.

**Teoría que la sostiene:** punto 11, memoria dinámica (Dragon Book 7.4; presentación
07, el montículo).

---

## Ticket 1.1: `push` y `length` en el semántico

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `frontend/ast/models/Expression.kt` (MODIFICAR: `PropertyAccess.listOperation`)
- `frontend/semantic/TypeChecker.kt` (MODIFICAR: `checkPropertyAccess`)
- `resources/programas/invalidos/` (NUEVOS: `lista_push_tipo.cps`,
  `lista_length_escritura.cps`, `lista_push_como_valor.cps`)
- Tests: `TypeCheckerCallsTest` (AMPLIAR)

### Qué es esto, en simple

La gramática no cambia: `lista.push(4)` ya se lee como una llamada sobre un acceso a
propiedad, igual que `perro.hablar()`, y `lista.length` como un acceso a propiedad. Lo
que cambia es que el verificador de tipos hoy rechaza `.algo` sobre cualquier cosa que
no sea un objeto. Ahora, sobre una lista, acepta exactamente esos dos nombres.

### Qué se hace

`PropertyAccess` gana un campo que pone el `TypeChecker`, igual que `resolvedMember`:

```kotlin
// Solo cuando el objetivo es una lista: cual de sus dos operaciones es.
var listOperation: ListOperation? = null

enum class ListOperation { PUSH, LENGTH }
```

En `checkPropertyAccess`, antes del chequeo de `ClassType`:

```kotlin
if (targetType is ArrayType) return checkListOperation(expr, targetType, isCallee, isWrite)
```

| Uso | Resultado |
|---|---|
| `lista.push(x)` como llamada | Tipo `(elemento) -> void`; `checkArguments` valida `x` con la misma regla de asignación, así que un `integer` entra a una lista de `float` |
| `lista.length` como valor | `integer` |
| `lista.length = 3` | Error: "'length' es de solo lectura" |
| `lista.length()` | Error: "'length' no es una función" |
| `lista.push` sin llamarlo, o `let n = lista.push(1)` | Error: "'push' solo se puede llamar" y el de siempre de `void` como valor |
| `lista.otra` | Error: "Una lista solo tiene 'push' y 'length'" |

### Por qué

**Un campo en `PropertyAccess` y no nodos nuevos del AST:** el `AstBuilder` no sabe
tipos, así que no puede distinguir `perro.length` de `lista.length`. El que lo sabe es
el `TypeChecker`, y deja la marca en el nodo, como ya hace con `resolvedMember`.

**`push` no devuelve nada:** en TypeScript devuelve el largo nuevo, pero ningún
programa lo necesita y un valor de retorno obliga al backend a calcularlo siempre.

### Aceptación

- Los cinco casos de la tabla tienen su test.
- Los tres `.cps` inválidos fallan con el mensaje esperado.
- Una clase con un método llamado `push` sigue funcionando: la regla solo aplica
  sobre listas.

---

## Ticket 1.2: El intérprete: `push`, `length` y el formato de flotantes

- **Estado**: completado
- **Depende de**: 1.1

**Archivos:**

- `interpreter/Interpreter.kt` (MODIFICAR: `evaluateFunctionCall`, `memberOf`)
- `interpreter/RuntimeValue.kt` (MODIFICAR: `FloatValue.display`)
- Tests: `InterpreterTest` (AMPLIAR)

### Qué se hace

`ArrayValue` ya guarda un `MutableList`, así que `push` es un `add` y `length` es
`size`. El chequeo de `null` sobre la lista es el mismo que ya hace el acceso por
índice.

El formato de flotante (decisión 71):

```kotlin
// Seis decimales sin los ceros sobrantes, y al menos uno: 3.5, 1.0, 0.333333.
// Es el formato que produce el runtime de ARM, para que las dos salidas coincidan.
override fun display(): String =
    String.format(Locale.ROOT, "%.6f", value).trimEnd('0').let {
        if (it.endsWith('.')) "${it}0" else it
    }
```

**La conversión a `float`:** el TAC convierte con `inttofloat` cada entero que se guarda
donde se declaró `float`, pero el intérprete guardaba el entero tal cual, y
`let x: float = 3; print(x);` imprimía `3` donde la máquina imprime `3.0`. Un ayudante
`convertedTo` hace la conversión en los mismos lugares que el TAC: declaración,
asignación (variable, campo y elemento), argumento, `return`, elemento de una lista
literal, inicializador de campo y `push`.

### Por qué

**`Locale.ROOT`:** sin él, una máquina configurada en español imprimiría `3,5`.

### Aceptación

- `push` sobre una lista vacía, sobre una de flotantes con un entero y sobre `null`
  (error en ejecución) tienen su test.
- `print(1.0 / 3.0)` imprime `0.333333`; `print(2.0)` imprime `2.0`.
- Todos los programas válidos siguen corriendo; los que imprimían flotantes se revisan
  a mano.

---

## Ticket 1.3: Listas con ficha y contenido en el TAC

- **Estado**: pendiente
- **Depende de**: ninguno

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR: `generateNewList`,
  `elementOffset`, `generateElementRead`, `generateElementStore`, `generateForEach`)
- Tests: `TacGeneratorListTest` (MODIFICAR)

### Qué es esto, en simple

Hoy una lista es un solo bloque: el largo y después los elementos. Si creciera, habría
que mudar el bloque entero, y cualquier variable que apuntara a él quedaría apuntando a
la casa vieja. Con la ficha separada, las variables apuntan a la ficha, que no se mueve
nunca, y solo se muda el contenido (decisión 65).

### Qué se hace

| Desplazamiento en la ficha | Campo |
|---|---|
| 0 | longitud |
| 4 | capacidad |
| 8 | dirección del contenido |

El contenido empieza en su desplazamiento 0, porque `malloc` entrega bloques alineados
a 8: un flotante ya no necesita el relleno que pedía la decisión 42.

`[10, 20, 30]` pasa a ser:

| TAC | Qué hace |
|---|---|
| `t1 = alloc 12` | La ficha |
| `t1[0] = 3` | longitud |
| `t1[4] = 4` | capacidad: el máximo entre la cantidad y 4 |
| `t2 = alloc 16` | El contenido: capacidad por tamaño |
| `t1[8] = t2` | La ficha apunta al contenido |
| `t2[0] = 10`, `t2[4] = 20`, `t2[8] = 30` | Los elementos |

Una lista vacía tiene capacidad 0 y contenido `null`: su tipo es `null[]` hasta que se
asigna, así que el generador no conoce el tamaño de sus elementos. El primer `push`
pide el contenido, porque `realloc` de un puntero nulo es un `malloc`.

Leer `lista[i]` agrega una lectura: `t1 = lista[8]` trae el contenido, y el elemento es
`t1[i * 4]`. El chequeo de rango sigue comparando con `lista[0]`. El `foreach` lee el
contenido en cada vuelta, porque el cuerpo puede hacer `push` y mudarlo.

### Por qué

**Releer el contenido en cada vuelta del `foreach`:** guardarlo en una local oculta
sería un acceso menos, pero un `push` dentro del cuerpo dejaría la local apuntando al
contenido viejo. Es exactamente el puntero colgante que la ficha quiere evitar.

### Aceptación

- Los tests de listas reflejan la ficha y el contenido, con lectura, escritura,
  matriz, `foreach` y lista vacía.
- Un `foreach` con un `push` en el cuerpo recorre los elementos nuevos, igual que el
  intérprete.

---

## Ticket 1.4: `push` y `length` en el TAC

- **Estado**: pendiente
- **Depende de**: 1.1, 1.3

**Archivos:**

- `frontend/intermediate/models/Quadruple.kt` (MODIFICAR: `ListPush`)
- `frontend/intermediate/TacPrinter.kt` y `models/QuadrupleRow.kt` (MODIFICAR)
- `frontend/intermediate/TacGenerator.kt` (MODIFICAR: `generateCall`, lectura de
  propiedad)
- Tests: `TacGeneratorListTest`, `TacPrinterTest`, `QuadrupleTest` (AMPLIAR)

### Qué se hace

`length` no necesita instrucción nueva: es el chequeo de `null` y `t1 = lista[0]`.

`push` es una instrucción propia:

```kotlin
// push_<tipo> lista, x: agrega x al final, y si el contenido esta lleno lo muda a uno
// del doble de capacidad.
data class ListPush(
    val list: Address,
    val value: Address,
    val kind: OperandKind
) : Quadruple
```

`lista.push(n + 1)` en una lista de enteros:

| TAC |
|---|
| `t1 = n + 1` |
| `if lista != null goto L1` |
| `throw "Acceso a null (línea 5)"` |
| `L1:` |
| `push_i lista, t1` |

### Por qué

**Instrucción propia y no una secuencia de TAC:** crecer es comparar, pedir memoria
nueva y copiar, que en la máquina es una rutina del runtime, igual que `concat`
(decisión 47). Escribirlo en TAC exigiría además una instrucción `realloc` que solo
usaría esto. El sufijo de tipo es por la misma razón que el de `print`: guardar un
flotante y un entero son instrucciones distintas.

### Aceptación

- `push_i`, `push_f`, `push_b` y `push_r` (referencias) se imprimen y tienen su fila en
  la tabla de cuádruplos.
- `lista.length` genera el chequeo de `null` y la lectura de la casilla 0.

---

## Ticket 1.5: El tipo de cada operando en el TAC

- **Estado**: pendiente
- **Depende de**: 1.4

**Archivos:**

- `frontend/intermediate/models/Quadruple.kt` (MODIFICAR: `Copy`, `IndexedLoad`,
  `IndexedStore`, `Param`, `Return`, `Call`, `IndirectCall`)
- `frontend/intermediate/TacGenerator.kt` (MODIFICAR: cada lugar que las emite)
- Tests: `QuadrupleTest`, `TacGeneratorExprTest` (AMPLIAR)

### Qué es esto, en simple

El backend necesita saber el tipo de cada valor que mueve: un flotante se carga con
`vldr` y ocupa 8 bytes, un booleano con `ldrb` y ocupa 1, y un entero con `ldr`. Para
una variable, el tipo está en su `Symbol`. Para un temporal no está en ningún lado:
`t1 = lista[t2]` no dice si `t1` es entero o flotante.

### Qué se hace

Las siete instrucciones que mueven valores sin operar sobre ellos ganan un
`kind: OperandKind`, como ya lo tienen `Arithmetic` y `Relational`. El generador ya
conoce el tipo en cada uno de esos lugares, porque lo usa para calcular tamaños y
conversiones.

`IndirectCall` gana además la etiqueta del método que el verificador resolvió, por
ejemplo `Animal.hablar` aunque en ejecución corra `Perro.hablar`. El backend la
necesita para saber cuánto mide la parte fija del registro que reserva quien llama
(decisión 69); todas las versiones sobrescritas tienen la misma firma, así que miden
lo mismo.

El `TacPrinter` no imprime nada de esto: el TAC se lee igual que antes y los `.tac`
dorados no cambian.

### Por qué

**En el TAC y no deducido en el backend:** la decisión 60 dice que el backend no mira
el AST, y deducir el tipo de un temporal exigiría seguirlo hasta su definición, que
puede estar en otro bloque, como el resultado de un ternario. El generador de TAC ya
lo sabe; perderlo para reconstruirlo después sería trabajo repetido.

### Aceptación

- Ningún `Quadruple` que mueve un valor queda sin su tipo.
- Los `.tac` dorados no cambian.

---

## Ticket 1.6: La batería y el documento del lenguaje intermedio

- **Estado**: pendiente
- **Depende de**: 1.2, 1.4, 1.5

**Archivos:**

- `resources/programas/validos/` (NUEVOS: `listas_push.cps`, `listas_length.cps`,
  `listas_foreach_push.cps`, con sus `.tac`)
- `resources/programas/validos/*.tac` (REGENERAR)
- `docs/lenguaje-intermedio.md` (MODIFICAR: listas, `push_<tipo>` y `length`)
- `README.md` (MODIFICAR: conteo de programas)

### Qué se hace

Se regeneran los dorados con `./gradlew test -DupdateGolden=true` y el `git diff` se
revisa programa por programa: solo deben cambiar las listas y los flotantes.

### Aceptación

- Todos los tests pasan.
- Los tres programas nuevos corren en el intérprete con la salida esperada.
- El documento explica la ficha, el contenido y por qué se separan, con una tabla de
  ejemplo.
