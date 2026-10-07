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

## 8. Temporales

Cada operación necesita un lugar donde dejar su resultado, y ese lugar es un temporal.
La forma más simple de asignarlos es inventar uno nuevo por operación, pero eso
desperdicia nombres: en una expresión larga, la mayoría de los temporales se lee una
sola vez y después ya no sirve. Es por esto que el enunciado pide un algoritmo que los
recicle.

El algoritmo clásico, que se vio en clase, es un contador. Cada vez que se necesita un
temporal se entrega `t` con el valor del contador y se incrementa, y cada vez que una
instrucción lee un temporal se decrementa. Esto funciona gracias a un invariante: en un
árbol, cada temporal tiene exactamente un lector, que es su padre, y los temporales
mueren en orden de pila, de tal forma que el último en crearse es el primero en
leerse. Por ejemplo, `r = a + b * c - d` necesita un solo temporal:

```
t1 = b * c
t1 = a + t1
t1 = t1 - d
r = t1
```

No obstante, este invariante deja de cumplirse con un GDA (sección 9), porque ahí un
nodo compartido tiene varios lectores. Si se aplica el contador al ejemplo de la
diapositiva 19, `r = a + a * (b - c) + (b - c) * d`, el resultado es incorrecto:

```
t1 = b - c
t1 = a * t1
t1 = a + t1
t1 = t1 * d
t0 = t1 + t1
r = t0
```

En la segunda instrucción, el contador ve que `t1` se leyó y lo da por libre, así que
el resultado de `a * t1` se escribe encima de `b - c`. Sin embargo, `b - c` todavía
tiene un lector pendiente, que es `(b - c) * d`, y cuando llega su turno lee un valor
que ya no es el suyo. Incluso aparece un `t0`, porque el contador bajó una vez de más.

La solución que usamos es un pool con conteo de usos, que generaliza al contador. Cada
temporal se pide indicando cuántas veces se va a leer, que en el GDA es la cantidad de
aristas que llegan al nodo, y cada lectura le resta uno. Recién cuando llega a cero, el
temporal vuelve al pool, y el pool entrega siempre el libre de índice más bajo, lo cual
hace que el resultado sea determinista. Con el mismo ejemplo:

```
t1 = b - c
t2 = a * t1
t2 = a + t2
t1 = t1 * d
t1 = t2 + t1
r = t1
```

Aquí `b - c` se pidió con dos usos, así que después de la primera lectura sigue vivo y
la multiplicación tiene que usar `t2`. Cabe mencionar que en un árbol, donde todo
temporal tiene un solo uso, el pool entrega exactamente los mismos nombres que el
contador, como se ve en el primer ejemplo. Es por esto que considero que el contador
es el caso particular del pool, y no un algoritmo distinto.

Adicional, importa el orden en que se libera y se pide. Una instrucción primero libera
sus operandos y después pide el temporal de su resultado, y eso es lo que permite
escribir `t1 = t1 * d`, reutilizando el nombre que acaba de liberarse. Si se pide antes
de liberar, el código sigue siendo correcto, pero gasta un temporal más:

```
t1 = b - c
t2 = a * t1
t3 = a + t2
t2 = t1 * d
t1 = t3 + t2
r = t1
```

## 9. GDA

Un grafo dirigido acíclico, o GDA, es un árbol sintáctico en el que una subexpresión
que aparece varias veces se representa con un solo nodo. En `a + a * (b - c) + (b - c) * d`,
la resta `b - c` está escrita dos veces, pero calcula el mismo valor, así que se
calcula una sola vez y su resultado se reutiliza.

Para construirlo usamos el método del número de valor de la diapositiva 14. Los nodos
se guardan en una lista, y el número de valor de cada nodo es su posición en ella. Antes
de crear una operación, se busca en una tabla hash la llave formada por el operador y
los números de sus hijos; si ya existe, se devuelve ese número en lugar de crear un
nodo nuevo. Para el ejemplo anterior, el GDA queda así:

| Número | Nodo | Padres |
|---|---|---|
| 0 | `a` | 2 |
| 1 | `b` | 1 |
| 2 | `c` | 1 |
| 3 | `-` sobre 1 y 2 | 2 |
| 4 | `*` sobre 0 y 3 | 1 |
| 5 | `+` sobre 0 y 4 | 1 |
| 6 | `d` | 1 |
| 7 | `*` sobre 3 y 6 | 1 |
| 8 | `+` sobre 5 y 7 | 0 (raíz) |

Son nueve nodos, como en la diapositiva 13. La columna de padres es justamente la
cantidad de usos con la que el generador pide el temporal de cada nodo (sección 8).

Hay tres detalles en la construcción. El primero es que las conversiones implícitas
también son nodos, de tal forma que si `x + 2.5` aparece dos veces, `x` se convierte a
flotante una sola vez. El segundo es que si el analizador semántico ya calculó el valor
de una subexpresión, como en `3 + 5`, esa subexpresión es directamente una hoja con la
constante `8`. El tercero es que las variables y las constantes son hojas que no
generan instrucción, así que siempre se comparten.

No obstante, hay casos en los que dos subexpresiones iguales no calculan el mismo
valor. En `a * b + g() + a * b`, la función `g` podría modificar `a` o `b`, y en
`(x = 5) + x`, la `x` de la derecha ya no vale lo mismo que antes de la asignación. Es
por esto que, si una expresión contiene una llamada o una asignación anidada, se
construye sin la tabla hash, y cada operación es un nodo nuevo:

| Número | Nodo | Padres |
|---|---|---|
| 0 | `a` | 2 |
| 1 | `b` | 2 |
| 2 | `*` sobre 0 y 1 | 1 |
| 3 | llamada a `g` | 1 |
| 4 | `+` sobre 2 y 3 | 1 |
| 5 | `*` sobre 0 y 1 | 1 |
| 6 | `+` sobre 4 y 5 | 0 (raíz) |

Las dos multiplicaciones son los nodos 2 y 5. Saber con exactitud qué nodos dependen de
lo que una llamada pudo cambiar requeriría un análisis de efectos, y considero que no
vale la pena, ya que una expresión con llamadas rara vez repite subexpresiones.

Cabe mencionar que este GDA se construye por expresión y desde el árbol sintáctico, tal
como en las diapositivas 12 y 13. El Dragon Book (sección 8.5) presenta además un GDA
por bloque básico, construido sobre el código intermedio, que puede compartir valores
entre sentencias distintas, pero que tiene que invalidar nodos cada vez que una
variable se reasigna. Esa es una optimización aparte, y encaja mejor en la etapa de
assembler.

## 10. Expresiones y sentencias simples

El generador recorre el árbol sintáctico con una función por construcción. Las
sentencias no devuelven nada, y las expresiones devuelven la dirección donde quedó su
valor. Toda expresión pasa por su GDA, que se emite en postorden desde la raíz: cada
operación se emite una sola vez, y la segunda vez que un padre la necesita, recibe el
mismo temporal. La siguiente tabla resume cómo se traduce cada sentencia.

| Sentencia | Traducción |
|---|---|
| `let x: integer = e;` y `const` | El código de `e`, y después `x = <dirección de e>` |
| `let x: integer;` | `x = 0`, el valor inicial de su tipo |
| `x = e;` | El código de `e`, y después `x = <dirección de e>` |
| `print(e);` | El código de `e`, y después `print_<tipo> <dirección de e>` |
| `e;` | El código de `e`, y su valor se descarta |
| `{ ... }` | Las sentencias de adentro, en orden |

Un bloque no genera nada propio, porque cada variable ya es una entrada de la tabla de
símbolos, de tal forma que dos variables con el mismo nombre en bloques distintos ya son
direcciones distintas. Del mismo modo, una variable sin inicializar arranca en el valor
inicial de su tipo, que es `0`, `0.0`, `""` o `false`, el mismo que usa el intérprete.

La copia final se conserva aunque cueste una instrucción: `t1 = a + b` seguido de
`x = t1`, que es la forma de la diapositiva 24. Escribir directo en `x` sería una
optimización que la teoría no muestra.

Para el ejemplo de la diapositiva 19, la diapositiva usa cinco temporales, uno por
operación, y nuestro generador usa dos (sección 8). La diferencia viene de las dos
ideas juntas: el GDA evita calcular `b - c` dos veces, y el pool recicla los nombres en
cuanto dejan de tener lectores.

En cuanto a los tipos, una suma de un entero con un flotante convierte primero el
entero, y una suma de textos es una concatenación. Si el analizador semántico ya plegó
una constante, se usa directamente, así que `a + 2 * 3` multiplica en tiempo de
compilación y `"Hola " + "mundo"` no genera ninguna operación:

```
t1 = inttofloat x
t1 = t1 +f 2.5
f = t1

t1 = a + 6
r = t1

s = "Hola mundo"
```

Al dividir entre una variable, se emite el chequeo de división entre cero de la sección
6, justo antes de la división y con la línea del código fuente en el mensaje. Si el
divisor es una constante, el chequeo no se emite, porque el analizador semántico ya
rechazó la división entre la constante cero:

```
    if d != 0 goto L1
    throw "División entre cero (línea 2)"
L1:
    t1 = a / d
    q = t1
```

Por último, una asignación puede aparecer dentro de una expresión, y su valor es lo
que quedó en la variable. Aquí hay un caso que no es obvio: en `x + (x = 5)`, la `x` de
la izquierda vale lo que valía antes de la asignación, porque se evalúa primero. Sin
embargo, una variable no genera instrucción, y se lee recién cuando se emite la suma,
cuando la asignación ya ocurrió. Es por esto que, si la parte derecha reasigna una
variable de la izquierda, esa variable se copia antes a un temporal:

```
t1 = x
x = 5
t1 = t1 + x
r = t1
```

## 11. Condiciones

En una sentencia condicional no interesa guardar si una comparación es verdadera, sino
ir a un lado o al otro del código. Es por esto que las condiciones no se traducen como
un valor, sino como código de saltos: a cada condición se le pasan dos etiquetas, una
para cuando es verdadera y otra para cuando es falsa, y su código salta a la que
corresponda. Las etiquetas las crea quien contiene a la condición y se las entrega a
ella, de tal forma que funcionan como un atributo heredado, que es justamente lo que
permite usar etiquetas simbólicas sin necesidad de backpatching.

El cortocircuito de la conjunción y la disyunción sale de cómo se reparten esas
etiquetas, sin que el operador genere ninguna instrucción propia. En una conjunción, si
la primera condición es falsa se salta directo a la etiqueta falsa, y la segunda ni
siquiera se evalúa; en una disyunción, si la primera es verdadera se salta directo a la
verdadera. La negación tampoco genera nada, ya que es la misma condición con las dos
etiquetas intercambiadas. Por ejemplo, una condición que pide que a sea menor que b y que
c sea mayor que d se traduce como dos saltos condicionales hacia el final del bloque,
uno por cada comparación, y si el primero se toma, la segunda comparación nunca ocurre.

Adicional, se aplica una técnica que el Dragon Book llama caída. Cuando la instrucción
siguiente es justamente a donde tendría que saltar la condición, ese salto es inútil, ya
que de todas formas la ejecución continúa ahí. En esos casos la condición solo salta
hacia el otro lado, invirtiendo la relación si hace falta: en lugar de saltar al cuerpo
cuando a es menor que b y después saltar al final en el caso contrario, se salta al final
directamente cuando a es mayor o igual que b. Esto ahorra una instrucción por cada
condición, y considero que es importante porque, sin ella, cada sentencia condicional y
cada ciclo tendría un salto que no hace nada. Cabe mencionar que invertir una relación
supone que no hay valores NaN, ya que con ellos negar una comparación no es lo mismo que
invertirla.

Por último, si el analizador semántico ya calculó el valor de una condición, como en un
ciclo cuya condición es la constante verdadera, no se compara nada: la condición
simplemente cae al cuerpo.

## 12. Sentencias de control

Todas las sentencias de control siguen la misma idea: se crean las etiquetas que hacen
falta, se genera la condición con las etiquetas que le corresponden y se coloca cada
etiqueta en su lugar. En el if, la condición cae al cuerpo y solo salta cuando es falsa;
si hay else, el cuerpo del if termina con un salto al final para no ejecutar también el
else.

En el while, la condición se evalúa al inicio y el cuerpo termina regresando a ella. En
el do-while, en cambio, el cuerpo va primero y es la condición verdadera la que regresa
al inicio, así que no hace falta un salto hacia atrás propio. El for ejecuta primero su
inicialización, luego la condición y el cuerpo, y al final la actualización antes de
regresar a la condición; si no tiene condición, no se compara nada y el ciclo solo
termina con un break.

Para poder traducir break y continue, el generador lleva una pila con las etiquetas del
ciclo más cercano: al entrar a un ciclo apila su etiqueta de salida y su etiqueta de
continuación, y al salir las desapila. Un break salta a la salida y un continue a la
continuación, que depende del ciclo: en el while es el inicio, en el do-while es la
condición, y en el for es la actualización. Esto último es importante, porque si un
continue dentro de un for saltara directo a la condición se saltaría la actualización, y
en el caso típico, donde la actualización incrementa un contador, el ciclo nunca
terminaría.

Cabe mencionar que las etiquetas de cada ciclo se emiten aunque ningún break o continue
las use, ya que quitarlas requeriría otra pasada sobre el código y una etiqueta sin uso
no cambia lo que el programa hace.

## 13. Switch, ternario y booleanos como valor

El switch se traduce como una cadena de comparaciones: el sujeto se compara con el valor
de cada caso, y si no coincide se salta a la comparación del caso siguiente. El Dragon
Book presenta también una traducción con una tabla de saltos indexada por el valor de
cada caso, pero esa técnica exige que los casos sean enteros constantes y cercanos entre
sí, y en Compiscript un caso puede ser un texto o una variable. Como Compiscript no tiene
fall-through, cada caso termina saltando al final del switch, y el caso por defecto va al
final de la cadena.

Si el sujeto es una expresión, se calcula una sola vez y su temporal se mantiene vivo
hasta la última comparación, aunque los cuerpos de los casos usen sus propios
temporales. Del mismo modo, como el switch no es un ciclo, no agrega nada a la pila de
break y continue, de tal forma que un break dentro de un switch sale del ciclo que lo
contiene.

El operador ternario es un if-else que deja un valor. El temporal del resultado se pide
antes de generar los saltos, y cada rama copia su valor en él antes de saltar al final.
Por otro lado, la conjunción y la disyunción solo existen como saltos, así que cuando se
usan para producir un valor, por ejemplo al guardarlas en una variable booleana, se
generan los saltos y después se escribe verdadero o falso según el lado al que se llegó.

Estas construcciones, junto con las llamadas, la creación de objetos y listas, y el
acceso a campos y elementos, no son una operación aritmética simple, sino que necesitan
su propia traducción. Dentro del GDA entran como una subexpresión que nunca se comparte, y
la operación de arriba solo recibe el temporal donde quedó su resultado. Adicional, si
una expresión contiene alguna de ellas, el GDA no reutiliza ningún cálculo en esa
expresión, porque una subexpresión dentro de una rama de un ternario puede no
ejecutarse. Por el mismo motivo, una variable que aparece a la izquierda de una de ellas
se copia antes a un temporal, ya que podría ser modificada por lo que se ejecuta a su
derecha.

## 14. Try/catch

El try/catch se traduce con las instrucciones de manejador de la sección 6. La
instrucción que abre el bloque protegido registra la etiqueta del catch y la variable
que recibe el mensaje del error, y el bloque termina con la instrucción que quita el
manejador, seguida de un salto que se brinca el catch.

La parte que no es obvia aparece cuando un break o un continue abandonan un bloque
protegido. Si solo se tradujeran como un salto, el manejador quedaría registrado, y un
error posterior, ya fuera del ciclo, saltaría a un catch que no le corresponde. Es por
esto que el generador lleva la cuenta de cuántos bloques protegidos hay abiertos, y un
break o continue quita un manejador por cada bloque que abandona antes de saltar. En
cambio, un break dentro del catch no quita ninguno, porque el error que llevó hasta ahí
ya quitó el manejador.

## 15. Memoria en ejecución

Hasta este punto, las direcciones del código intermedio son nombres, pero para poder
llegar a assembler hace falta saber dónde vive cada uno en memoria. Siguiendo al Dragon
Book, la memoria de un programa en ejecución se divide en cuatro zonas. La primera es el
código, donde están las instrucciones de cada función; la segunda son los datos
estáticos, cuyo tamaño se conoce al compilar y que existen durante toda la ejecución; la
tercera es la pila, donde cada llamada a una función reserva su propio espacio y lo
libera al retornar; y la cuarta es el montículo, donde se crean los valores cuyo tamaño o
tiempo de vida no se conoce al compilar.

La diferencia importante es entre lo estático y lo dinámico. Lo estático se decide una
sola vez, al compilar, de tal forma que una variable global siempre está en el mismo
lugar. Lo dinámico, en cambio, depende de la ejecución: una función recursiva como el
factorial puede tener varias llamadas vivas a la vez, y cada una necesita su propia copia
de sus parámetros, por lo que no es posible darles una dirección fija.

En Compiscript, cada cosa queda en una zona de forma bastante directa. Las funciones y
las clases son código, no datos, así que no ocupan lugar en las otras zonas. Las
variables declaradas en el nivel superior viven en los datos estáticos. Los parámetros,
las locales y los temporales de una función viven en la pila, dentro del espacio de la
llamada que los creó. Por último, los objetos, las listas y los textos viven en el
montículo, y una variable de esos tipos solo guarda una referencia hacia ellos. Cabe
mencionar que una variable declarada dentro de un bloque del nivel superior no es global,
ya que deja de existir al cerrar el bloque, así que vive en la pila, en el espacio del
programa principal.

## 16. Tamaños y alineación

Para poder calcular dónde empieza cada variable, primero hay que saber cuánto mide. Un
entero ocupa 4 bytes, acorde a los 32 bits que se establecieron en los supuestos; un
flotante ocupa 8, porque es de doble precisión, igual que en el intérprete; y un
booleano ocupa 1. Las referencias a objetos, listas y textos ocupan 4 bytes, que es lo
que mide una dirección en ARM de 32 bits, la arquitectura de la fase de assembler.

Los temporales son un caso aparte, ya que cada uno ocupa 8 bytes sin importar el tipo de
lo que guarde. Esto se debe a que el pool de temporales reutiliza los nombres sin mirar
el tipo, de tal forma que un mismo temporal puede guardar un entero y, más adelante, un
flotante, por lo que su espacio tiene que alcanzar para el más grande.

Adicional, se aplica la alineación natural del Dragon Book: cada valor empieza en un
desplazamiento que es múltiplo de su tamaño, porque así el procesador puede leerlo con
una sola instrucción. Cuando el siguiente desplazamiento libre no cumple esto, se deja un
hueco de relleno. Por ejemplo, en una función que recibe primero un booleano y después un
flotante, el booleano ocupa el byte 0, pero el flotante no puede empezar en el 1, sino
que empieza en el 8, y los siete bytes de en medio quedan como relleno. Por otro lado, el
espacio de cada llamada se redondea al final a un múltiplo de 8, que es la alineación que
pide la pila en ARM de 32 bits. Considero que el costo de estos huecos es pequeño
comparado con la ventaja de que cada acceso sea directo.

Los datos estáticos siguen la misma regla. Por ejemplo, un programa con una global
entera y después una global flotante pone la entera en el desplazamiento 0 y la flotante
en el 8, de tal forma que la zona estática mide 16 bytes.

## 17. Registro de activación

El espacio que cada llamada reserva en la pila se conoce como registro de activación, y
guarda todo lo que esa llamada necesita para poder ejecutarse y regresar. Siguiendo el
orden de la teoría, cada registro tiene primero los parámetros, luego el valor devuelto,
si la función devuelve algo, y después tres campos de control: el enlace de control, que
apunta al registro de quien hizo la llamada; el enlace de acceso, que apunta al registro
de la función que contiene a esta en el código fuente; y la dirección de retorno, que
indica a qué instrucción regresar. A continuación van las variables locales, y por último
los temporales.

Los desplazamientos se cuentan desde el inicio del registro y siempre son positivos. Por
ejemplo, en una función que recibe un booleano y un flotante, declara una local entera y
devuelve un flotante, el booleano queda en el byte 0, el flotante en el 8, el valor
devuelto en el 16, los enlaces de control y de acceso en el 24 y el 28, la dirección de
retorno en el 32 y la local en el 36. Hasta ahí el registro mide 40 bytes, y como esa
función usa un temporal, el registro final mide 48. En el caso del factorial, el
parámetro entero queda en el byte 0, el valor devuelto en el 4, los tres campos de
control del 8 al 16, y con sus dos temporales el registro mide 40 bytes.

Los temporales se agregan al final porque su cantidad solo se conoce después de generar
el código de la función. Es por esto que el tamaño de cada registro se calcula en dos
partes: primero se asigna el espacio de los parámetros y las locales, y después el
generador suma un espacio de 8 bytes por cada temporal que usó la función.

Cabe mencionar dos supuestos de esta forma de registro. El primero es que el enlace de
acceso está en todos los registros, aunque solo lo usen las funciones anidadas, ya que
considero que una sola forma de registro es más simple de explicar y de traducir, a
cambio de 4 bytes. El segundo es que, del estado de la máquina que se guarda al llamar,
solo se reserva la dirección de retorno, porque qué registros del procesador hay que
salvar depende de cómo los use la fase de assembler, así que esa parte le corresponde a
ella. Adicional, el código del nivel superior se trata como una función principal
implícita, con su propio registro, que tiene los tres campos de control y las variables
de los bloques del nivel superior.

## 18. Secuencia de llamadas

La secuencia de llamadas es el trabajo que hay que hacer para pasar el control de una
función a otra y regresar, y se reparte entre quien llama y la función llamada. En el
código intermedio cada parte de ese trabajo está representada por una instrucción, y la
fase de assembler es la que la convierte en los pasos concretos.

Primero, quien llama calcula todos los argumentos y después los entrega uno por uno con
una instrucción de parámetro, que representa escribirlos en el espacio de parámetros del
registro nuevo. Es importante que todos los argumentos se calculen antes de entregar el
primero, porque si un argumento es a su vez una llamada, sus parámetros no deben
mezclarse con los de la llamada de afuera. Luego, la instrucción de llamada indica la
función y cuántos argumentos recibe, y representa guardar el enlace de control, el
enlace de acceso y la dirección de retorno, y saltar al inicio de la función.

Después, la instrucción que abre la función lleva el tamaño de su registro, y representa
reservar ese espacio en la pila. Al final, la instrucción de retorno representa dejar el
valor en el campo del valor devuelto, liberar el registro y regresar a la dirección
guardada, de tal forma que quien llamó recibe el resultado en un temporal. Por último, la
instrucción que cierra la función cubre el caso de una función sin valor que llega al
final de su cuerpo sin retornar, y en ese caso se comporta como un retorno sin valor.

Por ejemplo, al imprimir el factorial de 5, el programa principal entrega el 5 como
parámetro, llama al factorial con un argumento y guarda el resultado en un temporal. A su
vez, el factorial, antes de llamarse a sí mismo, copia su parámetro a un temporal, ya
que está a la izquierda de una llamada que podría modificarlo, y después calcula el
argumento restándole uno. Cabe mencionar que cada función tiene sus propios temporales,
de tal forma que el primer temporal del factorial y el del programa principal no chocan,
porque viven en registros distintos.

## 19. Funciones anidadas

Una función declarada dentro de otra puede leer las variables de la que la contiene,
pero esas variables no están en su registro, sino en el de la función de afuera. Para
poder encontrarlas se usa el enlace de acceso, que apunta al registro de la función que
la contiene en el código fuente, a diferencia del enlace de control, que apunta a quien
hizo la llamada. Los dos no siempre coinciden: en una función recursiva anidada, quien
llama es la misma función, pero quien la contiene sigue siendo la de afuera.

En el código intermedio, una variable que vive en otro registro lleva el número de
saltos que hay que dar por la cadena de enlaces de acceso para llegar a él, y una
variable propia no lleva ninguno. Por ejemplo, si la función a declara una variable
cuenta, y dentro de ella está la función b, y dentro de b la función c, cuando c lee
cuenta lo hace con dos saltos, ya que cuenta está dos niveles arriba. Bajo esta idea, la
fase de assembler expande esa lectura en instrucciones explícitas: primero se lee el
enlace de acceso del registro de c, que está en el desplazamiento 8 y apunta al registro
de b; luego se lee el enlace de acceso del registro de b, también en el desplazamiento 8,
que apunta al registro de a; y finalmente se lee cuenta en el desplazamiento 16 del
registro de a.

La llamada también tiene que saber qué enlace de acceso darle a la función nueva, y para
eso lleva su propio número de saltos. Si una función llama a otra declarada directamente
dentro de ella, el enlace es su propio registro, así que la llamada lleva cero saltos;
si llama a una hermana, ambas están contenidas en la misma función, así que hay que subir
uno; y una función anidada que se llama a sí misma también sube uno, para pasar el mismo
enlace que recibió. Las funciones del nivel superior no usan el enlace, por lo que sus
llamadas no llevan saltos. Por último, el nombre de una función anidada en el código
intermedio es el camino de las funciones que la contienen, de tal forma que la función c
del ejemplo se llama a.b.c, y dos funciones anidadas con el mismo nombre dentro de
funciones distintas no se confunden.

## 20. Nombres en el código intermedio

Como el código intermedio se imprime como texto, dos variables distintas con el mismo
nombre podrían verse iguales. Internamente esto no es un problema, porque cada dirección
guarda el símbolo y no solo su nombre, pero al leer el código sí lo es. Es por esto que,
cuando dos variables chocan, la primera en declararse conserva su nombre y las demás
llevan como sufijo una arroba y la línea de su declaración, más la columna si comparten
línea.

Dos variables chocan solo si se llaman igual y además viven en el mismo registro de
activación, o si una es global y la otra local. Por ejemplo, en un programa con una
global x en la línea 1 y una x local en un bloque de la línea 2, la global se imprime
como x y la local como x@2. En cambio, el parámetro n del factorial y el de una función
de Fibonacci no chocan, porque viven en registros distintos y nunca se confunden.
Tampoco choca una local de una función anidada con la de la función que la contiene,
porque los saltos del enlace de acceso ya las distinguen.

## Supuestos

- Una instrucción tiene como máximo un operador del lado derecho.
- Un entero ocupa 32 bits: si una operación se pasa del máximo, el resultado da la
  vuelta, igual que en Java.
- Solo se pueden imprimir valores de tipo simple, es decir, enteros, flotantes, textos y
  booleanos; un objeto o una lista se muestran imprimiendo sus campos o recorriéndola.
- Los errores en ejecución se detectan con chequeos explícitos en el código intermedio,
  y llevan la línea del código fuente donde ocurrieron.
- Una expresión con llamadas o asignaciones anidadas no comparte subexpresiones.
- Una variable sin inicializar arranca en el valor inicial de su tipo: `0`, `0.0`, `""`
  o `false`, y `null` para las clases y las listas.
- El chequeo de división entre cero solo se emite para enteros: en flotantes, dividir
  entre cero da infinito, igual que en el intérprete.
- Si la parte derecha de una operación reasigna una variable de la izquierda, la
  variable se copia antes a un temporal, para respetar la evaluación de izquierda a
  derecha.
- Invertir una relación supone que no hay valores NaN.
- Las etiquetas de los ciclos se emiten aunque ningún salto las use.
- Una función solo se llama; no se guarda en una variable ni se pasa como argumento.
- Las locales de bloques hermanos no comparten espacio en el registro, aunque nunca
  vivan a la vez.
- Los desplazamientos son positivos desde el inicio del registro, y la fase de
  assembler puede reubicarlos según cómo organice la pila.
