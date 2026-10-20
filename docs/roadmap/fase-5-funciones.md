# Fase 5: Funciones

**Objetivo de la fase:** traducir `begin_func`, `end_func`, `param`, `call` y `return`
con la secuencia de llamadas de la decisión 69, que usa tal cual los registros de
activación que ya calcula el `StorageAllocator`.

**Por qué va aquí:** las llamadas cierran bloques y vacían registros (Fase 3), y los
programas de prueba usan `if` para el caso base de la recursión (Fase 4).

**Al terminar:** los programas de funciones de la batería, con recursión, funciones
anidadas y enlaces de acceso, corren en el contenedor e imprimen lo mismo que el
intérprete.

**Teoría que la sostiene:** puntos 2 y 10 (registros que se conservan y stack frame);
Dragon Book 7.2 y 7.3.

---

## Ticket 5.1: Prólogo y epílogo

- **Estado**: pendiente
- **Depende de**: 3.3

**Archivos:**

- `backend/CallingSequence.kt` (NUEVO)
- `backend/ArmGenerator.kt` (MODIFICAR: `FunctionBegin`, `FunctionEnd`, `Return`)
- Tests: `CallingSequenceTest` (NUEVO)

### Qué es esto, en simple

El registro de activación del TAC tiene sus casillas con desplazamientos que crecen
desde 0. La pila de ARM crece hacia abajo. Se reconcilian con un espejo: `fp` apunta
al tope del registro, y la casilla con desplazamiento `d` y tamaño `s` vive en
`fp − d − s`. Así no cambia ningún número del `StorageAllocator`.

Con el registro de `factorial`, que mide 40:

| Casilla | Desplazamiento | Tamaño | Dirección |
|---|---|---|---|
| `n` | 0 | 4 | `[fp, #-4]` |
| valor devuelto | 4 | 4 | `[fp, #-8]` |
| enlace de control | 8 | 4 | `[fp, #-12]` |
| enlace de acceso | 12 | 4 | `[fp, #-16]` |
| dirección de retorno | 16 | 4 | `[fp, #-20]` |
| `t1` | 24 | 8 | `[fp, #-32]` |
| `t2` | 32 | 8 | `[fp, #-40]` |

La **parte fija** es lo que va de la casilla 0 hasta el final de la dirección de
retorno: 20 bytes en `factorial`. La reserva quien llama; el resto, la función.

### Qué se hace

| Momento | ARM | Qué hace |
|---|---|---|
| Prólogo | `add ip, sp, #20` | El tope del registro, que la llamada dejó reservado |
| | `str fp, [ip, #-12]` | Enlace de control: el `fp` de quien llama |
| | `str lr, [ip, #-20]` | Dirección de retorno |
| | `mov fp, ip` | Este es el registro actual |
| | `sub sp, fp, #40` | Reserva locales y temporales |
| `return t1` | El valor a r0 (o d0) y `b .Lfactorial_fin` | |
| Epílogo | `ldr lr, [fp, #-20]` | Recupera a dónde volver |
| | `sub sp, fp, #20` | Deja la pila como la dejó quien llama |
| | `ldr fp, [fp, #-12]` | Recupera el registro de quien llama |
| | `bx lr` | Vuelve |

`main` es la excepción: lo llama libc, que no reservó nada, así que su prólogo empieza
con `sub sp, sp, #` su parte fija.

### Por qué

**El tamaño de cada registro ya es múltiplo de 8 (decisión 42):** `sp` queda alineado
a 8 en toda la función, que es lo que pide libc en cada llamada.

### Aceptación

- Los desplazamientos de la tabla salen del `ActivationRecordLayout`, no escritos a
  mano.
- Un programa con una función sin parámetros que imprime algo corre en el contenedor.

---

## Ticket 5.2: `param`, `call` y el enlace de acceso

- **Estado**: pendiente
- **Depende de**: 5.1

**Archivos:**

- `backend/CallingSequence.kt` (MODIFICAR)
- `backend/ArmGenerator.kt` (MODIFICAR: `Param`, `Call`)
- Tests: `CallingSequenceTest` (AMPLIAR)

### Qué se hace

Al llegar al primer `param` de una llamada, el generador busca hacia adelante el `call`
que la cierra, para saber a quién se llama y cuánto mide su parte fija. Después:

| TAC | ARM |
|---|---|
| (primer `param`) | Vaciado de registros y `sub sp, sp, #20` |
| `param t2` | `str Rt2, [sp, #16]`: la casilla de `n` en el registro nuevo, `20 − 0 − 4` |
| `t2 = call factorial, 1` | `bl cps_factorial`, `add sp, sp, #20`, y el resultado pasa de r0 al registro que getReg le da a `t2` |
| `call f, 1, ^1` | Antes del `bl`, el enlace de acceso: seguir un enlace desde `fp` y guardarlo en su casilla |

**El enlace de acceso** (decisión 40): `^h` dice cuántos enlaces sube quien llama para
llegar al registro de la función que contiene a la llamada. Con `h = 0` es el propio
`fp`; con `h = 1`, lo que hay en la casilla del enlace de acceso propio; y así.

### Por qué

**Los parámetros en la pila y no en r0 a r3:** el registro de la función ya tiene una
casilla para cada parámetro. Pasarlos en registros obligaría a la función a copiarlos
ahí en su prólogo, y a tratar aparte el quinto parámetro y los flotantes. Las rutinas
del runtime y libc sí usan r0 a r3, porque así lo exige libc.

**Buscar el `call` hacia adelante:** entre el primer `param` y su `call` el TAC no tiene
otra cosa, porque los argumentos se evalúan antes (decisión 55). La búsqueda es lineal
y corta.

### Aceptación

- `funciones_recursion`, `funciones_argumentos` y `funciones_acceso_anidado` imprimen
  lo mismo que el intérprete.
- `factorial(12)` funciona: la recursión deja 12 registros en la pila y cada uno vuelve
  al suyo.

---

## Ticket 5.3: El documento, funciones

- **Estado**: pendiente
- **Depende de**: 5.2

**Archivos:**

- `docs/codigo-arm.md` (MODIFICAR: sección del stack frame y la secuencia de llamadas)

### Qué se hace

La sección explica el espejo entre el registro del TAC y la pila de ARM, la parte fija
que reserva quien llama, el prólogo y el epílogo, y el recorrido de `factorial(3)`
mostrando la pila en cada llamada, en tablas.

### Aceptación

- El ejemplo coincide con el `.s` generado de `funciones_recursion`.
