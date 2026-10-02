# El lenguaje intermedio de Compiscript

Este documento describe el código de tres direcciones (TAC) que genera el compilador de
Compiscript: qué es, cómo se escribe cada instrucción, cómo se traduce cada construcción
del lenguaje y qué supuestos se tomaron en el camino.

## 1. Qué es el código intermedio y dónde encaja

### 1.1 Qué es

El código intermedio es una representación del programa que no depende ni del lenguaje
fuente ni de la máquina donde se va a ejecutar. Es decir, es una forma más del mismo
programa, que ya no tiene la estructura de Compiscript, como las clases, el `foreach` o
el `switch`, pero que tampoco tiene todavía los registros ni las instrucciones propias
de un procesador. Su propósito es servir de punto medio entre las dos cosas, para poder
traducir de un lenguaje de alto nivel a uno de máquina sin tener que hacerlo de un solo
salto.

Cabe mencionar que "representación" se usa aquí en el sentido de la forma que tiene el
programa en un momento dado de la compilación. El programa es siempre el mismo, lo que
cambia es cómo está escrito: primero como texto, luego como árbol sintáctico, después
como código de tres direcciones y, en la etapa siguiente, como assembler. A las formas
que quedan en medio, que no son ni el texto original ni el código final, se les llama
representaciones intermedias.

### 1.2 Dónde está dentro del compilador

Un compilador se divide en dos mitades. Por un lado está el front-end, que agrupa todo
lo que depende del lenguaje fuente: el análisis léxico, el sintáctico, el semántico y la
generación de código intermedio. Por otro lado está el back-end, que agrupa todo lo que
depende de la máquina destino, como la optimización propia del procesador y la
generación del código objeto. El código intermedio es justamente la frontera entre las
dos, ya que el front-end lo produce sin saber para qué máquina es, y el back-end lo
consume sin saber de qué lenguaje vino.

En el caso de este proyecto, todo lo que se construyó en la etapa anterior, es decir, el
analizador léxico y sintáctico generado con ANTLR, el árbol sintáctico propio, las dos
pasadas del análisis semántico y la tabla de símbolos, pertenece al front-end. La
generación del código de tres direcciones es el último paso de esa misma mitad, y la
traducción a assembler para ARM, que corresponde a la etapa siguiente, es el inicio del
back-end. Es por esto que el generador se ubicó junto al resto de las fases del
front-end, y no en un componente aparte.

### 1.3 Por qué existe si no es indispensable

Generar código intermedio no es indispensable. De hecho, el propio proyecto lo
demuestra, ya que el intérprete de la etapa anterior ejecuta los programas recorriendo
directamente el árbol sintáctico, sin ninguna representación intermedia de por medio.
No obstante, se usa por tres razones.

La primera es que divide el problema en dos. Si un compilador soporta varios lenguajes
y varias máquinas, sin una representación intermedia necesitaría un traductor por cada
combinación, mientras que con ella basta con un front-end por lenguaje y un back-end por
máquina. Por ejemplo, con tres lenguajes y cuatro máquinas serían doce traductores
contra siete piezas, y lo que más importa es el costo de agregar algo nuevo, ya que un
quinto procesador exigiría tres traductores nuevos sin representación intermedia, y uno
solo con ella.

La segunda es que permite traducir de a poco. Entre un lenguaje con clases, closures y
listas, y un assembler con registros y saltos, hay demasiada distancia para cruzarla de
una vez, de tal forma que el código de tres direcciones funciona como un escalón
intermedio: ya no tiene ciclos ni objetos, pero todavía no tiene registros.

La tercera es que permite optimizar una sola vez. Las optimizaciones que no dependen de
la máquina se hacen sobre la representación intermedia y les sirven a todas las
máquinas. Por ejemplo, darse cuenta de que una misma subexpresión se calcula dos veces
dentro de una expresión, para poder calcularla una sola vez.

En mi caso, considero que la razón que más pesa para este proyecto es la segunda, ya
que la siguiente etapa traduce a ARM, y gracias al código intermedio el back-end ya no
va a tener que entender construcciones como el `foreach`, el `this` o el `switch`,
porque esta etapa las habrá desarmado en copias y saltos.

### 1.4 Alto nivel y bajo nivel

Un compilador puede pasar por varias representaciones intermedias, cada una un poco más
cerca de la máquina que la anterior. Bajo esta idea, se suele distinguir entre dos
niveles. Una representación de alto nivel se parece al lenguaje fuente y conserva su
estructura, como un árbol, y sirve para tareas como la verificación de tipos. Una
representación de bajo nivel se parece a la máquina y ya no conserva estructura, sino
solo instrucciones simples y saltos, y sirve para tareas que dependen de la máquina,
como elegir instrucciones o asignar registros. En este proyecto, el árbol sintáctico
propio es la representación de alto nivel y el código de tres direcciones es la de bajo
nivel.

Para poder ver la diferencia, se puede tomar una asignación como `lista[i] = x + 1`. En
el árbol sintáctico se ve como una asignación cuyo destino es un acceso a una lista y
cuyo valor es una suma, y cada nodo sabe su tipo y la línea del código fuente donde
aparece. En el código de tres direcciones, en cambio, la misma asignación se convierte
en una secuencia de instrucciones simples: primero se calcula la suma en un valor
temporal, luego se calcula la posición del elemento dentro de la lista, y finalmente se
guarda el resultado en esa posición. Lo que más llama la atención es ese cálculo de la
posición, que no aparece en ninguna parte del árbol, ya que en el código de tres
direcciones el índice de una lista deja de ser el número del elemento y pasa a ser su
distancia en bytes desde el inicio, y para calcularla hay que saber cuánto mide cada
elemento.

De este ejemplo se desprende algo importante, y es que cada trabajo se hace en la
representación que tiene la información para hacerlo. Verificar que el índice sea un
entero se hace sobre el árbol, porque ahí cada nodo conoce su tipo y su ubicación en el
código fuente, de tal forma que el error se puede reportar con claridad. En cambio,
calcular en qué byte está el elemento se hace al generar el código intermedio, porque ahí
ya importan los tamaños. Es por esto que el generador no trabaja solo con el árbol, sino
que consulta la tabla de símbolos para conocer los tipos, tamaños y desplazamientos de
cada variable, que es lo que el enunciado describe cuando pide que la tabla de símbolos
interactúe con cada fase de la compilación.
