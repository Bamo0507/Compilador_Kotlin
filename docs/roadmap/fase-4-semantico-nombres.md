# Fase 4 · Semántico I, resolución de nombres

**Objetivo:** que cada nombre que aparece en una consulta quede resuelto. Las
tablas contra el catálogo, los alias contra el `FROM`, y cada columna contra su
tabla, dejando anotado **en qué nivel de anidamiento y en qué posición** está.

**Por qué va separado del chequeo de tipos:** son dos preguntas distintas. Esta
fase responde *a qué se refiere este nombre*; la fase 5 responde *tiene sentido lo
que se hace con él*. Separarlas hace que cada una quepa en un archivo legible y
que los tests digan qué se rompió.

**Estimación:** dos sesiones.

---

## El mecanismo central

```sql
SELECT u.name FROM users u
 WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id)
```

```
ambito de la consulta externa    { u -> users }
        ^ padre
scope de la subquery         { p -> posts }
```

Resolver `u.id` desde adentro es **subir por la cadena de padres**, que es lo que
hace `Scope.lookup`. Por eso se conservó `Scope` en la fase 0.

Y el **nivel** de la referencia es *cuántos saltos dio ese lookup*. Lo devuelve
`Scope.resolve`, que busca y cuenta en la misma recursión, así que el número que
el motor necesita sale gratis de buscar el nombre.

---

## Ticket 4.1 · El ámbito de consulta

- **Estado**: completado
- **Depende de**: 0.6, 3.5

**Archivos:**

- `frontend/semantic/symbols/ScopeKind.kt` (MODIFICA)
- `frontend/semantic/symbols/Symbol.kt` (MODIFICA)
- `frontend/semantic/symbols/Scope.kt` (MODIFICA)
- `app/src/test/.../ScopeTest.kt` (NUEVO)

```kotlin
enum class ScopeKind { GLOBAL, QUERY }

enum class DeclarationKind { TABLE, COLUMN, ALIAS }
```

Solo dos clases de ámbito: el global, que contiene las tablas del catálogo, y el
de consulta, uno por cada `SELECT`, anidados según el anidamiento del SQL.

`Symbol` queda con lo que hace falta para una columna visible, y pierde los nueve
campos que eran de Compiscript, entre ellos `offset`, `memberScope` y el conteo de
usos:

```kotlin
data class Symbol(
    val name: String,
    val kind: DeclarationKind,
    val type: Type,
    val location: LexemeLocation,
    val scopeName: String,
    val sourceTable: String? = null,  // de que tabla real viene
    val index: Int = -1               // posicion en la fila de ESTE nivel
)
```

`Scope` sí cambia, y no poco: se le quita toda la herencia de clases
(`superclass`, `attachSuperclass`, `lookupMember`, `enclosingClass`) junto con
`functionDepth` y el contador de offsets, y gana el método que el ticket 4.3
necesita:

```kotlin
fun resolve(name: String): Resolution?

data class Resolution(val symbol: Symbol, val level: Int)
```

**Por qué `resolve` y no contar el nivel afuera:** contarlo desde el llamador
obliga a recorrer la cadena de padres dos veces, una para encontrar y otra para
medir. Devolviéndolo desde la misma recursión el ticket 4.3 queda en una línea.

**Por qué el ámbito global contiene las tablas:** así una tabla no encontrada usa
el mismo mecanismo de error que una columna no encontrada, y el árbol de ámbitos
del IDE muestra la base completa arriba y cada consulta debajo.

**Aceptación:**

- declarar dos veces el mismo nombre en un nivel devuelve el símbolo anterior
- un alias de una subconsulta tapa al de la consulta externa sin borrarlo
- `lookupLocal` no sube por la cadena
- un nombre local resuelve en `level = 0`, uno de la consulta externa en 1, y con
  tres niveles de anidamiento en 2
- un nombre tapado resuelve en `level = 0`, no en el nivel del de afuera

---

## Ticket 4.2 · Resolución de tablas y guarda de cordura

- **Estado**: completado
- **Depende de**: 2.5, 4.1

**Archivos:**

- `frontend/semantic/NameResolver.kt` (NUEVO)
- `frontend/semantic/ColumnLookup.kt` (NUEVO)
- `frontend/semantic/OutputSchema.kt` (NUEVO, es el 4.5)
- `frontend/semantic/symbols/Symbol.kt` (MODIFICA)
- `storage/CsvReader.kt` (MODIFICA)
- `app/src/test/.../TableResolutionTest.kt` (NUEVO)

### Qué resuelve

| Caso | Resultado |
|---|---|
| `FROM users` | busca `users` en el catálogo y la declara con su propio nombre |
| `FROM users u` | declara el alias `u`, y `users` deja de ser visible |
| `FROM (SELECT ...) x` | resuelve la subconsulta primero y declara `x` con las columnas que le devuelve `schemaOf`, del ticket 4.5 |
| `FROM users u JOIN posts u` | error: el nombre se repite |
| `FROM fantasma` | error: la tabla no existe, y el análisis sigue |

**La tabla se busca en el catálogo, no subiendo por los ámbitos.** Un alias de la
consulta de afuera no es una tabla: `FROM u` tiene que fallar aunque afuera exista
`FROM users u`.

### Lo que queda declarado

Un origen del `FROM` deja **un** símbolo, no uno por columna. Guarda la `Table` a
la que apunta, que puede venir del catálogo o ser el esquema de salida de una
subconsulta, y en su `index` el **desplazamiento**: dónde arrancan sus columnas
dentro de la fila concatenada de un `JOIN`.

```
FROM users u JOIN posts p     users tiene 3 columnas, posts 2
   u -> index 0               la fila de trabajo tiene 5
   p -> index 3
```

De ahí sale `Scope.findColumn`, que busca una columna entre los orígenes y sube de
ámbito si no está. Devuelve una **lista** y no un solo resultado: que un nombre sin
calificar aparezca en dos tablas es ambigüedad, y decidir eso es del ticket 4.3, no
de la búsqueda.

**Por qué un símbolo por origen y no uno por columna:** declarar las columnas
planas haría que `name` en dos tablas chocara al declararse, cuando la ambigüedad
es un error del **uso**, no de la declaración. Y `u.*` con dos alias sobre la misma
tabla no tendría cómo distinguirlos.

### La guarda de cordura estructural

Aquí es donde se compara el header del CSV contra las columnas del JSON, porque
es el primer punto del pipeline que abre el archivo de una tabla concreta. Se lee
**solo la primera línea**, con `CsvReader.header`, que se agregó para esto.

```
users.csv fue editado fuera del DBMS: el archivo tiene la columna 'email',
que no esta en el esquema
```

Los otros dos mensajes son `al archivo le falta la columna 'city'` y `las columnas
del archivo estan en otro orden`. Cada tabla se revisa **una vez por corrida**,
aunque el script la use diez veces.

Es la decisión 4: el DBMS es el único que escribe, así que esto no debería pasar
nunca. Existe para que cuando pase, el mensaje diga qué pasó en vez de dar un
error confuso tres etapas más adelante.

**Por qué aquí y no en el `CatalogLoader`:** el cargador no abre CSV, por la regla
de esquema ansioso y datos perezosos. Esta fase ya sabe cuáles tablas se van a
tocar, así que revisa solo esas. Las tablas que toca un `INSERT` o un `DELETE` las
revisa la fase 5, que es donde se resuelven.

### El ámbito de una tabla derivada cuelga del global

Una tabla derivada se ejecuta **una sola vez**. Si pudiera mirar columnas de la
consulta que la contiene, el motor tendría que volver a correrla por cada fila, que
es lo que SQL llama `LATERAL` y este proyecto no implementa. Colgarla del ámbito
global lo impide, y el intento falla con el mismo mensaje de siempre.

**El ticket 4.5 se escribió junto con este**, porque una tabla derivada necesita
saber qué columnas produce su subconsulta.

**Dos cosas que este ticket decía mal:**

- *"tabla derivada sin alias reporta"* no es un caso del resolutor: la gramática ya
  exige el `Identifier` final, así que `DerivedSource.alias` no es nulo. Lo mismo
  vale para la fila correspondiente del ticket 5.4.
- `FROM users, users` tampoco existe: la gramática solo acepta `JOIN ... ON`, no
  comas en el `FROM`. El nombre repetido se prueba con un `JOIN`.

**Aceptación:**

- tabla inexistente reporta y sigue, sin cortar el análisis, y sin correr el
  desplazamiento del `JOIN`
- nombre repetido en el `FROM` reporta
- el segundo origen de un `JOIN` arranca en la cantidad de columnas del primero
- los tres mensajes de la guarda, más el de un CSV ilegible
- una tabla rota usada dos veces reporta una sola vez
- el análisis de una consulta sobre `users` no abre `posts.csv`
- una tabla derivada queda declarada con sus columnas y no ve el `FROM` de afuera

---

## Ticket 4.3 · Resolución de columnas, nivel e índice

- **Estado**: completado
- **Depende de**: 4.2

**Archivos:**

- `frontend/semantic/NameResolver.kt` (MODIFICA)
- `frontend/semantic/ColumnLookup.kt` (MODIFICA)
- `frontend/ast/models/Expression.kt` (MODIFICA)
- `app/src/test/.../ResolverFixture.kt` (NUEVO, la base de prueba de toda la fase)
- `app/src/test/.../ColumnResolutionTest.kt` (NUEVO)

### El resultado que deja pegado

```kotlin
class ColumnReference(
    val qualifier: String?,     // el "u" de u.name, o null si venia sin calificar
    val name: String,
    override val location: LexemeLocation
) : Expression {
    // Los tres los llena ESTA fase.
    var symbol: Symbol? = null   // la columna a la que se resolvio
    var level: Int = -1           // 0 = esta consulta, 1 = la de afuera
    var index: Int = -1          // posicion dentro de la fila de ESE nivel

    override var type: Type = ErrorType   // lo llena la fase 5, desde simbolo
}
```

### Por qué hacen falta dos números

```sql
SELECT u.name FROM users u
 WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id)
                                           ^^^^^  ^^^^
                                           level 0  level 1
```

Al evaluar `p.uid = u.id`, el motor está parado en una fila de `posts`, pero
`u.id` se refiere a la fila de `users` del nivel de arriba. Un índice solo no
distingue las dos.

El `level` y el `index` los devuelve ya calculados `Scope.findColumn`, del ticket
4.2, en cada `ColumnMatch`. Lo que agrega este ticket es decidir qué hacer con la
lista: vacía es un error, una es la buena, dos o más es ambigüedad.

**Por qué se guarda el `Symbol` y no solo el tipo:** el ticket 5.1 lee
`reference.symbol?.type` para tipar, la fase 6 no lo necesita porque le basta el
índice, y la fase 8 lo usa para decir de qué tabla viene cada columna de la
rejilla. Guardar la ficha completa evita copiar tres campos sueltos. Queda en
`null` si el nombre no se resolvió, y de ahí sale el `ErrorType` que corta la
cascada.

### Los casos de error

| Caso | Mensaje |
|---|---|
| `u.email` y `users` no tiene `email` | `'users' no tiene columna 'email'` |
| `x.name` y `x` no es alias de nada | `no hay ninguna tabla llamada 'x'` |
| `name` sin calificar y está en dos tablas | `'name' existe en 'users' y en 'posts'` |
| `name` sin calificar y no está en ninguna | `no existe la columna 'name'` |

### El índice en un `JOIN`

Durante un join, la fila que se evalúa es la **concatenación** de las dos. Si
`users` tiene 3 columnas y `posts` 4, la fila de trabajo tiene 7, y el alias `p`
arranca en el índice 3. El resolutor lleva ese desplazamiento al declarar los
símbolos de cada origen, en el orden en que aparecen en el `FROM`.

### Un calificador que existe en este nivel manda

```sql
SELECT 1 FROM users u WHERE EXISTS (SELECT 1 FROM posts u WHERE u.id = 1)
```

La subconsulta redeclara `u`, así que su `u` es `posts` y `u.id` es un error, no
una referencia correlacionada a `users`. La búsqueda **deja de subir** apenas
encuentra un origen con ese nombre, tenga o no la columna. Sin esa regla, el
resolutor se escaparía al `u` de afuera y el motor leería la fila equivocada.

**Un error que este ticket no listaba:** `SELECT z.*` con una `z` que nadie puso
en el `FROM`. Sin revisarlo, la columna simplemente no salía y nadie decía por qué.

**Aceptación:**

- un `EXISTS` correlacionado deja `level = 1` en la referencia externa y
  `level = 0` en la interna
- tres niveles de anidamiento dejan `level = 2` en la más externa
- en `FROM users u JOIN posts p`, una columna de `p` tiene índice mayor o igual a
  la cantidad de columnas de `users`
- los cuatro casos de error de la tabla tienen test, más el calificador
  redeclarado y el `*` calificado
- un nombre que no se resolvió queda con `symbol` en `null`

---

## Ticket 4.4 · Detección de subconsultas correlacionadas

- **Estado**: completado
- **Depende de**: 4.3

**Archivos:**

- `frontend/semantic/NameResolver.kt` (MODIFICA)
- `app/src/test/.../CorrelationTest.kt` (NUEVO)

**Qué es esto, en simple:** una subconsulta que no mira nada de afuera da siempre
el mismo resultado, así que se puede ejecutar una vez y guardar. Una que sí mira
afuera cambia con cada fila y hay que volver a correrla.

```sql
-- NO correlacionada: se ejecuta UNA vez
WHERE id IN (SELECT uid FROM posts WHERE vistas > 100)

-- correlacionada: se ejecuta por CADA fila
WHERE EXISTS (SELECT 1 FROM posts p WHERE p.uid = u.id)
```

### La regla, en una línea

Una subconsulta es correlacionada **si y solo si** alguna de sus referencias quedó
con `nivel > 0`.

**Por qué esto es lo más redondo del proyecto:** es un análisis semántico que no
reporta ningún error y que decide una optimización de ejecución. El dato ya está
ahí como subproducto del ticket 4.3, así que la detección no cuesta un recorrido
extra.

**No es un pase aparte.** El ticket proponía recorrer después las referencias de
cada consulta, pero eso es un segundo recorrido para leer algo que ya se supo en
el primero. Se marca **en el momento de resolver**: el resolutor lleva la pila de
consultas que contienen a la actual y, al pegar una referencia de nivel `L`,
marca las `L` consultas más internas de esa pila.

```kotlin
stack.takeLast(level).forEach { it.correlated = true }
```

De ahí sale solo el detalle que el ticket señalaba: una referencia que salta dos
niveles marca a la subconsulta **y a la del medio**, porque ninguna de las dos se
puede cachear, mientras que una que salta uno no toca a la de más adentro.

**Aceptación:**

- `IN` sin referencias externas queda con `correlated = false`
- `EXISTS` que usa `u.id` queda con `correlated = true`, y la consulta de más
  afuera nunca
- en tres niveles, si la más interna referencia a la más externa, las dos de
  adentro quedan marcadas; si la del medio es la que mira hacia afuera, la más
  interna no
- una tabla derivada nunca queda correlacionada
- el marcado no reporta ningún error

---

## Ticket 4.5 · Esquema de salida de una consulta

- **Estado**: completado, repartido entre el 4.2 y el 4.3
- **Depende de**: 4.3

**Archivos:**

- `frontend/semantic/OutputSchema.kt` (NUEVO)
- `app/src/test/.../TableResolutionTest.kt`, `ColumnResolutionTest.kt`

**Lo único que queda fuera** es el tipo de una columna **calculada** de una tabla
derivada: hoy sale `ErrorType`, porque tipar expresiones es de la fase 5 y esta
corre antes. El ticket 5.4 vuelve a pedir el `schemaOf` de la subconsulta ya
tipada y refresca la `Table` del origen derivado. Una columna que es una
referencia simple sí sale con su tipo real desde esta fase.

**Qué es esto, en simple:** qué columnas devuelve un `SELECT`, con qué nombre y de
qué tipo. Suena obvio y no lo es, porque `SELECT *` no lo dice, una expresión sin
`AS` no tiene nombre natural, y hay cuatro lugares distintos que necesitan la
respuesta.

### Quién lo consume

| Quién | Para qué |
|---|---|
| ticket 4.2 | declarar las columnas de una tabla derivada en el ámbito |
| ticket 5.2 | detectar dos columnas de salida con el mismo alias |
| ticket 6.1 | llenar `ResultSet.columns` |
| ticket 8.1 | los encabezados de la rejilla |

Sin este ticket, `ResultColumn` aparecería en la fase 6 como si el motor
inventara los nombres, cuando en realidad es información que el semántico ya
calculó.

```kotlin
fun schemaOf(query: Query): List<ResultColumn>
```

### Las tres cosas que resuelve

**1. Expansión de `*`.** La gramática acepta `selectAll` y `itemTableAll`, y
alguien tiene que convertirlos en la lista real de columnas. Es aquí, porque es el
único punto que ya tiene el ámbito del `FROM` resuelto.

```
SELECT *     FROM users u JOIN posts p  ->  las de users, luego las de posts
SELECT u.*   FROM users u JOIN posts p  ->  solo las de users
```

El orden es el del `FROM`, que es el mismo de la fila concatenada del ticket 6.4,
así que los índices cuadran solos.

**2. El nombre de cada columna de salida**, en este orden de preferencia:

| Caso | Nombre |
|---|---|
| lleva `AS total` | `total` |
| es una referencia simple `u.name` | `name` |
| es una expresión sin `AS` | sin nombre |
| es una agregación sin `AS` | sin nombre |

Una columna sin nombre se puede mostrar en la rejilla, con su texto fuente como
encabezado, pero **no** se puede usar desde afuera. Por eso el ticket 5.4 exige
`AS` en las tablas derivadas: `FROM (SELECT edad * 2 FROM users) x` haría que
`x.algo` no tuviera a qué referirse.

**3. El espacio de nombres de los alias del `SELECT`.** Es un ámbito **aparte**
del de las tablas, y es lo que hace cumplir la regla del ticket 5.2:

```sql
SELECT edad * 2 AS doble FROM users ORDER BY doble;   -- valido
SELECT edad * 2 AS doble FROM users WHERE doble > 40; -- ERROR
```

`ORDER BY` se resuelve contra los alias **y** contra las tablas; `WHERE`, `ON`,
`GROUP BY` y `HAVING`, solo contra las tablas. Sale directo del orden de
evaluación: cuando corre el `WHERE`, el `SELECT` todavía no se calculó.

Por eso el `ORDER BY` se resuelve **de último** dentro de una consulta: necesita
el esquema de salida ya calculado, que a su vez necesita el `SELECT` resuelto.

**Cómo queda marcado un alias.** Un alias del `SELECT` no nombra una columna de la
fila que entra sino de la que sale, así que su símbolo queda con
`kind = ALIAS` y su `index` es el lugar en el esquema de salida, no en la fila del
`FROM`. La fase 6 mira el `kind` para saber en cuál de las dos numeraciones
indexar. Solo un nombre **sin calificar** puede ser un alias: `ORDER BY u.city` va
derecho a la tabla.

**Aceptación:**

- `SELECT *` sobre un join da todas las columnas en el orden del `FROM`
- `SELECT u.*` da solo las de `u`, y un calificador que nadie declaró reporta
- `SELECT u.id AS clave, u.name` da `clave` y `name`, con sus tipos reales
- `SELECT edad * 2` da una columna sin nombre. El tipo de una calculada lo pone la
  fase 5, no esta
- un alias del `SELECT` usado en `ORDER BY` resuelve; en `WHERE` reporta
- el esquema de una consulta anidada es el que ve el `FROM` que la contiene
