# Fase 8 · Índices

**Objetivo:** que `CREATE INDEX` cree un archivo de índice, que un `WHERE` sobre
una columna indexada lea solo las filas que casan, y que los índices sigan
válidos después de escribir.

**Por qué existe esta fase:** el enunciado la pide con tres obligaciones, y
ninguna estaba en el diseño original. El roadmap las había excluido a propósito.

**Estimación:** tres sesiones.

---

## Cómo funciona, en una página

Un archivo es una tira de bytes. Lo que llamamos líneas son los pedazos entre
saltos de línea, y cada una arranca en una posición conocida:

```
carros.csv                        byte donde arranca
──────────────────────────        ──────────────────
id,marca,anio                     0
1,Toyota,2020                     17
2,Mazda,2019                      31
3,Toyota,2021                     44
4,Mazda,2022                      58
5,Toyota,2018                     71
```

El índice guarda esa segunda columna, ordenado por valor. Una columna no única
repite el valor en varias líneas, que es el caso normal:

```
idx_marca.idx
─────────────
value,offset
Mazda,31
Mazda,58
Toyota,17
Toyota,44
Toyota,71
```

Y la consulta queda en tres pasos:

```kotlin
WHERE marca = 'Toyota'

val offsets = index["Toyota"]            // [17, 44, 71], del TreeMap
offsets.forEach { archivo.seek(it) }     // tres saltos
                                         // las filas de Mazda nunca se leen
```

### Las tres decisiones que lo hacen simple

**Los desplazamientos salen del escritor.** `CsvWriter` los cuenta mientras
escribe. Son tres líneas de más en un archivo que de todos modos hay que
escribir.

**Nada se actualiza de forma incremental.** Un `UPDATE` o un `DELETE` reescriben
la tabla entera en el volcado, así que los índices se **reconstruyen** en ese
mismo recorrido. El enunciado pide que se mantengan actualizados al `INSERT`,
`UPDATE` y `DELETE`; reconstruirlos al guardar lo cumple y elimina una familia
completa de errores.

**La estructura en memoria es `java.util.TreeMap`**, un árbol rojo-negro del JDK,
sin dependencias nuevas. Se elige sobre `HashMap` porque el enunciado dice
*condiciones de WHERE*, no solo igualdad:

| Condición | Llamada |
|---|---|
| `marca = 'Toyota'` | `get("Toyota")` |
| `anio > 2020` | `tailMap(2020, false)` |
| `anio BETWEEN 2019 AND 2021` | `subMap(2019, 2021)` |
| `marca IN ('Mazda','Nissan')` | dos `get` |

Un árbol B+ a mano daría la misma complejidad y es un ejercicio de bases de
datos, no de compiladores. En la defensa: un DBMS real usa B+ en disco porque el
árbol no cabe en RAM; el nuestro cabe, así que un árbol balanceado en memoria
sobre datos ordenados en disco es el equivalente honesto.

### Lo que queda fuera, a propósito

| Cosa | Por qué |
|---|---|
| Índices compuestos | una columna por índice cubre todos los ejemplos del enunciado |
| Actualización incremental | se reconstruyen al volcar |
| Usar el índice para `ORDER BY` | no lo pide el enunciado |
| Combinar dos índices en un `WHERE` | se elige uno y el resto se filtra normal |
| Estadísticas y planificador por costos | ver la limitación de abajo |

**Limitación conocida, y se declara en la defensa:** si `marca = 'Toyota'` casa
con 3,000 de 10,000 filas, hacer 3,000 saltos puede salir más lento que leer el
archivo de corrido. Los DBMS reales guardan estadísticas de cardinalidad y a
veces deciden ignorar el índice. Aquí la regla es: **si hay índice utilizable, se
usa**.

---

## Ticket 8.1 · `CREATE INDEX` y `DROP INDEX`

- **Estado**: pendiente
- **Depende de**: 3.6

**Archivos:**

- `app/src/main/antlr/Sql.g4` (MODIFICA)
- `frontend/ast/models/Statement.kt` (MODIFICA)
- `frontend/ast/SqlAstBuilder.kt` (MODIFICA)
- `app/src/test/.../SqlAstBuilderIndexTest.kt` (NUEVO)

```antlr
statement
  : ...
  | createIndex ';'
  | dropIndex ';'
  ;

createIndex : 'CREATE' 'INDEX' Identifier 'ON' Identifier '(' columnList ')' ;
dropIndex   : 'DROP' 'INDEX' Identifier ;
```

```kotlin
class CreateIndex(
    val name: String,
    val table: String,
    val columns: List<String>,
    override val location: LexemeLocation
) : Statement

class DropIndex(val name: String, override val location: LexemeLocation) : Statement
```

### Decisión · la gramática acepta varias columnas aunque no las soportemos

`columnList` y no un solo `Identifier`. Si la gramática aceptara una sola,
escribir `ON carros(marca, anio)` daría `se esperaba ')'`, que no explica nada.
Aceptándolas y rechazando en el ticket 8.5, el mensaje es *"los índices son de una
sola columna"*.

Es el mismo criterio del `CREATE TABLE`, donde la gramática acepta
`constraint*` en cualquier orden y el semántico dice que `NOT NULL` junto a `NULL`
no tiene sentido.

**Aceptación:** las dos sentencias parsean, `ON t(a, b)` también parsea, y el AST
guarda la lista completa de columnas.

---

## Ticket 8.2 · El modelo `Index` y el catálogo

- **Estado**: pendiente
- **Depende de**: 2.2, 2.3

**Archivos:**

- `catalog/Index.kt` (NUEVO)
- `catalog/Catalog.kt` (MODIFICA)
- `catalog/SchemaJson.kt` (MODIFICA)
- `app/src/test/.../IndexCatalogTest.kt` (NUEVO)

```kotlin
class Index(val name: String, val table: String, val column: String, val auto: Boolean)
```

El esquema de la tabla gana una sección:

```json
{
  "columns": { "id": {...}, "marca": {...}, "anio": {...} },
  "indexes": { "pk_carros": "id", "idx_marca": "marca" }
}
```

```kotlin
fun Table.indexOn(column: String): Index?
fun Catalog.index(name: String): Index?
fun Catalog.indexes(): List<Index>
```

### Decisión · los índices viven en el esquema de su tabla

No hay un `indexes.json` global. Un índice es parte del esquema de una tabla, y
ponerlo ahí da tres cosas:

- el nombre duplicado se revisa contra el catálogo, que ya se carga entero
- `DROP TABLE carros` se lleva sus índices sin dejar metadatos huérfanos
- el `.idx` queda como un CSV de dos columnas, sin parser propio

Es la misma razón de la decisión 2: un archivo global es información derivada que
se puede quedar vieja.

### Decisión · `PRIMARY KEY` y `UNIQUE` generan su índice

```sql
CREATE TABLE users (
  id    INT PRIMARY KEY,
  email VARCHAR(80) UNIQUE,
  name  VARCHAR(80)
);
```

escribe cuatro archivos:

```
users.csv   users.json   pk_users.idx   uq_users_email.idx
```

Lo que gana no son las escrituras sino **las consultas**: `WHERE id = 7` usa el
índice sin que nadie haya escrito un `CREATE INDEX`, y la validación de llave
foránea del ticket 7.1 pasa de cargar la tabla referida a un `seek`.

La verificación de unicidad dentro de un `INSERT` **no** lo usa, y está bien: las
filas recién insertadas viven en memoria y todavía no tienen desplazamiento,
porque los desplazamientos se asignan al volcar. Para insertar hay que cargar la
tabla de todos modos.

Los nombres `pk_<tabla>` y `uq_<tabla>_<columna>` quedan reservados: el ticket 8.5
impide crearlos o borrarlos a mano.

**Aceptación:**

- un `CREATE TABLE` con `PRIMARY KEY` y `UNIQUE` deja dos índices en el catálogo
- `indexOn("marca")` devuelve el índice, y `null` si no hay
- ida y vuelta del JSON con la sección `indexes`
- una tabla sin índices escribe el JSON sin esa clave, no con un objeto vacío

---

## Ticket 8.3 · Desplazamientos y lectura por salto

- **Estado**: pendiente
- **Depende de**: 2.4

**Archivos:**

- `storage/CsvWriter.kt` (MODIFICA)
- `storage/RowReader.kt` (NUEVO)
- `app/src/test/.../RowReaderTest.kt` (NUEVO)

El escritor devuelve dónde quedó cada fila:

```kotlin
// Los offsets salen del mismo recorrido que ya escribe el archivo.
fun write(file: File, header: List<String>, rows: List<List<String?>>): List<Long>
```

Y el lector trae una sola fila sin tocar el resto:

```kotlin
fun readAt(file: File, offset: Long): List<String?>
```

### El detalle que muerde

`RandomAccessFile.readLine()` lee bytes como latin-1, no como UTF-8. Un nombre
con tilde saldría mal. Hay que leer los bytes hasta el salto y decodificar a mano:

```kotlin
val bytes = ByteArrayOutputStream()
var b = file.read()
while (b != -1 && b != '\n'.code) { bytes.write(b); b = file.read() }
String(bytes.toByteArray(), Charsets.UTF_8)
```

Doce líneas, en un solo lugar.

**Aceptación:**

- el desplazamiento de la fila `n` apunta a su primer byte
- `readAt` sobre una fila con tildes devuelve el texto correcto
- `readAt` sobre una fila con comas y comillas respeta el escapado del CSV
- escribir, leer por salto y comparar contra la lectura completa da lo mismo

---

## Ticket 8.4 · El archivo `.idx`

- **Estado**: pendiente
- **Depende de**: 8.2, 8.3

**Archivos:**

- `storage/IndexFile.kt` (NUEVO)
- `storage/DataDirectory.kt` (MODIFICA)
- `app/src/test/.../IndexFileTest.kt` (NUEVO)

```
data/idx_marca.idx
──────────────────
value,offset
Mazda,31
Mazda,58
Toyota,17
```

Un CSV de dos columnas ordenado por valor, que se lee con el `CsvReader` del
ticket 2.4. La extensión es `.idx` y no `.csv` **a propósito**: `DataDirectory`
lista los `.csv` como tablas, y un índice no es una tabla.

```kotlin
fun read(file: File, type: Type): TreeMap<Value, List<Long>>
fun write(file: File, entries: TreeMap<Value, List<Long>>)
```

El valor se decodifica con `ValueCodec` usando el tipo de la columna indexada, que
es lo que hace que un `anio` ordene como número y no como texto.

**Carga perezosa:** el catálogo no abre ningún `.idx`. Se abre el de un índice
solo cuando una consulta lo usa. Es el mismo *esquema ansioso, datos perezosos*
que ya rige para los `.csv`.

**Aceptación:**

- ida y vuelta de un índice con valores repetidos
- un índice sobre `INT` ordena 2, 10, 100 y no 10, 100, 2
- `DataDirectory.tables()` no lista los `.idx`
- `DataDirectory.indexFiles()` sí los lista
- leer el catálogo de tres tablas no abre ningún `.idx`

---

## Ticket 8.5 · Reglas semánticas

- **Estado**: pendiente
- **Depende de**: 8.1, 8.2, 5.5

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `app/src/test/.../ReglasIndiceTest.kt` (NUEVO)

**`CREATE INDEX`**

| Regla | Mensaje |
|---|---|
| La tabla existe | `la tabla 'carros' no existe` |
| La columna existe en esa tabla | `'carros' no tiene columna 'marka'` |
| El nombre no está tomado | `ya existe un indice llamado 'idx_marca'` |
| **Una sola columna** | `los indices son de una sola columna, se recibieron 2` |
| El nombre no es reservado | `'pk_carros' es un nombre reservado para indices automaticos` |
| Ya hay índice sobre esa columna | advertencia: `'carros.marca' ya esta indexada por 'idx_otro'` |

**`DROP INDEX`**

| Regla | Mensaje |
|---|---|
| El índice existe | `no existe un indice llamado 'idx_marca'` |
| No es automático | `'pk_carros' respalda la clave primaria y no se puede borrar` |

**Y dos que tocan sentencias viejas**

| Regla | Mensaje |
|---|---|
| `ALTER TABLE t DROP COLUMN c` con índice encima | `'carros.marca' esta indexada por 'idx_marca', hay que borrar el indice primero` |
| `DROP TABLE t` | borra sus `.idx` sin preguntar |

La advertencia de columna ya indexada era decorativa cuando los índices eran todos
manuales. Con los automáticos es la que evita duplicar lo que la tabla ya traía.

**Aceptación:** un test por fila de las tres tablas, y un `CREATE INDEX` sobre una
tabla creada en el mismo script funciona, porque el catálogo muta en memoria.

---

## Ticket 8.6 · El planificador y la reconstrucción

- **Estado**: pendiente
- **Depende de**: 8.4, 6.3, 7.4

**Archivos:**

- `engine/IndexPlanner.kt` (NUEVO)
- `engine/Operators.kt` (MODIFICA)
- `engine/Session.kt` (MODIFICA)
- `engine/Flush.kt` (MODIFICA)
- `app/src/test/.../IndexPlannerTest.kt` (NUEVO)

### Elegir el índice

```kotlin
// Busca, dentro de los AND de primer nivel, la forma `columna op constante`
// sobre una columna indexada. Devuelve null si no hay ninguna.
fun plan(query: Query, catalog: Catalog): IndexPlan?
```

Solo mira las conjunciones de primer nivel. `WHERE marca = 'Toyota' AND anio > 2020`
sirve; `WHERE marca = 'Toyota' OR anio > 2020` no, porque un `OR` puede traer
filas que el índice no señala.

Si hay dos columnas indexadas, se toma **la primera**. Elegir la mejor necesita
estadísticas de cardinalidad, que están fuera del alcance.

### Cuándo NO se usa el índice

**Si la tabla ya está cargada en la `Session`**, se filtra en memoria. Cargarla
otra vez por saltos sería más lento, no menos.

Y el camino del índice **no puebla el caché**: devuelve las filas que casan y ya.
Si después otra consulta necesita la tabla entera, la carga normal. Marcar una
carga parcial como completa daría resultados incorrectos, y distinguir las dos
cosas es estado de más.

### La reconstrucción

En el volcado, por cada tabla modificada:

```
1. CsvWriter.write(...)  devuelve los desplazamientos nuevos
2. por cada indice de esa tabla, armar el TreeMap y escribir su .idx
```

Sale del mismo recorrido que ya escribe el CSV. Cero lógica incremental.

**Aceptación:**

- `WHERE id = 7` sobre una tabla de 1,000 filas lee **una**, verificable con un
  contador de filas leídas
- `WHERE anio > 2020` usa el índice y devuelve el rango correcto
- un `OR` en el `WHERE` no usa índice
- después de un `DELETE` y el volcado, los desplazamientos del `.idx` siguen
  apuntando a la fila correcta
- una tabla ya cargada en memoria no dispara lecturas por salto
- el resultado es idéntico con índice y sin índice, para las mismas consultas
