# Fase 7 · Motor II, escritura y DDL

**Objetivo:** que los cambios lleguen al disco, o que no llegue ninguno.

**La regla de esta fase** es la decisión 5: las sentencias se aplican en memoria y
el volcado ocurre una sola vez al terminar. Si cualquier sentencia falla, no se
escribe nada.

**Estimación:** dos sesiones.

---

## Ticket 7.1 · Las validaciones de escritura

- **Estado**: pendiente
- **Depende de**: 6.3, 2.2

**Archivos:**

- `engine/Validaciones.kt` (NUEVO)
- `app/src/test/.../ValidacionesTest.kt` (NUEVO)

**Qué es esto, en simple:** lo que se revisa antes de aceptar una fila. Es lo que
de verdad protege la base, porque por la decisión 4 el DBMS confía en lo que él
mismo escribió: **valida lo que entra, no lo que ya está guardado**.

### El orden, que importa

```
1. tipo            el valor cuadra con la columna
2. NOT NULL        no llega nulo donde no se permite
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

**Aceptación:** una prueba por paso, más

- insertar en tabla vacía con `AUTOINCREMENT` da `1`
- insertar una PK repetida reporta con el valor en el mensaje
- una FK que apunta a un valor inexistente reporta con las dos tablas
- `NULL` explícito en columna `NOT NULL` reporta
- columna omitida con `DEFAULT` toma el valor por omisión

---

## Ticket 7.2 · `INSERT`, `UPDATE` y `DELETE`

- **Estado**: pendiente
- **Depende de**: 7.1, 6.0

**Archivos:**

- `engine/Writer.kt` (NUEVO)
- `engine/Session.kt` (MODIFICA)
- `app/src/test/.../WriterDmlTest.kt` (NUEVO)

```kotlin
// Las tres trabajan sobre las filas de la Session. Nada toca el disco aqui.
class Writer(private val session: Session) {
    fun aplicar(sentencia: Sentencia)     // el despachador que el 6.0 dejo pendiente

    fun insertar(sentencia: Insertar)
    fun actualizar(sentencia: Actualizar)
    fun borrar(sentencia: Borrar)
}
```

**Este ticket completa el `when` del bucle de `Session.correr`**, que en la fase 6
solo atendía `Consulta`. Toda modificación va contra `session.filasDe(tabla)` y
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
no se puede borrar users.id = 3: lo referencian 4 filas de posts
```

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

- **Estado**: pendiente
- **Depende de**: 7.2, 2.6

**Archivos:**

- `engine/Writer.kt` (MODIFICA)
- `engine/Session.kt` (MODIFICA)
- `app/src/test/.../WriterDdlTest.kt` (NUEVO)

Cada una toca **los dos archivos** de la tabla, en memoria, llamando a
`session.aplicarCatalogo(...)` con el resultado de las extensiones del ticket 2.6.
El catálogo de la `Session` muta; el original queda guardado para que `flush` sepa
qué esquemas cambiaron.

| Sentencia | Al `.json` | Al `.csv` |
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

**Aceptación:**

- `CREATE` y luego `INSERT` en el mismo script funciona
- `ALTER ADD` con `DEFAULT` rellena las filas existentes
- `ALTER ADD` sin `DEFAULT` deja nulos
- `ALTER DROP` quita el campo de todas las filas
- `DROP` y `CREATE` de la misma tabla en un script deja la tabla nueva

---

## Ticket 7.4 · El volcado y la vuelta atrás

- **Estado**: pendiente
- **Depende de**: 7.3

**Archivos:**

- `engine/Flush.kt` (NUEVO)
- `app/src/test/.../FlushTest.kt` (NUEVO)

```kotlin
// Escribe SOLO lo que cambio. Se llama una vez, al final, y solo si
// no hubo ningun error. Decision 5.
fun volcar(cambios: Cambios, directorio: DataDirectory)

// Lo que Session.cambios() devuelve, del ticket 6.0.
class Cambios(
    val esquemasModificados: List<Table>,
    val tablasModificadas: Map<String, List<Row>>,
    val tablasEliminadas: List<String>
)
```

**El `flush` no conoce la `Session`, solo su reporte.** Así se prueba pasándole un
`Cambios` armado a mano, sin montar una corrida entera.

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
3. borrar los pares de las tablas eliminadas
```

Cada escritura usa el temporal más renombre del ticket 2.4. El volcado completo
**no** es atómico entre tablas: si el proceso muere entre la tabla 1 y la 2, la 1
quedó escrita. Hacerlo atómico de verdad requiere un diario de transacciones, que
está fuera del alcance a propósito. Se documenta.

**Aceptación:**

- un script cuya cuarta sentencia falla deja `datos/` **idéntico**, comparado byte
  por byte contra una copia tomada antes
- un script que solo consulta no escribe ningún archivo
- un script que toca una tabla de tres no reescribe las otras dos, verificable por
  fecha de modificación
- todos los tests corren sobre un `DataDirectory` de `@TempDir`, nunca sobre
  `POR_OMISION`
- un `CREATE` seguido de un error deja el directorio sin la tabla nueva

---

## Ticket 7.5 · El pipeline completo

- **Estado**: pendiente
- **Depende de**: 7.4, 6.6

**Archivos:**

- `runtime/DbmsPipeline.kt` (MODIFICA)
- `runtime/models/CompilationResult.kt` (MODIFICA)
- `engine/ExecutionResult.kt` (NUEVO)
- `app/src/test/.../DbmsPipelineTest.kt` (MODIFICA)

```kotlin
object DbmsPipeline {
    fun ejecutar(
        fuente: String,
        directorio: DataDirectory = DataDirectory.POR_OMISION,
        escribir: Boolean = true
    ): CompilationResult {
        val diagnostics = Diagnostics()

        // Etapa A: sintaxis. La UNICA que corta.
        val parseTree = SqlSyntaxAnalyzer.parse(fuente, diagnostics)
            ?: return CompilationResult.fallida(diagnostics, fuente)
        val parseTreeView = parseTree.toTreeView()

        // Etapa B: AST propio.
        val ast = SqlAstBuilder().visit(parseTree) as Script

        // Etapa C: catalogo. Lee los .json, ningun .csv.
        val catalogo = CatalogLoader(directorio, diagnostics).cargar()

        // Etapa D: semantico. Corre AUNQUE C haya reportado, para que el
        // usuario vea todos sus problemas de una vez.
        SqlChecker(catalogo, diagnostics).revisar(ast)

        // Etapa E: ejecucion, solo si no quedo ningun error.
        // hasErrors ignora las ADVERTENCIA, asi que un script con avisos corre.
        val session = Session(directorio, catalogo, diagnostics)
        val ejecucion = if (!diagnostics.hasErrors) session.correr(ast) else null

        // Etapa F: volcado, solo si E tampoco fallo.
        if (escribir && ejecucion != null && !diagnostics.hasErrors) {
            Flush.volcar(session.cambios(), directorio)
        }

        return CompilationResult(
            fuente = fuente,
            parseTreeView = parseTreeView,
            ast = ast,
            catalogo = catalogo,
            errores = diagnostics.all(),
            ejecucion = ejecucion
        )
    }
}
```

**El `escribir = false` es para los tests** que quieren el resultado sin volcar.
Los que sí prueban la escritura pasan un `directorio` de `@TempDir`, que es lo que
evita que la batería toque el `datos/` real.

`CompilationResult` tiene todos los campos nulables a propósito, igual que en
Compiscript: un fuente que no parsea no tiene AST, pero sí tiene errores, y la GUI
debe poder mostrar resultados parciales.

**Aceptación:**

- un script válido de extremo a extremo: `CREATE`, `INSERT`, `SELECT`, y el
  `SELECT` devuelve lo insertado
- un error de sintaxis devuelve resultado con errores y sin AST
- un error semántico devuelve resultado con AST, con catálogo y sin ejecución
- con `escribir = false`, el directorio nunca cambia
- un script con solo advertencias se ejecuta y vuelca
- ninguna prueba del pipeline usa `DataDirectory.POR_OMISION`
