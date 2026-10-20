# Fase 2: Esqueleto del backend

**Objetivo de la fase:** que el compilador produzca un `.s` completo y ejecutable, que
corra en el contenedor e imprima. El programa todavía solo imprime constantes, pero ya
tiene sus secciones, su `main`, su registro de activación, su runtime y su salida.

**Por qué va antes que las expresiones:** cada ticket de las fases siguientes se
verifica corriendo el `.s`. Sin un esqueleto que corre, no hay forma de verificar
nada.

**Al terminar:** `print("hola"); print(42); print(3.5); print(true);` produce un `.s`
que imprime las cuatro líneas en el contenedor, igual que el intérprete.

**Teoría que la sostiene:** puntos 1 (registros), 4 (directivas) y 9 (llamadas al
sistema y libc).

---

## Ticket 2.1: El modelo del programa ARM

- **Estado**: pendiente
- **Depende de**: ninguno

**Archivos:**

- `backend/models/Register.kt` (NUEVO)
- `backend/models/Condition.kt` (NUEVO)
- `backend/models/ArmLine.kt` (NUEVO)
- `backend/models/AssemblyProgram.kt` (NUEVO)
- `backend/ArmPrinter.kt` (NUEVO)
- Tests: `ArmPrinterTest` (NUEVO)

### Qué es esto, en simple

Igual que el TAC no se genera como texto sino como una lista de `Quadruple`, el ARM se
genera como una lista de líneas con tipo, y un impresor aparte lo vuelve texto. Así
los tests comparan estructuras, y el texto vive en un solo lugar.

### Qué se hace

```kotlin
enum class Register(val text: String) {
    R0("r0"), R1("r1"), R2("r2"), R3("r3"), R4("r4"), R5("r5"), R6("r6"), R7("r7"),
    R8("r8"), R10("r10"), FP("fp"), IP("ip"), SP("sp"), LR("lr"), PC("pc")
}

enum class FloatRegister(val text: String) { D0("d0"), D1("d1"), D2("d2") }

// Los sufijos de condicion: lo que se pregunta despues de un cmp.
enum class Condition(val suffix: String) {
    EQUAL("eq"), NOT_EQUAL("ne"), LESS("lt"), LESS_EQUAL("le"), GREATER("gt"),
    GREATER_EQUAL("ge"), MINUS("mi"), LOWER_SAME("ls")
}

sealed interface ArmLine {
    data class Directive(val name: String, val arguments: List<String>) : ArmLine
    data class LabelLine(val name: String) : ArmLine
    data class Instruction(val mnemonic: String, val operands: List<String>) : ArmLine
}

data class AssemblyProgram(val lines: List<ArmLine>)
```

`Instruction` lleva el mnemónico y los operandos ya formateados (`"[fp, #-8]"`,
`"#5"`). Hay ayudantes para armar cada forma de operando, y ningún generador escribe
corchetes ni numerales a mano.

### Por qué

**Mnemónico como texto y no un `enum` de instrucciones:** ARM tiene cientos de
combinaciones (`ldr`, `ldrb`, `ldrne`, `vldr`…). Un `enum` de todas no se lee, y uno
de unas pocas obligaría a editarlo en cada ticket. Los conjuntos que sí son cerrados y
se razonan, los registros y las condiciones, son `enum` (principio 5).

### Aceptación

- `ArmPrinter` imprime directivas, etiquetas e instrucciones con sangría uniforme y
  comentarios con `@`.
- Hay un test por cada forma de operando.

---

## Ticket 2.2: Secciones, nombres y el `main`

- **Estado**: pendiente
- **Depende de**: 2.1

**Archivos:**

- `backend/ArmGenerator.kt` (NUEVO)
- `backend/ArmNames.kt` (NUEVO)
- Tests: `ArmGeneratorTest` (NUEVO)

### Qué se hace

El generador recorre el `TacProgram` y produce, en este orden:

| Sección | Contenido | Viene de |
|---|---|---|
| Encabezado | `.syntax unified`, `.arm`, `.fpu vfp` | Decisión 58 |
| `.data` | Una tabla de métodos por clase: `.word` con la etiqueta de cada método | `VirtualTableDefinition` |
| `.bss` | La zona estática: `.balign 8` y `.space` con su tamaño | `StorageLayout.staticSize` |
| `.section .rodata` | Cada literal de string, con `.asciz` y una etiqueta | Las constantes de texto del TAC |
| `.text` | Las funciones, empezando por `main` | `FunctionBegin` a `FunctionEnd` |
| Al final | El runtime y `.section .note.GNU-stack` | Ticket 2.3 |

`$main` se traduce como `main`, el punto de entrada que espera `gcc`. Su prólogo
reserva su registro y su epílogo devuelve 0 en r0, que es el código de salida.

**Los nombres**, en `ArmNames`:

| TAC | ARM | Por qué |
|---|---|---|
| `$main` | `main` | La entrada que busca libc |
| `factorial`, `Perro.hablar`, `externa.sumar` | `cps_factorial`, `cps_Perro.hablar`, `cps_externa.sumar` | El prefijo evita chocar con un símbolo de libc: una función de Compiscript llamada `printf` o `exit` rompería el enlace |
| `L3` dentro de `factorial` | `.Lfactorial_3` | `.L` hace la etiqueta local al archivo, y el nombre de la función la vuelve única |
| Literal de string número 2 | `.Lstr_2` | |

### Por qué

**`.bss` para la zona estática:** las globales reciben su valor con instrucciones dentro
de `$main` (decisión 39), así que no hay valores iniciales que escribir. `.bss` llega en
ceros sin ocupar espacio en el ejecutable.

### Aceptación

- El `.s` de un programa vacío ensambla y corre en el contenedor, y termina con código 0.
- Un programa con clases tiene sus tablas en `.data`, una línea por método.
- Dos funciones con una etiqueta `L1` cada una ensamblan sin choque.

---

## Ticket 2.3: El runtime base y `print`

- **Estado**: pendiente
- **Depende de**: 2.2

**Archivos:**

- `resources/runtime/runtime.s` (NUEVO)
- `backend/ArmGenerator.kt` (MODIFICAR: `Print`)
- Tests: `ArmGeneratorTest` (AMPLIAR)

### Qué es esto, en simple

El runtime es una pequeña biblioteca escrita en ensamblador que el compilador pega al
final de cada `.s`. El código generado nunca llama a libc directo: llama a una rutina
del runtime, y solo el runtime habla con libc (decisión 62).

### Qué se hace

Las rutinas de esta fase. Todas reciben su argumento en r0 (o d0 si es flotante),
siguen el estándar de llamadas de ARM y conservan r4 a r11:

| Rutina | Qué hace | Por dentro |
|---|---|---|
| `cps_imprimir_entero` | Imprime r0 y un salto de línea | `printf("%d\n")` |
| `cps_imprimir_flotante` | Imprime d0 con el formato de la decisión 71 | `snprintf` con `%.6f` a un búfer, quita los ceros sobrantes y lo imprime |
| `cps_imprimir_string` | Imprime el texto al que apunta r0 | `printf("%s\n")` |
| `cps_imprimir_booleano` | Imprime `true` o `false` | Elige el literal y llama a la anterior |

`print_i 42` se traduce a `mov r0, #42` y `bl cps_imprimir_entero`.

Al llamar a `printf` con un flotante, el valor va en r2 y r3 y no en d0, porque
`printf` recibe una cantidad variable de argumentos, y en ese caso el estándar de ARM
usa siempre los registros de enteros (`vmov r2, r3, d0`). La pila tiene que estar
alineada a 8 en cada llamada a libc.

### Por qué

**Un archivo `.s` como recurso y no texto generado en Kotlin:** el runtime no depende
del programa. Escrito como ensamblador real, se puede leer, ensamblar y probar solo.

### Aceptación

- El programa de los cuatro `print` imprime exactamente lo mismo que el intérprete.
- `runtime.s` ensambla solo, sin el código generado.

---

## Ticket 2.4: El backend en el pipeline

- **Estado**: pendiente
- **Depende de**: 2.2

**Archivos:**

- `runtime/CompilerPipeline.kt` (MODIFICAR: etapa H)
- `runtime/models/CompilationResult.kt` (MODIFICAR: `assembly`)
- Tests: `CompilerPipelineTest` (AMPLIAR)

### Qué se hace

```kotlin
// Etapa H: el ensamblador ARM, a partir del TAC y de la memoria ya asignada.
val assembly = tac?.let { ArmGenerator(it, storageLayout).generate() }
```

`CompilationResult` gana `assembly: AssemblyProgram?`, nulo en los mismos casos que el
TAC.

### Aceptación

- Un programa con errores no tiene ensamblador; uno válido sí.
- Mientras el generador no traduzca una instrucción, falla con un mensaje que la nombra,
  y el pipeline sigue mostrando el TAC.
