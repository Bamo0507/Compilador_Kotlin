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

Resolver `u.id` desde adentro es **subir por la cadena de padres**, que es
literalmente `Scope.lookup` sin modificarlo. Por eso se conservó `Scope` en la
fase 0.

Y el **nivel** de la referencia es *cuántos saltos dio ese lookup*. El número que
el motor necesita lo produce esta fase como subproducto de buscar el nombre.

---

## Ticket 4.1 · El ámbito de consulta

- **Estado**: pendiente
- **Depende de**: 0.6, 3.5

**Archivos:**

- `frontend/semantic/symbols/ScopeKind.kt` (MODIFICA)
- `frontend/semantic/symbols/Symbol.kt` (MODIFICA)
- `app/src/test/.../ScopeTest.kt` (MODIFICA)

```kotlin
enum class ScopeKind { GLOBAL, QUERY }

enum class DeclarationKind { TABLA, COLUMNA, ALIAS }
```

Solo dos clases de ámbito: el global, que contiene las tablas del catálogo, y el
de consulta, uno por cada `SELECT`, anidados según el anidamiento del SQL.

`Symbol` gana lo que hace falta para una columna visible:

```kotlin
class Symbol(
    val name: String,
    val kind: DeclarationKind,
    val declaredAt: LexemeLocation,
    val type: Type,
    val tablaOrigen: String? = null,   // de que tabla real viene
    val index: Int = -1               // posicion en la fila de ESTE nivel
)
```

**Por qué el ámbito global contiene las tablas:** así una tabla no encontrada usa
el mismo mecanismo de error que una columna no encontrada, y el árbol de ámbitos
del IDE muestra la base completa arriba y cada consulta debajo.

**Aceptación:** `Scope` compila sin cambios en su lógica, solo con los valores
nuevos de los dos enums.

---

## Ticket 4.2 · Resolución de tablas y guarda de cordura

- **Estado**: pendiente
- **Depende de**: 2.5, 4.1

**Archivos:**

- `frontend/semantic/NameResolver.kt` (NUEVO)
- `app/src/test/.../ResolucionTablasTest.kt` (NUEVO)

### Qué resuelve

| Caso | Resultado |
|---|---|
| `FROM users` | busca `users` en el catálogo |
| `FROM users u` | además declara el alias `u` en el ámbito de la consulta |
| `FROM (SELECT ...) x` | resuelve la subconsulta primero y declara `x` con las columnas que le devuelve `schemaOf`, del ticket 4.5 |
| `FROM users, users` | error: alias repetido |
| `FROM (SELECT ...)` sin alias | error: la tabla derivada necesita nombre |

### La guarda de cordura estructural

Aquí es donde se compara el header del CSV contra las columnas del JSON, porque
es el primer punto del pipeline que abre el archivo de una tabla concreta. Se lee
**solo la primera línea**.

```
users.csv fue editado fuera del DBMS: el archivo tiene la columna 'email',
que no esta en el esquema
```

Es la decisión 4: el DBMS es el único que escribe, así que esto no debería pasar
nunca. Exists para que cuando pase, el mensaje diga qué pasó en vez de dar un
error confuso tres etapas más adelante.

**Por qué aquí y no en el `CatalogLoader`:** el cargador no abre CSV, por la regla
de esquema ansioso y datos perezosos. Esta fase ya sabe cuáles tablas se van a
tocar, así que revisa solo esas.

**El ticket 4.5 se escribe junto con este**, porque una tabla derivada necesita
saber qué columnas produce su subconsulta, y eso es lo que calcula `schemaOf`.
No es un ciclo: la subconsulta se resuelve **entera** primero, y recién entonces
se le pregunta su esquema. Están en tickets separados porque el esquema de salida
lo consumen también las fases 5, 6 y 8.

**Aceptación:**

- tabla inexistente reporta y sigue, sin cortar el análisis
- alias repetido reporta
- tabla derivada sin alias reporta
- un CSV con una columna de más reporta el mensaje de la guarda
- el análisis de una consulta sobre `users` no abre `posts.csv`

---

## Ticket 4.3 · Resolución de columnas, nivel e índice

- **Estado**: pendiente
- **Depende de**: 4.2

**Archivos:**

- `frontend/semantic/NameResolver.kt` (MODIFICA)
- `frontend/ast/models/Expression.kt` (MODIFICA)
- `app/src/test/.../ResolucionColumnasTest.kt` (NUEVO)

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

El `level` sale de contar cuántos ámbitos subió el `lookup`. No hay que calcularlo
aparte.

**Por qué se guarda el `Symbol` y no solo el tipo:** el ticket 5.1 lee
`referencia.simbolo?.tipo` para tipar, la fase 6 no lo necesita porque le basta el
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

**Aceptación:**

- un `EXISTS` correlacionado deja `nivel = 1` en la referencia externa y
  `nivel = 0` en la interna
- tres niveles de anidamiento dejan `nivel = 2` en la más externa
- en `FROM users u JOIN posts p`, una columna de `p` tiene índice mayor o igual a
  la cantidad de columnas de `users`
- los cuatro casos de error de la tabla tienen test

---

## Ticket 4.4 · Detección de subconsultas correlacionadas

- **Estado**: pendiente
- **Depende de**: 4.3

**Archivos:**

- `frontend/semantic/NameResolver.kt` (MODIFICA)
- `app/src/test/.../CorrelacionTest.kt` (NUEVO)

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

```kotlin
query.correlated = query.referencias().any { it.level > 0 }
```

**Por qué esto es lo más redondo del proyecto:** es un análisis semántico que no
reporta ningún error y que decide una optimización de ejecución. El dato ya está
ahí como subproducto del ticket 4.3, así que la detección no cuesta un recorrido
extra.

Hay un detalle: `nivel > 0` se mide **relativo a la subconsulta**. Una referencia
con `nivel = 2` dentro de una subconsulta de nivel 2 correlaciona con la consulta
más externa, no con su padre inmediato, y las **dos** intermedias tienen que
marcarse como correlacionadas, porque ninguna de las dos se puede cachear.

**Aceptación:**

- `IN` sin referencias externas queda con `correlacionada = false`
- `EXISTS` que usa `u.id` queda con `correlacionada = true`
- en tres niveles, si la más interna referencia a la más externa, las dos
  intermedias quedan marcadas
- el marcado no reporta ningún error

---

## Ticket 4.5 · Esquema de salida de una consulta

- **Estado**: pendiente
- **Depende de**: 4.3

**Archivos:**

- `frontend/semantic/OutputSchema.kt` (NUEVO)
- `frontend/semantic/NameResolver.kt` (MODIFICA)
- `app/src/test/.../EsquemaSalidaTest.kt` (NUEVO)

**Qué es esto, en simple:** qué columnas devuelve un `SELECT`, con qué nombre y de
qué tipo. Suena obvio y no lo es, porque `SELECT *` no lo dice, una expresión sin
`AS` no tiene nombre natural, y hay cuatro lugares distintos que necesitan la
respuesta.

### Quién lo consume

| Quién | Para qué |
|---|---|
| ticket 4.2 | declarar las columnas de una tabla derivada en el ámbito |
| ticket 5.2 | detectar dos columnas de salida con el mismo alias |
| ticket 6.1 | llenar `ResultSet.columnas` |
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

**Aceptación:**

- `SELECT *` sobre un join de 3 y 4 columnas da 7 columnas en el orden del `FROM`
- `SELECT u.*` da solo las de `u`
- `SELECT edad * 2 AS doble` da una columna llamada `doble` de tipo `INT`
- `SELECT edad * 2` da una columna sin nombre
- un alias del `SELECT` usado en `ORDER BY` resuelve; en `WHERE` reporta
- el esquema de una consulta anidada es el que ve el `FROM` que la contiene
