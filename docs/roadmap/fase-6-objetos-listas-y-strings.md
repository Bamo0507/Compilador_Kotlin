# Fase 6: Objetos, listas y strings

**Objetivo de la fase:** traducir todo lo que vive en el montículo: `alloc`, los accesos
con desplazamiento, las llamadas a métodos por la tabla, las listas que crecen y la
concatenación. Aquí entra el administrador de memoria del runtime y la respuesta a qué
hacer cuando no hay espacio.

**Por qué va aquí:** un método es una función con `this` como primer parámetro, así que
necesita la secuencia de llamadas de la Fase 5.

**Al terminar:** los programas de clases, listas y strings de la batería, incluidos los
de `push`, corren en el contenedor e imprimen lo mismo que el intérprete.

**Teoría que la sostiene:** puntos 7 y 11 (acceso a memoria y memoria dinámica);
Dragon Book 7.4 y 6.4.

**Cómo quedó:** los programas de clases y listas traen chequeos de null y de índice que
terminan en `throw`, así que la fase adelantó una versión básica de `cps_lanzar`: imprime
el mensaje en la salida de errores y termina con código 1. La Fase 7 le agrega la pila de
manejadores. Los dos errores, el de `throw` y el de falta de memoria, pasan por
`cps_terminar_con_error`, que primero vacía la salida pendiente de `printf`. Además, un
resultado flotante escrito directo en memoria ahora se anota en la libreta de direcciones
(`recordResultInMemory`), para que el vaciado no lo pise con el valor viejo de un registro.
29 de los 31 programas válidos imprimen en ARM lo mismo que el intérprete; los dos que
faltan usan `try`.

---

## Ticket 6.1: `alloc` y el administrador de memoria

- **Estado**: completado
- **Depende de**: 5.2

**Archivos:**

- `resources/runtime/runtime.s` (MODIFICAR: `cps_reservar`, `cps_sin_memoria`)
- `backend/ArmGenerator.kt` (MODIFICAR: `Allocate`, `VirtualTableAddress`)
- Tests: `ArmGeneratorMemoryTest` (NUEVO)

### Qué es esto, en simple

`malloc` es el administrador del montículo: le pides bytes y te da la dirección de un
bloque libre, alineado a 8. Cuando no queda espacio devuelve 0. El runtime envuelve esa
llamada para que ningún programa siga con una dirección nula.

### Qué se hace

| Rutina | Recibe | Devuelve | Si falla |
|---|---|---|---|
| `cps_reservar` | r0: bytes | r0: la dirección del bloque, en ceros | Imprime "Error: sin memoria" y termina con código 1 |

`t1 = alloc 16` se traduce a `mov r0, #16`, `bl cps_reservar` y el resultado pasa al
registro de `t1`. `t1[0] = vtable.Perro` carga la dirección de la tabla con
`ldr Rx, =cps_vtable_Perro`.

### Por qué

**El bloque en ceros (`calloc`):** un campo de referencia sin inicializar debe ser
`null`, que es 0, y no basura.

**Terminar y no lanzar una excepción:** quedarse sin memoria no es un error del
programa que un `catch` pueda arreglar, y el manejador mismo podría necesitar memoria.

### Aceptación

- Un programa que pide un bloque enorme termina con "Error: sin memoria" y código 1.

---

## Ticket 6.2: Campos y llamadas a métodos

- **Estado**: completado
- **Depende de**: 6.1

**Archivos:**

- `backend/ArmGenerator.kt` (MODIFICAR: `IndexedLoad`, `IndexedStore`,
  `IndirectCall`)
- Tests: `ArmGeneratorMemoryTest` (AMPLIAR)

### Qué se hace

| TAC | ARM |
|---|---|
| `t1 = perro[4]` | `ldr Rt1, [Rperro, #4]` |
| `t1 = lista[t2]` | `ldr Rt1, [Rlista, Rt2]` |
| `perro[8] = t1` (booleano) | `strb Rt1, [Rperro, #8]` |
| `t1 = obj[8]` (flotante) | `vldr d0, [Robj, #8]` y `vstr d0` en la casilla de `t1` |
| `t3 = call *t2, 2` | La secuencia de la Fase 5, con `blx Rt2` en vez de `bl` |

`vldr` no acepta un registro como desplazamiento, así que con un índice variable primero
se suma: `add ip, Rbase, Roff`.

Para la llamada indirecta, la parte fija sale del método que resolvió el verificador
(ticket 1.5).

### Aceptación

- `clases_constructor`, `clases_herencia`, `clases_despacho`, `clases_this` y
  `clases_inicializador_this` imprimen lo mismo que el intérprete.

---

## Ticket 6.3: Listas que crecen

- **Estado**: completado
- **Depende de**: 6.2, 1.4

**Archivos:**

- `resources/runtime/runtime.s` (MODIFICAR: `cps_lista_agregar`)
- `backend/ArmGenerator.kt` (MODIFICAR: `ListPush`)
- Tests: `ArmGeneratorMemoryTest` (AMPLIAR)

### Qué es esto, en simple

Es el ArrayList. La ficha guarda la longitud, la capacidad y la dirección del
contenido. Mientras haya capacidad, agregar es escribir en la siguiente casilla. Cuando
se llena, se pide un contenido del doble de tamaño, se copian los elementos, y la ficha
pasa a apuntar al nuevo. Las variables siguen apuntando a la misma ficha.

### Qué se hace

Una sola rutina para todos los tipos, que no escribe el valor: devuelve dónde
escribirlo.

| Rutina | Recibe | Devuelve |
|---|---|---|
| `cps_lista_agregar` | r0: la ficha; r1: el tamaño de un elemento | r0: la dirección de la casilla nueva, con la longitud ya aumentada |

Por dentro:

1. Si longitud es igual a capacidad: la nueva capacidad es el doble, o 4 si era 0;
   `realloc` del contenido a capacidad por tamaño; si devuelve 0, "sin memoria"; se
   guardan en la ficha la capacidad y la dirección nuevas.
2. La casilla es contenido más longitud por tamaño.
3. La longitud aumenta en 1.

`push_f lista, t1` se traduce a la llamada con `mov r1, #8`, y después
`vstr d0, [r0]` con el valor de `t1`.

### Por qué

**La rutina devuelve la casilla en vez de recibir el valor:** así no hace falta una
rutina por tipo. El generador ya sabe guardar un entero, un booleano o un flotante en
una dirección.

**Duplicar:** con capacidad 4, la lista se muda al agregar el elemento 5, el 9, el 17…
Sumando todas las copias, `n` agregados copian menos de `2n` elementos: en promedio,
cada `push` cuesta lo mismo sin importar el tamaño de la lista.

### Aceptación

- `listas_push`, `listas_length` y `listas_foreach_push` imprimen lo mismo que el
  intérprete.
- Una prueba agrega 1000 elementos a una lista vacía y suma todos.
- Una copia de la referencia hecha antes de la mudanza ve los elementos nuevos.

---

## Ticket 6.4: Strings

- **Estado**: completado
- **Depende de**: 6.1

**Archivos:**

- `resources/runtime/runtime.s` (MODIFICAR: `cps_concatenar`)
- `backend/ArmGenerator.kt` (MODIFICAR: `Concat`)
- Tests: `ArmGeneratorMemoryTest` (AMPLIAR)

### Qué se hace

| Rutina | Recibe | Devuelve |
|---|---|---|
| `cps_concatenar` | r0 y r1: los dos textos | r0: un texto nuevo en el montículo |

Por dentro mide los dos con `strlen`, pide la suma más 1 con `cps_reservar` y copia
los dos con `memcpy`. Los literales siguen en `.rodata` (decisión 47) y nunca se
modifican.

### Aceptación

- Los programas de la batería que concatenan imprimen lo mismo que el intérprete.

---

## Ticket 6.5: El documento, memoria dinámica

- **Estado**: completado
- **Depende de**: 6.3, 6.4

**Archivos:**

- `docs/codigo-arm.md` (MODIFICAR: sección de objetos, listas y el montículo)

### Qué se hace

La sección explica el montículo y `malloc`, qué pasa cuando no hay memoria, la tabla de
métodos en ARM, y la lista como ArrayList: la ficha, la mudanza del contenido, por qué
no quedan punteros colgantes y por qué duplicar.

### Aceptación

- El recorrido de una lista que pasa de capacidad 4 a 8 está en una tabla con las
  direcciones de la ficha y del contenido antes y después.
