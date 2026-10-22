# Fase 3: Expresiones y getReg

**Objetivo de la fase:** traducir las instrucciones de cálculo del TAC con el generador
simple del Dragon Book 8.6: bloques básicos, información de próximo uso, descriptores
de registro y de dirección, y la función `getReg`. Es el corazón de la etapa.

**Por qué va aquí:** las fases siguientes (saltos, llamadas, objetos) solo agregan
instrucciones nuevas sobre esta maquinaria. Si getReg y los descriptores no están
bien, todo lo demás hereda el error.

**Al terminar:** cualquier programa sin funciones, saltos ni objetos, con enteros,
flotantes, booleanos y variables globales y locales, corre en el contenedor e imprime
lo mismo que el intérprete.

**Teoría que la sostiene:** puntos 2, 3, 5 y 7 (registros que se conservan, getReg,
aritmética y acceso a memoria).

---

**Cómo quedó:** además de lo que planea cada ticket, la fase agregó `ArmEmitter`, donde
escriben sus líneas el generador, getReg y el acceso a memoria; `QuadrupleOperands`, que
dice qué lee y qué escribe cada instrucción; y `Frame.temporaryOffset` y
`Frame.accessLinkOffset`. El acceso a memoria vive en `MemoryAccess.kt`, y las dos
libretas en `RegisterDescriptor.kt` y `AddressDescriptor.kt`. Los seis programas de la
batería que no usan saltos, llamadas ni memoria dinámica imprimen en ARM lo mismo que en
el intérprete.

---

## Ticket 3.1: Bloques básicos e información de próximo uso

- **Estado**: completado
- **Depende de**: 2.2

**Archivos:**

- `backend/BasicBlocks.kt` (NUEVO)
- `backend/NextUseAnalysis.kt` (NUEVO)
- Tests: `BasicBlocksTest`, `NextUseAnalysisTest` (NUEVOS)

### Qué es esto, en simple

Un bloque básico es un tramo de TAC por el que se entra solo por arriba y se sale solo
por abajo. Dentro de él se sabe siempre qué instrucción viene después, y por eso ahí se
puede confiar en lo que dicen los descriptores.

El próximo uso responde, para cada operando de cada instrucción, "¿se vuelve a leer
este valor más adelante?". Es lo que le permite a getReg vaciar un registro sin guardar
nada cuando su valor ya no se usa.

### Qué se hace

**Los líderes** (Dragon Book 8.4.1), lo que empieza un bloque:

- la primera instrucción de cada función
- cada etiqueta (`LabelDefinition`)
- la instrucción que sigue a un salto, a una llamada, a un `return`, a un `throw` o a
  un `try`

**El próximo uso** (Dragon Book 8.4.2) se calcula recorriendo cada bloque de abajo
hacia arriba. Para `x = y op z`:

1. Se anota en la instrucción lo que se sabe en ese momento de `x`, `y` y `z`.
2. `x` pasa a "no vivo, sin próximo uso".
3. `y` y `z` pasan a "vivo, próximo uso aquí".

El orden importa cuando `x` es también `y` o `z`, como en `t1 = t1 * t2`.

**Qué está vivo al salir de un bloque:** todo, variables y temporales.

### Por qué

**Una llamada termina el bloque:** la función llamada lee de memoria sus parámetros y
las globales, y una función anidada puede escribir una local de quien la llama a través
del enlace de acceso. Después de la llamada, ningún registro es confiable.

**Los temporales vivos al salir:** el Dragon Book supone que un temporal no cruza
bloques. En nuestro TAC sí los cruza: el resultado de un ternario se escribe en dos
ramas y se lee después de la etiqueta. Suponerlos vivos es conservador y siempre
correcto; cuesta, como mucho, guardar un temporal que no hacía falta.

### Aceptación

- `BasicBlocksTest` cubre cada tipo de líder.
- `NextUseAnalysisTest` reproduce el ejemplo del libro y el caso `t1 = t1 * t2`.

---

## Ticket 3.2: Los descriptores y getReg

- **Estado**: completado
- **Depende de**: 3.1

**Archivos:**

- `backend/RegisterDescriptor.kt` (NUEVO)
- `backend/AddressDescriptor.kt` (NUEVO)
- `backend/RegisterAllocator.kt` (NUEVO: `getReg`, `spill`, `flush`)
- Tests: `RegisterAllocatorTest` (NUEVO)

### Qué es esto, en simple

Dos libretas. La de registros dice, para cada registro, qué variables tiene su valor.
La de direcciones dice, para cada variable, dónde está su valor actual: en memoria, en
uno o más registros, o en ambos. `getReg` consulta las dos para entregar un registro
sin perder ningún valor, y funciona como un `getOrPut`: si la variable ya está en un
registro, lo devuelve; si no, consigue uno, lo carga, lo anota y lo devuelve.

### Qué se hace

```kotlin
// Donde puede estar el valor actual de una variable.
sealed interface Location {
    data object Memory : Location
    data class InRegister(val register: Register) : Location
}
```

**`getReg` para un operando `y`**, en este orden (Dragon Book 8.6.3):

| Paso | Si… | Entonces |
|---|---|---|
| 1 | `y` ya está en un registro | Ese registro, sin instrucciones |
| 2 | Hay un registro vacío | Ese, con un `ldr` |
| 3 | Hay un registro cuyas variables están todas a salvo: también en memoria, sin próximo uso, o son el resultado `x` de esta instrucción | Ese, con un `ldr` |
| 4 | Ninguno | El que pida menos guardados: un `str` por cada variable que solo vivía ahí, y después el `ldr` |

**`getReg` para el resultado `x`:** cualquier registro que solo tenga a `x`; o el de un
operando que muere en esta instrucción; o los pasos 2 a 4.

**Cómo cambian las libretas** (Dragon Book 8.6.2):

| Instrucción | Libreta de registros | Libreta de direcciones |
|---|---|---|
| `ldr R, x` | R tiene solo a `x` | Se agrega R a `x` |
| `str R, x` | Sin cambio | Se agrega memoria a `x` |
| `x = y op z` | Rx tiene solo a `x`; Rx sale de las demás variables | `x` está solo en Rx |
| `x = y` | Se agrega `x` a Ry | `x` está solo en Ry, sin instrucción |

**El vaciado** (`flush`), al final de cada bloque y antes de cada llamada: un `str` por
cada variable viva cuyo valor solo está en un registro, y después las dos libretas
quedan en blanco.

Los registros disponibles son r4 a r8 y r10 (decisión 67).

### Por qué

**r4 a r10 y no r0 a r3:** los primeros los debe conservar la función llamada, así que
un valor que vive en ellos sobrevive a una llamada a `printf`. r0 a r3 y r12 quedan
como registros de paso: argumentos del runtime, resultados de libc y direcciones de
globales.

**Una función cerrada en `RegisterAllocator`:** el generador nunca escoge un registro a
mano, así que no hay forma de pisar un valor por accidente.

### Aceptación

- Un test por cada paso de la tabla de getReg, incluido un derrame forzado con siete
  valores vivos a la vez.
- El ejemplo trabajado del libro (`t = a - b; u = a - c; v = t + u; a = d; d = v + u`)
  produce las mismas nueve instrucciones que la versión del Dragon Book, con r4, r5 y
  r6.

---

## Ticket 3.3: Dónde vive cada variable

- **Estado**: completado
- **Depende de**: 3.2

**Archivos:**

- `backend/MemoryOperands.kt` (NUEVO)
- Tests: `MemoryOperandsTest` (NUEVO)

### Qué se hace

La traducción de cada `Address` a su lugar en memoria, que es lo que usan el `ldr` y el
`str` de getReg:

| Dirección del TAC | Dónde vive | ARM |
|---|---|---|
| Local o parámetro, desplazamiento `d`, tamaño `s` | El registro propio | `[fp, #-(d+s)]` |
| Temporal `tN` | Su casilla al final del registro, de 8 bytes | `[fp, #-(d+8)]` |
| Global, desplazamiento `d` | La zona estática | `ldr ip, =cps_estatico` y `[ip, #d]` |
| `cuenta^2`, desplazamiento `d` | El registro dos niveles arriba | Seguir el enlace de acceso dos veces con `ldr ip, [ip, #-(a+4)]` y después `[ip, #-(d+s)]` |
| Constante entera chica | En la instrucción | `#5` |
| Constante grande, flotante o string | El *literal pool* o `.rodata` | `ldr r0, =100000` o `ldr r0, =.Lstr_2` |

El registro crece hacia abajo desde `fp`, y por eso el desplazamiento se vuelve
negativo (decisión 69). El tamaño elige la instrucción: `ldr` y `str` para 4 bytes,
`ldrb` y `strb` para un booleano, `vldr` y `vstr` para un flotante.

### Por qué

**`ip` para las direcciones de paso:** es el registro de paso del estándar de ARM. No
lo usa getReg, así que calcular una dirección nunca le roba un registro a un valor.

### Aceptación

- Un test por cada fila de la tabla, con los desplazamientos de `funciones_recursion`.

---

## Ticket 3.4: Aritmética de enteros y booleanos

- **Estado**: completado
- **Depende de**: 3.3

**Archivos:**

- `backend/ArmGenerator.kt` (MODIFICAR: `Arithmetic`, `Unary`, `Relational`, `Copy`)
- `resources/runtime/runtime.s` (MODIFICAR: `cps_comparar_strings`, adelantada de la
  Fase 4: una comparación de textos como valor también es una comparación)
- Tests: `ArmGeneratorExprTest` (NUEVO), `ArmExecutionTest` (NUEVO: la batería completa
  en el contenedor con `-DrunArm=true`, adelantada de la Fase 9)

### Qué se hace

| TAC | ARM |
|---|---|
| `x = a + b` | `add Rx, Ra, Rb` |
| `x = a + 5` | `add Rx, Ra, #5` |
| `x = a - b` | `sub Rx, Ra, Rb` |
| `x = a * b` | `mul Rx, Ra, Rb` |
| `x = a / b` | `mov r0, Ra`, `mov r1, Rb`, `bl __aeabi_idiv`, `mov Rx, r0` |
| `x = a % b` | Igual con `__aeabi_idivmod`, y el residuo sale en r1 |
| `x = -a` | `rsb Rx, Ra, #0` |
| `x = !a` | `eor Rx, Ra, #1` |
| `x = a < b` como valor | `cmp Ra, Rb`, `movlt Rx, #1`, `movge Rx, #0` |
| `x = y` | Ninguna instrucción: `x` se anota en el registro de `y` |

El recorte a 32 bits de la decisión 21 sale gratis: los registros miden 32 bits.

### Por qué

**`mul` y no `lsl` para multiplicar por una potencia de 2:** el GDA ya pliega las
constantes, y la diferencia en una Pi es de un ciclo. Se deja como posible mejora.

**La división es una llamada, pero no termina el bloque:** `__aeabi_idiv` solo toca r0
a r3 y r12, y los valores de getReg viven en r4 a r10, que el estándar obliga a
conservar.

### Aceptación

- `tipos_aritmetica`, `tipos_logicos`, `tipos_desborde_entero` y `tac_temporales`
  imprimen lo mismo que el intérprete.

---

## Ticket 3.5: Flotantes

- **Estado**: completado
- **Depende de**: 3.4

**Archivos:**

- `backend/ArmGenerator.kt` (MODIFICAR: los casos con `OperandKind.FLOAT`)
- Tests: `ArmGeneratorExprTest` (AMPLIAR)

### Qué se hace

Los flotantes no pasan por getReg (decisión 67): cada instrucción carga sus operandos
en d1 y d2, opera en d0 y guarda el resultado en su casilla.

| TAC | ARM |
|---|---|
| `x = a +f b` | `vldr d1, [a]`, `vldr d2, [b]`, `vadd.f64 d0, d1, d2`, `vstr d0, [x]` |
| `x = inttofloat i` | El entero pasa por getReg; después `vmov s0, Ri`, `vcvt.f64.s32 d0, s0` y `vstr` |
| `x = a <f b` como valor | `vcmp.f64 d1, d2`, `vmrs APSR_nzcv, fpscr`, `movmi Rx, #1`, `movpl Rx, #0` |
| Constante `3.5` | Una etiqueta `.Lfloat_N` con `.double 3.5` en `.rodata`, y `vldr` desde ella |

### Por qué

**`mi` y no `lt` para "menor que" en flotantes:** después de `vcmp`, el sufijo `lt`
también es verdadero cuando un operando no es un número (NaN). `mi` solo lo es cuando
de verdad es menor. Para "menor o igual" se usa `ls`.

### Aceptación

- Los programas con flotantes de la batería imprimen lo mismo que el intérprete, con el
  formato de la decisión 71.
