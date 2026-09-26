# Fase 6 · Motor I, consultas

**Objetivo:** que un `SELECT` del alcance devuelva el `ResultSet` correcto.

**La regla de esta fase:** el motor **no vuelve a validar nada**. Los tipos ya los
verificó la fase 5 y los nombres la fase 4. Si el motor tiene que preguntar algo
que ya se sabía, es que una fase anterior no dejó su trabajo hecho.

**Estimación:** tres sesiones.

---

## Ticket 6.0 · `Session`, el estado de una corrida

- **Estado**: pendiente
- **Depende de**: 2.5, 2.6, 5.5

**Archivos:**

- `engine/Session.kt` (NUEVO)
- `app/src/test/.../SessionTest.kt` (NUEVO)

**Qué es esto, en simple:** el objeto que guarda todo lo que pasa durante una
corrida y que no ha llegado al disco. Sin él, cada pieza del motor tendría su
propia copia de los datos y no se verían entre sí.

### El problema que resuelve

Tres piezas tocan el mismo estado:

- `scan`, del ticket 6.3, cachea las tablas que ya leyó
- el `Writer`, del ticket 7.2, modifica filas en memoria
- `flush`, del ticket 7.4, necesita saber qué cambió para escribir solo eso

**Si el caché de lectura y el almacén de escritura no son el mismo objeto, esto no
funciona:**

```sql
INSERT INTO users VALUES (1, 'Ana');
SELECT * FROM users;              -- no veria a Ana
```

El `INSERT` escribiría en un lado y el `SELECT` leería del otro. Es el caso que
hay que probar explícitamente, porque es fácil que cada mitad funcione sola.

### Diseño

```kotlin
// Una instancia por corrida. Vive lo que dura un script y se descarta.
class Session(
    private val directory: DataDirectory,
    initialCatalog: Catalog,
    private val diagnostics: Diagnostics
) {
    // El catalogo MUTA con CREATE, ALTER y DROP. El inicial se guarda
    // para que flush sepa que esquemas cambiaron.
    private val originalCatalog = initialCatalog
    var catalog = initialCatalog
        private set

    // Las tablas cargadas. Es a la vez el cache de lectura Y el almacen
    // de escritura: leer y escribir van al mismo mapa, a proposito.
    private val rows = mutableMapOf<String, MutableList<Row>>()
    private val modified = mutableSetOf<String>()

    // Lee del disco la primera vez; despues devuelve lo que ya esta,
    // incluidos los cambios de este mismo script.
    fun rowsOf(table: String): MutableList<Row>

    fun markModified(table: String)
    fun applyCatalog(updated: Catalog)     // usa las extensiones del 2.6

    // Lo que consume flush, en el ticket 7.4.
    fun changes(): Changes
}
```

### El bucle que recorre el script

Vive aquí y no en el pipeline, porque necesita la `Session`:

```kotlin
fun run(script: Script): ExecutionResult {
    val results = mutableListOf<ResultSet>()

    for (statement in script.statements) {
        when (statement) {
            is Query -> results += Engine(this).execute(statement)
            else -> Writer(this).execute(statement)   // fase 7
        }
        // La decision 5: al primer error se corta, y como nada toco el
        // disco, el directorio queda intacto.
        if (diagnostics.hasErrors) break
    }
    return ExecutionResult(results, this)
}
```

En esta fase la rama del `Writer` queda sin implementar y el `when` solo atiende
`Query`. El ticket 7.2 la completa.

### Decisión · una `Session` por corrida, no un singleton

Es el principio 7 del README. Un `object` con el estado de la base adentro haría
que dos corridas seguidas compartieran filas, que dos tests se pisaran, y que
hubiera que acordarse de limpiarlo entre una y otra. Fue un problema real en el
proyecto anterior.

**Aceptación:**

- `INSERT` seguido de `SELECT` sobre la misma tabla **ve la fila nueva**
- la misma tabla leída dos veces abre el archivo una sola vez
- `rowsOf` sobre una tabla que no se tocó no la marca como modificada
- dos `Session` sobre el mismo directorio no comparten nada
- al terminar, `cambios()` lista exactamente las tablas tocadas

---

## Ticket 6.1 · `ResultSet`, `Row` y el contexto de evaluación

- **Estado**: pendiente
- **Depende de**: 1.2, 4.3, 4.5

**Archivos:**

- `engine/ResultSet.kt` (NUEVO)
- `engine/RowContext.kt` (NUEVO)
- `app/src/test/.../ResultSetTest.kt` (NUEVO)

```kotlin
class ResultSet(
    val columns: List<ResultColumn>,
    val rows: List<Row>
)

class Row(val values: List<Value>)

class ResultColumn(
    val name: String,
    val source: String?,    // el alias de la tabla de donde viene, si aplica
    val type: Type
)
```

**`ResultColumn` no lo inventa el motor:** se lo da `schemaOf`, del ticket
4.5, que ya resolvió la expansión de `*`, los nombres de salida y los tipos. Aquí
solo se declara la estructura y se copia.

**Por qué `Row` es posicional y no un mapa:** es lo que ya es una línea de CSV, y
en un `JOIN` puede haber dos columnas llamadas `id`, que en un mapa se pisarían.

### El contexto de evaluación

```kotlin
// Una pila de filas, una por nivel de anidamiento.
// El nivel 0 es la consulta que se esta evaluando ahora.
class RowContext(private val stack: List<Row>) {

    fun value(level: Int, index: Int): Value = stack[level].values[index]

    fun nest(row: Row) = RowContext(listOf(row) + stack)
}
```

**Por qué una pila y no una sola fila:** es la consecuencia de las subconsultas
correlacionadas. Al evaluar `p.uid = u.id` dentro de un `EXISTS`, el motor está
parado en una fila de `posts` pero necesita también la de `users`.

Es el mismo mecanismo que el `Environment` encadenado de Compiscript, con una
diferencia: aquí el salto **no se recorre en ejecución**, porque la fase 4 ya
dejó el número. `valor(nivel, indice)` es dos indexaciones de arreglo.

**Aceptación:**

- `anidar` no muta el contexto original
- `valor(1, 0)` sobre un contexto de dos niveles devuelve la fila de afuera

---

## Ticket 6.2 · Evaluación de expresiones

- **Estado**: pendiente
- **Depende de**: 6.1, 5.1

**Archivos:**

- `engine/Evaluator.kt` (NUEVO)
- `app/src/test/.../EvaluatorTest.kt` (NUEVO)

```kotlin
class Evaluator(private val motor: Engine) {
    fun evaluate(expression: Expression, context: RowContext): Value
}
```

Un `when` sobre `Expression`. Los casos interesantes:

**Referencia a columna:** `contexto.valor(expresion.nivel, expresion.indice)`, y
ya. Sin buscar nombres.

**Binary:** el operador se aplica según `expresion.tipo`, que la fase 5 dejó
pegado. El motor **no decide** si `1 + 2.5` es suma entera o flotante: eso ya está
resuelto, solo convierte los operandos al tipo del resultado y opera.

**Nulos:** cualquier operación aritmética o de comparación con un `NullValue` da
`NullValue`, no error. Es la lógica de tres valores de SQL, simplificada: `NULL`
se propaga.

```kotlin
// AND y OR son la excepcion, y es deliberado:
//   false AND NULL  ->  false, porque ya no puede ser verdadero
//   true  OR  NULL  ->  true
```

**Filtrado:** una fila pasa el `WHERE` solo si la condición da `BoolValue(true)`.
`NullValue` **no pasa**. Por eso `= NULL` nunca filtra nada y la fase 5 advierte.

**Subconsulta escalar:** delega en el motor, que devuelve el `ResultSet`. Si trae
más de una fila, error de ejecución. Si trae cero, `NullValue`.

**Sobre el ciclo entre `Evaluator` y `Engine`:** el evaluador necesita al motor
para las subconsultas, y el motor necesita al evaluador para los predicados. Es
una recursión mutua deliberada y refleja la del lenguaje, donde una expresión
puede contener una consulta y una consulta contiene expresiones. Se resuelve
pasando el motor al evaluador en el constructor, y no al revés.

**Aceptación:**

- `1 + NULL` da `NullValue`
- `false AND NULL` da `BoolValue(false)`
- `true AND NULL` da `NullValue`
- una fila con `WHERE` en `NullValue` no pasa el filtro
- una subconsulta escalar con dos filas reporta error de ejecución

---

## Ticket 6.3 · Operadores básicos

- **Estado**: pendiente
- **Depende de**: 6.2, 6.0, 2.4

**Archivos:**

- `engine/Operators.kt` (NUEVO)
- `app/src/test/.../OperatorsTest.kt` (NUEVO)

| Operador | Qué hace |
|---|---|
| `scan(tabla)` | pide las filas a la `Session`, que lee el CSV la primera vez |
| `filter(pred)` | el `WHERE` y el `HAVING` |
| `project(cols)` | el `SELECT` |
| `sort(claves)` | el `ORDER BY` |
| `distinct()` | quita filas repetidas |
| `limit(n)` | corta |

Cada uno es una función de `ResultSet` a `ResultSet`, así que un `SELECT` es una
cadena, y la cadena sigue el orden de evaluación de la fase 5.

**Sobre `sort` y los nulos:** los `NullValue` van al final en `ASC` y al principio
en `DESC`, que es la convención de SQLite. Hay que decidirlo porque comparar un
nulo con cualquier cosa da nulo, así que el comparador necesita una regla
explícita.

**Sobre `distinct` y `BigDecimal`:** `BigDecimal("1.0")` y `BigDecimal("1.00")`
son distintos para `equals` y el mismo número para `compareTo`. `distinct` tiene
que normalizar la escala antes de comparar, o `1.0` y `1.00` saldrían como dos
filas.

**`scan` no lee el disco directamente:** llama a `Session.rowsOf(tabla)`, del
ticket 6.0. Eso da dos cosas de una vez: una tabla leída dos veces en el mismo
script, por ejemplo en un autojoin, abre el archivo **una sola vez**; y un `SELECT`
después de un `INSERT` ve la fila nueva, porque es el mismo mapa.

La decodificación con `ValueCodec` guiada por el esquema ocurre dentro de
`rowsOf`, la primera vez que la tabla se carga.

**Aceptación:**

- `scan` decodifica los 11 tipos correctamente desde un CSV escrito a mano
- `sort` pone los nulos al final en `ASC`
- `distinct` colapsa `1.0` y `1.00`
- la misma tabla en un autojoin abre el archivo una sola vez
- `scan` sobre una tabla ya modificada en memoria devuelve las filas modificadas

---

## Ticket 6.4 · Join

- **Estado**: pendiente
- **Depende de**: 6.3

**Archivos:**

- `engine/Operators.kt` (MODIFICA)
- `app/src/test/.../JoinTest.kt` (NUEVO)

```kotlin
// Bucles anidados: por cada fila de la izquierda, se recorre la derecha
// entera y se conserva el par que cumple la condicion.
fun join(izquierda: ResultSet, derecha: ResultSet, condition: Expression): ResultSet
```

La fila resultante es la **concatenación** de las dos, en el orden del `FROM`. Los
índices que dejó la fase 4 ya apuntan al lugar correcto de esa fila compuesta, así
que el join no tiene que reordenar nada.

### Decisión · bucles anidados, sin índices

Es cuadrático: 1,000 por 1,000 filas son un millón de comparaciones. Para el
tamaño de datos de este proyecto es correcto, y meter índices o un hash join sería
optimizar algo que nadie va a notar. **Es el único lugar que habría que tocar si
el volumen creciera**, y conviene decirlo así en la defensa en vez de que lo
señalen.

**Aceptación:**

- un join de dos tablas con una coincidencia devuelve una fila
- sin coincidencias devuelve cero filas
- tres tablas encadenadas funcionan, asociando de izquierda a derecha
- la fila resultante tiene la suma de las columnas de las dos

---

## Ticket 6.5 · Agregación

- **Estado**: pendiente
- **Depende de**: 6.3, 5.3

**Archivos:**

- `engine/Aggregator.kt` (NUEVO)
- `app/src/test/.../AggregatorTest.kt` (NUEVO)

```kotlin
// Agrupa las filas por los valores de las expresiones de GROUP BY,
// y calcula una fila de salida por grupo.
fun aggregate(input: ResultSet, keys: List<Expression>, selection: List<SelectItem>): ResultSet
```

**Sin `GROUP BY` pero con agregación**, todo es **un solo grupo**:
`SELECT COUNT(*) FROM users` devuelve una fila aunque la tabla esté vacía.

### Cómo trata los nulos cada función

Es la parte que más se equivoca y donde más vale un test por caso.

| Función | Con nulos | Sobre cero filas |
|---|---|---|
| `COUNT(*)` | cuenta **todas** las filas | `0` |
| `COUNT(col)` | **ignora** los nulos | `0` |
| `SUM` | ignora los nulos | `NULL`, no `0` |
| `AVG` | ignora los nulos, divide entre los no nulos | `NULL` |
| `MIN` y `MAX` | ignoran los nulos | `NULL` |

**`COUNT(*)` contra `COUNT(col)` es la diferencia clásica:** el primero cuenta
filas, el segundo cuenta valores presentes. Si `edad` tiene tres nulos de diez
filas, `COUNT(*)` da 10 y `COUNT(edad)` da 7.

**`SUM` sobre cero filas da `NULL` y no `0`** porque no hay nada que sumar, y
decir `0` sería inventar un dato. Es lo que dice el estándar y lo que hace SQLite.

**`DISTINCT` dentro de una agregación**, como `COUNT(DISTINCT depto)`, quita
repetidos antes de contar.

**Aceptación:** una prueba por celda de la tabla, más

- `SELECT COUNT(*) FROM users` sobre tabla vacía da una fila con `0`
- `SELECT SUM(edad) FROM users` sobre tabla vacía da una fila con `NULL`
- `COUNT(DISTINCT depto)` no cuenta repetidos

---

## Ticket 6.6 · Subconsultas

- **Estado**: pendiente
- **Depende de**: 6.2, 6.5, 6.0, 4.4

**Archivos:**

- `engine/Engine.kt` (NUEVO)
- `app/src/test/.../SubconsultaTest.kt` (NUEVO)

`Engine(session)` es el que arma la cadena de operadores desde una `Query` y la
ejecuta. Toda lectura de datos pasa por la `Session`, así que el motor nunca abre
un archivo por su cuenta.

Es también el que decide cómo tratar cada subconsulta:

```kotlin
// La fase 4 ya marco cuales son correlacionadas.
private fun executeSubquery(query: Query, context: RowContext): ResultSet =
    if (query.correlated) {
        run(query, context)                      // por fila
    } else {
        cache.getOrPut(query) { run(query, context) }   // una vez
    }
```

**El cacheo es lo que hace usable el `IN`:** una subconsulta no correlacionada
dentro de un `WHERE` sobre 10,000 filas se ejecutaría 10,000 veces sin esto, y da
siempre lo mismo.

### Las cuatro formas

| Forma | Qué devuelve |
|---|---|
| escalar | el único valor, `NullValue` si cero filas, error si más de una |
| `IN` | `true` si el valor está entre los resultados |
| `EXISTS` | `true` si hay al menos una fila; **corta al encontrar la primera** |
| en el `FROM` | un `ResultSet` que se usa como si fuera una tabla escaneada |

**`EXISTS` corta al encontrar la primera fila.** Es la única optimización que vale
la pena aquí, porque un `EXISTS` correlacionado corre por cada fila de afuera, y
sin el corte recorrería la tabla entera cada vez solo para descartar el resultado.

**La tabla derivada se ejecuta una sola vez**, antes del `FROM` de la consulta que
la contiene, porque no puede ser correlacionada: su ámbito no ve el de afuera, ya
que el `FROM` es lo primero que se evalúa.

**Aceptación:**

- `IN` no correlacionado ejecuta la interna una sola vez, verificable con un
  contador
- `EXISTS` correlacionado ejecuta la interna una vez por fila de afuera
- `EXISTS` corta: sobre una tabla de 1,000 filas donde la primera coincide, lee
  una
- tres niveles de anidamiento dan el resultado correcto
- una tabla derivada con agregación adentro funciona
