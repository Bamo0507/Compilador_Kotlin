# Roadmap: Generación de código ARM para Compiscript

Plan de desarrollo de la etapa final: traducir el código de tres direcciones (TAC) que
ya produce el compilador a ensamblador ARM de 32 bits, con asignación de registros,
secuencia de llamadas, memoria dinámica y un runtime propio, y ejecutarlo.

Los roadmaps de las etapas anteriores están archivados: el del analizador semántico en
[`semantico/`](./semantico/README.md), con las decisiones 1 a 16, y el del código
intermedio en [`intermedio/`](./intermedio/README.md), con las decisiones 17 a 57. Todas
siguen vigentes.

---

## Qué se entrega

Un archivo `.s` por programa, que se ensambla y corre en una Raspberry Pi o en un
contenedor ARM de 32 bits, y que imprime lo mismo que el intérprete. Además: la
generación visible en el IDE, la batería de pruebas de punta a punta, y la
documentación de la traducción.

El enunciado de esta etapa todavía no está en el repositorio. Cuando llegue, la rúbrica
se agrega aquí y se revisa el mapa de fases contra ella.

---

## Cómo se trabaja

Igual que en las etapas anteriores: cada punto de teoría se estudia primero, después se
ejecuta la fase que sale de él, un ticket a la vez. Antes de cada ticket se da una
sinopsis corta, y se toca código solo con el visto bueno.

La fuente de verdad son las presentaciones del curso, el Dragon Book (capítulo 8 para
la generación de código) y el estándar de llamadas de ARM, AAPCS32. La referencia del
curso es MIPS: cada concepto se estudia con su equivalente en ARM.

Los documentos entregables siguen la misma regla de siempre: prosa conceptual, sin
diagramas ASCII ni secciones pendientes, porque se pasan a Google Docs. Los ejemplos de
traducción van en tablas de dos columnas, TAC a la izquierda y ARM a la derecha.

---

## Mapa de fases

| Fase | Qué se logra |
|---|---|
| [0, Preparación](./fase-0-preparacion.md) | Roadmap archivado, entorno ARM en Docker probado, documento iniciado |
| [1, Cambios al lenguaje](./fase-1-cambios-al-lenguaje.md) | `push` y `length`, listas con ficha y contenido, formato común de flotantes |
| [2, Esqueleto del backend](./fase-2-esqueleto-del-backend.md) | El generador de ARM, las secciones, `main`, el runtime base y `print` |
| [3, Expresiones y getReg](./fase-3-expresiones-y-getreg.md) | Bloques básicos, próximo uso, descriptores, getReg y la aritmética |
| [4, Control de flujo](./fase-4-control-de-flujo.md) | Etiquetas, saltos y comparaciones |
| [5, Funciones](./fase-5-funciones.md) | Secuencia de llamadas, enlace de acceso, retorno y recursión |
| [6, Objetos, listas y strings](./fase-6-objetos-listas-y-strings.md) | Montículo, vtables, listas que crecen, concatenación y chequeos |
| [7, Excepciones](./fase-7-excepciones.md) | `try`, `catch` y `throw` con una pila de manejadores |
| [8, El IDE y la ejecución](./fase-8-ide-y-ejecucion.md) | La pantalla del `.s`, exportarlo y el script para correrlo |
| [9, Batería y documentación](./fase-9-bateria-y-documentacion.md) | Los `.s` dorados, la comparación con el intérprete y los documentos finales |

## Mapa de los puntos de teoría

Los diez puntos que pidió el catedrático, más la memoria dinámica:

| # | Punto de teoría | Fuente | Fase |
|---|---|---|---|
| 1 | Los registros y para qué sirve cada uno | AAPCS32, MIPS | Fase 2 |
| 2 | Registros que se salvan al llamar y los que no (`$t` y `$s`) | AAPCS32 | Fase 3 y Fase 5 |
| 3 | Descriptores de registro y de dirección, y `getReg(I)` | Dragon Book 8.6 | Fase 3 |
| 4 | Directivas (`.data`, `.asciz`, `.space`…) | GNU as | Fase 2 |
| 5 | Instrucciones aritméticas y de desplazamiento | ARM ARM | Fase 3 |
| 6 | Comparaciones (`slt` en MIPS, `cmp` y sufijos en ARM) | ARM ARM | Fase 4 |
| 7 | Acceso a memoria | ARM ARM | Fase 3 y Fase 6 |
| 8 | Flujo de control: etiquetas y saltos | ARM ARM, Dragon Book 8.4 | Fase 4 |
| 9 | Llamadas al sistema y biblioteca de C | Linux EABI, libc | Fase 2 |
| 10 | El stack frame | AAPCS32, Dragon Book 7.2 | Fase 5 |
| 11 | Memoria dinámica: montículo, listas que crecen, falta de memoria | Dragon Book 7.4, presentaciones 07 y 08 | Fase 1 y Fase 6 |

---

## Formato de cada ticket

Cada ticket lleva:

- **Estado**: `pendiente` | `en progreso` | `completado`. Se actualiza a mano.
- **Depende de**: tickets previos requeridos.
- **Archivos**: qué crea, modifica o elimina.
- **Qué es esto, en simple**: explicación en lenguaje llano, cuando el concepto
  lo necesita.
- **Qué se hace**: el diseño concreto, con código.
- **Por qué**: la razón de la decisión. Presente siempre que la decisión no sea
  obvia, porque son las que se preguntan en la defensa.
- **Aceptación**: cuándo se considera terminado, en criterios verificables.
- **Respaldo**: la sección del enunciado, del libro o de las notas de clase que
  lo justifica.

---

## Principios de código para todo el proyecto

Los mismos ocho de las etapas anteriores, sin cambios:

1. **Simple antes que ingenioso.** Los comentarios son para el *por qué*, no para el
   *qué*.
2. **Nombres completos.** `currentScope`, no `cs`. `registerDescriptor`, no `rd`.
3. **`sealed interface` / `sealed class` para jerarquías cerradas.** Da `when`
   exhaustivo.
4. **`data object` para constantes únicas**, `data class` para lo que lleva datos.
5. **`enum` en vez de `String`** para conjuntos cerrados (registros, condiciones).
6. **Nada de `object` con estado mutable.** Una instancia por compilación.
7. **Los modelos son datos; las reglas son funciones aparte.**
8. **Una función por construcción.** En esta etapa, una por familia de cuádruplo.

---

## Decisiones de esta etapa

Se numeran desde la 58. Las que se tomen al ejecutar un ticket se agregan aquí en el
momento.

| # | Decisión | Elegida | Por qué |
|---|---|---|---|
| 58 | Arquitectura destino | ARM de 32 bits en modo ARM (no Thumb), Linux, ensamblador de GNU, flotantes en VFP de doble precisión con el ABI de flotantes por hardware; solo instrucciones de ARMv6 y los registros d0 a d15 | Es el conjunto que corre en cualquier Raspberry Pi, desde la Pi 1 hasta la Pi 5 en 32 bits, y en el contenedor de Debian armhf. Las instrucciones de ARMv7 que se pierden (`movw`, `movt`, `sdiv`) tienen reemplazo directo. |
| 59 | Dónde vive el generador | `backend/`, como reservó la decisión 17 | El TAC es el último producto del front-end; traducirlo a una máquina concreta es el back-end (Dragon Book 1.2). |
| 60 | Entrada del generador | El `TacProgram` y el `StorageLayout`, nunca el AST | Es la promesa de la representación intermedia: el back-end no sabe de Compiscript. Si algo falta en el TAC, se agrega al TAC. |
| 61 | Biblioteca de C | Se enlaza con libc: `printf`, `snprintf`, `calloc`, `realloc`, `free`, `strlen`, `memcpy`, `strcmp`, `exit` | Imprimir un flotante solo con la llamada `write` exige un algoritmo de conversión que no aporta a la materia. El programa se arma con `gcc` y su `main` es el punto de entrada. |
| 62 | El runtime | Rutinas propias en ensamblador, una por servicio (`$imprimir_entero`, `$reservar`, `$concatenar`…), que el compilador agrega al final de cada `.s`; solo ellas llaman a libc | El código generado depende de una interfaz nuestra y no de libc. Si una rutina cambia, cambia en un solo lugar. Es la opción A de la discusión. |
| 63 | División y módulo de enteros | `bl __aeabi_idiv` y `bl __aeabi_idivmod`, de libgcc | `sdiv` no existe en la Pi 1 ni en la Zero. libgcc se enlaza siempre con `gcc`. El chequeo de división entre cero del TAC se queda antes de la llamada. |
| 64 | Memoria dinámica | `calloc` para objetos, fichas de lista, contenidos y strings, con el bloque en ceros; `realloc` para el contenido que crece. Un resultado nulo imprime "Error: sin memoria" y termina con código 1 | Es la respuesta a qué hacer cuando no hay espacio: detectarlo y terminar con un mensaje claro. El bloque en ceros hace que un campo sin inicializar sea `null`. `realloc` ya copia los elementos. Completa la decisión 48: lo que pide el programa sigue sin liberarse; solo el runtime libera sus notas de `try`. |
| 65 | Listas | Una ficha fija de 12 bytes, `{longitud, capacidad, contenido}`, y el contenido aparte, que duplica su capacidad al llenarse, con mínimo 4 | Las variables apuntan a la ficha, que nunca se muda; solo se muda el contenido. Así una copia de la referencia nunca queda colgando. Duplicar da costo amortizado constante por `push`. Reemplaza la disposición de la decisión 42 para listas. |
| 66 | Operaciones de lista | `lista.push(x)`, que no devuelve nada, y `lista.length`, de solo lectura | Lo pidió el catedrático al hablar de ArrayList. Sin `length` no se puede recorrer con índices una lista cuyo tamaño cambia. `pop` queda fuera. |
| 67 | Registros para getReg | r4 a r8 y r10, solo para enteros, booleanos y referencias; los flotantes van por d0 a d2 sin descriptores (cargar, operar, guardar) | Son los registros que la función llamada debe conservar (AAPCS32), así que sobreviven a cada llamada sin guardarlos. r9 queda fuera porque algunas plataformas lo reservan. getReg para flotantes duplicaría el trabajo sin cambiar la idea. |
| 68 | Alcance de los descriptores | Un bloque básico; al final de cada bloque y antes de cada llamada se guardan las variables vivas cuyo valor solo está en un registro | Es el generador simple del Dragon Book 8.6. Fuera del bloque no se sabe quién llega por un salto, y la función llamada lee de memoria. |
| 69 | Secuencia de llamadas | El registro de activación crece hacia abajo desde `fp`: la casilla con desplazamiento `d` y tamaño `s` vive en `fp − d − s`. Quien llama reserva la parte fija (parámetros, valor devuelto, enlaces y dirección de retorno), escribe ahí cada `param` y el enlace de acceso, y salta con `bl`; la función llamada guarda `fp` y `lr` en sus casillas y reserva el resto | Usa tal cual los desplazamientos que ya calcula el `StorageAllocator`. Una pila descendente y un registro ascendente se reconcilian con el espejo. La parte fija es igual en todas las versiones de un método sobrescrito, así que también sirve para las llamadas indirectas. Precisa la opción A de la discusión: los parámetros van en la pila y no en r0 a r3. |
| 70 | Valor de retorno | En r0, o en d0 si es flotante; la casilla "valor devuelto" del registro queda reservada y sin uso | Es el estándar, y ahorra un acceso a memoria. Quitar la casilla cambiaría todos los `.tac` dorados sin ganar nada. |
| 71 | Formato de un flotante al imprimir | Seis decimales sin ceros sobrantes, dejando siempre al menos uno: `3.5`, `1.0`, `0.333333` | El `toString` de Kotlin muestra los dígitos mínimos que identifican al número, y reproducirlo en ensamblador es un algoritmo avanzado. Se cambia el intérprete para que los dos coincidan. |
| 72 | Excepciones | Una pila de manejadores en una global; `try` apila `{anterior, sp, fp, etiqueta}`, `endtry` desapila, `throw` restaura `sp` y `fp` y salta. Sin manejador, imprime el mensaje y termina con código 1 | Es la idea de `setjmp`/`longjmp` (decisión 28) escrita a la vista. Descartar los registros de activación intermedios es restaurar `sp` y `fp`. |
| 73 | Dónde se ejecuta | Un contenedor Debian armhf en Docker, con un script; el mismo `.s` corre en una Pi real con los mismos comandos | En una Mac no hay forma nativa de correr ARM de 32 bits. CPUlator no implementa las llamadas al sistema de Linux. |

## Decisiones pendientes

Ninguna por ahora.
