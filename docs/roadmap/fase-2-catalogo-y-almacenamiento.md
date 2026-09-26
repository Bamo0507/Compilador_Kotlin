# Fase 2 · Catálogo y almacenamiento

**Objetivo:** que el directorio `data/` se pueda leer y escribir sin perder nada.
Al terminar existe el catálogo completo de la base en memoria, y las filas de
cualquier tabla se cargan y se guardan.

**Lo que hace única a esta fase:** no toca ANTLR ni SQL. Es Kotlin y archivos.
**Se puede hacer en paralelo con la fase 3.**

**Estimación:** dos sesiones.

---

## El modelo de persistencia, resumido

```
data/
  users.csv     el header y las filas
  users.json    tipo y restricciones por columna
  posts.csv
  posts.json
```

- **existencia de tabla** = hay un `.csv` (decisión 2)
- **esquema** = su `.json` hermano (decisión 3)
- `CREATE` escribe dos archivos, `DROP` borra dos, `ALTER` toca dos
- sin archivo global, no hay nada que se pueda quedar viejo

---

## Ticket 2.1 · `Constraint`

- **Estado**: pendiente
- **Depende de**: 1.2

**Archivos:**

- `catalog/Constraint.kt` (NUEVO)
- `app/src/test/.../RestriccionTest.kt` (NUEVO)

**Qué es esto, en simple:** las siete cosas que se le pueden exigir a una columna.
Cinco son un sí o un no; dos cargan datos: `FOREIGN KEY` guarda a qué tabla y
columna apunta, y `DEFAULT` guarda el valor.

### Diseño

```kotlin
@Serializable
sealed interface Constraint

@Serializable @SerialName("PRIMARY_KEY")
data object PrimaryKey : Constraint

@Serializable @SerialName("NOT_NULL")
data object NotNull : Constraint

@Serializable @SerialName("NULL")
data object Nullable : Constraint

@Serializable @SerialName("UNIQUE")
data object Unique : Constraint

@Serializable @SerialName("AUTOINCREMENT")
data object AutoIncrement : Constraint

@Serializable @SerialName("FOREIGN_KEY")
data class ForeignKey(val table: String, val column: String) : Constraint

@Serializable @SerialName("DEFAULT")
data class Default(val value: String) : Constraint
```

### Decisión · jerarquía sellada y no `enum`

Con un `enum` más campos opcionales, `Constraint(NOT_NULL, tabla = "users")`
compilaría y no significa nada. Con la jerarquía sellada los estados imposibles no
existen, y el `when` avisa si se olvida un caso.

Se pierde poder escribir `Regla.PRIMARY_KEY` como valor suelto, que era la idea
original. A cambio se gana que el compilador verifique la forma, y preguntar por
una regla concreta queda igual de corto:

```kotlin
val esPk = column.constraints.any { it is PrimaryKey }
```

### Decisión · `Default` guarda texto, no `Value`

`Value` incluye `BigDecimal`, `LocalDate` y `LocalTime`, que no tienen
serializador de `kotlinx.serialization` sin escribirlo. Guardar el texto y
decodificarlo con `ValueCodec.decodificar(texto, columna.tipo)` al cargar el
catálogo evita esos tres serializadores y además hace el JSON legible: en el
archivo se ve `"value": "2026-01-01"` y no una marca de tiempo.

El costo es que un `DEFAULT` mal escrito se detecta al cargar el catálogo y no al
deserializar. Eso es aceptable porque el mensaje sale igual, y sale mejor: dice
qué columna y qué tipo esperaba.

**Aceptación:**

- `Json.encodeToString<Constraint>(NotNull)` da `{"constraint":"NOT_NULL"}`
- `Json.encodeToString<Constraint>(ForeignKey("users","id"))` da
  `{"constraint":"FOREIGN_KEY","table":"users","column":"id"}`
- ida y vuelta para las siete

---

## Ticket 2.2 · `Catalog`, `Table` y `Column`

- **Estado**: pendiente
- **Depende de**: 2.1

**Archivos:**

- `catalog/Catalog.kt` (NUEVO)
- `app/src/test/.../CatalogTest.kt` (NUEVO)

### Diseño

```kotlin
class Catalog(val tables: Map<String, Table>) {
    fun table(name: String): Table? = tables[name]
}

class Table(
    val name: String,
    val columns: List<Column>       // LISTA: el orden es el del header del CSV
) {
    fun column(name: String): Column? = columns.firstOrNull { it.name == name }
    fun indexOf(name: String): Int = columns.indexOfFirst { it.name == name }

    val primaryKey: Column? get() = columns.firstOrNull { it.tiene<PrimaryKey>() }
}

class Column(
    val name: String,
    val type: Type,
    val constraints: List<Constraint>
) {
    inline fun <reified R : Constraint> tiene(): Boolean = constraints.any { it is R }

    // NOT NULL explicito, o implicito por ser clave primaria.
    val nullable: Boolean get() = !tiene<NotNull>() && !tiene<PrimaryKey>()

    val reference: ForeignKey? get() = constraints.filterIsInstance<ForeignKey>().firstOrNull()
}
```

**Por qué `columns` es lista y no mapa:** el orden importa dos veces. Es el orden
del header del CSV, y es el que usa `INSERT INTO users VALUES (...)` cuando no se
nombran las columnas. Un mapa lo perdería, o lo escondería en un `LinkedHashMap`
que no dice en su tipo que el orden es significativo.

**Por qué `PRIMARY KEY` implica `NOT NULL`:** es la regla de SQL, y ponerla aquí
evita que cada llamador tenga que acordarse.

**Aceptación:**

- `indexOf` devuelve `-1` para una columna que no existe y no lanza
- una columna con `PrimaryKey` tiene `nullable == false` sin llevar `NotNull`

---

## Ticket 2.3 · Serialización del esquema

- **Estado**: pendiente
- **Depende de**: 2.2

**Archivos:**

- `catalog/SchemaJson.kt` (NUEVO)
- `app/src/test/.../SchemaJsonTest.kt` (NUEVO)

### El formato

```json
{
  "columns": {
    "id": {
      "type": "INT",
      "constraints": [{ "constraint": "PRIMARY_KEY" }, { "constraint": "AUTOINCREMENT" }]
    },
    "name": {
      "type": "VARCHAR(80)",
      "constraints": [{ "constraint": "NOT_NULL" }]
    },
    "uid": {
      "type": "INT",
      "constraints": [
        { "constraint": "NOT_NULL" },
        { "constraint": "FOREIGN_KEY", "table": "users", "column": "id" }
      ]
    },
    "active": {
      "type": "BOOLEAN",
      "constraints": [{ "constraint": "DEFAULT", "value": "true" }]
    }
  }
}
```

### Diseño

```kotlin
private val json = Json {
    classDiscriminator = "constraint"     // el campo que distingue cada Constraint
    prettyPrint = true               // el punto de elegir JSON es leerlo a ojo
    encodeDefaults = true
}
```

`columns` es un **objeto y no un arreglo** a propósito: el orden ya lo manda el
header del CSV (decisión 3), así que repetirlo aquí sería una segunda fuente de
verdad para lo mismo. El JSON anota columnas por nombre.

El tipo va como **texto**, con la misma sintaxis que en el `CREATE TABLE`. Se
parsea con una función corta en este mismo archivo, `typeFromText`, que es la
inversa de `Type.name`. Así el JSON se lee igual que el SQL que lo creó.

**Por qué no un serializador de `Type`:** tendría que inventar una forma JSON para
los tres tipos con parámetros, y el resultado sería `{"type":"DECIMAL","precision":10,"scale":2}`,
más ruidoso y menos parecido al SQL.

**Aceptación:**

- ida y vuelta de un esquema con los 11 tipos y las 7 restricciones
- `typeFromText("DECIMAL(10,2)")` da `DecimalType(10, 2)`
- `typeFromText("VARCHAR(80)")` da `VarcharType(80)`
- `typeFromText("DECIMAL")` sin paréntesis devuelve `null`, no revienta
- el archivo escrito tiene saltos de línea y sangría

---

## Ticket 2.4 · Lector y escritor de CSV

- **Estado**: pendiente
- **Depende de**: 1.3

**Archivos:**

- `storage/CsvReader.kt` (NUEVO)
- `storage/CsvWriter.kt` (NUEVO)
- `app/src/test/.../CsvTest.kt` (NUEVO)

**Qué es esto, en simple:** leer y escribir el archivo de filas. Suena trivial y
no lo es, porque un campo puede contener comas, comillas y saltos de línea.

### Las reglas del formato

| Regla | Por qué |
|---|---|
| Header con los nombres, en orden de declaración | es lo que amarra el CSV con el JSON |
| Comillas si el campo trae coma, comilla o salto | RFC 4180, lo que entiende Excel |
| Comilla interna se duplica | `"dijo ""hola"""` |
| **Campo vacío sin comillas = `NULL`** | |
| **`""` = cadena vacía** | |

Esa última distinción es la única sutileza del formato, y hace falta: sin ella,
una columna de carácter no puede diferenciar *no hay dato* de *el dato es la
cadena vacía*.

```
id,name,edad
1,Ana,23
2,"Perez, Luis",
3,"",31
```

La fila 2 no tiene edad. La fila 3 tiene nombre, y es la cadena vacía.

### Diseño

```kotlin
object CsvReader {
    // Devuelve el header y las filas como texto crudo. NO convierte a Value:
    // eso necesita el tipo de cada columna, que viene del catalogo.
    fun read(archivo: File): CsvContent

    // null en la lista significa campo vacio, es decir NULL.
    class CsvContent(val header: List<String>, val rows: List<List<String?>>)
}

object CsvWriter {
    fun write(archivo: File, header: List<String>, rows: List<List<String?>>)
}
```

**Por qué el lector no convierte a `Value`:** separar la mecánica del formato de
la interpretación de los datos. El lector no necesita el catálogo, así que se
prueba solo y sirve igual si algún día se lee un CSV de una tabla desconocida.

**Sobre la escritura atómica:** `write` escribe a un archivo temporal en el
mismo directorio y lo renombra encima al final. Un `ALTER` sobre una tabla grande
que se interrumpa a la mitad dejaría el CSV truncado; con el renombre, o está el
archivo viejo completo o el nuevo completo.

**Aceptación:**

- ida y vuelta con comas, comillas dobles y saltos de línea dentro de campos
- un campo vacío se lee como `null` y uno con `""` como `""`
- escribir y volver a leer da exactamente lo mismo
- un archivo con solo header da lista de filas vacía, no error
- si el proceso muere a media escritura, el archivo original queda intacto

---

## Ticket 2.5 · `CatalogLoader`

- **Estado**: pendiente
- **Depende de**: 2.3, 2.4

**Archivos:**

- `catalog/CatalogLoader.kt` (NUEVO)
- `app/src/test/.../CatalogLoaderTest.kt` (NUEVO)

### Qué hace

```kotlin
class CatalogLoader(private val diagnostics: Diagnostics) {

    // Lee TODOS los .json del directorio. NO abre ningun .csv:
    // esquema ansioso, datos perezosos.
    fun cargar(): Catalog
}
```

Recorre `DataDirectory.tablas()`, que lista los `.csv`, y para cada nombre busca
su `.json`. Reporta a `Diagnostics`, sin cortar, estos casos:

| Situación | Mensaje |
|---|---|
| `users.csv` sin `users.json` | `users.csv no tiene esquema` |
| `users.json` sin `users.csv` | `users.json no tiene datos` |
| JSON mal formado | `el esquema de users no se pudo leer` |
| Tipo desconocido en el JSON | `tipo 'VARCHA(80)' no reconocido en users.name` |
| `DEFAULT` que no cuadra con el tipo | `el valor por omision de users.edad no es INT` |
| FK que apunta a una tabla que no existe | `users.uid referencia la tabla 'posts', que no existe` |

**Las FK se validan en una segunda vuelta**, cuando ya se cargaron todas las
tablas, porque `posts` puede referenciar a `users` y al revés.

**Por qué reporta sin cortar:** el principio de que todos los errores salgan
juntos. Si el catálogo tiene tres tablas rotas, se quieren ver las tres, no una
por corrida.

**El chequeo estructural NO va aquí**, va en el semántico, porque necesita abrir
el CSV para leer el header y esta etapa no abre CSV. Ver ticket 4.2.

**Aceptación:**

- directorio vacío da `Catalog` vacío sin errores
- cada caso de la tabla de arriba tiene su test
- un ciclo de FK entre dos tablas carga bien y no cuelga
- cargar 3 tablas no abre ningún `.csv`, verificable con archivos inexistentes

---

## Ticket 2.6 · Escritura del catálogo y `addConstraint`

- **Estado**: pendiente
- **Depende de**: 2.5

**Archivos:**

- `catalog/CatalogWriter.kt` (NUEVO)
- `catalog/CatalogExtensions.kt` (NUEVO)
- `app/src/test/.../CatalogWriterTest.kt` (NUEVO)

### La función de extensión

```kotlin
// Devuelve un catalogo NUEVO con la restriccion agregada.
fun Catalog.addConstraint(table: String, column: String, constraint: Constraint): Catalog

fun Catalog.addColumn(table: String, column: Column): Catalog
fun Catalog.dropColumn(table: String, column: String): Catalog
fun Catalog.addTable(table: Table): Catalog
fun Catalog.dropTable(name: String): Catalog
```

### Decisión · devuelve catálogo nuevo, no muta

El volcado de la fase 7 necesita saber **qué archivos cambiaron**, para no
reescribir tablas que nadie tocó. Con catálogos inmutables eso es comparar el
original contra el final. Con mutación habría que llevar una lista de tablas
sucias por fuera, que es estado paralelo que se puede desincronizar.

El costo es copiar el mapa de tablas en cada cambio. Con decenas de tablas y unos
pocos `ALTER` por script, no es medible.

### El escritor

```kotlin
object CatalogWriter {
    // Escribe SOLO las tablas cuyo esquema cambio.
    fun flush(original: Catalog, final: Catalog)
}
```

Compara tabla por tabla y para cada diferencia escribe el `.json`, borra el par de
archivos si la tabla desapareció, o crea ambos si es nueva. Usa la escritura
atómica de 2.4.

**Aceptación:**

- `addConstraint` sobre una tabla inexistente devuelve el catálogo sin cambios
- el catálogo original no se modifica al llamar cualquiera de las extensiones
- `volcar(c, c)` no escribe ningún archivo
- una prueba de ida y vuelta completa: escribir a mano un `users.csv` y un
  `users.json`, cargarlos y volverlos a escribir, y comparar byte por byte. Eso
  prueba de una sola vez el formato, el escapado, los nulos y las fechas
