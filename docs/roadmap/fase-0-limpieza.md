# Fase 0 · Limpieza

**Objetivo:** quitar todo lo específico de Compiscript y dejar un esqueleto que
compile, abra ventana y esté listo para recibir el DBMS.

**Por qué es una fase y no un paso previo:** borrar 4,000 líneas rompe la
compilación en cadena. La GUI depende del pipeline, el pipeline del intérprete, el
intérprete del AST. Si se hace a medias, el proyecto queda sin compilar varios
días y nadie puede avanzar en paralelo. Con criterio de terminado explícito, se
hace de una vez y se sigue.

**Estimación:** una sesión.

**Regla de la fase:** al terminar, `grep -ri compiscript app/src/main` no devuelve
nada y `./gradlew build` pasa.

**El build queda en rojo entre el ticket 0.2 y el 0.7, y es normal.** Delete el
AST rompe el `TypeChecker`, que rompe el pipeline, que rompe la GUI. Los tickets
intermedios no prometen compilación; el que la cierra es el 0.7. Por eso esto es
una fase y no un paso previo: si se hace a medias, el proyecto queda sin compilar
varios días.

---

## Ticket 0.1 · Delete la gramática y el frontend sintáctico

- **Estado**: pendiente
- **Depende de**: nada

**Archivos (ELIMINA):**

- `app/src/main/antlr/Compiscript.g4`
- `frontend/syntax/SyntaxAnalyzer.kt`
- `frontend/syntax/ParseTreeView.kt`
- `app/src/test/.../SyntaxAnalyzerTest.kt`, `AntlrSmokeTest.kt`

**Archivos (CONSERVA):**

- `frontend/syntax/DiagnosticsErrorListener.kt`, que no menciona Compiscript en
  ningún lado: recibe lo que ANTLR le pasa y lo vuelca en `Diagnostics`. Sirve
  igual para `Sql.g4`.

**Qué se hace:** eliminar los archivos y, en `build.gradle.kts`, dejar el bloque
de `generateGrammarSource` intacto pero apuntando al paquete nuevo si se decide
renombrar (ver ticket 0.6). El plugin `antlr` compila cualquier `.g4` que
encuentre en `app/src/main/antlr/`, así que al borrar el archivo la tarea
simplemente no genera nada.

**Aceptación:** no queda ningún `.g4` de Compiscript y
`./gradlew generateGrammarSource` corre sin error.

---

## Ticket 0.2 · Delete el análisis semántico

- **Estado**: pendiente
- **Depende de**: 0.1

**Archivos (ELIMINA):**

- `frontend/ast/AstBuilder.kt` y `frontend/ast/AstView.kt`
- `frontend/ast/models/` completo: `Node`, `Statement`, `Expression`,
  `Operators`, `TypeReference`, `Declarations`, `SimpleStatements`, `ControlFlow`
- `frontend/semantic/TypeResolver.kt`
- `frontend/semantic/TypeRules.kt`
- `frontend/semantic/ScopeDeclaration.kt`
- `frontend/semantic/DeclarationCollector.kt`
- `frontend/semantic/TypeChecker.kt`
- `frontend/semantic/FlowAnalyzer.kt`
- `frontend/semantic/LivenessReportBuilder.kt`
- `frontend/semantic/models/GarbageCollectorReport.kt`
- todos sus tests

**Archivos (CONSERVA):**

- `frontend/semantic/symbols/Scope.kt` y `ScopeKind.kt`
- `frontend/semantic/symbols/Symbol.kt` y `DeclarationKind.kt`

**Por qué se conserva `Scope`:** es el mecanismo que resuelve los alias de tabla,
y con subconsultas correlacionadas deja de ser una comodidad y pasa a ser
indispensable. El ámbito de la subconsulta tiene como padre el de la consulta
externa, y resolver un nombre de afuera es subir por la cadena de padres, que es
`Scope.lookup` sin tocarlo.

**Aceptación:** `frontend/semantic/` contiene solo `symbols/`.

---

## Ticket 0.3 · Delete el intérprete y los programas de ejemplo

- **Estado**: pendiente
- **Depende de**: 0.2

**Archivos (ELIMINA):**

- `interpreter/Interpreter.kt`
- `interpreter/Environment.kt`
- `interpreter/ControlFlowSignals.kt`
- `interpreter/RuntimeError.kt`
- `samples/SampleProgram.kt` y `samples/SamplePrograms.kt`
- `app/src/main/resources/programas/` completo, los 38 `.cps`
- `ProgramasValidosTest.kt`, `ProgramasInvalidosTest.kt`, `InterpreterTest.kt`,
  `EnvironmentTest.kt`, `SampleProgramsTest.kt`

**Archivos (MODIFICA):**

- `interpreter/RuntimeValue.kt` se mueve a `types/Value.kt` y se recorta.

**Qué se hace con `RuntimeValue`:** se conservan `IntValue`, `FloatValue`,
`StringValue`, `BoolValue` y `NullValue`, que son exactamente lo que es una celda.
Se eliminan `ArrayValue`, `ObjectValue` y `FunctionValue`. La fase 1 le agrega
`DecimalValue`, `DateValue` y `TimeValue`.

**Por qué no se borra entero:** el archivo ya modela lo correcto. Borrarlo para
volver a escribir las mismas cinco clases sería trabajo por nada.

**Sobre `RuntimeError.kt`:** se borra como clase suelta, pero el concepto no
desaparece. Los errores que solo se detectan ejecutando (PK repetida, FK rota,
una subconsulta escalar que devuelve dos filas) pasan a ser una variante más de
`CompilerError`, que se agrega en el ticket 0.6. Así caen en el mismo
`Diagnostics` que todo lo demás y la lista de errores del IDE los muestra sin
saber de dónde vienen.

**Aceptación:** el paquete `interpreter/` ya no existe, y `types/Value.kt` tiene
cinco variantes.

---

## Ticket 0.4 · Reducir la GUI a editor y errores

- **Estado**: pendiente
- **Depende de**: 0.3

**Archivos (ELIMINA):**

- `gui/screens/TreesScreen.kt`
- `gui/screens/SymbolTableScreen.kt`
- `gui/components/ScopeTreeView.kt`
- `gui/components/GarbageCollectorReportView.kt`
- `gui/components/ProgramSelector.kt`
- `gui/components/TreeCanvas.kt` y `TreeLayout.kt`
- `ast/models/TreeNodeView.kt`
- `AppStateTest.kt`, `TreeViewTest.kt`

**Archivos (MODIFICA):**

- `gui/App.kt`: quitar las pestañas de árboles y símbolos del `ViewMenu`
- `gui/state/AppState.kt`: quitar `selectedSample`, `loadSample` y el default que
  venía de `SamplePrograms`
- `gui/screens/WorkspaceScreen.kt`: quitar el `ProgramSelector`
- `gui/components/OutputConsole.kt`: dejarlo mostrando texto plano

**Archivos (CONSERVA):**

- `gui/components/CodeEditor.kt`, con su medianil de números ya resuelto
- `gui/components/FileMenu.kt`, que cambia su extensión de `.cps` a `.sql`
- `gui/components/ErrorList.kt`, que trabaja sobre `CompilerError` y no sabe de
  qué lenguaje viene
- `gui/components/PlayButton.kt`, `FileMenu.kt`, `ViewMenu.kt`

**Por qué se borran `TreeCanvas` y `TreeLayout` si van a volver:** vuelven en la
fase 8 pero sobre un `TreeNodeView` distinto, y mientras tanto no compilan porque
dependen del AST eliminado. Dejarlos comentados es peor que borrarlos: el
historial de `main` los tiene.

**`AppState` sigue sin compilar al terminar este ticket**, porque llama a
`CompilerPipeline.compile`, que se reconstruye en el 0.7. Es el estado rojo
esperado de la fase.

**Aceptación:** los archivos de la lista ya no existen, y lo único que queda
pendiente de compilar en `gui/` es la llamada al pipeline.

---

## Ticket 0.5 · Preparar el andamiaje

- **Estado**: pendiente
- **Depende de**: 0.4

**Archivos (CREA):**

- `app/src/main/antlr/Sql.g4`, mínimo: solo `script: EOF;` más `WS -> skip`
- `storage/DataDirectory.kt`
- `data/.gitkeep`

**Archivos (MODIFICA):**

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `.gitignore`

**Qué se hace en Gradle:**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)   // NUEVO
    antlr
}

dependencies {
    implementation(libs.kotlinx.serialization.json)   // NUEVO
    // ... el resto igual
}
```

El plugin de serialización es obligatorio: `kotlinx.serialization` genera los
serializadores en tiempo de compilación, así que sin el plugin el runtime solo da
un error al arrancar.

**Qué se hace en `DataDirectory`:**

```kotlin
// CLASE y no object: los tests necesitan apuntarla a un directorio temporal.
class DataDirectory(private val root: File) {

    fun root(): File = root.apply { if (!exists()) mkdirs() }

    fun csv(table: String) = File(root(), "$tabla.csv")
    fun json(table: String) = File(root(), "$tabla.json")

    // La existencia de la tabla la da el CSV, no el JSON. Decision 2.
    fun tables(): List<String> =
        root().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "csv" }
            .map { it.nameWithoutExtension }
            .sorted()

    companion object {
        // La de produccion: relativa a la raiz del repo y no al home,
        // porque el punto de usar CSV es poder verlos al lado del codigo.
        val DEFAULT = DataDirectory(File("data"))
    }
}
```

### Decisión · clase con raíz inyectable, no `object` con ruta fija

Un `object` con la ruta escrita adentro es imposible de probar: toda la batería de
las fases 2, 6, 7 y 9 escribiría sobre el `data/` real, y el orden en que corran
los tests cambiaría sus resultados.

Con la raíz inyectada, cada test usa `@TempDir` de JUnit y se lleva su propio
directorio. `DEFAULT` es la única que apunta al de verdad, y la usa el
pipeline. Todo lo que toca disco (`CatalogLoader`, `CatalogWriter`, `scan`,
`flush`) recibe un `DataDirectory` en el constructor.

Es también el principio 7 del README: nada de `object` con estado.

**Por qué `tables()` filtra por `.csv` y no por `.json`:** es la decisión 2. La
tabla existe porque hay datos, no porque haya esquema. Un `.json` huérfano es un
error que el catálogo reporta; un `.csv` huérfano también, pero por el otro lado.

**Sobre `.gitignore`:** `data/` se versiona con un `.gitkeep` y los `.csv`
generados se ignoran, para que la batería de pruebas no ensucie el repo. La fase 9
define si algún juego de datos de ejemplo sí se versiona.

**Aceptación:** `gradle/libs.versions.toml` declara el plugin y la librería de
serialización, existe `Sql.g4` mínimo, y `DataDirectory(tmp).tablas()` sobre un
directorio vacío devuelve lista vacía sin reventar. El build sigue rojo: lo cierra
el 0.7.

---

## Ticket 0.6 · Podar los cimientos y reorganizar la documentación

- **Estado**: pendiente
- **Depende de**: 0.5

**Archivos (MODIFICA):**

- `frontend/semantic/symbols/Type.kt`, que se reduce a la interfaz sellada:

```kotlin
sealed interface Type {
    val name: String
}

data object ErrorType : Type {
    override val name = "<error>"
}
```

Se queda solo esto para que `Symbol` y `Scope` sigan compilando. La fase 1 lo
llena y lo mueve a `types/Type.kt`.

- `frontend/semantic/symbols/DeclarationKind.kt`: los valores de Compiscript
  (`VARIABLE`, `CONSTANT`, `FUNCTION`, `CLASS`, `PARAMETER`, `FIELD`) se cambian
  por los de aquí: `TABLA`, `COLUMNA`, `ALIAS`.

- `diagnostics/CompilerError.kt` gana una cuarta variante y una severidad:

```kotlin
enum class Severity { ERROR, WARNING }

sealed class CompilerError {
    abstract val location: LexemeLocation
    abstract val message: String
    open val severity: Severity = Severity.ERROR

    class LexerError(...) : CompilerError()
    class ParserError(...) : CompilerError()

    class SemanticError(
        override val location: LexemeLocation,
        override val message: String,
        override val severity: Severity = Severity.ERROR
    ) : CompilerError()

    // Solo se detecta ejecutando: PK repetida, FK rota, una subconsulta
    // escalar que devolvio dos filas.
    class ExecutionError(...) : CompilerError()
}
```

- `diagnostics/Diagnostics.kt`: `hasErrors` cuenta **solo** los de severidad
  `ERROR`.

### Decisión · la advertencia es una severidad, no un colector aparte

La fase 5 reporta dos cosas que no deben detener el script: `= NULL` en vez de
`IS NULL`, y un `UPDATE` o `DELETE` sin `WHERE`. Si `hasErrors` las contara, el
pipeline no ejecutaría y la advertencia se volvería un error con otro nombre.

Un colector separado tampoco sirve: la lista de errores del IDE tendría que leer
dos fuentes y ordenarlas por línea a mano. Con una severidad en el mismo
`CompilerError`, la lista las muestra juntas con distinto color y el clic a la
línea funciona igual.

### Decisión · el error de ejecución vive en `CompilerError`

Podría ser una excepción, como era `RuntimeError` en Compiscript. No lo es, porque
aquí el motor **sigue** después de un error en una sentencia para poder reportar
qué pasó con las demás, y porque la decisión 5 ya garantiza que nada se escribió.
Una excepción obligaría a un `try` por sentencia para lograr lo mismo.

**Archivos (ELIMINA):**

- `docs/roadmap/` completo, que es el de Compiscript y vive en `main`
- `docs/reglas-de-tipos.md`
- `docs/arquitectura.excalidraw`
- `_to_delete/`, `extras/example.yaml`, `.DS_Store`
- el PDF del enunciado que está en la raíz
- `app/src/test/.../TypeTest.kt`, que prueba los tipos de Compiscript; la fase 1
  escribe el suyo

**Archivos (MUEVE):**

- `docs/roadmap-dbms/` pasa a ser `docs/roadmap/`

**Decisión pendiente de este ticket:** si el paquete raíz se renombra de
`org.compiler` a `org.dbms`. Es ahora o nunca, porque después toca cada archivo
nuevo. A favor de renombrar: el nombre dice lo que es. En contra: no compra nada
funcional y toca 56 archivos más el `mainClass` de Compose y el `-package` de
ANTLR. **Recomendación: no renombrar**, y dejarlo anotado en el README para que
en la defensa no parezca un descuido.

**Aceptación:** `grep -ri compiscript app/src/main` no devuelve nada, y
`docs/roadmap/README.md` es el de este proyecto. El build lo cierra el 0.7.

El criterio NO incluye `docs/`: el roadmap menciona Compiscript a proposito, para
explicar de donde viene cada decision. Tampoco `app/src/test`, porque el ultimo
rastro vive en `CompilerPipelineTest`, que se reescribe en el 0.7.

---

## Ticket 0.7 · Reconstruir el runtime y cerrar el build

- **Estado**: pendiente
- **Depende de**: 0.6

**Archivos:**

- `runtime/models/CompilationResult.kt` (MODIFICA)
- `runtime/DbmsPipeline.kt` (NUEVO, reemplaza `CompilerPipeline.kt`)
- `gui/state/AppState.kt` (MODIFICA)

**Por qué este ticket existe:** `CompilerPipeline.kt` y `CompilationResult.kt`
referencian `Program`, `Scope`, `GarbageCollectorReport` y `ExecutionResult`, que
se borraron en los tickets 0.2 y 0.3. Y `AppState` llama al pipeline. Sin este
ticket, la fase 0 termina con el proyecto sin compilar, que es exactamente lo que
la fase promete evitar.

### El resultado, con los campos que va a tener al final

```kotlin
// Todos los campos nulables a proposito: un fuente que no parsea no tiene AST,
// pero si tiene errores, y la GUI debe mostrar resultados parciales.
class CompilationResult(
    val source: String,
    val parseTreeView: TreeNodeView? = null,   // fase 3
    val ast: Script? = null,                   // fase 3
    val catalog: Catalog? = null,             // fase 2
    val errors: List<CompilerError> = emptyList(),
    val execution: ExecutionResult? = null     // fase 6
) {
    val hasErrors get() = errors.any { it.severity == Severity.ERROR }

    companion object {
        fun failed(diagnostics: Diagnostics, source: String) =
            CompilationResult(source, errors = diagnostics.all())
    }
}
```

Los tipos que todavía no existen se dejan comentados y se descomentan en la fase
que los crea. El ticket 3.6 conecta las etapas A y B, el 7.5 las demás.

### El pipeline, en su forma más pequeña

```kotlin
object DbmsPipeline {
    // Por ahora solo colecta: no hay gramatica hasta la fase 3.
    fun run(source: String, write: Boolean = true): CompilationResult =
        CompilationResult(source)
}
```

`AppState.onCompile()` pasa a llamar a `DbmsPipeline.ejecutar`. El botón de correr
funciona y no hace nada, que es lo correcto en este punto.

**Aceptación:**

- `./gradlew build` pasa
- `./gradlew run` abre la ventana, se escribe texto, se presiona correr y no pasa
  nada ni truena
- `grep -ri compiscript app/src/main` no devuelve nada
