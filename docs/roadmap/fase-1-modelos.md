# Fase 1 — Modelos congelados

**Objetivo de la fase:** definir las **cuatro estructuras de datos** sobre las que
se construye todo lo demás, y congelarlas. Sin lógica: solo datos y operaciones
triviales.

**Por qué esta fase es la más importante del proyecto:** todo lo que viene después
—recolectar declaraciones, verificar tipos, analizar flujo, ejecutar, dibujar en la
GUI— **lee y escribe estas cuatro estructuras**. Si cambian a mitad del camino, los
tres integrantes tienen que rehacer trabajo al mismo tiempo. Si quedan bien
definidas el primer día, los tres pueden avanzar en paralelo sin bloquearse, que
es exactamente lo que el enunciado exige al pedir commits individuales.

**Regla de la fase:** nadie escribe lógica de análisis hasta que estos archivos
estén revisados por los tres y mergeados a `main`.

**Estimación:** una o dos sesiones. Es escribir estructuras, no resolver problemas.

**Reparto sugerido:** 1.1 + 1.2 uno, 1.3 otro, 1.4 + 1.5 el tercero. Se revisan
entre todos y se mergean juntos.

---

## Las cuatro estructuras, en una frase cada una

| Estructura | Qué representa | Analogía |
|---|---|---|
| `Type` | Un tipo de dato | *"esto es un entero"*, *"esto es un arreglo de textos"* |
| `Symbol` | Un nombre declarado, con todo lo que se sabe de él | La ficha de una variable: cómo se llama, de qué tipo es, dónde se declaró |
| `Scope` | Un ámbito: el conjunto de nombres visibles en un lugar del programa | Un cajón de fichas. Los cajones se anidan |
| AST | El programa, como árbol limpio | El plano del programa, sin paréntesis ni puntos y comas |

---

## Ticket 1.1 — `Type`

- **Estado**: completado
- **Depende de**: 0.6

**Archivos:**

- `frontend/semantic/symbols/Type.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/TypeTest.kt` (NUEVO)

**Qué es esto, en simple:** un "tipo" es la respuesta a la pregunta *¿qué clase de
valor es esto?* En Compiscript hay tipos **simples** (`integer`, `float`,
`string`, `boolean`) y tipos **compuestos**, que se construyen a partir de otros:
un arreglo de enteros (`integer[]`) es un tipo hecho con el tipo `integer` adentro.
Por eso se modela como una jerarquía: los simples son constantes únicas, los
compuestos guardan sus partes.

### Diseño

```kotlin
// Un tipo de dato de Compiscript.
//
// Sellado: la lista de tipos posibles es cerrada y conocida, así que el compilador
// de Kotlin puede avisar si un `when` se olvida de alguno.
sealed interface Type {
    val name: String        // cómo se escribe en un mensaje de error
}

// ── Tipos simples ────────────────────────────────────────────────────────
// No tienen partes: son constantes únicas del lenguaje. Por eso son `data object`
// y no `data class`: existe UNA sola instancia de cada uno en todo el programa.

data object IntegerType : Type { override val name = "integer" }
data object FloatType   : Type { override val name = "float" }
data object StringType  : Type { override val name = "string" }
data object BooleanType : Type { override val name = "boolean" }

// ── Tipos especiales del compilador ──────────────────────────────────────
// No se escriben en el código fuente; existen solo dentro del análisis.

// El "tipo" de una función que no devuelve nada.
data object VoidType : Type { override val name = "void" }

// El tipo del literal `null`. Compatible con cualquier tipo de clase o de arreglo,
// pero NO con integer / float / string / boolean.
data object NullType : Type { override val name = "null" }

// Tipo comodín que se devuelve cuando YA se reportó un error.
//
// Su razón de ser es CORTAR CASCADAS: si `1 + "a"` falla y devuelve ErrorType,
// entonces `(1 + "a") * 2` NO reporta un segundo error, porque ErrorType absorbe
// todo en silencio. El usuario ve UN error por equivocación, no una lluvia.
data object ErrorType : Type { override val name = "<error>" }

// ── Tipos compuestos ─────────────────────────────────────────────────────
// Se construyen a partir de otros tipos.

// integer[]   es ArrayType(IntegerType)
// integer[][] es ArrayType(ArrayType(IntegerType))
data class ArrayType(val element: Type) : Type {
    override val name = "${element.name}[]"
}

// El tipo de una instancia de clase. Guarda SOLO el nombre de la clase.
// Ver la decisión 2 abajo: es lo que hace que Perro y Gato sean tipos distintos
// aunque tengan los mismos campos.
data class ClassType(val className: String) : Type {
    override val name = className
}

// El tipo de una función: los tipos de sus parámetros en orden, y su retorno.
//   (integer, integer) -> integer
data class FunctionType(
    val parameters: List<Type>,
    val returns: Type
) : Type {
    override val name =
        "(${parameters.joinToString(", ") { it.name }}) -> ${returns.name}"
}
```

### Decisión 1 — comparar tipos con el `==` de Kotlin, sin canonicalización

Kotlin ya da lo necesario gratis:

```kotlin
ArrayType(IntegerType) == ArrayType(IntegerType)    // true, sin hacer nada
ClassType("Perro") == ClassType("Gato")             // false, sin hacer nada
```

Los `data class` de Kotlin generan `equals` comparando sus campos, recursivamente.
Y en Compiscript la profundidad máxima real es 2 (`integer[][]`), así que
"recursivo" son dos pasos. **Comparar tipos es literalmente `a == b`.**

Un mecanismo de canonicalización (que `integer[]` construido dos veces devolviera
el mismo objeto, para comparar por número de id) agregaría una clase, una caché y
un riesgo de desincronización para ahorrar nanosegundos. Es sobre-ingeniería.

### Decisión 2 — `ClassType` guarda solo el nombre: tipado nominal y sin ciclos

La tentación natural es que `ClassType` guarde el `Scope` de la clase, para poder
resolver `perro.nombre` directamente. **No hacerlo.** Dos problemas: crearía una
referencia circular (`Type` → `Scope` → `Symbol` → `Type`), y rompería el `equals`
del `data class` (el `Scope` tiene hijos mutables).

Guardando solo el nombre se obtienen tres cosas de una:

1. **Tipado nominal.** `ClassType("Perro") != ClassType("Gato")` aunque tengan
   campos idénticos, porque se llaman distinto.
2. **Sin ciclos.** `Type` no conoce a `Scope`.
3. Cuando se necesiten los miembros de la clase, se buscan:
   `globalScope.lookup("Perro")?.memberScope`. Un paso más, cero acoplamiento.

#### Nominal vs estructural, para tenerlo claro

La pregunta es: **¿cuándo dos tipos son "el mismo tipo"?** Hay dos respuestas
posibles.

- **Nominal**: son el mismo tipo si tienen el **mismo nombre**.
- **Estructural**: son el mismo tipo si tienen la **misma forma**.

```cps
class Perro { let nombre: string; }
class Gato  { let nombre: string; }

let p: Perro = new Perro();
let g: Gato = p;              // ¿error o no?
```

`Perro` y `Gato` tienen estructura idéntica. Con tipado **nominal**, esa asignación
es **error**: se llaman distinto, son tipos distintos. Con tipado **estructural**
sería legal.

Compiscript es **nominal**, y la pista está en la sintaxis: te obliga a
**declarar** la relación de subtipo (`class Perro : Animal`). Un lenguaje
estructural no la hace declarar — la deduce de la forma. Si el lenguaje pide
declararla, es nominal. Y además es mucho más simple de implementar: comparar dos
`ClassType` es comparar dos strings.

**Los arreglos no tienen opción: son estructurales por necesidad.** `integer[]` no
tiene nombre. Si en la línea 3 se escribe `let a: integer[]` y en la línea 40
`let b: integer[]`, esos dos tipos **tienen que ser el mismo**, y la única forma de
saberlo es comparar el tipo del elemento. El `equals` recursivo del `data class` lo
hace solo.

### Decisión 3 — `Type` es solo datos; las reglas viven en la Fase 4

Nada de `isNumeric`, `isAssignableFrom` ni `widen` en este archivo. No tiene
lógica: es la definición del vocabulario. Las reglas de tipo (qué se puede sumar
con qué, qué se puede asignar a qué) van en `TypeRules.kt` en la Fase 4, junto a su
documento de reglas de inferencia. Separar el vocabulario de las reglas es lo que
permite que el documento de reglas se lea solo.

### Decisión 4 — no hay tabla de tipos numerada

Las notas de clase piden una *"tabla de tipos"* donde cada tipo tiene un número, y
que la tabla de símbolos guarde **ese número** en vez del tipo completo. La idea era
comparar enteros en vez de estructuras.

Se evaluó y **se descartó**. Tres razones:

**1. Un `Int` solo contesta una pregunta, y no es la que más se hace.**

El verificador de tipos pregunta mucho más que *"¿son iguales?"*:

```kotlin
TypeRules.isNumeric(type)                    // ¿es integer o float?
if (targetType !is ArrayType) error(...)     // ¿es un arreglo?
return targetType.element                    // ¿arreglo DE QUÉ?
checkArguments(calleeType.parameters, ...)   // ¿cuáles son sus parámetros?
classScopeOf(targetType.className)           // ¿de qué clase?
```

Ninguna de esas la puede contestar un número. El `3` no sabe que es un arreglo ni de
qué. Con ids, cada uso obligaría a des-resolver el número de vuelta a un `Type`, y
además con un `!!` en cada sitio porque la búsqueda devolvería `Type?`.

**2. Comparar tipos ya es tan barato como comparar enteros.**

Los primitivos son `data object`, o sea **singletons**: existe una sola instancia de
cada uno. Entonces `IntegerType == IntegerType` es una comparación de **referencias**
— literalmente igual de rápida que comparar dos enteros. Y los compuestos tienen
profundidad máxima 2 en Compiscript (`integer[][]`).

| Comparación | Qué hace | Costo |
|---|---|---|
| `IntegerType == IntegerType` | comparar referencias | = comparar 2 ints |
| `ClassType("Perro") == ClassType("Perro")` | comparar 1 string | ~igual |
| `ArrayType(IntegerType) == ...` | 1 nivel + referencias | 2 pasos |

El objetivo de *"comparar enteros y no estructuras"* **ya está cumplido** sin ids.

**3. En Kotlin, `val type: Type` ya ES la referencia que piden las notas.**

La idea del índice viene del contexto histórico: una fila de la tabla de símbolos era
un registro de **ancho fijo** en memoria o disco, y no cabía una estructura de tamaño
variable, así que se guardaba un índice a otra tabla. En Kotlin, `type: Type` es una
referencia de 8 bytes al objeto compartido — la misma idea, manejada por el lenguaje y
con seguridad de tipos.

**Consecuencia:** `Symbol` guarda `type: Type` directamente y no hay archivo
`TypeTable.kt`. Si en la defensa preguntan por la tabla de tipos, la respuesta es:
*"el `Symbol` guarda una referencia al tipo, que es el equivalente del índice; el
número entero solo tendría sentido en generación de código, donde se vuelve un índice
real de memoria."*

### Aceptación

- `ArrayType(IntegerType) == ArrayType(IntegerType)` es `true`. ✅
- `ClassType("Perro") == ClassType("Gato")` es `false`. ✅
- `ArrayType(ArrayType(IntegerType)).name` es `"integer[][]"`. ✅
- `FunctionType(listOf(IntegerType, IntegerType), IntegerType).name` es
  `"(integer, integer) -> integer"`. ✅
- `IntegerType === IntegerType` (identidad de referencia): prueba de que los
  primitivos son singletons y de que compararlos no recorre nada. ✅
- Un `when (type)` sin rama `else` compila (prueba de que el `sealed` está
  completo). ✅ *El test `categoria(type)` lo verifica: si se agrega un `Type` nuevo y
  no se cubre ahí, el test no compila.*
- `Type.kt` no importa `Scope` ni `Symbol` (prueba de que no hay ciclo). ✅
  *El archivo no tiene ningún `import`.*
- `./gradlew build` en verde, 8 tests en `TypeTest`. ✅

### Respaldo

Enunciado, *"Sistema de Tipos"*. Dragon Book §6.3.1 (expresiones de tipo) y §6.3.2
(equivalencia de tipos).

---

## Ticket 1.2 — `Symbol` y `DeclarationKind`

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `frontend/semantic/symbols/Symbol.kt` (NUEVO)
- `frontend/semantic/symbols/DeclarationKind.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/SymbolTest.kt` (NUEVO)

**Qué es esto, en simple:** cada vez que el programa **declara un nombre**
(`let x`, `function saludar`, `class Animal`), el compilador crea una ficha con
todo lo que sabe de ese nombre. Esa ficha es un `Symbol`, y es la unidad de
información de la tabla de símbolos.

### `DeclarationKind`

```kotlin
// Qué clase de declaración es este nombre. Determina qué reglas se le aplican.
enum class DeclarationKind {
    VARIABLE,     // let x   /   var x
    CONSTANT,     // const PI    -> no se puede reasignar
    PARAMETER,    // el `nombre` de function saludar(nombre: string)
    FUNCTION,     // function saludar()
    CLASS         // class Animal
}
```

**Por qué no hay `FIELD` ni `METHOD`.** La tentación es agregarlos para distinguir
un `let` suelto de un `let` dentro de una clase. Pero eso mezcla **dos ejes
distintos** en un solo enum:

| | Suelto | Dentro de una clase |
|---|---|---|
| variable | `VARIABLE` | `FIELD` |
| función | `FUNCTION` | `METHOD` |
| constante | `CONSTANT` | ¿`CONSTANT_FIELD`? |

Esa última fila delata el problema: `const PI = 314;` dentro de una clase no tendría
categoría clara. Y agregar `CONSTANT_FIELD` empeora las cosas, porque la regla más
importante que usa esto —*"no se puede reasignar una constante"*, que es requisito
del enunciado— se partiría en dos:

```kotlin
if (kind == CONSTANT || kind == CONSTANT_FIELD) { ... }   // una pregunta, dos comparaciones
```

Dos ejes metidos en un enum crecen **multiplicándose**. La solución es separarlos:
el enum lleva **qué es**, y un booleano lleva **dónde vive**.

### `isMember`: el segundo eje, en su propio campo

```kotlin
val isMember: Boolean    // ¿fue declarado dentro de una clase?
```

Y no se pasa a mano: lo pone `Scope.declare`, en el mismo lugar donde ya asigna el
offset (ver ticket 1.3). El ámbito que declara ya sabe si es una clase, así que es
imposible que quede mal.

| Escrito | `kind` | `isMember` |
|---|---|---|
| `let x: integer;` suelto | `VARIABLE` | `false` |
| `let nombre: string;` en clase | `VARIABLE` | `true` |
| `const PI = 314;` suelto | `CONSTANT` | `false` |
| `const PI = 314;` en clase | `CONSTANT` | `true` |
| `function f()` suelta | `FUNCTION` | `false` |
| `function hablar()` en clase | `FUNCTION` | `true` |

Cada regla pregunta una sola cosa:

```kotlin
if (symbol.kind == CONSTANT) -> no reasignable          // sin importar dónde viva
if (symbol.isMember)         -> alcanzable con `this.`  // sin importar qué sea
```

La GUI combina los dos para la columna "Categoría" (`"Campo"`, `"Método"`,
`"Constante de clase"`), pero eso es **texto de presentación**, no estado que se
pueda contradecir.

### `Symbol`

```kotlin
// La ficha de un nombre declarado.
//
// Los campos están en dos grupos:
//   - IDENTIDAD (`val`): lo que se sabe al declararlo. No cambia.
//   - ANÁLISIS  (`var`): lo que se descubre después, recorriendo el programa.
data class Symbol(
    // ── Identidad ─────────────────────────────────────────────────────
    val name: String,
    val kind: DeclarationKind,
    val type: Type,
    val location: LexemeLocation,      // línea y columna de la DECLARACIÓN
    val scopeName: String,             // en qué ámbito vive; para mostrar en la GUI

    // ¿Fue declarado dentro de una clase? Lo pone `Scope.declare` automáticamente,
    // igual que el offset: el ámbito que declara ya sabe si es una clase.
    // Ver "isMember: el segundo eje" arriba.
    val isMember: Boolean = false,

    // Posición dentro de su ámbito. Un contador simple: el primer símbolo del
    // ámbito tiene offset 0, el siguiente 1, y así.
    //
    // Es un índice de RANURA, no de bytes. Convertirlo a bytes exige comprometerse
    // con tamaños y alineación del procesador destino, que no se conoce todavía; es
    // una conversión mecánica cuando se sepa.
    //
    // Este proyecto no lo exige, pero uno de los objetivos generales del enunciado
    // es "diseñar una tabla de símbolos capaz de sostener las fases posteriores
    // del compilador", y la generación de código necesita exactamente esto.
    val offset: Int,

    // Cuántos ámbitos de FUNCIÓN había encima cuando se declaró este símbolo.
    // Lo pone `Scope.declare`, igual que offset e isMember.
    //
    // Sirve para detectar captura sin un segundo recorrido: si un nombre se usa a
    // una profundidad de función MAYOR que ésta, lo está usando una función
    // anidada. Ver `usedInNestedFunction` abajo y el ticket 5.2.
    val declarationFunctionDepth: Int = 0,

    // Solo para kind == CLASS: el ámbito con los campos y métodos de la clase.
    // Es cómo se resuelve `perro.nombre`.
    val memberScope: Scope? = null,

    // ── Análisis ──────────────────────────────────────────────────────

    // ¿Ya se le dio un valor? Una `const` debe estar inicializada en su
    // declaración (regla del enunciado). Una `let` sin inicializador arranca en
    // false y se marca true en su primera asignación.
    var initialized: Boolean = false,

    // El valor de una CONSTANTE, si se conoce en compilación.
    //
    // Solo se llena para kind == CONSTANT. Una variable mutable NO lo guarda: su
    // valor puede depender de un `if` o de un bucle, y propagarlo daría respuestas
    // falsas. Una `const` no se puede reasignar, así que su valor es el mismo en
    // todo el programa. Ver `checkIdentifier` en el ticket 4.2.
    var constantValue: Any? = null,

    // Cuántas veces se LEE este nombre. Alimenta el reporte del recolector de
    // basura (Fase 5): un símbolo con useCount 0 nunca se usa.
    //
    // Lo incrementa la Fase 4 (checkIdentifier y checkPropertyAccess), y SOLO ella:
    // la Fase 5 ya no recorre el AST, solo formatea lo que está aquí.
    var useCount: Int = 0,

    // La última línea donde se usa. Después de esa línea su valor ya no importa:
    // es el dato que un recolector de basura necesitaría para liberarlo.
    var lastUseLine: Int? = null,

    // ¿Este nombre se usa dentro de una función anidada?
    //
    // Lo pone la Fase 4 comparando `currentScope.functionDepth()` contra
    // `declarationFunctionDepth`: si el uso está más adentro, hay captura.
    //
    // Es la OBSERVACIÓN, no una conclusión sobre su vida. En Compiscript una
    // función anidada no puede sobrevivir a la de afuera (no hay forma de
    // devolverla ni de guardarla), así que esto NO obliga a mover nada al montón.
    // Es el dato que un recolector de basura necesitaría si el lenguaje algún día
    // permitiera que una función escape de su ámbito. Ver ticket 5.2.
    var usedInNestedFunction: Boolean = false
)
```

### Nota sobre `data class` con campos `var`

Mezclarlos es intencional y seguro aquí. Los `Symbol` **no se comparan por valor**
en ninguna parte del compilador: se buscan por nombre en un ámbito. El `data class`
se usa por su `toString()` generado, que hace que depurar sea mucho más fácil.

Y aunque `Symbol` referencia un `Scope` (que a su vez contiene `Symbol`s), no hay
riesgo de recursión infinita en `equals`, porque `Scope` es una clase normal y su
`equals` compara por identidad. Es el único riesgo real del diseño, y hay un test
para él.

### Simplificación importante: NO hay sobrecarga de funciones

El enunciado pide *"detección de redeclaración de funciones con el mismo nombre"*
— o sea, dos funciones con el mismo nombre es **error**, punto. Eso significa que
un ámbito guarda **un símbolo por nombre**: `Map<String, Symbol>`, no
`Map<String, List<Symbol>>`.

Esto elimina un bloque entero de complejidad: no hay que filtrar candidatos por
aridad, ni elegir "el más específico", ni resolver empates. Una clase tiene **un**
constructor. Una llamada resuelve a **un** símbolo o a ninguno. Es una
simplificación real respecto a lenguajes como Java, y viene del enunciado, no de
una decisión del equipo.

### Aceptación

- Se construye un `Symbol` de cada `DeclarationKind` y se imprime legible.
- `isMember` arranca en `false` y solo lo cambia `Scope.declare`.
- Un `Symbol` de `kind = CLASS` puede llevar `memberScope`; los demás lo dejan en
  `null`.
- `initialized`, `useCount`, `lastUseLine` y `usedInNestedFunction` son mutables desde
  fuera.
- Comparar dos `Symbol` cuyos `memberScope` se referencian mutuamente **no** entra
  en recursión infinita (test explícito).

### Respaldo

Enunciado, *"Tabla de Símbolos: estructura que acompaña todas las fases"*. Dragon
Book §2.7. Notas de clase: *"atributos a guardar: lexema, tipo de dato, alcance,
posición de memoria, número de línea y posición… offsets, pointers"*.

---

## Ticket 1.3 — `Scope` y `ScopeKind`

- **Estado**: pendiente
- **Depende de**: 1.2

**Archivos:**

- `frontend/semantic/symbols/Scope.kt` (NUEVO)
- `frontend/semantic/symbols/ScopeKind.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/ScopeTest.kt` (NUEVO)

**Qué es esto, en simple:** un **ámbito** es *"el conjunto de nombres que puedo
usar aquí"*. Cuando se abre un `{ }`, se entra a un ámbito nuevo: lo que se declare
ahí adentro deja de existir al salir. Y desde adentro se ve lo de afuera, pero no
al revés.

La imagen que sirve: cada ámbito es un **cajón de fichas**, y los cajones están
metidos uno dentro de otro. Para buscar un nombre se abre el cajón propio; si no
está, el de afuera; y así hasta el más externo. Si no está en ninguno: *"variable
no declarada"*.

Esto es literalmente la **"pila de tablas"** de las notas de clase, y la estructura
de **hash de hashes**: cada cajón es un hash (nombre → ficha), y los cajones
anidados forman la estructura que los contiene.

### `ScopeKind`

```kotlin
// Qué clase de ámbito es. La etiqueta importa porque hay reglas que dependen de
// DÓNDE se está parado.
enum class ScopeKind {
    GLOBAL,     // la raíz: el programa entero
    CLASS,      // dentro de class Animal { ... }
    FUNCTION,   // dentro de function saludar(...) { ... }
    BLOCK,      // un { ... } suelto, o el cuerpo de un if
    LOOP        // el cuerpo de while / do-while / for / foreach
}
```

**Para qué sirve la etiqueta**, con las tres reglas del enunciado que la necesitan:

| Regla del enunciado | Cómo se contesta |
|---|---|
| *"`return` solo permitido dentro del cuerpo de una función"* | subir por `parent` buscando un `FUNCTION` |
| *"`break` y `continue` solo permitidos dentro de bucles"* | subir por `parent` buscando un `LOOP` |
| *"manejo correcto de `this` dentro del ámbito de la clase"* | subir por `parent` buscando un `CLASS` |

Y esa "subida" es una función de dos líneas. Sin `ScopeKind` no habría forma de
escribir esas condiciones.

### `Scope`

```kotlin
// Un ámbito: los nombres visibles en un lugar del programa.
//
// Es una clase normal (no data class) a propósito: un ámbito tiene IDENTIDAD. Dos
// ámbitos distintos con los mismos símbolos siguen siendo dos ámbitos distintos.
class Scope(
    val kind: ScopeKind,
    // Etiqueta legible. Las clases y funciones traen su nombre del código fuente;
    // los ámbitos anónimos se nombran por la CONSTRUCCIÓN que los creó más su
    // línea: "if@4", "for@3", "case@10". Ver "Cómo se nombran los ámbitos" abajo.
    val name: String,
    val parent: Scope?                  // enlace hacia AFUERA (ámbito léxico)
) {
    // Enlace hacia ARRIBA (herencia). Es `var` con setter privado porque de qué
    // hereda una clase se sabe DESPUÉS de crear su ámbito: la Fase 3 registra
    // primero todos los nombres de clase y recién después resuelve la herencia.
    // `attachSuperclass` solo se puede llamar una vez.
    var superclass: Scope? = null
        private set

    fun attachSuperclass(scope: Scope) {
        require(superclass == null) { "La superclase de '$name' ya fue asignada" }
        superclass = scope
    }

    // El hash de ESTE nivel. Linked para conservar el orden de declaración:
    // importa para mostrar la tabla en la GUI y para asignar offsets.
    private val symbols = linkedMapOf<String, Symbol>()

    // Los hijos son lo que vuelve esto un ÁRBOL y no una pila que se descarta.
    // Ver la decisión de abajo.
    private val childScopes = mutableListOf<Scope>()
    val children: List<Scope> get() = childScopes

    private var nextOffset = 0

    // ── Crear un ámbito hijo ──────────────────────────────────────────
    // Crea el hijo, lo registra en la lista de hijos, y lo devuelve. Eso es todo:
    // no hay magia. El nombre "openChild" es de este proyecto, no de Kotlin.
    fun openChild(kind: ScopeKind, name: String): Scope {
        val child = Scope(kind, name, parent = this)
        childScopes.add(child)      // <-- por esto el hijo SOBREVIVE al salir
        return child
    }

    // ── Declarar ──────────────────────────────────────────────────────
    // Falla solo si el nombre ya existe en ESTE nivel. Tapar un nombre de un
    // ámbito exterior es legal (shadowing); redeclararlo en el mismo ámbito no.
    fun declare(symbol: Symbol): DeclareResult {
        val previous = symbols[symbol.name]
        if (previous != null) return DeclareResult.AlreadyDeclared(previous)

        // El ámbito completa los tres datos que solo él conoce:
        //   - offset:                    la posición dentro de este nivel
        //   - isMember:                  si este ámbito es una clase, es un miembro
        //   - declarationFunctionDepth:  cuántas funciones hay encima
        // Así ningún llamador puede equivocarse en ninguno de los tres.
        symbols[symbol.name] = symbol.copy(
            offset = nextOffset,
            isMember = (kind == ScopeKind.CLASS),
            declarationFunctionDepth = functionDepth()
        )
        nextOffset += 1
        return DeclareResult.Ok
    }

    // ── Buscar ────────────────────────────────────────────────────────

    // Busca un nombre visible desde aquí.
    //
    // El ORDEN es la regla del ámbito más anidado: gana la primera coincidencia,
    // empezando por lo más cercano. Los tres `return` son esa regla.
    fun lookup(name: String): Symbol? {
        // 1. ¿Está declarado en ESTE nivel?
        val local = symbols[name]
        if (local != null) return local

        // 2. ¿Lo hereda de una superclase?
        val inherited = superclass?.lookupMember(name)
        if (inherited != null) return inherited

        // 3. Buscar hacia afuera, en el ámbito que me contiene.
        return parent?.lookup(name)
    }

    // Solo ESTE nivel, sin subir a ningún lado. Se usa para detectar
    // redeclaración, y para buscar el `constructor` de una clase (que NO se
    // hereda: una clase sin constructor propio tiene el implícito de cero
    // parámetros, aunque su superclase tenga uno).
    fun lookupLocal(name: String): Symbol? = symbols[name]

    // Este nivel y la cadena de superclases, SIN salir hacia afuera.
    //
    // Es lo que necesita `perro.nombre`: un campo heredado de Animal sí, una
    // variable global llamada `nombre` no. Por eso no consulta `parent`.
    fun lookupMember(name: String): Symbol? =
        symbols[name] ?: superclass?.lookupMember(name)

    // ── Consultas sobre la posición en el árbol ───────────────────────

    // El ámbito de clase más cercano hacia afuera, o null si no hay ninguno.
    //
    // Lo usa `this` (Fase 4): `this` no dice de qué clase es, hay que subir hasta
    // la clase que lo contiene y su `name` es la respuesta. Si devuelve null,
    // `this` se está usando fuera de una clase, que es error.
    fun enclosingClass(): Scope? =
        if (kind == ScopeKind.CLASS) this else parent?.enclosingClass()

    // Cuántos ámbitos de FUNCIÓN hay entre aquí y la raíz. Las clases, bloques y
    // bucles no cuentan.
    //
    // Lo usa `checkIdentifier` (Fase 4): si un nombre se usa a una profundidad
    // de función mayor que la de su declaración, lo está usando una función
    // anidada. Que no cuenten los bloques es justo el punto: un uso dentro de un
    // `if` no cambia la profundidad, así que no dispara nada.
    fun functionDepth(): Int =
        (if (kind == ScopeKind.FUNCTION) 1 else 0) + (parent?.functionDepth() ?: 0)

    // NO hay `isInside(kind)` ni `enclosingFunction()`. Parecían necesarias al
    // diseñar y ninguna terminó usándose:
    //   - `break`/`continue`/`return` los valida el FlowAnalyzer (Fase 5), que
    //     recorre el AST y no tiene un cursor de ámbito: lleva dos contadores.
    //   - El tipo de retorno lo lleva el TypeChecker en un campo
    //     `currentReturnType`, porque el Scope no guarda el tipo de retorno de su
    //     función (vive en el Symbol, que está en el ámbito padre).

    // ── Para la GUI ───────────────────────────────────────────────────
    // Los símbolos de este nivel, en orden de declaración.
    fun localSymbols(): List<Symbol> = symbols.values.toList()
}

// El resultado de declarar.
//
// Es un sealed interface en vez de un booleano porque cuando falla se necesita el
// símbolo ANTERIOR, para poder decir "'x' ya fue declarado en la línea 3".
sealed interface DeclareResult {
    data object Ok : DeclareResult
    data class AlreadyDeclared(val previous: Symbol) : DeclareResult
}
```

### Decisión clave: el ámbito NO se destruye al salir. Es un árbol, no una pila

Cuando se termina de analizar un bloque, su `Scope` **se queda** colgado en
`parent.children`. Tres razones independientes, y las tres son requisitos, no
gustos:

1. **El enunciado lo exige como salida**: *"Estado de la tabla de símbolos por
   cada entorno (global, función, clase, bloque)."* Si se descarta el ámbito al
   salir, no hay qué mostrar en la GUI. **Son 25 puntos.**
2. **Herencia**: `class Perro : Animal` necesita el `Scope` de `Animal` ya cerrado
   para resolver `this.nombre`.
3. **Closures**: una función anidada captura variables de su ámbito de definición.
   Ese ámbito no puede morir.

Lo que **sí** se comporta como pila es el **cursor**: la variable `currentScope`
del recorrido. Sube y baja por el árbol, pero no destruye nada.

### La pila de tablas ya existe: es el call stack

Cuando la Fase 4 recorre el programa, cada función del recorrido mueve el cursor:

```kotlin
private fun checkFor(stmt: ForStatement) {
    currentScope = currentScope.openChild(ScopeKind.LOOP, "for@${stmt.location.line}")
    stmt.body.statements.forEach { checkStatement(it) }
    currentScope = currentScope.parent!!
}
```

Y el anidamiento se ve así, sobre este programa:

```cps
1  function procesar(datos: integer[]): integer {
2    let total: integer = 0;
3    for (let i: integer = 0; i < 3; i = i + 1) {
4      if (i > 1) {
5        total = total + i;
6      }
7    }
8    return total;
9  }
```

```
checkProgram                  currentScope = global
 └ checkFunctionDeclaration   currentScope = global > procesar
    └ checkFor                currentScope = global > procesar > for@3
       └ checkIf              currentScope = global > procesar > for@3 > if@4
```

Cuando se está en el `checkIf` hay **cuatro funciones vivas en el call stack de
Kotlin**, cada una con su `currentScope` pendiente de restaurar. Esa es la "pila de
tablas" de las notas: no se escribe, es el call stack. Lo único que se administra es
el cursor de una variable.

Y `lookup("total")` desde adentro del `if` camina
`if@4 → for@3 → procesar → global` siguiendo `parent`. Ese camino **es** la pila,
recorrida de arriba hacia abajo.

### Cómo se nombran los ámbitos

Las clases y funciones traen su nombre del código fuente. Los ámbitos anónimos se
nombran por **la construcción que los creó**, más su línea:

| Construcción | `kind` | `name` |
|---|---|---|
| el programa | `GLOBAL` | `global` |
| `class Animal { }` | `CLASS` | `Animal` |
| `function saludar() { }` | `FUNCTION` | `saludar` |
| `if (…) { }` | `BLOCK` | `if@4` |
| `else { }` | `BLOCK` | `else@8` |
| `while (…) { }` | `LOOP` | `while@7` |
| `do { } while (…)` | `LOOP` | `do@7` |
| `for (…) { }` | `LOOP` | `for@3` |
| `foreach (…) { }` | `LOOP` | `foreach@5` |
| `case 1:` | `BLOCK` | `case@10` |
| `default:` | `BLOCK` | `default@14` |
| `try { }` | `BLOCK` | `try@15` |
| `catch (e) { }` | `BLOCK` | `catch@18` |
| `{ }` suelto | `BLOCK` | `block@20` |

**Por qué la construcción y no la palabra "block":** el `kind` ya dice que es un
bloque; repetirlo en el nombre gasta la etiqueta sin informar. Nombrándolo por la
construcción, el árbol de la GUI se lee **contra el código fuente sin contar nada**:
ves `for@3`, vas a la línea 3, y ahí está el `for`.

**La unicidad no es requisito.** Dos ámbitos pueden llamarse igual sin romper nada:
el `Scope` tiene identidad de objeto (por eso es `class` y no `data class`), y el
árbol muestra dos nodos separados aunque compartan etiqueta. Aun así, nombrar por
construcción colisiona mucho menos que un `block@N` genérico.

### Los dos enlaces, y por qué son dos

```
parent      →  hacia AFUERA:  un bloque dentro de una función dentro de global
superclass  →  hacia ARRIBA:  Perro hereda los campos de Animal
```

Sin el segundo, este ejemplo —que está **literal** en `Especificaciones.md`— no
compila:

```cps
class Perro : Animal {
  function hablar(): string { return this.nombre + " ladra."; }
}
```

`nombre` no está en `Perro` ni en ningún ámbito exterior: está en `Animal`. Es un
caso de prueba obligatorio.

### Aceptación

- Declarar `x` dos veces en el mismo ámbito devuelve `AlreadyDeclared` con la
  ubicación de la primera.
- Declarar `x` en un hijo cuando el padre ya tiene `x` devuelve `Ok` (shadowing
  legal), y `lookup("x")` desde el hijo devuelve **el del hijo**.
- `lookup` a través de cuatro niveles anidados encuentra un símbolo del más
  externo.
- `lookup` resuelve a través de `superclass` (el caso `Perro`/`Animal`).
- `lookupMember` **no** encuentra una variable global (solo campos y heredados).
- `attachSuperclass` funciona una vez y lanza en la segunda llamada.
- Un ámbito cerrado **sigue siendo enumerable** desde su padre vía `children`.
  Esta es la prueba de la decisión del árbol.
- `enclosingClass()` desde un bloque dentro de un método devuelve el ámbito de la
  clase; desde global devuelve `null`.
- `functionDepth()` es 0 en global, 1 dentro de una función, 2 dentro de una
  función anidada.
- Los offsets dentro de un ámbito son 0, 1, 2… en orden de declaración.
- Declarar en un `Scope` de tipo `CLASS` deja el símbolo con `isMember = true`;
  declararlo en un `BLOCK` o `FUNCTION` lo deja en `false`. Un `const` declarado
  dentro de una clase queda con `kind = CONSTANT` **y** `isMember = true`, que es
  el caso que el enum solo no podía representar.

### Respaldo

Enunciado, *"Manejo de Ámbito"* y *"creación de un nuevo entorno de símbolos por
cada función, clase y bloque"*. Dragon Book §2.7 (Fig. 2.37). Notas de clase:
*"implementarse como una pila de tablas… pero hay que tener referencias entre
tablas"*.

---

## Ticket 1.4 — AST: expresiones y operadores

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `frontend/ast/models/Node.kt` (NUEVO)
- `frontend/ast/models/Expression.kt` (NUEVO)
- `frontend/ast/models/Operators.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/AstExprTest.kt` (NUEVO)

**Qué es esto, en simple:** ANTLR construye un árbol muy fiel al texto: incluye los
paréntesis, los puntos y comas, y un nodo por cada nivel de precedencia aunque no
haga nada. Para la expresión `x` sola, el árbol de ANTLR tiene **once** nodos
encadenados:

```
expression → assignmentExpr → conditionalExpr → logicalOrExpr → logicalAndExpr
→ equalityExpr → relationalExpr → additiveExpr → multiplicativeExpr → unaryExpr
→ primaryExpr → Identifier
```

El **AST** (árbol sintáctico abstracto) es el árbol propio, limpio: para `x` es
**un** nodo. Es el árbol sobre el que se verifican tipos y se ejecuta.

### La base

```kotlin
// Todo nodo del AST sabe dónde estaba en el archivo fuente.
// Sin esto no se puede reportar "error en la línea 12, columna 5".
sealed interface Node {
    val location: LexemeLocation
}
```

```kotlin
// Una expresión: algo que produce un valor.
//
// Es sealed CLASS y no interface (a diferencia de Statement) por una razón concreta:
// necesita dos campos MUTABLES que se rellenan después, y una clase abstracta los
// declara UNA vez en vez de repetirlos en las 12 subclases.
sealed class Expression : Node {

    // El tipo de esta expresión. Lo rellena el TypeChecker (Fase 4).
    // "Decorar el árbol" es exactamente esto: recorrerlo escribiendo este campo.
    var type: Type? = null

    // El valor de esta expresión, SI se puede calcular en tiempo de compilación.
    // Para `3 + 5` queda 8. Para `x + 5` queda null (no se sabe cuánto vale x).
    // Es lo que hace que print(3+5) imprima 8 sin necesitar un intérprete.
    var constantValue: Any? = null
}
```

### Las doce expresiones de Compiscript

```kotlin
// ── Valores literales ─────────────────────────────────────────────────
// 123   3.14   "texto"   true   false   null
data class Literal(
    val value: Any?,
    val literalType: Type,
    override val location: LexemeLocation
) : Expression()

// [1, 2, 3]
data class ArrayLiteral(
    val elements: List<Expression>,
    override val location: LexemeLocation
) : Expression()

// ── Nombres ───────────────────────────────────────────────────────────
// x   nombre   miFuncion
data class Identifier(
    val name: String,
    override val location: LexemeLocation
) : Expression() {

    // A qué declaración se resolvió este nombre. Lo pone `checkIdentifier` (Fase 4).
    //
    // Resolver un nombre cuesta caminar el árbol de ámbitos. Guardarlo aquí evita
    // que cada fase posterior lo repita — y que lo repita MAL: ver la nota
    // "Por qué el símbolo resuelto vive en el nodo" abajo.
    var resolvedSymbol: Symbol? = null
}

// this
data class ThisReference(override val location: LexemeLocation) : Expression()

// ── Operadores ────────────────────────────────────────────────────────
// -x   !bandera
data class UnaryOperation(
    val operator: UnaryOperator,
    val operand: Expression,
    override val location: LexemeLocation
) : Expression()

// a + b   x < y   p && q
//
// SIEMPRE dos operandos. ANTLR entrega listas planas (a + b + c llega como UN nodo
// con tres hijos); el AstBuilder las PLIEGA A LA IZQUIERDA para producir
// BinaryOperation(BinaryOperation(a, +, b), +, c). Ver ticket 2.2.
data class BinaryOperation(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    override val location: LexemeLocation
) : Expression()

// condicion ? siVerdadero : siFalso
data class TernaryOperation(
    val condition: Expression,
    val ifTrue: Expression,
    val ifFalse: Expression,
    override val location: LexemeLocation
) : Expression()

// x = 5   usado como EXPRESIÓN (dentro de otra expresión)
data class AssignmentExpression(
    val target: Expression,
    val value: Expression,
    override val location: LexemeLocation
) : Expression()

// ── Acceso y llamadas ─────────────────────────────────────────────────
// saludar("mundo")   perro.hablar()
//
// Para `perro.hablar()` el callee es un PropertyAccess. El verificador de tipos
// tiene que manejar ese caso: es la llamada a método.
data class FunctionCall(
    val callee: Expression,
    val arguments: List<Expression>,
    override val location: LexemeLocation
) : Expression()

// lista[0]
data class IndexAccess(
    val target: Expression,
    val index: Expression,
    override val location: LexemeLocation
) : Expression()

// perro.nombre
data class PropertyAccess(
    val target: Expression,
    val propertyName: String,
    override val location: LexemeLocation
) : Expression() {

    // El miembro de la clase al que se resolvió. Lo pone `checkPropertyAccess`
    // (Fase 4), buscando con `lookupMember` en la clase y sus superclases.
    //
    // Misma razón que en Identifier, más una propia: aquí la búsqueda involucra la
    // cadena de herencia, así que repetirla es todavía más caro.
    var resolvedMember: Symbol? = null
}

// new Perro("Toby")
data class ObjectCreation(
    val className: String,
    val arguments: List<Expression>,
    override val location: LexemeLocation
) : Expression()
```

### Los operadores como enum, no como texto

```kotlin
// Operadores de dos operandos, agrupados por lo que hacen.
// El grupo es lo que la regla de tipos consulta.
enum class BinaryOperator(val symbol: String, val group: OperatorGroup) {
    ADD("+", OperatorGroup.ARITHMETIC),
    SUBTRACT("-", OperatorGroup.ARITHMETIC),
    MULTIPLY("*", OperatorGroup.ARITHMETIC),
    DIVIDE("/", OperatorGroup.ARITHMETIC),
    MODULO("%", OperatorGroup.ARITHMETIC),

    EQUAL("==", OperatorGroup.EQUALITY),
    NOT_EQUAL("!=", OperatorGroup.EQUALITY),

    LESS("<", OperatorGroup.RELATIONAL),
    LESS_EQUAL("<=", OperatorGroup.RELATIONAL),
    GREATER(">", OperatorGroup.RELATIONAL),
    GREATER_EQUAL(">=", OperatorGroup.RELATIONAL),

    AND("&&", OperatorGroup.LOGICAL),
    OR("||", OperatorGroup.LOGICAL);

    companion object {
        fun fromSymbol(symbol: String): BinaryOperator =
            entries.first { it.symbol == symbol }
    }
}

enum class OperatorGroup { ARITHMETIC, EQUALITY, RELATIONAL, LOGICAL }

enum class UnaryOperator(val symbol: String) {
    NEGATE("-"),   // -x    opera sobre integer o float
    NOT("!");      // !x    opera sobre boolean

    companion object {
        fun fromSymbol(symbol: String): UnaryOperator =
            entries.first { it.symbol == symbol }
    }
}
```

**Por qué enum y no `String`:** con un enum, el `when` del verificador de tipos es
**exhaustivo**. Si se agrega un operador y se olvida darle su regla, **Kotlin no
compila** y dice exactamente dónde. Con `String` habría un `else` que devuelve algo
por defecto y el operador nuevo pasaría sin verificarse, en silencio. Esto es
directamente el requisito de *"encadenar bien las operaciones y llevar la secuencia
de tipos y operaciones"*: lo vigila el compilador de Kotlin.

**Por qué el campo `group`:** las trece reglas de tipo no son trece, son
**cuatro**. `+ - * / %` comparten regla (operandos numéricos → numérico);
`< <= > >=` comparten otra (operandos comparables → boolean); `== !=` otra;
`&& ||` otra. Agrupar en el enum hace que `TypeRules.kt` tenga cuatro funciones en
vez de trece ramas. Esa es la simplificación real.

### Por qué el símbolo resuelto vive en el nodo

`Identifier.resolvedSymbol` y `PropertyAccess.resolvedMember` son el tercer campo de
decoración, junto a `type` y `constantValue`. Los pone la Fase 4, y existen para que
**nadie más tenga que resolver el mismo nombre otra vez**.

Sin ellos, cada fase posterior necesita su propio cursor de ámbito y tiene que abrir
y cerrar **exactamente los mismos ámbitos, con los mismos nombres y en el mismo
orden** que el `TypeChecker`, o los `lookup` se desincronizan y el resultado sale mal
**en silencio**. Es lo que le pasaba al `LivenessAnalyzer` en una versión anterior del
plan (ticket 5.2).

Con el símbolo en el nodo:

| Consumidor | Qué gana |
|---|---|
| Fase 5 (vivacidad) | deja de recorrer el AST: lee los contadores del `Symbol` |
| Fase 6 (intérprete) | no re-resuelve nombres para encontrar la variable |
| GUI | *"ir a la declaración"* es un clic sobre `resolvedSymbol.location` |
| Generación de código | necesita el `Symbol` para saber si es global, local o parámetro, y su `offset` |

**Por qué solo en esos dos nodos y no en `Expression`:** de las doce expresiones, solo
esas dos resuelven un nombre. Un `BinaryOperation` o un `Literal` no tienen símbolo, y
un campo nulo permanente en las otras diez sería ruido.

### Aceptación

- Se construye a mano el AST de `3 + 5 * 2` con la agrupación correcta
  —`BinaryOperation(3, +, BinaryOperation(5, *, 2))`— y se recorre.
- Se construye el AST de `a - b - c` como `BinaryOperation(BinaryOperation(a, -, b), -, c)` y se
  verifica que **no** sea `BinaryOperation(a, -, BinaryOperation(b, -, c))`. Este test existe porque
  los dos árboles dan resultados distintos, y es el que atrapa el error de plegado
  del ticket 2.2.
- `BinaryOperator.fromSymbol("+")` devuelve `ADD`; `BinaryOperator.ADD.group` es `ARITHMETIC`.
- Un `when (op.group)` sin `else` compila.
- `expr.type` y `expr.constantValue` arrancan en `null` y se pueden escribir.
- Todo nodo tiene `location`.

### Respaldo

Enunciado, *"construyendo un árbol sintáctico con representación visual"*. Dragon
Book §2.5.1 y §5.3.1.

---

## Ticket 1.5 — AST: sentencias, declaraciones y `TypeReference`

- **Estado**: pendiente
- **Depende de**: 1.4

**Archivos:**

- `frontend/ast/models/Statement.kt` (NUEVO)
- `frontend/ast/models/TypeReference.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/AstStmtTest.kt` (NUEVO)

**Qué es esto, en simple:** una **expresión** produce un valor (`3 + 5` vale 8).
Una **sentencia** hace algo (`print(x);` imprime, `let x = 1;` declara). Este
ticket cubre las sentencias.

### `TypeReference` — la distinción que no es obvia pero es necesaria

```kotlin
// El tipo tal como el programador lo ESCRIBIÓ, antes de resolverlo.
//
//   "integer"     -> TypeReference("integer", 0)
//   "integer[]"   -> TypeReference("integer", 1)
//   "integer[][]" -> TypeReference("integer", 2)
//   "Perro"       -> TypeReference("Perro", 0)
data class TypeReference(
    val baseName: String,
    val arrayDimensions: Int,
    override val location: LexemeLocation
) : Node {
    // Como el programador lo ESCRIBIO, para los mensajes de error.
    // Es el equivalente de Type.name, del lado de lo todavia no resuelto.
    //
    // Hace falta porque baseName solo no alcanza: para `integer[]` diria "integer",
    // y el mensaje del FlowAnalyzer sobre el tipo de retorno saldria mal.
    val name: String get() = baseName + "[]".repeat(arrayDimensions)
}
```

**¿Por qué no guardar directamente un `Type`?** Porque al construir el AST todavía
**no se sabe** si `Perro` existe. Compiscript permite esto:

```cps
let p: Perro = new Perro();            // usa Perro...
class Perro { let nombre: string; }    // ...declarada más abajo
```

Si se resolviera el tipo al construir el AST, habría que reportar *"el tipo Perro
no existe"* — y sería falso. El AST guarda **lo que se escribió**; la Fase 3 lo
resuelve a un `Type`, o reporta *"tipo no declarado"* si de verdad no existe. Esa
separación es lo que permite las referencias adelantadas.

### Las sentencias

```kotlin
// Una sentencia: algo que se ejecuta. No produce un valor.
//
// Es sealed INTERFACE (no class, a diferencia de Expression) porque las sentencias no se
// decoran: no tienen tipo ni valor constante que guardar.
sealed interface Statement : Node

// La raíz del árbol: el programa completo.
data class Program(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Node

// ── Declaraciones ─────────────────────────────────────────────────────

// let x: integer = 5;    var y;    const PI: integer = 314;
// Un solo nodo para los tres casos; `isConstant` los distingue.
data class VariableDeclaration(
    val name: String,
    val declaredType: TypeReference?,       // null si no se anotó: hay que inferirlo
    val initializer: Expression?,           // null si no se inicializó
    val isConstant: Boolean,
    override val location: LexemeLocation
) : Statement

// function saludar(nombre: string): string { ... }
data class FunctionDeclaration(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: TypeReference?,         // null = no devuelve nada (void)
    val body: Block,
    override val location: LexemeLocation
) : Statement

data class Parameter(
    val name: String,
    val declaredType: TypeReference?,
    override val location: LexemeLocation
) : Node

// class Perro : Animal { ... }
data class ClassDeclaration(
    val name: String,
    val superclassName: String?,      // null si no hereda
    val members: List<Statement>,          // VariableDeclaration (campos) y FunctionDeclaration (métodos)
    override val location: LexemeLocation
) : Statement

// ── Sentencias simples ────────────────────────────────────────────────

// x = 5;   perro.nombre = "Toby";   lista[0] = 1;
//
// La gramática tiene DOS caminos para asignar (como statement y como expresión);
// el AstBuilder normaliza los dos a este nodo. Ver ticket 2.3.
data class Assignment(
    val target: Expression,
    val value: Expression,
    override val location: LexemeLocation
) : Statement

// saludar("mundo");    una expresión usada como sentencia
data class ExpressionStatement(
    val expr: Expression,
    override val location: LexemeLocation
) : Statement

// print(x);
data class Print(
    val expr: Expression,
    override val location: LexemeLocation
) : Statement

// { ... }
data class Block(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Statement

// ── Control de flujo ──────────────────────────────────────────────────

data class If(
    val condition: Expression,
    val thenBranch: Block,
    val elseBranch: Block?,           // null si no hay else
    override val location: LexemeLocation
) : Statement

data class While(
    val condition: Expression,
    val body: Block,
    override val location: LexemeLocation
) : Statement

// El orden de los campos refleja el orden de ejecución: cuerpo, luego condición.
data class DoWhile(
    val body: Block,
    val condition: Expression,
    override val location: LexemeLocation
) : Statement

// for (init; condition; update) body      los tres son opcionales
data class For(
    val initializer: Statement?,
    val condition: Expression?,
    val update: Expression?,
    val body: Block,
    override val location: LexemeLocation
) : Statement

// foreach (item in lista) { ... }
//
// `variableName` NO lleva tipo anotado: se infiere del tipo de elemento de
// `iterable`. Junto con `let x = <expr>`, es el único punto de inferencia del
// lenguaje.
data class ForEach(
    val variableName: String,
    val iterable: Expression,
    val body: Block,
    override val location: LexemeLocation
) : Statement

data class Switch(
    val subject: Expression,
    val cases: List<SwitchCase>,
    val defaultBody: List<Statement>?,     // null si no hay default
    override val location: LexemeLocation
) : Statement

data class SwitchCase(
    val value: Expression,
    val body: List<Statement>,
    override val location: LexemeLocation
) : Node

// try { ... } catch (err) { ... }
data class TryCatch(
    val tryBlock: Block,
    val catchParameterName: String,
    val catchBlock: Block,
    override val location: LexemeLocation
) : Statement

// ── Saltos ────────────────────────────────────────────────────────────

data class Break(override val location: LexemeLocation) : Statement
data class Continue(override val location: LexemeLocation) : Statement

data class Return(
    val value: Expression?,                 // null en `return;`
    override val location: LexemeLocation
) : Statement
```

### Dos notas de diseño que evitan preguntas después

**`Break` y `Continue` son `data class` sin campos, no `data object`.** Un
`data object` es una instancia única compartida, y estas necesitan su propia
`location`: cada `break` del programa está en una línea distinta y hay que poder
señalarla en el error *"`break` fuera de un bucle"*.

**`ClassDeclaration.members` es `List<Statement>`, no un tipo propio.** La gramática dice
`classMember: functionDeclaration | variableDeclaration | constantDeclaration`, o
sea que los miembros son exactamente `FunctionDeclaration` y `VariableDeclaration`, que ya existen.
Crear un `sealed interface ClassMember` duplicaría los dos nodos sin agregar nada.
La Fase 3 filtra por tipo (`members.filterIsInstance<VariableDeclaration>()`), que es una
línea.

### Aceptación

- Se construye a mano el AST completo de este programa y se recorre imprimiendo el
  árbol indentado:

  ```cps
  class Animal {
    let nombre: string;
    function constructor(nombre: string) { this.nombre = nombre; }
  }
  let a: Animal = new Animal("Rex");
  print(a.nombre);
  ```

- `TypeReference("integer", 2)` representa `integer[][]`.
- Un `when (stmt)` sobre `Statement` sin `else` compila (prueba de que el `sealed` está
  completo).
- Dos nodos `Break` en líneas distintas tienen `location` distinta.
- Cada nodo tiene `location`.

### Respaldo

Enunciado, *"árbol sintáctico con representación visual"*. Dragon Book §5.3.1.
`Compiscript.g4`, todas las reglas de `statement`.

---

## Resumen de la fase

| Ticket | Archivos | Deja listo |
|---|---|---|
| 1.1 | `Type.kt` | El vocabulario de tipos del lenguaje |
| 1.2 | `Symbol.kt`, `DeclarationKind.kt` | La ficha de un nombre declarado |
| 1.3 | `Scope.kt`, `ScopeKind.kt` | El árbol de ámbitos, con `declare` y `lookup` |
| 1.4 | `Node.kt`, `Expression.kt`, `Operators.kt` | Las 12 expresiones y los operadores como enum |
| 1.5 | `Statement.kt`, `TypeReference.kt` | Las 17 sentencias, `Program` y `TypeReference` |

**Cero lógica de análisis en toda la fase.** Solo datos y operaciones triviales
(`declare`, `lookup`, `openChild`). Eso es a propósito: son las piezas, no el
motor.
