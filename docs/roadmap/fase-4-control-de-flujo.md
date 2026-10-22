# Fase 4: Control de flujo

**Objetivo de la fase:** traducir las etiquetas y los saltos del TAC. Como el TAC ya
trae las condiciones convertidas en saltos con la relación invertida (decisión 34),
esta fase es casi uno a uno. Lo delicado es vaciar los registros en el momento justo.

**Por qué va aquí:** los saltos cierran bloques básicos, y vaciar al final de un bloque
ya existe desde la Fase 3. Las funciones de la Fase 5 se prueban con programas que
tienen `if` y bucles, así que esta fase va antes.

**Al terminar:** los programas de flujo de la batería (condiciones, bucles, `switch`,
`break`, `continue`, ternario y `foreach`) corren en el contenedor e imprimen lo mismo
que el intérprete.

**Teoría que la sostiene:** puntos 6 y 8 (comparaciones y saltos).

---

## Ticket 4.1: Etiquetas y saltos

- **Estado**: pendiente
- **Depende de**: 3.4

**Archivos:**

- `backend/ArmGenerator.kt` (MODIFICAR: `LabelDefinition`, `Goto`, `IfGoto`,
  `IfFalseGoto`, `IfRelationalGoto`)
- Tests: `ArmGeneratorFlowTest` (NUEVO)

### Qué es esto, en simple

MIPS compara y salta en una sola instrucción (`blt $t0, $t1, L1`). ARM lo hace en dos:
`cmp` deja la respuesta en las banderas, y el salto con sufijo (`blt`) la lee. Como un
`str` no toca las banderas, el vaciado de registros puede ir justo entre las dos.

### Qué se hace

| TAC | ARM |
|---|---|
| `L3:` | Vaciado del bloque anterior, `.Lfuncion_3:` y libretas en blanco |
| `goto L3` | Vaciado y `b .Lfuncion_3` |
| `if a < b goto L3` | `cmp Ra, Rb`, vaciado, `blt .Lfuncion_3` |
| `if t goto L3` | `cmp Rt, #0`, vaciado, `bne .Lfuncion_3` |
| `ifFalse t goto L3` | `cmp Rt, #0`, vaciado, `beq .Lfuncion_3` |
| `if p != null goto L3` | `cmp Rp, #0`, vaciado, `bne .Lfuncion_3` |
| `if x <f y goto L3` | `vcmp.f64`, `vmrs APSR_nzcv, fpscr`, vaciado, `bmi .Lfuncion_3` |
| `if s ==s t goto L3` | `bl cps_comparar_strings`, `cmp r0, #0`, vaciado, `beq .Lfuncion_3` |

Las relaciones de enteros, booleanos y referencias usan `eq`, `ne`, `lt`, `le`, `gt` y
`ge`. Las de flotantes usan `eq`, `ne`, `mi`, `ls`, `gt` y `ge`. `null` es la dirección
0.

`cps_comparar_strings`, que ya existe desde el ticket 3.4, llama a `strcmp` y devuelve
en r0 un número negativo, cero o positivo, que se compara con 0 usando la misma relación
del TAC.

### Por qué

**Vaciar antes de saltar y no en la etiqueta:** en la etiqueta no se sabe de qué bloque
se viene; en el salto sí, y ahí las libretas todavía dicen qué falta guardar.

### Aceptación

- `flujo_condiciones`, `flujo_switch`, `flujo_break_continue`, `flujo_ternario`,
  `tipos_comparaciones` y los programas de `foreach` imprimen lo mismo que el
  intérprete.
- Una prueba con un valor que vive en un registro antes de un `if` y se lee después
  de la etiqueta de salida muestra el `str` antes del salto.

---

## Ticket 4.2: El documento, flujo de control

- **Estado**: pendiente
- **Depende de**: 4.1

**Archivos:**

- `docs/codigo-arm.md` (MODIFICAR: secciones de getReg y de flujo de control)

### Qué se hace

Dos secciones nuevas, con los ejemplos sacados del generador real:

- **Bloques básicos, descriptores y getReg**, con el ejemplo trabajado del libro y las
  dos libretas instrucción por instrucción, en tablas.
- **Comparaciones y saltos**, con la tabla de sufijos y la comparación con `slt` de MIPS.

### Aceptación

- Cada ejemplo del documento sale del `.s` de un programa de la batería.
