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

## 3. Los bloques básicos y la asignación de registros

### 3.1 Qué es un bloque básico

El código de tres direcciones es una lista plana de instrucciones por la que el flujo no
siempre avanza de una a la siguiente: una etiqueta puede recibir un salto desde cualquier
parte, y un salto puede llevarse el control a otro lado. Un bloque básico es un tramo de
esa lista en el que eso no pasa, es decir, un tramo en el que se entra solo por la primera
instrucción y se sale solo por la última.

Un bloque nuevo empieza en cada líder, y los líderes son tres: la primera instrucción de
la función, cada etiqueta, porque alguien puede saltar ahí desde otro lado, y la
instrucción que sigue a un salto, a una llamada, a un retorno, a un lanzamiento de
excepción o a la apertura de un bloque protegido, porque después de ellas el flujo puede
no llegar en orden o los registros dejan de ser confiables.

La razón por la que esta división importa es que adentro de un bloque el generador puede
confiar en lo que recuerda. Si sabe que la variable `x` quedó en el registro r4, y la
siguiente instrucción también pertenece al bloque, entonces es seguro que `x` sigue en r4
cuando esa instrucción se ejecute, porque no hay forma de llegar a ella sin haber pasado
por la anterior. En la primera instrucción de un bloque esa garantía no existe, ya que
pueden llegar varios caminos distintos y cada uno traería los registros ocupados de otra
forma. Es por esto que el generador empieza cada bloque con las libretas en blanco, es
decir, suponiendo que todo está únicamente en memoria.

### 3.2 Las dos libretas

El generador lleva dos registros de lo que sabe, que el Dragon Book llama descriptores.
Son dos vistas de la misma información, y se mantienen al mismo tiempo porque cada una
responde una pregunta distinta de forma inmediata.

| Libreta | Qué guarda | Qué pregunta responde |
|---|---|---|
| Descriptor de registro | Para cada registro, qué variables tienen ahí su valor actual | Si puedo pisar este registro, y qué perdería si lo hago |
| Descriptor de dirección | Para cada variable, en qué lugares está su valor actual: su casilla de memoria, uno o más registros, o ambos | Si ya la tengo en un registro, y si puedo pisar ese registro sin guardarla antes |

Una variable puede estar en varios lugares a la vez, y esa es justamente la situación
cómoda: si el valor de `x` está en r4 y además en su casilla de memoria, entonces r4 se
puede reutilizar sin generar ninguna instrucción, porque nada se pierde. Al revés, una
variable cuyo valor existe únicamente en un registro obliga a guardarla antes de pisarlo.

### 3.3 getReg

Cada vez que una instrucción necesita un operando en un registro, el generador llama a la
función que el libro llama `getReg`. Funciona como un `getOrPut`: si la variable ya está
en un registro, lo devuelve sin generar nada; si no, consigue uno, genera la carga y lo
anota. Lo que tiene de interesante es cómo consigue ese registro, porque las opciones
están ordenadas de la más barata a la más cara.

| Orden | Qué registro se elige | Qué cuesta |
|---|---|---|
| 1 | El que ya tiene el valor de la variable | Ninguna instrucción |
| 2 | Uno que esté vacío | Una carga |
| 3 | Uno cuyas variables estén todas también en memoria o ya no se usen | Una carga |
| 4 | El que obligue a guardar menos variables | Un guardado por cada variable que solo vivía ahí, y después la carga |

Para el registro donde se va a escribir el resultado hay un paso extra al principio: si
existe un registro que contiene únicamente a la variable que está por escribirse, ese se
usa, porque su valor anterior deja de servir en el momento en que se calcule el nuevo.
Después se intenta con el registro de alguno de los operandos, siempre que ese operando no
se vuelva a leer en el bloque y su valor esté a salvo en otro lado, y recién entonces se
cae en los pasos de arriba.

La decisión de cuál registro vaciar cuando ya no quedan libres se apoya en la información
de próximo uso, que el generador calcula para cada bloque recorriéndolo de atrás hacia
adelante. Para cada instrucción se anota, de cada variable que aparece, si su valor se va
a volver a necesitar y en qué instrucción del bloque se vuelve a leer. Entre dos registros
igual de seguros conviene vaciar el que tenga las variables que se leen más tarde, o las
que no se vuelven a leer dentro del bloque.

### 3.4 El ejemplo del libro, instrucción por instrucción

El Dragon Book trabaja este bloque de cinco instrucciones suponiendo una máquina de tres
registros. El generador de este compilador acepta que se le diga con cuántos registros
trabajar, de modo que el ejemplo se puede reproducir tal cual y comparar con el libro.

| Código de tres direcciones | ARM generado |
|---|---|
| `t = a - b` | `ldr r4, [fp, #-4]` y `ldr r5, [fp, #-8]` y `sub r5, r4, r5` |
| `u = a - c` | `ldr r6, [fp, #-12]` y `sub r4, r4, r6` |
| `v = t + u` | `add r5, r5, r4` |
| `a = d` | `ldr r6, [fp, #-16]` |
| `d = v + u` | `add r5, r5, r4` |
| Final del bloque | `str r5, [fp, #-16]` y `str r6, [fp, #-4]` |

Son diez instrucciones, las mismas que llega a contar el libro. La tabla siguiente
muestra cómo quedan las dos libretas después de cada paso, que es donde se ve por qué se
eligió cada registro. La columna de los registros está completa, mientras que la de las
direcciones lista solo lo que cambió en ese paso.

| Instrucción | Descriptor de registro | Descriptor de dirección |
|---|---|---|
| `t = a - b` | r4 tiene `a`, r5 tiene `t`, r6 está vacío | `a` en memoria y en r4, `b` solo en memoria, `t` solo en r5 |
| `u = a - c` | r4 tiene `u`, r5 tiene `t`, r6 tiene `c` | `u` solo en r4, `c` en memoria y en r6 |
| `v = t + u` | r4 tiene `u`, r5 tiene `v`, r6 tiene `c` | `v` solo en r5, `t` ya no está en ningún lado |
| `a = d` | r4 tiene `u`, r5 tiene `v`, r6 tiene `d` y `a` | `a` solo en r6, `d` en memoria y en r6 |
| `d = v + u` | r4 tiene `u`, r5 tiene `d`, r6 tiene `a` | `d` solo en r5, `v` ya no está en ningún lado |

Hay tres momentos que vale la pena señalar. El primero es que `t = a - b` escribe su
resultado en el registro de `b`, porque el valor de `b` está a salvo en su casilla de
memoria y no se vuelve a leer en el bloque. El segundo es que `a = d` no genera ninguna
instrucción de copia: `d` se carga en un registro y ahí mismo se anota que `a` también
vale eso, de modo que la copia cuesta una anotación y no una instrucción. El tercero es
que al final del bloque solo se guardan `a` y `d`, que son las únicas variables cuyo valor
quedó únicamente en un registro; los temporales `t`, `u` y `v` ya no le sirven a nadie.

### 3.5 Qué se guarda al final del bloque

Como el bloque siguiente va a empezar con las libretas en blanco, todo lo que haga falta
después tiene que estar en memoria antes de salir. Ese vaciado genera un guardado por cada
variable viva cuyo valor exista únicamente en un registro, y es lo último que hace el
bloque.

Decidir cuáles están vivas no es un detalle menor. Las variables del programa se guardan
siempre, porque otra función, o la misma función después de volver de una llamada, pueden
leerlas, y el generador de una sola función no puede saberlo. Los temporales son distintos:
se puede saber con precisión si alguien los vuelve a leer, y guardar los que no hace falta
es trabajo perdido en una instrucción que se ejecuta en cada vuelta de un ciclo.

Para saberlo, el generador resuelve la vivacidad sobre el grafo de flujo de la función
completa antes de empezar a traducirla, con el análisis hacia atrás de siempre: lo que
está vivo al salir de un bloque es la unión de lo que está vivo al entrar a cada uno de
sus sucesores, y lo que está vivo al entrar a un bloque es lo que ese bloque lee antes de
escribirlo, más lo que necesitaba el final y el bloque no sobrescribe. Se repite hasta que
ninguno de los dos conjuntos cambia, lo que siempre ocurre porque los conjuntos solo
crecen y son finitos.

En mi caso, considero que hacer este análisis sobre el grafo y no sobre el orden del
archivo es la parte menos evidente de toda la fase. La tentación es mirar hacia adelante
en la lista de instrucciones, que es mucho más simple, y funciona en casi todos los casos.
Falla justamente en el operador ternario: la rama verdadera termina en un salto
incondicional, y lo que está escrito a continuación de ese salto es la rama falsa, que
vuelve a escribir el mismo temporal. Leído en orden de archivo, el temporal parece muerto
al salir de la rama verdadera y no se guarda; leído sobre el grafo está vivo, porque el
camino real va a la etiqueta de abajo, donde se lee. Sin el grafo, el ternario imprime
basura por una de sus dos ramas, y el error no aparece en ninguna prueba que no tome
precisamente esa rama.

## 4. Las comparaciones y los saltos

### 4.1 Las banderas

Esta es la diferencia más visible entre las dos arquitecturas. MIPS compara y salta en una
sola instrucción: `blt $t0, $t1, L1` mira los dos registros y salta si el primero es menor.
ARM no tiene nada parecido, porque separa las dos mitades del trabajo. La instrucción `cmp`
resta los dos operandos, tira el resultado y deja en el registro de estado cuatro banderas
que describen cómo salió esa resta. Después, una instrucción de salto con un sufijo de
condición las lee y decide si salta o no.

| | MIPS | ARM |
|---|---|---|
| Comparar y saltar si es menor | `blt $t0, $t1, L1` | `cmp r4, r5` y después `blt .L1` |
| Comparar y saltar si es igual | `beq $t0, $t1, L1` | `cmp r4, r5` y después `beq .L1` |
| Dejar 1 o 0 según una comparación | `slt $t0, $t1, $t2` | `cmp r5, r6` y después `mov r4, #0` y `movlt r4, #1` |

Conviene notar que `blt` de MIPS tampoco es una instrucción real del procesador, sino una
pseudoinstrucción que el ensamblador expande en un `slt` seguido de un `bne`. Es decir,
MIPS también necesita dos instrucciones; lo que cambia es que ARM no las esconde, y que su
resultado intermedio no ocupa un registro, sino el registro de estado.

Esa última diferencia es la que hace cómoda la traducción. Como el resultado de la
comparación no vive en un registro, no compite con los valores que reparte `getReg`, y
como ninguna instrucción de carga ni de guardado toca las banderas, entre el `cmp` y el
salto se puede meter trabajo que no tenga que ver con la comparación.

Comparar flotantes necesita un paso más, porque la unidad de punto flotante tiene su
propio registro de estado. La instrucción `vcmp.f64` compara y deja el resultado ahí, y
`vmrs APSR_nzcv, fpscr` lo copia al registro de estado principal, que es el que leen los
sufijos. Comparar textos no es una comparación del procesador en absoluto: se llama a una
rutina del runtime que a su vez llama a `strcmp`, y lo que se compara después contra cero
es el número negativo, cero o positivo que esa rutina devuelve en r0.

### 4.2 La tabla de sufijos

| Relación del código intermedio | Enteros, booleanos y referencias | Flotantes |
|---|---|---|
| Igual | `eq` | `eq` |
| Distinto | `ne` | `ne` |
| Menor | `lt` | `mi` |
| Menor o igual | `le` | `ls` |
| Mayor | `gt` | `gt` |
| Mayor o igual | `ge` | `ge` |

Las dos columnas coinciden salvo en menor y en menor o igual, y el motivo es el valor no
numérico que define el estándar de punto flotante, conocido como NaN. Cuando alguno de los
dos operandos es NaN, la comparación no queda ordenada, y las banderas toman una
combinación que hace que `lt` y `le` se cumplan, lo cual es incorrecto, porque un número
no es menor que algo que no es un número. Los sufijos `mi` y `ls` miran banderas distintas
y no se cumplen en ese caso, por lo que son los que corresponden.

Las referencias usan la columna de los enteros porque una referencia es una dirección, y
la ausencia de objeto, que en Compiscript se escribe `null`, es la dirección cero. Así,
preguntar si una referencia es nula es compararla contra cero con `cmp`, exactamente igual
que un entero.

### 4.3 Una condición del lenguaje, de principio a fin

El código intermedio ya traía casi todo resuelto. Al generarlo se decidió que una
condición no produce un valor que después se consulta, sino que se convierte directamente
en un salto, y que ese salto lleva la relación invertida, porque lo que interesa es saltar
cuando la condición no se cumple y seguir de corrido cuando sí. El back-end no vuelve a
invertir nada: toma la relación tal como viene y le busca su sufijo.

| Código intermedio | ARM generado |
|---|---|
| `L3:` | `.Lmain_3:` |
| `goto L3` | el vaciado del bloque y después `b .Lmain_3` |
| `if i >= 5 goto L4` | `ldr ip, =cps_estatico` y `ldr r4, [ip, #4]` y `cmp r4, #5` y el vaciado y `bge .Lmain_4` |
| `if t goto L3` | `cmp r4, #0` y el vaciado y `bne .Lmain_3` |
| `ifFalse t goto L1` | `cmp r4, #0` y el vaciado y `beq .Lmain_1` |
| `if p != null goto L1` | `cmp r4, #0` y el vaciado y `bne .Lmain_1` |
| `if x <f y goto L1` | `vcmp.f64 d1, d2` y `vmrs APSR_nzcv, fpscr` y el vaciado y `bmi .Lf_1` |
| `if s ==s t goto L1` | `bl cps_comparar_strings` y `cmp r0, #0` y el vaciado y `beq .Lf_1` |

Las primeras cinco filas están tomadas del ensamblador de los programas `flujo_condiciones`
y `tipos_logicos` de la batería de pruebas. Las tres últimas salen de las pruebas del
generador, porque ningún programa de la batería llega todavía a ellas: la comparación
contra `null` aparece al usar objetos o listas, y las de flotantes y de textos aparecen
dentro de condiciones que además necesitan llamadas a funciones, y las dos cosas son de
fases posteriores.

Un booleano del código intermedio vale 1 o 0, de modo que preguntar si es verdadero es
preguntar si no es cero. Esa es la razón por la que `if` e `ifFalse` generan exactamente
la misma comparación y se distinguen solo en el sufijo del salto.

Las etiquetas del archivo llevan el nombre de la función de la que salieron, de forma que
la etiqueta `L3` de una función y la `L3` de otra no se pisan, y quien lee el ensamblador
puede saber de dónde viene cada salto. Además empiezan con punto y L, que es la marca con
la que el ensamblador de GNU reconoce una etiqueta local, es decir, una que no aparece en
la tabla de símbolos del ejecutable.

### 4.4 Dónde se vacían los registros

El vaciado del bloque aparece en la tabla anterior en un lugar que a primera vista
sorprende: entre la comparación y el salto, y no antes de la comparación ni en la etiqueta
de destino.

Que pueda ir ahí es consecuencia de lo que se dijo arriba. Las instrucciones de carga y de
guardado de ARM no modifican las banderas, así que lo que dejó el `cmp` sigue intacto
cuando se llega al salto, por más guardados que haya en el medio.

Que tenga que ir ahí es un asunto distinto. La alternativa natural sería vaciar al llegar
a la etiqueta de destino, pero en una etiqueta no se sabe de dónde se viene: pueden llegar
varios caminos, cada uno con los registros ocupados de otra forma, y el generador no
tendría nada confiable que guardar. En el salto, en cambio, el camino es uno solo y las
libretas todavía dicen exactamente qué quedó pendiente. El salto es el último lugar donde
esa información existe.

La primera condición del programa `flujo_condiciones` de la batería muestra el caso
completo. La variable `x` acaba de recibir el valor 20, que existe únicamente en r4, y se
vuelve a leer más adelante, después de las dos ramas.

| Código intermedio | ARM generado |
|---|---|
| `x = 20` | `mov r4, #20` |
| `if x <= 10 goto L1` | `cmp r4, #10` y después `ldr ip, =cps_estatico` y `str r4, [ip]` y después `ble .Lmain_1` |

El guardado de `x` queda en medio de la comparación y el salto, y el cálculo de su
dirección también, porque `x` es una variable global y hay que llegar a ella sumando su
desplazamiento a la etiqueta de la zona estática. Ninguna de las dos instrucciones toca
las banderas. Los dos caminos que salen de ahí, el que salta y el que sigue de corrido,
encuentran a `x` en su casilla, que es exactamente lo que el bloque siguiente va a suponer
cuando empiece con las libretas en blanco.

En mi caso, considero que esta es la parte de la fase donde más se nota que el back-end
razona sobre un grafo y no sobre una lista. Todo lo demás de esta etapa se puede explicar
leyendo el código intermedio de arriba hacia abajo; el vaciado, no.

## 5. El registro de activación y la secuencia de llamadas

### 5.1 El espejo entre el registro y la pila

La etapa anterior ya calculó, para cada función, qué campo va en qué desplazamiento
dentro de su registro de activación y cuánto mide la hoja completa. Esos desplazamientos
crecen desde cero: el primer parámetro está en el 0, el siguiente justo después, y los
temporales al final. La pila de ARM, en cambio, crece hacia abajo, de modo que el dato
que se agrega más tarde queda en una dirección menor.

Las dos cosas se reconcilian con un espejo. El registro `fp` apunta al **tope** del
registro de activación, que es su dirección más alta, y una casilla con desplazamiento
`d` y tamaño `s` vive en `fp − d − s`. Así, el campo que la etapa anterior puso en el
desplazamiento 0 queda pegado a `fp`, y el que puso al final queda pegado al fondo. No
hubo que cambiar un solo número de los que calculó el analizador.

Con el registro de `factorial`, que mide 40 bytes:

| Casilla | Desplazamiento | Tamaño | Dirección |
|---|---|---|---|
| `n` | 0 | 4 | `[fp, #-4]` |
| valor devuelto | 4 | 4 | `[fp, #-8]` |
| enlace de control | 8 | 4 | `[fp, #-12]` |
| enlace de acceso | 12 | 4 | `[fp, #-16]` |
| dirección de retorno | 16 | 4 | `[fp, #-20]` |
| `t1` | 24 | 8 | `[fp, #-32]` |
| `t2` | 32 | 8 | `[fp, #-40]` |

Conviene notar que la casilla del valor devuelto existe en el registro pero el código
generado no la usa. El resultado viaja en `r0`, o en `d0` si es un flotante, por la misma
razón por la que viaja ahí en cualquier otra arquitectura: cuando quien llama lee el
resultado, el registro de activación de la función que lo produjo ya se liberó, así que
leerlo de ahí sería leer memoria que ya no es de nadie.

### 5.2 La parte fija y quién reserva qué

El trabajo de armar un registro de activación se reparte entre los dos lados de la
llamada. Quien llama reserva la **parte fija**, que es lo que va desde la casilla 0 hasta
el final de la dirección de retorno, y escribe ahí lo que solo él sabe: los argumentos y
el enlace de acceso. La función llamada termina de armar el registro, guarda los dos
valores que solo ella conoce, y reserva lo que le falta para sus locales y sus
temporales.

En `factorial` la parte fija llega hasta 20, y se redondea a 24. El redondeo a 8 no es un
detalle. El tamaño del registro completo ya era múltiplo de 8 desde la etapa anterior,
pero eso solo no alcanza: si la parte fija no lo fuera, quien llama dejaría `sp` en un
múltiplo de 4, y el `sp` de la función llamada, que se calcula restando el tamaño del
registro, heredaría ese desfase durante toda su ejecución. El estándar de llamadas de ARM
exige que `sp` esté alineado a 8 en cada llamada, y la biblioteca de C cuenta con ello.

Lo único que cambia el redondeo es cuántos bytes reserva quien llama antes del `bl`; la
función reserva el resto, y la suma es la misma. Ninguna casilla se mueve, porque todas se
cuentan desde `fp`, que sigue siendo el tope del registro.

| Quién | Qué reserva | Qué escribe |
|---|---|---|
| Quien llama | La parte fija, 24 bytes en `factorial` | Los argumentos y el enlace de acceso |
| La función llamada | El resto, 16 bytes más | El enlace de control y la dirección de retorno |

`main` es la excepción, porque lo llama la biblioteca de C, que no sabe nada de esta
convención y no le reservó nada. Por eso su prólogo empieza reservando su propia parte
fija, y su epílogo la libera antes de volver.

### 5.3 El prólogo y el epílogo

| Momento | ARM | Qué hace |
|---|---|---|
| Prólogo | `add ip, sp, #24` | El tope del registro, que la llamada dejó reservado |
| | `str fp, [ip, #-12]` | Enlace de control: el `fp` de quien llama |
| | `str lr, [ip, #-20]` | Dirección de retorno |
| | `mov fp, ip` | Este es el registro actual |
| | `sub sp, fp, #40` | Reserva locales y temporales |
| Epílogo | `ldr lr, [fp, #-20]` | Recupera a dónde volver |
| | `sub sp, fp, #24` | Deja la pila como la dejó quien llama |
| | `ldr fp, [fp, #-12]` | Recupera el registro de quien llama |
| | `bx lr` | Vuelve |

El orden de las cuatro instrucciones del prólogo no es libre. El tope del registro nuevo
se calcula en `ip` y no directamente en `fp`, porque hasta la última instrucción hace
falta el `fp` de quien llama: es justamente lo que hay que guardar en el enlace de
control. En el epílogo pasa lo simétrico: `lr` y `fp` se leen mientras `fp` todavía
apunta a este registro, y por eso se recuperan antes de mover nada.

El epílogo se escribe **una sola vez por función**, detrás de una etiqueta de salida que
lleva el nombre de la función y termina en `_fin`. Cada `return` deja su valor en `r0` y
salta ahí. Una función sin valor de retorno simplemente cae en esa etiqueta cuando se le
acaba el cuerpo.

| Código intermedio | ARM |
|---|---|
| `return 1` | `mov r0, #1` y `b .Lfactorial_fin` |
| `return t1` | `mov r0, r4` y `b .Lfactorial_fin` |
| `end_func factorial` | `.Lfactorial_fin:` y el epílogo |

### 5.4 Los argumentos y el resultado

Los argumentos no viajan en registros, sino en la pila, dentro del registro de activación
que se está armando. La razón es que ese registro **ya tiene una casilla para cada
parámetro**, calculada por la etapa anterior. Pasarlos en `r0` a `r3` obligaría a la
función llamada a copiarlos a esas casillas en su prólogo, y además habría que tratar
aparte el quinto parámetro en adelante, los flotantes, que no caben en un registro
entero, y los booleanos, que miden un byte. Las rutinas del runtime sí reciben sus
argumentos en `r0` a `r3`, porque así lo exige la biblioteca de C.

Como el registro nuevo todavía no tiene `fp`, las casillas se cuentan desde `sp`: el tope
del registro va a quedar en `sp` más la parte fija, de modo que la casilla de
desplazamiento `d` y tamaño `s` cae en `sp + F − d − s`.

| Código intermedio | ARM |
|---|---|
| (primer `param` de la llamada) | `sub sp, sp, #24` |
| `param t2` | `str r5, [sp, #20]`, que es la casilla de `n`: 24 − 0 − 4 |
| `param true` | `strb r2, [sp, #23]`, porque la casilla de un booleano mide un byte |
| `param 1.5` | `vldr d0, [ip]` y `vstr d0, [sp, #16]` |
| `t2 = call factorial, 1` | `bl cps_factorial`, `add sp, sp, #24` y `mov r4, r0` |

Las tres filas del medio están tomadas de tres funciones distintas, y por eso sus
desplazamientos no se parecen: cada uno se cuenta dentro de la parte fija de la función a
la que se está llamando.

La instrucción la decide el tamaño de la casilla y no el tipo que dice el código
intermedio. Escribir cuatro bytes en la casilla de un booleano pisaría la casilla de al
lado, que es la del parámetro anterior.

Hay una instrucción más que no aparece en la tabla y que va justo antes del `bl`: el
vaciado de registros. Una función de Compiscript usa los mismos registros que reparte
`getReg` y no los devuelve intactos, de modo que todo lo que quede en uno se pierde en la
llamada. El generador se asegura de que al llegar al `bl` no quede nada: los valores
vivos ya están en sus casillas. Las rutinas del runtime, en cambio, sí conservan esos
registros, y por eso un `print` no obliga a vaciar nada.

En mi caso, considero que esta es la decisión que más simplifica el back-end a cambio de
un poco de velocidad. La alternativa que manda el estándar es que la función llamada
guarde al entrar los registros que vaya a usar y los restaure al salir, lo que ahorraría
los guardados de quien llama; pero obligaría a saber de antemano cuáles usa, y a tener
dos convenios distintos, uno para las funciones propias y otro para las rutinas del
runtime. Como una llamada ya cierra un bloque básico, y al final de un bloque el
generador vacía de todos modos, la regla «en un `bl` no sobrevive ningún registro» no
cuesta casi nada y se explica en una línea.

### 5.5 El enlace de acceso

Compiscript permite declarar funciones adentro de otras, y una función anidada puede leer
las variables de la que la contiene. Esas variables viven en el registro de activación de
la función exterior, que está en algún lugar de la pila, y el enlace de acceso es la
dirección de ese registro.

Quien llama es el que sabe cuál es, porque es quien está dentro de la función exterior, o
dentro de una que la tiene más arriba. El código intermedio escribe esa distancia como
`^h` en la llamada: cuántos enlaces hay que subir desde el registro actual para encontrar
el registro de la función que contiene a la llamada.

| Código intermedio | ARM |
|---|---|
| `t1 = call externa.hermana, 0, ^0` | `str fp, [sp, #4]`: el registro actual es el que hace falta |
| `t1 = call externa.media, 0, ^1` | `ldr ip, [fp, #-12]` y `str ip, [sp, #4]`: sube un enlace |

Leer una variable de un nivel exterior usa el mismo mecanismo en la dirección contraria.
`base^2` significa subir dos enlaces desde el registro actual y buscar la casilla ahí:
`ldr ip, [fp, #-12]`, `ldr ip, [ip, #-12]` y finalmente `ldr r4, [ip, #-20]`.

### 5.6 factorial(3), paso a paso

El programa calcula `factorial(3)` con tres llamadas anidadas. Si se llama `S` a la
dirección que tenía `sp` cuando la biblioteca de C entró a `main`, la pila queda así, con
las direcciones más altas arriba:

| Registro de activación | Tope, es decir `fp` | Fondo, es decir `sp` | `n` |
|---|---|---|---|
| `main`, 24 bytes | `S` | `S − 24` | — |
| `factorial(3)`, 40 bytes | `S − 24` | `S − 64` | 3 |
| `factorial(2)`, 40 bytes | `S − 64` | `S − 104` | 2 |
| `factorial(1)`, 40 bytes | `S − 104` | `S − 144` | 1 |

La regularidad que se ve en la tabla es la que explica toda la mecánica: **el tope de cada
registro nuevo es exactamente el fondo del registro de quien llama**. Cada llamada empuja
la pila 40 bytes más abajo, repartidos entre los 24 que reserva quien llama y los 16 que
reserva la función; cada retorno los devuelve en el orden inverso.

La llamada de `main` se arma así:

| Código intermedio | ARM |
|---|---|
| `param 5` | `sub sp, sp, #24` y `mov r2, #5` y `str r2, [sp, #20]` |
| `t1 = call factorial, 1` | `bl cps_factorial` y `add sp, sp, #24` y `mov r4, r0` |

Y la llamada recursiva, que es la misma secuencia con una diferencia importante:

| Código intermedio | ARM |
|---|---|
| `param t2` | `sub sp, sp, #24` y `str r5, [sp, #20]` |
| `t2 = call factorial, 1` | `str r4, [fp, #-32]` y `str r5, [fp, #-40]` y `bl cps_factorial` y `add sp, sp, #24` y `mov r4, r0` y `str r4, [fp, #-40]` |

Los dos guardados que aparecen antes del `bl` son el vaciado: `t1`, que guarda el valor
de `n` para multiplicarlo después, y `t2`, estaban solo en registros, y la llamada los
habría perdido. El guardado que aparece después del `bl` es el resultado, que pasa de
`r0` a su casilla porque la llamada cierra el bloque básico y el bloque siguiente va a
buscarlo en memoria.

Cuando `factorial(1)` llega a su caso base, deja `1` en `r0` y salta a `.Lfactorial_fin`.
El epílogo recupera `lr` y `fp` de las casillas de **su** registro, devuelve `sp` a
`S − 128`, que es donde lo había dejado `factorial(2)` después de reservar la parte fija,
y vuelve. `factorial(2)` libera esos 24 bytes con `add sp, sp, #24`, toma el `1` de `r0`,
lo multiplica por su `n` y repite la operación un nivel más arriba. Las tres llamadas
usan el mismo código y las mismas casillas; lo único que las distingue es dónde está
`fp`.

En mi caso, considero que la recursión es la prueba más honesta de que el espejo entre el
registro de activación y la pila está bien hecho. Un programa sin recursión puede
funcionar aunque los desplazamientos estén mal, porque cada función tiene su lugar fijo;
en cambio, `factorial(12)` crea doce registros con la misma forma, uno encima de otro, y
cada retorno tiene que encontrar exactamente el suyo. Si un solo desplazamiento estuviera
corrido, el programa devolvería un número cualquiera.
