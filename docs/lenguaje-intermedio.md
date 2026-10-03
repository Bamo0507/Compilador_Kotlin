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

## 2. Direcciones

El código de tres direcciones recibe su nombre de la forma de sus instrucciones: cada
una tiene como máximo un operador del lado derecho, y por lo tanto como máximo tres
direcciones, dos operandos y un resultado. En este contexto, una dirección no significa
todavía una posición de memoria, sino cualquier cosa que puede ocupar el lugar de un
operando o de un resultado. En nuestro lenguaje intermedio existen tres clases de
dirección.

La primera es el nombre de una variable del programa fuente. Cabe mencionar que
internamente una variable no se identifica por su nombre escrito, sino por su entrada
en la tabla de símbolos, de tal forma que dos variables que se llaman igual en ámbitos
distintos son direcciones distintas. Esto también es lo que va a permitir, más
adelante, sustituir cada variable por su ubicación real en memoria. La segunda clase es
la constante, que es un valor literal del programa, como un número, un texto, un
booleano o el valor nulo. La tercera es el temporal, un nombre que inventa el
compilador para guardar un resultado intermedio, y que se escribe como `t1`, `t2` y así
sucesivamente.

Por ejemplo, la asignación `x = a + 5` usa las tres clases: `a` y `x` son nombres, `5`
es una constante y `t1` es el temporal donde queda la suma antes de copiarse a `x`.

```
t1 = a + 5
x = t1
```

Adicional a las direcciones, existen las etiquetas, que marcan una posición dentro del
código para que un salto pueda llegar a ella, y se escriben como `L1`, `L2`, etc. Las
etiquetas no son direcciones, ya que nunca se leen ni se escriben como un valor: solo
sirven como destino de un salto. Del mismo modo, cada función tiene una etiqueta propia
con su nombre, que es a donde se llega cuando se la llama.

## 3. Instrucciones

El conjunto de instrucciones parte de las operaciones comunes del código de tres
direcciones que se vieron en clase, es decir, la asignación, la copia, los saltos
condicionales e incondicionales, las llamadas y la copia indexada, y se complementa con
las que Compiscript necesita para imprimir y para manejar errores en ejecución. La
siguiente tabla las resume, con su forma y un ejemplo de cada una.

| Instrucción | Forma | Ejemplo | Qué hace |
|---|---|---|---|
| Asignación binaria | `x = y op z` | `t1 = a + b` | Aplica un operador aritmético a dos operandos |
| Comparación como valor | `x = y relop z` | `t1 = a < b` | Guarda en `x` el resultado verdadero o falso de una comparación |
| Concatenación | `x = y concat z` | `t1 = "Hola " concat nombre` | Une dos textos en uno nuevo |
| Asignación unaria | `x = op y` | `t1 = - c` | Aplica un operador de un solo operando: negativo, negación lógica o conversión |
| Copia | `x = y` | `x = t1` | Copia un valor de una dirección a otra |
| Etiqueta | `L:` | `L1:` | Marca una posición del código |
| Salto incondicional | `goto L` | `goto L1` | Continúa la ejecución en la etiqueta |
| Salto condicional | `if x goto L` / `ifFalse x goto L` | `ifFalse t1 goto L1` | Salta si el valor es verdadero, o si es falso |
| Salto con comparación | `if x relop y goto L` | `if i < v goto L1` | Compara y salta en una sola instrucción |
| Parámetro | `param x` | `param t1` | Prepara un argumento para la siguiente llamada |
| Llamada | `call f, n` / `x = call f, n` | `t2 = call factorial, 1` | Llama a una función con sus `n` argumentos y, si devuelve algo, lo guarda |
| Retorno | `return` / `return x` | `return t1` | Termina la función, devolviendo un valor si tiene |
| Lectura indexada | `x = y[i]` | `t2 = lista[t1]` | Lee el valor que está a `i` bytes del inicio de `y` |
| Escritura indexada | `x[i] = y` | `lista[t1] = 5` | Escribe un valor a `i` bytes del inicio de `x` |
| Impresión | `print_<tipo> x` | `print_i t1` | Imprime un valor de tipo simple |
| Bloque protegido | `try L, e` / `endtry` | `try L1, e` | Abre y cierra la zona donde un error salta al manejador |
| Error | `throw x` | `throw "División entre cero (línea 7)"` | Dispara un error en ejecución con su mensaje |

Hay dos detalles de esta tabla que no son obvios. El primero es que en la copia indexada
el índice es un desplazamiento en bytes y no el número del elemento, de tal forma que
para leer el tercer elemento de una lista de enteros no se usa un `2`, sino la distancia
en bytes hasta él. El segundo es que la llamada indica cuántos parámetros consume,
porque cuando una llamada aparece como argumento de otra, como en `f(g(x))`, los
parámetros de las dos se van preparando intercalados, y sin ese número la llamada no
sabría cuáles le pertenecen.

## 4. Tipos en las operaciones

En el lenguaje fuente, el mismo símbolo `+` sirve para sumar enteros, para sumar
flotantes y para unir textos. Sin embargo, en la máquina esas son operaciones
completamente distintas: sumar enteros y sumar flotantes se hace con instrucciones
diferentes y en registros diferentes, y unir textos ni siquiera es una instrucción,
sino una rutina que reserva memoria para el texto nuevo. Es por esto que el código
intermedio no puede dejar el tipo implícito, y cada operación indica sobre qué tipo de
valores trabaja.

La forma en que se resolvió es con un sufijo en el operador. Las operaciones sobre
enteros, booleanos y referencias van sin sufijo, de tal forma que el caso más común se
lee igual que en la teoría; las operaciones sobre flotantes llevan una `f`, y las
comparaciones entre textos llevan una `s`. La unión de textos tiene su propia
instrucción, `concat`, y la impresión lleva el tipo en su nombre (`print_i`, `print_f`,
`print_s` y `print_b`), porque imprimir un entero y un texto son llamadas distintas al
sistema.

Adicional, Compiscript permite combinar un entero con un flotante, como en `x + 2.5`
cuando `x` es entero. En el lenguaje fuente esa conversión es implícita, pero en el
código intermedio se vuelve explícita con la instrucción `inttofloat`, que convierte el
entero antes de operar:

```
t1 = inttofloat x
t1 = t1 +f 2.5
```

Del mismo modo, una unión y una comparación de textos se escriben así:

```
t1 = "Hola " concat nombre
t2 = nombre <s "m"
```

Considero que dejar la conversión visible es importante, ya que es justamente el tipo
de detalle que el lenguaje fuente esconde y que el código intermedio tiene que hacer
explícito para que la siguiente etapa no tenga que deducirlo.

## 5. Etiquetas y saltos

Un salto necesita indicar a qué instrucción va. Hay dos formas de hacerlo: con el
número de la instrucción de destino, o con una etiqueta simbólica que se coloca donde
corresponde. Elegimos las etiquetas, y la razón tiene que ver con los saltos hacia
adelante.

Cuando el generador produce el código, lo escribe instrucción por instrucción, de arriba
hacia abajo. En un salto hacia atrás, como el regreso al inicio de un ciclo, la
instrucción de destino ya fue escrita, así que su posición se conoce. Por ejemplo, en el
ciclo `do i = i + 1; while (a[i] < v);` el último salto regresa a una etiqueta que ya
existe:

```
L1:
    t1 = i + 1
    i = t1
    t2 = i * 4
    t3 = a[t2]
    if t3 < v goto L1
```

No obstante, en un `if` el salto tiene que brincarse un cuerpo que todavía no se ha
generado, y en ese momento no se sabe en qué posición va a terminar. La técnica clásica
para resolverlo con números es el backpatching, que deja el salto incompleto y lo
rellena después. Sin embargo, el backpatching existe para traducir en una sola pasada
mientras el analizador sintáctico reconoce la entrada, y en nuestro caso el generador
recorre el árbol sintáctico ya completo, de tal forma que puede inventar una etiqueta
antes de generar el cuerpo, usarla en el salto y colocarla al llegar a su posición.
Adicional, los números de instrucción del código intermedio no sobreviven a la etapa
siguiente, porque cada instrucción se convierte en varias instrucciones de assembler, y
el ensamblador ya trabaja con etiquetas de forma nativa. Es por esto que considero que
el backpatching sería trabajo extra para producir números que de todas formas se
descartarían.

## 6. Errores en ejecución

Hay errores que no se pueden detectar al compilar, porque dependen de los valores que
aparezcan al ejecutar, como dividir entre una variable que vale cero. Para estos casos,
el código intermedio incluye un chequeo justo antes de la operación, y si el chequeo
falla, dispara el error con la instrucción `throw`, que lleva el mensaje y la línea del
código fuente.

A su vez, Compiscript permite atrapar esos errores con `try` y `catch`. Para poder
representarlo, se tomó la idea de un manejador: la instrucción `try` registra a qué
etiqueta hay que saltar si algo falla y en qué variable se deja el mensaje, y la
instrucción `endtry` quita ese manejador cuando el bloque protegido termina sin errores.
Por ejemplo, un bloque que divide entre una variable y, si falla, imprime el mensaje
del error, se traduce así:

```
    try L1, e
    if d != 0 goto L3
    throw "División entre cero (línea 2)"
L3:
    t1 = a / d
    q = t1
    endtry
    goto L2
L1:
    print_s e
L2:
```

Cabe mencionar que el `throw` no necesita estar escrito dentro del bloque protegido
para que el manejador lo atrape. Si el error ocurre dentro de una función que se llamó
desde el bloque, el `throw` encuentra igualmente el manejador más reciente, y se
descartan los registros de activación de las funciones que quedaron de por medio. El
código intermedio declara esa intención, y la forma concreta de descartar esos
registros le corresponde a la etapa de assembler.

## 7. Representación interna

Dentro del compilador, las instrucciones no se guardan como texto sino como
estructuras de datos, y la teoría presenta dos formas de hacerlo. En los cuádruplos,
cada instrucción guarda su operador, sus dos argumentos y su resultado, y el resultado
tiene un nombre propio. En las tripletas, el resultado no tiene nombre, y quien lo
necesita se refiere a la posición de la instrucción que lo produjo, lo cual es más
compacto pero tiene un costo: si una optimización reordena las instrucciones, las
referencias por posición quedan apuntando a otra cosa.

Elegimos los cuádruplos, principalmente porque el enunciado pide un algoritmo de
asignación y reciclaje de temporales, y en las tripletas los temporales ni siquiera
existen. Además, el costo de los cuádruplos, que es el espacio extra, no es relevante
para este proyecto, y la ventaja de las tripletas indirectas, que es poder reordenar
sin romper referencias, no se aprovecharía porque las instrucciones no se mueven
después de generarse.

Adicional, en lugar de un registro genérico de cuatro campos para todas las
instrucciones, cada tipo de instrucción tiene su propia estructura con exactamente los
campos que necesita, de tal forma que no se puede construir, por ejemplo, un salto con
un operador aritmético. Cada una sigue teniendo como máximo cuatro campos, así que
conceptualmente sigue siendo un cuádruplo, y el IDE puede mostrar cualquier código
generado en la tabla de cuatro columnas de la teoría. Por ejemplo, para
`a = b * - c + b * - c`:

```
t1 = - c
t2 = b * t1
t3 = - c
t4 = b * t3
t5 = t2 + t4
a = t5
```

| Operador | Argumento 1 | Argumento 2 | Resultado |
|---|---|---|---|
| minus | c | | t1 |
| * | b | t1 | t2 |
| minus | c | | t3 |
| * | b | t3 | t4 |
| + | t2 | t4 | t5 |
| = | t5 | | a |

En la tabla, el negativo se escribe `minus` para poder distinguirlo de la resta, ya que
en la columna de operador los dos se verían iguales. En las instrucciones que escriben
algo, la columna de resultado es lo que se escribe, y en los saltos es la etiqueta de
destino.

## Supuestos

- Una instrucción tiene como máximo un operador del lado derecho.
- Un entero ocupa 32 bits: si una operación se pasa del máximo, el resultado da la
  vuelta, igual que en Java.
- Solo se pueden imprimir valores de tipo simple, es decir, enteros, flotantes, textos y
  booleanos; un objeto o una lista se muestran imprimiendo sus campos o recorriéndola.
- Los errores en ejecución se detectan con chequeos explícitos en el código intermedio,
  y llevan la línea del código fuente donde ocurrieron.
