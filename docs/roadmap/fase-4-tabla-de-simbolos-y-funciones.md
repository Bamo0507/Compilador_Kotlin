# Fase 4: Tabla de símbolos extendida y funciones

**Objetivo de la fase:** cada símbolo sabe en qué zona de la memoria vive, cuánto mide
y en qué desplazamiento está. Cada función tiene su registro de activación con los
siete campos de la diapositiva 21, y el TAC traduce definiciones, parámetros,
llamadas, retornos, recursión y funciones anidadas con su enlace de acceso.

**Por qué va junta:** la tabla de símbolos extendida existe para que las funciones se
puedan traducir. El tamaño del registro de activación es lo que dice `begin_func`, y
el desplazamiento de cada local es lo que la fase de assembler va a usar para leerla.
Separarlas dejaría una tabla sin consumidor.

**Al terminar:** el `factorial` recursivo y el contador con función anidada generan
TAC completo, y la tabla de símbolos del IDE muestra zona, tamaño y desplazamiento de
cada símbolo y la forma de cada registro de activación. Es la componente de 10 puntos
de la rúbrica.

**Teoría que la sostiene:** puntos 7, 8 y 9. Presentación 07, diapositivas 10 y 17 a
25; Dragon Book 6.3.4 (alineación), 7.2 (pila, registros y secuencia de llamadas) y 7.3
(enlaces de acceso).

**Objetivo de assembler:** ARM de 32 bits, de una Raspberry Pi. Las direcciones miden 4
bytes y la pila se alinea a 8 en cada llamada (decisión 42).

---

## Ticket 4.1: Las funciones solo se llaman

- **Estado**: completado
- **Depende de**: 0.2

**Archivos:**

- `frontend/semantic/TypeChecker.kt` (MODIFICAR)
- `resources/programas/invalidos/funcion_como_valor.cps` (NUEVO)
- `app/src/test/kotlin/org/compiler/TypeCheckerCallsTest.kt` (AMPLIAR)

### Qué se hace

La regla de la decisión 38. Un nombre que se resuelve a una función, o un acceso a un
método, solo es válido como `callee` de una llamada:

```kotlin
// Prendido solo mientras se verifica el callee de una llamada: es el unico lugar
// donde un nombre de funcion es legal.
private var checkingCallee = false
```

`checkFunctionCall` lo prende al verificar `expr.callee`. `checkIdentifier` y
`checkPropertyAccess` reportan si el símbolo es una función y la bandera está apagada:

```
'sumar' es una función: solo se puede llamar, no usarse como valor
```

### Aceptación

| Programa | Resultado esperado |
|---|---|
| `let r: integer = sumar(2, 3);` | válido |
| `let g = sumar;` | **error** con *"solo se puede llamar"* |
| `guardada = interna;` dentro de `externa` | **error** |
| `print(perro.hablar);` sin paréntesis | **error** |
| `f * 2` con `f` una función | **error**: ahora lo reporta esta regla, antes que la del operador |
| `funciones_closures.cps` | sigue válido: solo llama a la función anidada |

**Al implementarlo:** la bandera solo se prende cuando el callee es directamente un
nombre o un acceso a un método, y `checkIdentifier` y `checkPropertyAccess` la apagan al
leerla, para que no se filtre hacia sus subexpresiones. Los tests quedaron en
`TypeCheckerStmtTest`, que tiene el helper de programas completos que necesitan las
funciones anidadas. `multiplicar_funciones.cps`, de la batería anterior, ahora reporta
este error en lugar del del operador, y se actualizó su anotación.

---

## Ticket 4.2: Tamaños, zonas y desplazamientos

- **Estado**: completado
- **Depende de**: 4.1

**Archivos:**

- `frontend/intermediate/StorageAllocator.kt` (NUEVO)
- `frontend/intermediate/models/StorageLocation.kt` (NUEVO)
- `frontend/intermediate/models/ActivationRecordLayout.kt` (NUEVO)
- `frontend/semantic/symbols/Symbol.kt` (MODIFICAR: `storage`, sin `offset`)
- `frontend/semantic/symbols/Scope.kt` (MODIFICAR: `activationRecord`, sin `nextOffset`)
- `frontend/semantic/TypeChecker.kt`, `DeclarationCollector.kt` (MODIFICAR: dejan de pasar
  `offset`)
- `runtime/CompilerPipeline.kt` (MODIFICAR: corre antes del generador)
- `app/src/test/kotlin/org/compiler/StorageAllocatorTest.kt` (NUEVO)

### Los tamaños (decisión 42)

| Tipo | Bytes | Por qué |
|---|---|---|
| `integer` | 4 | La decisión 21 |
| `float` | 8 | Doble precisión, igual que el `Double` del intérprete |
| `boolean` | 1 | |
| Referencias: objetos, listas, strings | 4 | Una dirección en ARM de 32 bits |
| Cada temporal | 8 | Un mismo `t1` puede guardar un entero y después un `float` (el pool recicla nombres sin mirar el tipo), así que su espacio tiene que alcanzar para el más grande |

**Alineación natural** (Dragon Book 6.3.4): cada valor empieza en un desplazamiento
múltiplo de su tamaño, y el registro completo se redondea a 8, la alineación de la pila
en ARM de 32 bits.

```kotlin
private fun align(offset: Int, size: Int): Int = (offset + size - 1) / size * size
```

### Dónde vive cada símbolo

```kotlin
sealed interface StorageLocation {
    // Datos estaticos: las globales. Desplazamiento desde el inicio de esa zona.
    data class Static(val offset: Int) : StorageLocation

    // La pila: desplazamiento desde el inicio del registro de activacion de su funcion.
    data class Frame(val offset: Int) : StorageLocation
}
```

| Símbolo | Zona |
|---|---|
| Declarado directamente en el ámbito global | `Static` |
| Parámetro o local de una función, incluidos los de sus bloques, `if` y bucles | `Frame` de esa función |
| Local de un bloque del nivel superior, como `{ let x = 1; }` fuera de toda función | `Frame` del `main` implícito: muere al cerrar el bloque, así que no es global |
| Funciones y clases | ninguna: son código, no datos |
| Campos de clase | ninguna en esta fase: viven dentro del objeto, en el montículo. Es la fase de objetos |

`Symbol` cambia su campo `offset`, que hoy es un índice de ranura por ámbito, por
`var storage: StorageLocation? = null`. Es lo que la Fase 0 dejó pendiente a propósito.

### El registro de activación

El orden de la diapositiva 21, con desplazamientos positivos desde el inicio del
registro:

```kotlin
data class ActivationRecordLayout(
    val function: FunctionLabel,
    val fields: List<ActivationRecordField>,
    val size: Int
)

// Un renglon del registro, para la GUI y el documento.
data class ActivationRecordField(val name: String, val offset: Int, val size: Int)
```

```
function suma(a: integer, b: float): float { let r: float = a + b; return r; }

desplazamiento  campo                       tamaño
 0              a                (param)    4
 8              b                (param)    8     alineado a 8
16              valor devuelto              8     es float
24              enlace de control           4
28              enlace de acceso            4
32              dirección de retorno        4
40              r                (local)    8     alineado a 8
48              t1 ... tn       (temporales) 8 cada uno
                tamaño total                redondeado a 8
```

**El enlace de acceso va en todos los registros**, aunque solo lo usen las funciones
anidadas. Una sola forma de registro es más simple de explicar y de traducir, por 4
bytes.

**El estado de la máquina guardado** se limita a la dirección de retorno. Qué
registros del procesador hay que salvar depende de cómo la fase de assembler los use,
así que esa parte la agrega ella.

**Los locales de bloques hermanos no comparten espacio.** `if (c) { let a; } else { let b; }`
reserva lugar para `a` y para `b`, aunque nunca vivan a la vez. Compartirlo es una
optimización que no cambia ningún resultado.

**Los temporales se suman al final**, porque su cantidad recién se conoce después de
generar el código de la función. El `StorageAllocator` deja el registro sin esa parte,
y el generador la completa (ticket 4.3).

### El nombre de cada variable en el TAC (decisión 41)

El mismo recorrido calcula cómo se imprime cada símbolo. Dos símbolos **chocan** si se
llaman igual y además están en el mismo registro de activación, o si uno es global y el
otro local. El primero en declararse queda con su nombre; los demás llevan `@línea`, y
`@línea:columna` si comparten línea.

Dos locales de funciones distintas no chocan: el parámetro `n` de `factorial` y el de
`fibonacci` viven en registros distintos y se imprimen igual. Una local de una función
anidada tampoco choca con la de su padre, porque los saltos ya las distinguen: `x` y
`x^1`.

### Aceptación

| Programa | Resultado esperado |
|---|---|
| `let a: integer; let b: float;` globales | `a` en `Static(0)`, `b` en `Static(8)`: alineado a 8 |
| La función `suma` de arriba | los desplazamientos de la tabla, con relleno donde corresponde |
| `function f() { if (c) { let x: integer; } }` | `x` en el `Frame` de `f`, no en un registro propio del `if` |
| `{ let x: integer = 1; }` en el nivel superior | `x` en el `Frame` del `main` implícito |
| Un registro de activación | su tamaño es múltiplo de 8 |
| Global `x` y local `x` en una función | `x` y `x@3` |
| `n` en `factorial` y en `fibonacci` | los dos `n`, sin sufijo |

**Al implementarlo:** `StorageLocation` vive en `frontend/semantic/symbols/` y no en el
paquete del generador: `Symbol` la guarda, y si viviera en el generador la dependencia
entre los dos paquetes iría en ambos sentidos. Por la misma razón `Scope` no guarda su
`ActivationRecordLayout`: el `StorageAllocator` devuelve un `StorageLayout` con la zona
estática, el `main` implícito y el registro de cada función, indexado por su ámbito, y el
pipeline lo deja en `CompilationResult.storageLayout`. El nombre con sufijo de la decisión
41 quedó en `Symbol.tacName`, y el printer lo usa. La columna `Offset` de la tabla del
IDE pasó a `Ubicación` como cambio mínimo; la tabla completa es del ticket 4.5.

---

## Ticket 4.3: La traducción de funciones

- **Estado**: pendiente
- **Depende de**: 4.2, 3.4

**Archivos:**

- `frontend/intermediate/models/Quadruple.kt` (MODIFICAR: `FunctionBegin`, `FunctionEnd`)
- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `frontend/intermediate/TacPrinter.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorFunctionTest.kt` (NUEVO)

### Las dos instrucciones nuevas

```kotlin
// begin_func f, 24: aqui empieza f, y su registro de activacion mide 24 bytes.
data class FunctionBegin(val function: FunctionLabel, val frameSize: Int) : Quadruple

// end_func f: llegar aqui es retornar sin valor.
data class FunctionEnd(val function: FunctionLabel) : Quadruple
```

### Las etiquetas de función

| Función | Etiqueta |
|---|---|
| El código del nivel superior | `$main`: el `$` no es válido en un identificador, así que no choca con una función del usuario llamada `main` |
| Una función del nivel superior | su nombre: `factorial` |
| Una función anidada | el camino de funciones que la contienen: `externa.sumar` |

### Qué genera cada construcción

```
function factorial(n: integer): integer {
  if (n <= 1) { return 1; }
  return n * factorial(n - 1);
}
print(factorial(5));

begin_func $main, 24
    param 5
    t1 = call factorial, 1
    print_i t1
end_func $main

begin_func factorial, 32
    if n > 1 goto L1
    return 1
L1:
    t1 = n - 1
    param t1
    t1 = call factorial, 1
    t1 = n * t1
    return t1
end_func factorial
```

- **La secuencia de llamadas** (punto 9): `param` escribe cada argumento, `call` guarda
  la dirección de retorno y salta, `begin_func` reserva el registro, y `return` deja el
  valor y regresa. El documento explica qué pasos de la secuencia representa cada una.
- **Un pool de temporales por función.** Cada registro tiene sus propios temporales,
  así que el `t1` de `factorial` no choca con el de `$main`. El `temporaryCount` de
  cada pool completa el registro de su función.
- **Cada función se genera aparte.** El cuerpo se genera primero en una lista propia,
  y después se emite `begin_func` con el tamaño ya completo. Una función anidada se
  emite después de la que la contiene, no en medio de su código.
- **`return` dentro de un `try`** emite un `endtry` por cada `try` abierto en esa
  función, igual que `break` y `continue` (ticket 3.4).
- **`end_func`** cubre el caso de una función `void` que llega al final sin `return`.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `factorial` de arriba | el TAC de arriba, y que el `t1` de `factorial` no afecta al de `$main` |
| `f(g(x))` | `param x`, `t1 = call g, 1`, `param t1`, `call f, 1`: el orden del punto 3 |
| Una función `void` llamada como sentencia | `call f, 0`, sin resultado |
| `return` dentro de un `try` | un `endtry` antes del `return` |
| `begin_func` de cada función | su tamaño coincide con su `ActivationRecordLayout` |

---

## Ticket 4.4: Funciones anidadas y el enlace de acceso

- **Estado**: pendiente
- **Depende de**: 4.3

**Archivos:**

- `frontend/intermediate/models/Address.kt` (MODIFICAR: `Name` lleva `hops`)
- `frontend/intermediate/models/Quadruple.kt` (MODIFICAR: `Call` lleva `accessHops`)
- `frontend/intermediate/TacGenerator.kt`, `TacPrinter.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorFunctionTest.kt` (AMPLIAR)

### Leer una variable de otro registro (decisión 40)

```kotlin
// hops = cuantos enlaces de acceso hay que subir. 0 es el registro propio.
data class Name(val symbol: Symbol, val hops: Int = 0) : Address
```

```
hops = (profundidad de la función actual) - symbol.declarationFunctionDepth
```

Las dos profundidades ya existen: `Scope.functionDepth()` y
`Symbol.declarationFunctionDepth`. Una global no lleva saltos, porque vive en datos
estáticos.

### Pasar el enlace de acceso al llamar

Quien llama tiene que darle a la función llamada la dirección del registro de **quien
la contiene**, no el suyo. Cuántos saltos dar desde su propio registro para
encontrarlo depende de dónde está cada una:

| Quien llama | A quién | Saltos | Por qué |
|---|---|---|---|
| `externa` | `externa.sumar` | 0 | `externa` misma contiene a `sumar`: pasa su propio registro |
| `externa.ayudar` | `externa.sumar` | 1 | son hermanas: sube a `externa` y pasa ese |
| `externa.sumar` | `externa.sumar`, recursiva | 1 | se contiene a sí misma un nivel arriba |

```kotlin
// accessHops: cuantos enlaces sube quien llama para encontrar el registro de la
// funcion que contiene a la llamada. null para funciones del nivel superior, que no
// usan el enlace.
data class Call(
    val result: Address?, val function: FunctionLabel, val argumentCount: Int,
    val accessHops: Int?
) : Quadruple
```

```
saltos = (profundidad de quien llama) - (profundidad de la llamada) + 1
```

### El ejemplo completo

```
function externa(): integer {
  let cuenta: integer = 41;
  function sumar(): integer { return cuenta + 1; }
  function ayudar(): integer { return sumar(); }
  return ayudar();
}

begin_func externa.sumar, 24
    t1 = cuenta^1 + 1
    return t1
end_func externa.sumar

begin_func externa.ayudar, 24
    t1 = call externa.sumar, 0, ^1
    return t1
end_func externa.ayudar
```

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `sumar` lee `cuenta` de `externa` | `cuenta^1` |
| Tres niveles: `c` lee una variable de `a` | `^2` |
| Una función anidada lee su propia local | sin `^` |
| `externa` llama a `sumar` | `^0` en el `call` |
| `ayudar` llama a su hermana `sumar` | `^1` |
| Una función anidada recursiva | `^1` |
| Una función del nivel superior | su `call` no lleva saltos |
| `funciones_closures.cps` | genera TAC completo |

---

## Ticket 4.5: La tabla de símbolos extendida en el IDE

- **Estado**: pendiente
- **Depende de**: 4.3

**Archivos:**

- `gui/screens/SymbolTableScreen.kt` (MODIFICAR)
- `gui/components/ActivationRecordView.kt` (NUEVO)
- `runtime/models/CompilationResult.kt` (MODIFICAR: los registros de activación)

Es la salida que pide el enunciado: *"estado de la tabla de símbolos con la información
agregada para la generación de código (direcciones, desplazamientos, registros de
activación)"*.

- **Las columnas de cada símbolo:** se quita `Offset`, que era el índice de ranura, y
  entran **Zona** (estática o pila), **Tamaño** y **Desplazamiento**, más el nombre con
  que aparece en el TAC si lleva sufijo.
- **El registro de activación:** al seleccionar el ámbito de una función, debajo de sus
  símbolos aparece su registro, renglón por renglón, como la tabla del ticket 4.2. El
  relleno de alineación se ve como un renglón propio.
- **El `main` implícito** aparece en el árbol de ámbitos, colgado de `global`, para que
  su registro también se pueda ver.
- Con un programa con errores, las columnas nuevas dicen *"no disponible"*: los
  desplazamientos solo se calculan si el programa compiló.

### Aceptación

- Seleccionar `factorial` muestra sus símbolos con zona, tamaño y desplazamiento, y su
  registro de activación con los siete campos.
- El relleno de alineación aparece en el registro.
- Una global muestra zona estática.

---

## Ticket 4.6: El documento, secciones de memoria y funciones

- **Estado**: pendiente
- **Depende de**: 4.4, 3.5

**Archivos:**

- `docs/lenguaje-intermedio.md` (AMPLIAR)

| Sección | Contenido |
|---|---|
| 15. Memoria en ejecución | Las cuatro zonas, estático contra dinámico, y dónde vive cada cosa de Compiscript |
| 16. Tamaños y alineación | La tabla de la decisión 42, la alineación natural y el ejemplo con relleno |
| 17. Registro de activación | Los siete campos, su orden y los supuestos (enlace de acceso siempre presente, estado guardado reducido a la dirección de retorno) |
| 18. Secuencia de llamadas | Quién hace qué al llamar y al retornar, y qué paso representa cada una de `param`, `call`, `begin_func`, `return` y `end_func` |
| 19. Funciones anidadas | El enlace de acceso, `x^n` y los saltos del `call`, con la expansión a instrucciones explícitas como ejemplo |
| 20. Nombres en el TAC | El sufijo `@línea` de la decisión 41 |
| Supuestos | Se agregan: una función solo se llama, nunca se guarda; los locales de bloques hermanos no comparten espacio; los desplazamientos son positivos desde el inicio del registro y la fase de assembler puede reubicarlos |

### Aceptación

- Los ejemplos son salida real del generador, incluido el registro de activación.
- La sección 19 muestra la expansión de `cuenta^2` a instrucciones que siguen el
  enlace.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 4.1 | Las funciones solo se llaman |
| 4.2 | Zona, tamaño y desplazamiento de cada símbolo; la forma de cada registro de activación; los nombres con sufijo |
| 4.3 | `begin_func`, `end_func`, parámetros, llamadas, retornos y recursión |
| 4.4 | Funciones anidadas con el enlace de acceso |
| 4.5 | La tabla de símbolos extendida en el IDE |
| 4.6 | Las secciones 15 a 20 del documento |
