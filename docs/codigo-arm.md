# Generación de código ARM para Compiscript

Este documento describe la última etapa del compilador de Compiscript: la traducción del
código de tres direcciones a ensamblador ARM de 32 bits. Explica cómo se pasa de una
representación que no depende de ninguna máquina a instrucciones de un procesador real,
qué equivalencias hay con MIPS, que es la arquitectura de referencia del curso, y qué
decisiones se tomaron en el camino.

## 1. De la representación intermedia a la máquina

### 1.1 Qué hace el back-end

Un compilador se divide en dos mitades. El front-end agrupa todo lo que depende del
lenguaje fuente, desde el análisis léxico hasta la generación del código de tres
direcciones, mientras que el back-end agrupa todo lo que depende de la máquina donde se
va a ejecutar el programa. Esta etapa es justamente el back-end: recibe el código de
tres direcciones que produjo la etapa anterior y lo convierte en un archivo de
ensamblador que se puede ensamblar, enlazar y correr.

La distancia que cubre esta traducción es menor de lo que parece, ya que el código
intermedio ya desarmó las construcciones de alto nivel. Para cuando llega al back-end,
un ciclo ya es una etiqueta con saltos, un objeto ya es un bloque de memoria con
desplazamientos fijos, y una llamada a método ya es una búsqueda en la tabla de métodos
seguida de una llamada indirecta. Lo que le queda por resolver al back-end es lo que el
código intermedio dejó a propósito sin decidir: en qué registro vive cada valor, cómo se
arma físicamente cada registro de activación en la pila, y cómo se piden al sistema los
servicios que el programa no puede hacer por sí mismo, como imprimir o pedir memoria.

### 1.2 Por qué recibe el código intermedio y no el árbol sintáctico

El back-end lee únicamente el código de tres direcciones y la información de memoria que
calculó la etapa anterior, es decir, el tamaño de la zona estática, la forma de cada
registro de activación y la disposición de cada objeto. En ningún momento consulta el
árbol sintáctico ni la tabla de tipos de Compiscript.

Esto es lo que justifica haber generado código intermedio en primer lugar. Si el
back-end tuviera que mirar el árbol, volvería a depender del lenguaje fuente y la
separación entre las dos mitades se perdería. Es por esto que, cuando al diseñar esta
etapa se encontró algo que el back-end necesitaba saber y que el código intermedio no
decía, como el tipo de un temporal, la solución fue agregarlo al código intermedio y no
ir a buscarlo al árbol.

### 1.3 Por qué ARM de 32 bits

La máquina destino es un procesador ARM de 32 bits como el de una Raspberry Pi, con el
sistema operativo Linux. Las direcciones miden 4 bytes y la pila se alinea a 8, que son
los tamaños que ya usó la etapa anterior al calcular los registros de activación, de
tal forma que ningún desplazamiento tuvo que cambiar.

Dentro de la familia ARM, el compilador usa solo las instrucciones que existen desde
ARMv6, que es la versión del procesador de la primera Raspberry Pi. Las versiones
posteriores agregan instrucciones como la división entera o la carga de una constante
de 32 bits en dos pasos, pero todas tienen un reemplazo directo, y a cambio el mismo
archivo corre en cualquier modelo de Raspberry Pi. Los flotantes se calculan en doble
precisión con la unidad de punto flotante del procesador, que en ARM se llama VFP.

Cabe mencionar que una Mac no puede ejecutar ARM de 32 bits, ni siquiera las que tienen
un procesador ARM, ya que estas solo ejecutan la versión de 64 bits. Para probar los
programas se usa un contenedor de Linux ARM emulado, y el mismo archivo de ensamblador
corre sin cambios en una Raspberry Pi real.

## 2. De MIPS a ARM

La arquitectura de referencia del curso es MIPS, por lo que cada concepto de esta etapa
se estudió primero en MIPS y después se buscó su equivalente en ARM. Las dos son
arquitecturas RISC, es decir, solo operan sobre registros y solo tocan la memoria con
instrucciones dedicadas de carga y de guardado, así que las ideas se trasladan casi
completas. Las diferencias están en la cantidad de registros, en cómo se comparan los
valores y en cómo se piden servicios al sistema.

### 2.1 Los registros

Un registro es un espacio de almacenamiento dentro del procesador. Sirve pensarlo como
una mesa de trabajo, en la que caben muy pocas cosas pero todo lo que está encima se
puede operar al instante, mientras que la memoria es una bodega enorme a la que hay que
ir a buscar las cosas y a la que hay que devolverlas. El procesador no puede sumar dos
valores que están en la bodega: primero los trae a la mesa.

MIPS tiene 32 registros y ARM de 32 bits tiene 16, de los cuales tres tienen un uso
fijo. Esta diferencia es la que más pesa en la generación de código, ya que en ARM
quedan muy pocos registros libres para guardar valores, y decidir cuáles se quedan en
la mesa se vuelve más importante.

| ARM | Uso | Equivalente en MIPS |
|---|---|---|
| r0 a r3 | Los primeros cuatro argumentos de una llamada; r0 devuelve el resultado | $a0 a $a3, y $v0 para el resultado |
| r4 a r8, r10 | Valores que deben sobrevivir a una llamada | $s0 a $s7 |
| r9 | Reservado por algunas plataformas | Sin equivalente |
| r11, también llamado fp | Apunta al registro de activación de la función actual | $fp |
| r12, también llamado ip | Registro de paso para cálculos de un momento | $at, aproximadamente |
| r13, también llamado sp | El tope de la pila | $sp |
| r14, también llamado lr | La dirección a la que se vuelve después de una llamada | $ra |
| r15, también llamado pc | La dirección de la siguiente instrucción | No es visible en MIPS |
| d0 a d15 | Registros de punto flotante de doble precisión | $f0 a $f31, en pares |

A diferencia de MIPS, ARM no tiene un registro que valga siempre cero como $zero, ni
registros reservados para el sistema operativo como $k0 y $k1. En cambio, tiene un
registro de estado con cuatro banderas que guardan el resultado de la última
comparación, algo que MIPS no tiene y que cambia la forma de traducir las condiciones.

### 2.2 Los registros temporales y los que se conservan

MIPS divide sus registros de uso general en dos grupos según quién se encarga de
conservarlos durante una llamada. Los registros $t son temporales: la función llamada
puede escribir sobre ellos sin avisar, y si quien llama tenía algo importante ahí, le
toca guardarlo antes de la llamada. Los registros $s, en cambio, se consideran
prestados: si la función llamada quiere usarlos, debe guardar su valor al empezar y
restaurarlo antes de volver, de modo que quien llama los encuentra intactos.

ARM tiene exactamente la misma división, definida por su estándar de llamadas. Los
registros r0 a r3 y r12 se comportan como los $t, y los registros r4 a r11 como los $s.
La diferencia práctica es el tamaño de cada grupo: MIPS tiene diez registros $t,
mientras que en ARM los temporales son solo cinco, y cuatro de ellos son también los
registros por donde viajan los argumentos.

En mi caso, considero que esta división es la que define dónde conviene guardar los
valores del programa. Un valor que vive en r4 sobrevive a una llamada a otra función sin
que haya que guardarlo, mientras que uno que vive en r1 se puede perder en la primera
llamada. Es por esto que el compilador guarda los valores del programa únicamente en
r4 a r8 y en r10, y deja r0 a r3 y r12 para los argumentos, los resultados de las
rutinas de apoyo y los cálculos de un instante.

### 2.3 Las directivas

Las directivas no son instrucciones. Son indicaciones para el ensamblador, que le dicen
cómo organizar el archivo y cuánto espacio reservar, pero que no se ejecutan. Un
programa se divide en secciones, que funcionan como cajones: uno para los datos con
valor inicial, otro para los datos que empiezan en cero, otro para los textos que nunca
cambian y otro para el código.

ARM usa el ensamblador de GNU, cuyas directivas son casi idénticas a las del simulador
MARS que se usa en el curso. La siguiente tabla reúne todas las que necesita el
compilador.

| Directiva en ARM | Equivalente en MARS | Qué hace | Ejemplo |
|---|---|---|---|
| .data | .data | Abre la sección de datos con valor inicial | Las tablas de métodos de las clases |
| .bss | Sin equivalente directo | Abre la sección de datos que empiezan en cero, que no ocupan espacio en el ejecutable | La zona estática de las variables globales |
| .section .rodata | Sin equivalente directo | Abre la sección de datos de solo lectura | Los literales de texto y las constantes flotantes |
| .text | .text | Abre la sección de código | Todas las funciones del programa |
| .global | .globl | Hace visible una etiqueta fuera del archivo | .global main, para que el sistema encuentre el inicio |
| .asciz | .asciiz | Un texto terminado con un byte en cero | saludo: .asciz "hola" |
| .ascii | .ascii | Un texto sin el byte en cero al final | Texto que se imprime con una longitud conocida |
| .space o .skip | .space | Reserva una cantidad de bytes en cero | globales: .space 8 |
| .word | .word | Un valor de 4 bytes | Cada entrada de una tabla de métodos |
| .byte | .byte | Un valor de 1 byte | Un booleano con valor inicial |
| .double | .double | Un flotante de 8 bytes | tres_y_medio: .double 3.5 |
| .align | .align | Alinea a una potencia de 2: .align 3 alinea a 8 | Antes de un flotante |
| .balign | Sin equivalente directo | Alinea a la cantidad de bytes indicada: .balign 8 alinea a 8 | Se prefiere a .align porque se lee directo |
| .syntax unified, .arm, .fpu vfp | Sin equivalente | Fijan la sintaxis, el modo de instrucciones y la unidad de punto flotante | Al inicio de cada archivo |

Hay dos diferencias que vale la pena remarcar. La primera es que en ARM los comentarios
empiezan con arroba y no con numeral, ya que el numeral marca las constantes dentro de
una instrucción. La segunda es que ARM no puede escribir una constante grande dentro de
una instrucción, porque cada instrucción mide 32 bits y la constante ocuparía todo ese
espacio. Para resolverlo, el ensamblador guarda la constante en una pequeña tabla junto
al código, llamada literal pool, y la carga desde ahí con una instrucción de carga.

En el caso de este compilador, la zona estática de las variables globales va completa
en la sección que empieza en cero, ya que las globales reciben su valor con
instrucciones al inicio del programa y no al cargarlo. Su tamaño es el mismo que la
etapa anterior calculó para la tabla de símbolos, y cada global se encuentra sumando su
desplazamiento a la etiqueta de la zona.

### 2.4 Las llamadas al sistema y la biblioteca de C

Un programa no puede escribir en la pantalla ni pedir memoria por sí mismo, ya que esas
son tareas del sistema operativo. Para pedírselas existe la llamada al sistema, que en
las dos arquitecturas funciona igual: se pone el número del servicio en un registro, los
datos en otros, y se ejecuta una instrucción especial que le pasa el control al sistema.

| | MARS | Linux en ARM |
|---|---|---|
| Registro del número de servicio | $v0 | r7 |
| Registros de los datos | $a0, $a1, $a2 | r0, r1, r2 |
| Instrucción que llama al sistema | syscall | svc #0 |
| Registro de la respuesta | $v0 | r0 |

La diferencia importante está en los servicios disponibles. MARS es un simulador hecho
para la enseñanza, y por eso ofrece servicios cómodos como imprimir un entero, imprimir
un flotante o leer un número. Linux no ofrece nada de eso: solo sabe escribir bytes, de
modo que imprimir el número 42 exige convertirlo antes en los caracteres 4 y 2, y
convertir un flotante a texto es un algoritmo bastante más delicado.

| Servicio | MARS | Linux en ARM |
|---|---|---|
| Imprimir un entero | 1 | No existe |
| Imprimir un texto | 4 | 4, que escribe bytes |
| Pedir memoria | 9 | 45, que mueve el final del montículo |
| Terminar el programa | 10 | 1 |

Es por esto que el compilador no llama al sistema directamente, sino que enlaza el
programa con la biblioteca estándar de C, que ya resuelve estas tareas sobre las mismas
llamadas al sistema. De ella se usan las funciones para imprimir con formato, para
reservar y agrandar bloques de memoria, para medir y copiar textos y para comparar
textos. Además, el compilador agrega a cada programa un pequeño conjunto de rutinas
propias escritas en ensamblador, una por cada servicio que necesita el código generado,
y solo esas rutinas hablan con la biblioteca. Así, el código que produce el compilador
depende de una interfaz propia, y si la forma de imprimir o de reservar memoria cambia,
cambia en un solo lugar.

En mi caso, considero que enlazar con la biblioteca de C es la decisión que mejor
equilibra el esfuerzo con lo que se aprende en el curso. Escribir a mano la conversión
de un flotante a texto no aporta nada a la generación de código, mientras que la
asignación de registros, la secuencia de llamadas y el manejo de la memoria dinámica,
que son el centro de esta etapa, se resuelven completos en el ensamblador generado.
