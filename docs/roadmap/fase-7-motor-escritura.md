# Fase 7 · Motor II, escritura y DDL

**Objetivo:** que los cambios lleguen al disco, o que no llegue ninguno.

**La regla de esta fase** es la decisión 5: las sentencias se aplican en memoria y
el volcado ocurre una sola vez al terminar. Si cualquier sentencia falla, no se
escribe nada.

**Estimación:** dos sesiones.

---

## Ticket 7.1 · Las validaciones de escritura

- **Estado**: completado
- **Depende de**: 6.3, 2.2

**Archivos:**

- `engine/WriteRules.kt` (NUEVO)
- `app/src/test/.../WriteRulesTest.kt` (NUEVO)

**Qué es esto, en simple:** lo que se revisa antes de aceptar una fila. Es lo que
de verdad protege la base, porque por la decisión 4 el DBMS confía en lo que él
mismo escribió: **valida lo que entra, no lo que ya está guardado**.

### El orden, que importa

```
1. tipo            el valor cuadra con la columna
2. NOT NULL        no llega nulo where no se permite
3. DEFAULT         se rellenan las columnas ausentes
4. AUTOINCREMENT   se asigna el siguiente
5. PRIMARY KEY     no hay repetido en la tabla
   UNIQUE
6. FOREIGN KEY     el valor existe en la tabla referida
```

**Los pasos 3 y 4 van antes del 5** porque rellenan valores que después hay que
verificar: un `AUTOINCREMENT` genera la clave primaria, así que revisarla antes no
tendría qué revisar.

**Los pasos 5 y 6 son los caros:** obligan a cargar la tabla entera, o la
referida. Es lo único que hace costoso un `INSERT`, y es inevitable sin índices.

### El largo, que el semántico no pudo verificar

La fase 5 revisa el largo solo sobre literales. Una expresión como
`name || apellido` no tiene largo conocido hasta evaluarla, así que aquí se
verifica de nuevo, ahora sobre el valor ya calculado.

```
'Maria Fernanda Gonzalez' no cabe en VARCHAR(10)
```

### El siguiente `AUTOINCREMENT`

Es el máximo actual de la columna más uno, calculado al cargar la tabla. Sobre
tabla vacía arranca en 1. Es simple y correcto sin estado persistido; el costo es
que si se borra la última fila, el número se reusa. Se documenta como
simplificación consciente.

### Ajustar y validar son dos cosas

Quedaron separadas porque se usan en momentos distintos. `prepare` ajusta cada
celda a su columna, que es rellenar un `CHAR`, redondear un `DECIMAL` y rechazar lo
que no cabe. `validate` mira la fila ya ajustada contra las reglas de la tabla.

Un `INSERT` arma la fila y hace las dos. Un `UPDATE` ya tiene la fila y solo
necesita las dos sobre el resultado, sin volver a rellenar lo que no tocó.

**El `NULL` que la fase 5 no podía ver.** Un `NULL` escrito lo atrapa el
semántico. El que sale de `(SELECT name FROM users WHERE id = 99)`, que no devolvió
filas, solo se ve con los datos delante, y ese lo atrapa aquí.

**Aceptación:** una prueba por paso, más

- insertar en tabla vacía con `AUTOINCREMENT` da `1`, y el siguiente sigue desde el
  mayor que ya hay
- el valor generado respeta la clave primaria, porque se asigna antes de revisarla
- insertar una PK repetida reporta con el valor en el mensaje, aunque la otra fila
  la haya insertado una sentencia anterior del mismo script
- una FK que apunta a un valor inexistente reporta con las dos tablas, y una en
  nulo no se verifica
- un `NULL` que aparece al ejecutar reporta
- columna omitida con `DEFAULT` toma el valor por omisión
- un texto calculado que no cabe reporta, un decimal con escala de más se redondea
  y uno con parte entera de más reporta

---

## Ticket 7.2 · `INSERT`, `UPDATE` y `DELETE`

- **Estado**: completado
- **Depende de**: 7.1, 6.0

**Archivos:**

- `engine/Writer.kt` (NUEVO)
- `engine/Session.kt` (MODIFICA)
- `engine/Engine.kt` (MODIFICA, `valueOf` para una expresión suelta)
- `app/src/test/.../WriterDmlTest.kt` (NUEVO)

```kotlin
// Las tres trabajan sobre las filas de la Session. Nada toca el disco aqui.
class Writer(private val session: Session) {
    fun execute(statement: Statement)   // el despachador que el 6.0 dejo pendiente

    fun insert(statement: Insert)
    fun update(statement: Update)
    fun delete(statement: Delete)
}
```

**Este ticket completa el `when` del bucle de `Session.correr`**, que en la fase 6
solo atendía `Query`. Toda modificación va contra `session.rowsOf(tabla)` y
marca la tabla, que es lo que después lee `flush`.

**`UPDATE` valida la fila resultante completa**, no solo las columnas asignadas.
Cambiar `edad` no puede romper la PK, pero cambiar `id` sí, y la única forma
robusta de saberlo es revisar la fila entera después de aplicar los cambios.

**`UPDATE` excluye la propia fila al revisar unicidad.** Si no, poner
`id = 5` en la fila que ya tiene `id = 5` reportaría un duplicado consigo misma.
La comparación es por posición en la tabla, no por valor.

**`DELETE` revisa las FK al revés:** borrar una fila de `users` es error si alguna
fila de `posts` la referencia.

```
no se puede borrar users.id = 1: lo referencian 2 filas de posts
```

**Un `UPDATE` que no alcanza ninguna fila no marca la tabla**, y por eso el volcado
no la reescribe. Marcar al entrar sería más simple y haría que `UPDATE ... WHERE
id = 99` reescribiera un archivo idéntico.

**Aceptación:**

- `UPDATE` que cambia una columna no relacionada no reporta duplicado
- `UPDATE id = id` sobre una fila no reporta duplicado consigo misma
- `DELETE` de una fila referenciada reporta con el conteo
- `DELETE` sin `WHERE` borra todo y la advertencia de la fase 5 sale sin detenerlo
- las tres operan en memoria: el archivo no cambia hasta el volcado
- **`INSERT` y luego `SELECT` en el mismo script: el `SELECT` ve la fila nueva**
- **`DELETE` y luego `SELECT`: el `SELECT` ya no la ve**
- **`INSERT` de una PK que otra sentencia del mismo script acaba de insertar
  reporta duplicado**, aunque ninguna de las dos esté en disco

---

## Ticket 7.3 · `CREATE`, `ALTER` y `DROP`

- **Estado**: completado
- **Depende de**: 7.2, 2.6

**Archivos:**

- `engine/Writer.kt` (MODIFICA)
- `frontend/semantic/NameResolver.kt` (MODIFICA, ver abajo)
- `app/src/test/.../WriterDdlTest.kt` (NUEVO)

Cada una toca **los dos archivos** de la tabla, en memoria, llamando a
`session.applyCatalog(...)` con el resultado de las extensiones del ticket 2.6.
El catálogo de la `Session` muta; el original queda guardado para que `flush` sepa
qué esquemas cambiaron.

| Statement | Al `.json` | Al `.csv` |
|---|---|---|
| `CREATE TABLE` | lo crea con el esquema | lo crea con solo el header |
| `ALTER ADD` | agrega la columna | agrega al header y un campo al final de cada fila |
| `ALTER DROP` | quita la columna | quita del header y ese campo de cada fila |
| `DROP TABLE` | lo borra | lo borra |

**`ALTER ADD` con `DEFAULT` rellena las filas existentes con ese valor**, no con
nulo. Es lo que permite que la regla de la fase 5 acepte agregar una columna
`NOT NULL` si trae `DEFAULT`.

**Un `ALTER` seguido de un `INSERT` en el mismo script funciona**, porque todo
opera sobre el catálogo en memoria y el `INSERT` ve la columna nueva. Es un caso
que hay que probar explícitamente, porque es donde se nota si alguna parte del
motor se quedó con el catálogo viejo.

### Dos errores que este ticket destapó

**La guarda de cordura se disparaba después de un `ALTER`.** Comparaba el header
del CSV contra el esquema **como va quedando**, así que apenas una sentencia
agregaba una columna, la siguiente reportaba que el archivo estaba editado a mano.
Y tenía razón en que no coincidían: lo que estaba mal era contra qué comparaba. La
guarda existe para detectar una edición externa, así que compara contra el esquema
que hay **en disco**, el del catálogo inicial, y salta las tablas que nacieron en
este mismo script.

**El `ALTER` cargaba las filas después de cambiar el catálogo.** `rowsOf` decodifica
guiándose por el esquema, así que al pedirlas después del cambio leía el archivo
viejo con el esquema nuevo: un `ADD` dejaba la columna dos veces y un `DROP` se
comía una de más. Las filas se materializan **antes** de tocar el catálogo.

Los dos tienen la misma forma, que es la única cosa delicada de esta fase: hay dos
esquemas a la vez, el que está en disco y el que va quedando en memoria, y cada
operación tiene que saber cuál de los dos le toca.

**Aceptación:**

- `CREATE` y luego `INSERT` en el mismo script funciona
- `ALTER ADD` con `DEFAULT` rellena las filas existentes
- `ALTER ADD` sin `DEFAULT` deja nulos
- `ALTER ADD` y usar la columna nueva en la sentencia siguiente funciona
- `ALTER DROP` quita el campo de todas las filas y lo que queda sigue alineado
- `DROP` y `CREATE` de la misma tabla en un script deja la tabla nueva

---

## Ticket 7.4 · El volcado y la vuelta atrás

- **Estado**: completado
- **Depende de**: 7.3

**Archivos:**

- `engine/Flush.kt` (NUEVO)
- `app/src/test/.../FlushTest.kt` (NUEVO)

```kotlin
// Escribe SOLO lo que cambio. Se llama una vez, al final, y solo si
// no hubo ningun error. Decision 5.
fun flush(changes: Changes, directory: DataDirectory)

// Lo que Session.changes() devuelve, del ticket 6.0.
class Changes(
    val rows: Map<String, List<Row>>,
    val catalog: Catalog,
    val schemas: List<String>,
    val dropped: List<String>
)
```

Los esquemas viajan como **nombres** y no como `Table`, con el catálogo final al
lado. El volcado necesita ese catálogo de todos modos, porque para escribir un CSV
tiene que saber el orden y el tipo de cada columna, así que llevar las tablas
sueltas además sería la misma información dos veces.

**El `flush` no conoce la `Session`, solo su reporte.** Así se prueba pasándole un
`Changes` armado a mano, sin montar una corrida entera.

### Cómo se ve por dentro

Hay dos momentos en que una sentencia puede fallar, y en los dos el resultado
visible es el mismo:

- **antes de correr**, si el semántico detecta que la tabla o la columna no
  existe: ni se empieza
- **corriendo**, si por ejemplo una PK sale repetida: las sentencias anteriores ya
  se aplicaron **en memoria**, pero como nada tocó el disco, el directorio queda
  idéntico

Eso es una vuelta atrás sin haber escrito una sola palabra de gramática nueva, y
es lo que pediste: o se aplica todo, o no se aplica nada.

### El orden del volcado

```
1. los .json de las tablas cuyo esquema cambio
2. los .csv de las tablas cuyas filas cambiaron
3. los .idx de esas mismas tablas, reconstruidos  (ticket 8.6)
4. borrar los archivos de las tablas eliminadas, incluidos sus .idx
```

El paso 3 sale del paso 2 sin recorrido extra: `CsvWriter.write` devuelve los
desplazamientos nuevos, y con ellos se arma cada indice. Reconstruir en vez de
parchear es lo que hace que no exista logica incremental de indices.

Cada escritura usa el temporal más renombre del ticket 2.4. El volcado completo
**no** es atómico entre tablas: si el proceso muere entre la tabla 1 y la 2, la 1
quedó escrita. Hacerlo atómico de verdad requiere un diario de transacciones, que
está fuera del alcance a propósito. Se documenta.

**Aceptación:**

- un script cuya cuarta sentencia falla deja el directorio **idéntico**, comparado
  byte por byte contra una copia tomada antes
- un script que solo consulta no escribe ningún archivo
- un script que toca una tabla de dos no reescribe la otra, ni reescribe el `.json`
  de la que cambió si su esquema no cambió, verificable por fecha de modificación
- todos los tests corren sobre un `DataDirectory` de `@TempDir`, nunca sobre
  `DEFAULT`
- un `CREATE` seguido de un error deja el directorio sin la tabla nueva
- lo que se escribió se vuelve a leer igual, con los nueve tipos guardables

---

## Ticket 7.5 · El pipeline completo

- **Estado**: completado
- **Depende de**: 7.4, 6.6

**Archivos:**

- `runtime/DbmsPipeline.kt` (MODIFICA)
- `runtime/models/CompilationResult.kt` (MODIFICA)
- `app/src/test/.../DbmsPipelineTest.kt` (MODIFICA)

`ExecutionResult` no necesitó archivo propio: nació con la `Session` en el ticket
6.0, que es quien lo arma.

```kotlin
object DbmsPipeline {
    fun run(
        source: String,
        directory: DataDirectory = DataDirectory.DEFAULT,
        write: Boolean = true
    ): CompilationResult {
        val diagnostics = Diagnostics()

        // Etapa A: sintaxis. La UNICA que corta.
        val parseTree = SqlSyntaxAnalyzer.parse(source, diagnostics)
            ?: return CompilationResult.failed(diagnostics, source)
        val parseTreeView = parseTree.toTreeView()

        // Etapa B: AST propio.
        val ast = SqlAstBuilder().visit(parseTree) as Script

        // Etapa C: catalogo. Lee los .json, ningun .csv.
        val catalog = CatalogLoader(directory, diagnostics).cargar()

        // Etapa D: semantico, en sus dos mitades. Corre AUNQUE C haya reportado,
        // para que el usuario vea todos sus problemas de una vez.
        val scope = NameResolver(catalog, directory, diagnostics).resolve(ast)
        SqlChecker(catalog, directory, diagnostics).check(ast)

        // Etapa E: ejecucion, solo si no quedo ningun error.
        // hasErrors ignora las WARNING, asi que un script con avisos corre.
        val session = Session(directory, catalog, diagnostics)
        val execution = if (!diagnostics.hasErrors) session.run(ast) else null

        // Etapa F: volcado, solo si E tampoco fallo.
        if (write && execution != null && !diagnostics.hasErrors) {
            Flush.flush(session.changes(), directory)
        }

        return CompilationResult(
            source = source,
            parseTreeView = parseTreeView,
            ast = ast,
            catalog = catalog,
            errors = diagnostics.all(),
            execution = execution
        )
    }
}
```

**El `write = false` es para los tests** que quieren el resultado sin volcar. Los
que sí prueban la escritura pasan un `directory` de `@TempDir`, que es lo que evita
que la batería toque el `data/` real.

### Un error que solo se ve con las seis etapas juntas

`CREATE TABLE users (...); SELECT * FROM users;` reportaba que la tabla no existe.
La fase 5 ya había enseñado al resolutor a llevar un catálogo que avanza con el
script, pero `declareTable`, que es quien resuelve el `FROM`, seguía preguntándole
al catálogo **inicial**. El caso no salía antes porque los tests de la fase 5
probaban `CREATE` seguido de `INSERT`, que va por otro camino.

Es el valor de conectar el pipeline: ninguna de las dos fases podía ver el error
desde adentro.

`CompilationResult` tiene todos los campos nulables a propósito: un fuente que no
parsea no tiene AST, pero sí tiene errores, y la GUI debe poder mostrar resultados
parciales. Además del catálogo y la ejecución, lleva el **árbol de ámbitos**, que
el resolutor ya devolvía y que el ticket 9.3 dibuja.

**Aceptación:**

- un script válido de extremo a extremo: `CREATE`, `INSERT`, `SELECT`, y el
  `SELECT` devuelve lo insertado, que además quedó escrito en el archivo
- un error de sintaxis devuelve resultado con errores y sin AST
- un error semántico devuelve resultado con AST, con catálogo y sin ejecución
- un error de ejecución deja el AST y el catálogo
- el árbol de ámbitos llega al resultado
- con `write = false`, el directorio nunca cambia
- un script con solo advertencias se ejecuta y vuelca
- ninguna prueba del pipeline usa `DataDirectory.DEFAULT`
