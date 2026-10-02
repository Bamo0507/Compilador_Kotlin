# Roadmap: Generación de código intermedio para Compiscript

Plan de desarrollo de la etapa de generación de código intermedio: traducir el AST ya
validado de Compiscript a código de tres direcciones (TAC), con manejo de temporales,
tabla de símbolos extendida y registros de activación.

El roadmap de la etapa anterior, el analizador semántico, está archivado en
[`semantico/`](./semantico/README.md). Sus decisiones 1 a 16 siguen vigentes.

---

## Qué se entrega

El enunciado pide el TAC generado, el reporte de errores, y el estado de la tabla de
símbolos con la información para la generación de código (direcciones,
desplazamientos, registros de activación). Además: la batería de pruebas, la
documentación de la arquitectura y de cómo ejecutar, la documentación del lenguaje
intermedio con ejemplos y supuestos, y el IDE funcional.

| Componente | Puntos | Qué es realmente |
|---|---|---|
| Diseño de CI | 25 | `Quadruple`, su sintaxis y `docs/lenguaje-intermedio.md` |
| Generación de TAC | 65 | El generador, el manejo de temporales y la batería de pruebas |
| Tabla de Símbolos | 10 | Zonas, tamaños, desplazamientos y registros de activación |

El assembler de la etapa siguiente es ARM de 32 bits (Raspberry Pi). Lo único de esta
etapa que depende de eso son los tamaños de la decisión 42.

---

## Cómo se trabaja

Cada punto de teoría se estudia primero, hasta entenderlo. Después se estructura su
fase con sus tickets, y al cerrarla se pasa al siguiente punto. La fuente de verdad son
las presentaciones del curso (06 Generación de código intermedio, 07 Entornos en tiempo
de ejecución, 08 Introducción a la recolección de basura), y el Dragon Book donde las
presentaciones no profundizan.

Antes de empezar un ticket se da una sinopsis corta de lo que aborda, y se toca código
solo con el visto bueno. Un ticket a la vez.

---

## Mapa de fases

| Fase | Qué se logra | Rúbrica |
|---|---|---|
| [0, Preparación](./fase-0-preparacion.md) | Roadmap archivado, semántico corregido, repo limpio | prerrequisito |
| [1, Diseño del lenguaje intermedio](./fase-1-diseno-del-lenguaje-intermedio.md) | `Quadruple`, su sintaxis y su documento | Diseño de CI, 25 |
| [2, Expresiones y temporales](./fase-2-expresiones-y-temporales.md) | El generador, el GDA y el pool de temporales | Generación de TAC, 65 |
| [3, Control de flujo](./fase-3-control-de-flujo.md) | Condiciones con caída, sentencias, `switch` y `try/catch` | Generación de TAC, 65 |
| [4, Tabla de símbolos y funciones](./fase-4-tabla-de-simbolos-y-funciones.md) | Zonas, tamaños, desplazamientos, registros de activación y funciones | Tabla de Símbolos, 10 |
| [5, Objetos y listas](./fase-5-objetos-y-listas.md) | Objetos, métodos con despacho, listas y chequeos | Generación de TAC, 65 |
| [6, El IDE](./fase-6-ide.md) | La pantalla de código intermedio | entregable |
| [7, Batería y documentación](./fase-7-bateria-y-documentacion.md) | Los `.tac` dorados y los documentos finales | Generación de TAC, 65, y Diseño de CI, 25 |

## Mapa de los puntos de teoría

| # | Punto de teoría | Fuente | Fase |
|---|---|---|---|
| 1 | Por qué una representación intermedia | 06 | Fase 0 |
| 2 | GDA y número de valor | 06 | Fase 2 |
| 3 | Código de tres direcciones: direcciones e instrucciones | 06 | Fase 1 |
| 4 | Cuádruplos y tripletas | 06 | Fase 1 |
| 5 | Temporales: asignación y reciclaje | Dragon Book 6 | Fase 2 |
| 6 | Traducción de control de flujo | Dragon Book 6.6 a 6.8 | Fase 3 |
| 7 | Subdivisión de la memoria en ejecución | 07 | Fase 4 |
| 8 | Árboles de activación y la pila durante las llamadas | 07 | Fase 4 |
| 9 | Registros de activación, secuencia de llamadas y enlaces de acceso | 07, Dragon Book 7.2 y 7.3 | Fase 4 |
| 10 | Objetos, métodos y listas en memoria | Dragon Book 6.4 | Fase 5 |
| 11 | Montículo, administrador de memoria y recolección de basura | 07, 08 | Fase 5 |

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

Estos aplican a cada ticket sin repetirlos:

1. **Simple antes que ingenioso.** Si una solución necesita un comentario para
   entenderse a nivel de mecánica, probablemente hay una más simple. Los
   comentarios son para el *por qué*, no para el *qué*.
2. **Nombres completos.** `currentScope`, no `cs`. `declaredType`, no `dt`.
3. **`sealed interface` / `sealed class` para jerarquías cerradas.** Da `when`
   exhaustivo: si agregas un caso y olvidas manejarlo, Kotlin no compila.
4. **`data object` para constantes únicas**, `data class` para lo que lleva datos.
5. **`enum` en vez de `String`** para conjuntos cerrados (operadores, categorías).
   Misma razón que el punto 3.
6. **Nada de `object` con estado mutable.** Una instancia por compilación. Fue un
   problema real en el proyecto anterior: obligaba a acordarse de limpiar el
   estado global antes de cada corrida.
7. **Los modelos son datos; las reglas son funciones aparte.** `Type.kt` no sabe
   qué se puede sumar con qué; eso vive en `TypeRules.kt`.
8. **Una función por construcción del lenguaje.** Es la forma que impone el
   patrón visitor y es lo que pide el catedrático: al ámbito en el que estoy le
   corresponde una función que procesa toda su información.

---

## Decisiones de esta etapa

Se numeran desde la 17, a continuación de las 16 de la etapa anterior. Las que se tomen
al estudiar o ejecutar un ticket se agregan aquí en el momento.

| # | Decisión | Elegida | Por qué |
|---|---|---|---|
| 17 | Dónde vive el generador | `frontend/intermediate/` | En la teoría, generar código intermedio es el último paso del front-end (Dragon Book 1.2). `backend/` queda para la fase de assembler. |
| 18 | Cuándo corre | Etapa G, solo si no hubo errores | Paso 4 del enunciado: *"Si no existen errores, el generador recorre el árbol"*. Es la misma regla que ya sigue el intérprete. |
| 19 | El intérprete | Se queda y convive | No estorba, la GUI ya muestra su salida, y la decisión de validar el TAC ejecutándolo se toma en el punto 3. |
| 20 | Miembros de clase sin `this.` | No son visibles por nombre suelto | Es la regla de TypeScript, del que Compiscript es subconjunto. Hoy el verificador los aceptaba y el intérprete fallaba. |
| 21 | Rango de `integer` | 32 bits, con recorte | El TAC va a declarar que un `integer` mide 4 bytes; el plegado y el intérprete calculaban en 64. |
| 22 | Cuerpos del nivel superior | Se revisan al final de la Pasada 2 | Así una función puede usar una global declarada más abajo. Limitación: llamarla antes de la declaración da error en ejecución, igual que el `ReferenceError` de TypeScript. |
| 23 | Qué cuenta como uso en la vivacidad | Solo las lecturas | Para un recolector de basura importa la última lectura. Una variable que solo se escribe nunca necesitó su memoria. |
| 24 | Destino de los saltos | Etiquetas simbólicas, sin backpatching | El backpatching existe para traducir en una sola pasada dentro de un parser ascendente, donde la condición se genera antes de conocer su destino. Aquí el generador recorre el AST completo y pasa las etiquetas a los hijos como atributo heredado. Además, un número de instrucción del TAC no sobrevive al assembler: cada instrucción se vuelve varias, y el ensamblador trabaja con etiquetas de forma nativa. Se documenta en `docs/lenguaje-intermedio.md`. |
| 25 | Representación de las instrucciones | Cuádruplos, como `sealed interface Quadruple` con una `data class` por familia | El enunciado pide temporales y su reciclaje, y las tripletas no tienen temporales. La ventaja de las tripletas indirectas, reordenar barato, no se usa porque no se mueven instrucciones después de generarlas. Una clase por familia, y no un registro genérico `(op, arg1, arg2, result)`, hace que cada instrucción traiga exactamente sus campos y que el `when` sea exhaustivo. La tabla de cuatro columnas de la teoría se obtiene con `toRow()`. |
| 26 | `print` | Instrucción propia, `print_<tipo> x` | Se lee directo en el TAC. El sufijo es obligatorio porque imprimir un entero y un string son llamadas al sistema distintas en assembler. |
| 27 | Tipos en las operaciones | Sufijo de tipo en el operador (`+`, `+f`, `<s`), `concat` para strings y conversión explícita `inttofloat` | En assembler, sumar enteros y flotantes son instrucciones distintas. La conversión explícita deja visible el ensanchamiento implícito del lenguaje (Dragon Book 6.5.2). |
| 28 | `try/catch` | Instrucciones `try L`, `endtry` y `throw x`, con semántica de manejador | Se pidió en la etapa anterior. Es la idea de `setjmp`/`longjmp`: el `throw` encuentra el manejador aunque esté varias llamadas abajo, y descarta los registros de activación de por medio. Cómo se descartan lo implementa la fase de assembler. |
| 29 | Errores en ejecución | Chequeos emitidos en el TAC, en línea, que disparan `throw` con la línea del fuente | Sin chequeos, la máquina lee memoria basura en silencio, y el `catch` nunca tendría nada que atrapar. División entre cero en la Fase 2; índice y `null` con los objetos y las listas; la recursión demasiado profunda queda fuera de alcance, documentada. |
| 30 | GDA | Uno por expresión, construido desde el AST, sin compartir en expresiones con llamadas o asignaciones anidadas | Es lo que muestran las diapositivas 12 y 13. Una llamada puede cambiar una global, y una asignación anidada cambia una variable: en esos casos dos subexpresiones iguales no son el mismo valor. |
| 31 | Reciclaje de temporales | Pool con conteo de usos, que entrega el libre de índice más bajo | Con un GDA un temporal tiene varios lectores y deja de morir en orden de pila, y el contador clásico generaría código que pisa valores vivos. En un árbol el pool entrega los mismos nombres que el contador. |
| 32 | Plegado de constantes en el TAC | Se usa: una expresión con `constantValue` se emite como constante | El `TypeChecker` ya calculó el valor y garantiza que es correcto; recalcularlo en ejecución sería trabajo repetido. |
| 33 | La copia al destino | Se conserva: `t1 = a + b` y después `x = t1` | Es la forma de la diapositiva 24. Escribir directo en `x` es una optimización que la teoría no muestra. |
| 34 | Condiciones con caída | Sí: la condición solo salta hacia el lado que no sigue, con la relación invertida | Es la técnica del Dragon Book 6.6.5. Ahorra un `goto` por condición, que sin ella aparecería en cada `if` y cada bucle. |
| 35 | Traducción del `switch` | Cadena de comparaciones | La tabla de saltos (Dragon Book 6.8) exige `case` enteros constantes y cercanos, y en Compiscript un `case` puede ser un string o una variable. |
| 36 | Subexpresiones con saltos adentro (ternario, `&&` y `\|\|` como valor) | Entran al GDA como nodo `Opaque`, y la expresión que las contiene no comparte nodos | Extiende la decisión 30: entre dos ramas solo se ejecuta una, y un nodo compartido entre ellas no estaría calculado en la otra. Las llamadas y los accesos a campos y elementos entran por el mismo nodo. |
| 37 | Variable del `catch` | `try L, e`: la instrucción nombra la variable que recibe el mensaje | Hace explícito en el TAC el flujo del mensaje del `throw`, en vez de dejarlo en la semántica de la instrucción. |
| 38 | Funciones como valores | Prohibidas: una función solo se puede llamar. `let g = f;` es error semántico | Una función anidada guardada y llamada después de que retorna la que la contiene seguiría su enlace de acceso hasta un registro de activación ya liberado. Con la regla, una pila y los enlaces de acceso alcanzan siempre, y el TAC solo necesita llamadas indirectas para los métodos (decisión 43), que no capturan variables de ninguna hoja. Llamar en cualquier posición de valor (`r = sumar(2, 3)`) sigue permitido. Ningún programa de la batería lo usaba. |
| 39 | El código del nivel superior | Se envuelve en un `main` implícito, con su propio registro de activación | Así todo temporal vive en un registro de activación, y la fase de assembler maneja los temporales de una sola forma. Las globales siguen en datos estáticos. |
| 40 | Variables de otro registro de activación | La dirección lleva los saltos de enlace de acceso: `cuenta^1` | El TAC queda legible y el número de saltos visible. Expandirlo a instrucciones que siguen el enlace es trabajo mecánico de la fase de assembler, igual que cualquier acceso a una local. El documento muestra la expansión como ejemplo. |
| 41 | Variables con el mismo nombre en el TAC | Sufijo con la línea de la declaración, solo cuando hay ambigüedad: `x` y `x@3`; la columna se agrega si dos chocan en la misma línea | El sufijo dice dónde está declarada sin abrir la tabla de símbolos. El nombre del ámbito no sirve porque no es único, y un contador no informa nada. Una variable sin otra del mismo nombre que la pueda confundir, como el parámetro `n` de dos funciones distintas, no lleva sufijo. |
| 42 | Tamaños y alineación | `integer` 4, `float` 8, `boolean` 1, referencias 4, cada temporal 8; alineación natural y registro redondeado a 8 | El objetivo de assembler es ARM de 32 bits (Raspberry Pi): las direcciones miden 4 bytes y la pila se alinea a 8. El `float` en doble precisión coincide con el intérprete. Un temporal mide 8 porque el pool recicla nombres sin mirar el tipo. La alineación natural (Dragon Book 6.3.4) es una sola función y deja visibles los tamaños que pide el enunciado. |
| 43 | Despacho de métodos sobrescritos | Tabla de métodos (vtable) por clase en datos estáticos; la casilla 0 de cada objeto apunta a la de su clase, y la llamada es indirecta | Con `let a: Animal = new Perro()`, el compilador no sabe la clase real, y llamar a `Animal.hablar` imprimiría lo incorrecto. La tabla cuesta tres instrucciones por llamada sin importar cuántas subclases haya, y es la técnica estándar. Los métodos heredados ocupan en la tabla la misma posición que en la del padre, igual que los campos en el objeto. |
| 44 | Inicialización de campos | Una rutina `$init` por clase, separada del constructor, que primero llama a la de la superclase | Si una subclase hereda el constructor del padre, ese constructor no conoce los campos de la subclase. Con `$init` aparte, todos los campos se inicializan siempre. Es la traducción de los inicializadores que pueden usar `this`. |
| 45 | Chequeo de `null` | Antes de cada acceso a un campo, a un elemento y de cada llamada a método, salvo sobre `this` | Completa la decisión 29: sin el chequeo, la máquina lee la dirección 0 en vez de disparar un error que el `catch` pueda atrapar. `this` nunca es `null`. |
| 46 | Ubicación de las tablas de métodos | Al inicio del TAC, antes de `$main` | Son datos estáticos, no código. La fase de assembler las traduce a su sección de datos. |
| 47 | Strings | Referencias de 4 bytes; los literales viven en datos estáticos y un `concat` pide su bloque en el montículo | El texto de un literal se conoce al compilar; el de una concatenación solo al ejecutar. |
| 48 | Liberación del montículo | No se libera: `alloc` es la única operación, y se documenta como limitación | El enunciado de esta etapa pide el TAC, no un recolector. Los programas de la batería son pequeños y terminan pronto. El diseño deja posible un recolector por rastreo más adelante: las referencias apuntan al inicio del objeto y la casilla 0 identifica su clase, las dos suposiciones de la presentación 08. |
| 49 | Cómo verifica la batería el TAC | Archivos dorados: cada `.cps` válido lleva su `.tac` esperado; los inválidos no deben producir TAC | Agregar un caso sigue siendo agregar archivos, y los `.tac` sirven de ejemplos para el documento y para quien califica. Un intérprete de TAC verificaría además que el código calcula bien, pero es una pieza grande que el enunciado no pide. Los `.tac` se regeneran con `./gradlew test -DupdateGolden=true` y el cambio se revisa en el `git diff`. |

## Decisiones pendientes

Ninguna por ahora. La de las funciones que escapan de la función que las contiene se
cerró con la decisión 38, al estudiar los enlaces de acceso.
