# Fase 7: Excepciones

**Objetivo de la fase:** traducir `try L, e`, `endtry` y `throw x` con la pila de
manejadores de la decisión 72, de modo que un error lanzado varias llamadas abajo
llegue a su `catch` y descarte los registros de activación de por medio.

**Por qué va al final del backend:** `throw` necesita la secuencia de llamadas (para
saber qué es un registro de activación y cómo se descarta) y los chequeos que lanzan
errores, que vienen de objetos y listas (Fase 6).

**Al terminar:** `flujo_try_retorno`, `flujo_errores_ejecucion` y cualquier error sin
`catch` se comportan igual que en el intérprete.

**Teoría que la sostiene:** decisión 28 (la idea de `setjmp` y `longjmp`); Dragon Book
7.2.

---

## Ticket 7.1: La pila de manejadores

- **Estado**: pendiente
- **Depende de**: 6.1

**Archivos:**

- `resources/runtime/runtime.s` (MODIFICAR: `cps_try_entrar`, `cps_try_salir`,
  `cps_lanzar`, las globales `cps_manejadores` y `cps_excepcion`). `cps_lanzar` ya
  existe desde la Fase 6 en su versión sin manejadores, que termina el programa.
- `backend/ArmGenerator.kt` (MODIFICAR: `TryBegin`, `TryEnd`, `Throw`, y la etiqueta
  del manejador)
- Tests: `ArmGeneratorFlowTest` (AMPLIAR)

### Qué es esto, en simple

Cada `try` deja una nota en una pila global: "si algo falla, vuelve a este registro de
activación (`fp`), con la pila así (`sp`), y salta a esta etiqueta". `throw` toma la
nota de arriba y hace exactamente eso. Restaurar `sp` y `fp` es descartar de golpe todos
los registros de las funciones que estaban de por medio.

### Qué se hace

Cada nota mide 16 bytes y se pide con `cps_reservar`:

| Desplazamiento | Campo |
|---|---|
| 0 | La nota anterior |
| 4 | `sp` |
| 8 | `fp` |
| 12 | La dirección de la etiqueta del `catch` |

| TAC | ARM | Qué hace la rutina |
|---|---|---|
| `try L1, e` | Vaciado, `ldr r0, =.Lf_1`, `bl cps_try_entrar` | Apila una nota con el `sp` y el `fp` de quien llama |
| `endtry` | `bl cps_try_salir` | Desapila y libera la nota con `free` |
| `throw x` | `mov r0, Rx`, `bl cps_lanzar` | Sin notas: imprime el mensaje en la salida de errores y termina con código 1. Con una nota: la desapila, guarda el mensaje en `cps_excepcion`, restaura `sp` y `fp` y salta a la etiqueta |
| `L1:` (la etiqueta del manejador) | Además de la etiqueta, `ldr` de `cps_excepcion` y `str` en la casilla de `e` | |

### Por qué

**El `return` dentro de un `try` ya está resuelto:** el TAC emite un `endtry` por cada
`try` abierto antes del `return` (decisión 28 y el generador de la etapa anterior), así
que la pila de notas nunca guarda un registro que ya no existe.

**Vaciar antes del `try`:** después del salto del `throw`, el código del `catch` lee de
memoria. Cualquier valor que solo estuviera en un registro se perdería.

### Aceptación

- `flujo_try_retorno` y `flujo_errores_ejecucion` imprimen lo mismo que el intérprete.
- Un `throw` dentro de una función llamada desde un `try` en `main` llega al `catch`, y
  después la recursión y los `print` siguen funcionando: la pila quedó bien.
- Un error sin `catch` termina con código 1, igual que un error en ejecución del
  intérprete.
