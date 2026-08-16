# Fase 0 — Limpieza y base

**Objetivo de la fase:** dejar el repositorio sin nada del proyecto anterior,
compilando en verde, y con ANTLR generando el lexer y el parser de Compiscript
desde el archivo `.g4`.

**Por qué va primero:** si se arranca a escribir semántica sobre el repo actual,
se arrastran unas 4,000 líneas de código muerto que confunden, veinte archivos de
test que fallan, y tres singletons mutables que ya habían dado problemas. Es
media jornada de trabajo que ahorra semanas de fricción.

**Al terminar:** repo limpio, en verde, ANTLR funcionando, y un `.cps` de ejemplo
que ya parsea. **Cero semántica todavía** — eso es correcto, esta fase es la base.

**Estimación:** una o dos sesiones. La mayor parte es borrar.

---

## Ticket 0.1 — Eliminar la documentación del proyecto anterior

- **Estado**: completado
- **Depende de**: ninguno

**Archivos eliminados:**

- `docs/ROADMAP.md`
- `docs/PROJECT_2_PLAN.md`
- `docs/plans/` (completa, 2 archivos)

Queda solo `docs/roadmap/` (este plan) y lo que se cree de aquí en adelante.

**Sin tag de respaldo.** El equipo decidió no etiquetar: el trabajo del proyecto
anterior ya se calificó y no hace falta conservarlo señalizado. Los archivos siguen
siendo recuperables desde el historial mientras el commit exista:

```bash
git show 0319b23:docs/PROJECT_2_PLAN.md
```

**Por qué eliminar y no dejar:**

`PROJECT_2_PLAN.md` describe módulos que van a dejar de existir (LR(0), LR(1)) y
tipos con nombres distintos a los reales — ya era un problema con el código actual.
`docs/plans/` documenta el reescritor de precedencia y la terminología SLR/LALR, que
se van con los parsers. Si se quedan en `docs/`, alguien —un compañero o el
catedrático— los va a leer y buscar archivos que no existen.

Los entregables de documentación que pide el enunciado (arquitectura del proyecto
y cómo ejecutarlo) son **nuevos**, así que borrar los viejos no quita nada de la
nota.

**Aceptación:**

- `docs/` no contiene nada del proyecto anterior. ✅

---

## Ticket 0.2 — Eliminar los módulos que ANTLR reemplaza

- **Estado**: pendiente
- **Depende de**: 0.1

**Archivos a eliminar:**

| Carpeta / archivo | Archivos | Qué era |
|---|---|---|
| `frontend/lexicalAnalyzer/` (completa) | 17 | El generador de lexer: regex → AFD → scanner |
| `frontend/syntaxAnalyzer/` (completa) | 41 | Gramática, FIRST/FOLLOW, LL(1), SLR(1), LALR(1), `runtime/`, visualización |
| `frontend/models/` (completa) | 2 | `Token` y `TokenEntry`: ANTLR trae los suyos |
| `symbolTable/` (completa) | 2 | La tabla de símbolos del **lexer**: lista plana de lexemas |
| `LexerApp.kt`, `PreprocessorApp.kt` | 2 | Los dos entry points de CLI |
| `resources/*.yal`, `*.yalp`, `*.yaml`, `output/`, `cadenas.txt` | — | Especificaciones y AFD serializados |
| `app/generatedTrees/` | — | PNGs de árboles sintácticos de regex |

**Tests a eliminar** (22 archivos):

`JavaGrammarSpecTest`, `JavaLexerSpecTest`, `LexerTest`, `YalpReaderTest`,
`GrammarValidatorTest`, `PrecedenceRewriterTest`, `PrecedenceOverrideTest`,
`LeftRecursionRewriterTest`, `FirstSetComputerTest`, `FollowSetComputerTest`,
`LL1TableBuilderTest`, `LL1ParserTest`, `LL1ProjectGrammarTest`,
`SLR1AutomataBuilderTest`, `SLR1TableBuilderTest`, `SLR1ParserTest`,
`LALR1AutomatonMergerTest`, `LALR1TableBuilderTest`, `LALR1ParserTest`,
`TableFormatterTest`, `DotExporterTest`, `TokenStreamTest`.

**Los once archivos que sobreviven:**

| Se queda | Por qué |
|---|---|
| `models/LexemeLocation.kt` | Es exactamente lo que se necesita: `(line, position)` 1-based |
| `diagnostics/CompilerError.kt` | Se reescribe en el ticket 0.6 |
| `GuiApp.kt`, `gui/App.kt` | El punto de entrada de la aplicación |
| `gui/state/AppState.kt` | Se reescribe mínimo en el ticket 0.3 |
| `gui/screens/WorkspaceScreen.kt` | Se simplifica en el ticket 0.3 |
| `gui/components/CodeEditor.kt` | El editor de texto |
| `gui/components/FileMenu.kt` | Abrir y guardar |
| `gui/components/PlayButton.kt` | El botón de compilar |
| `gui/components/ErrorList.kt` | La lista de errores |
| `gui/components/ViewMenu.kt` | Se simplifica en el ticket 0.3 |

`diagnostics/DiagnosticsTable.kt` no está en la lista: lo elimina el ticket 0.6, que
es el que lo reemplaza.

**Por qué se borra todo de una y no por partes:** una versión anterior de este ticket
aplazaba `frontend/models/` y `syntaxAnalyzer/runtime/` hasta el ticket 1.4. El único
que los usa es `AppState`, que se reescribe en el ticket **siguiente** (0.3):

```kotlin
// gui/state/AppState.kt — los imports que desaparecen en 0.3
import org.compiler.frontend.syntaxAnalyzer.runtime.Pipeline
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import org.compiler.frontend.syntaxAnalyzer.runtime.models.PipelineResult
```

Aplazarlos los dejaba muertos en el repo durante cinco tickets (0.2 a 1.3), que es
justo lo que esta fase existe para evitar. Borrando todo aquí queda **una sola
frontera**: antes del ticket, 83 archivos; después, 11.

**Por qué se borra:** es cerca del 87% del código actual. Ese trabajo **ya se
calificó** — fue el Proyecto 1 y la fase anterior del 2. Dejarlo en el repo no suma
nota al proyecto nuevo y sí resta claridad. Lo que se aprendió construyéndolo a mano
es lo que permite explicar qué hace ANTLR por dentro, y eso no vive en los archivos.

**Aceptación:**

- `find app/src/main -name "*.kt" | wc -l` baja de **83 a 11**.
- El proyecto **no compila todavía**: es esperado, la GUI referencia lo borrado y
  eso lo arregla el ticket 0.3.

---

## Ticket 0.3 — Dejar el proyecto compilando con una GUI mínima

- **Estado**: pendiente
- **Depende de**: 0.2

**Archivos:**

| Archivo | Acción |
|---|---|
| `gui/components/MethodDropdown.kt` | ELIMINAR (ya no hay LL1/SLR1/LALR1 que elegir) |
| `gui/screens/AutomatonScreen.kt` | ELIMINAR (no hay autómatas) |
| `gui/screens/TablesScreen.kt` | ELIMINAR (no hay tablas de parsing) |
| `gui/components/ParseTreeView.kt`, `ParseTreeCanvas.kt`, `ParseTreeImage.kt` | ELIMINAR (se reescriben en la Fase 7 contra los árboles nuevos) |
| `gui/components/TokenList.kt` | ELIMINAR |
| `gui/components/ViewMenu.kt` | SIMPLIFICAR: por ahora solo `WORKSPACE` |
| `gui/state/AppState.kt` | REESCRIBIR mínimo |
| `gui/screens/WorkspaceScreen.kt` | SIMPLIFICAR |
| `gui/components/CodeEditor.kt`, `FileMenu.kt`, `PlayButton.kt`, `ErrorList.kt` | SE QUEDAN |
| `app/src/test/kotlin/org/compiler/AppStateTest.kt` | AJUSTAR |

**Qué se hace:** dejar la GUI en su forma más simple que compile y arranque —
**un editor de texto, un botón de compilar, y una lista de errores vacía**. Sin
pestañas de resultados todavía.

### Los tres editores pasan a ser uno

| Hoy | Qué era | Ahora |
|---|---|---|
| `yalexContent` | la especificación léxica `.yal` | se elimina: la escribe ANTLR |
| `yalpContent` | la gramática `.yalp` | se elimina: es `Compiscript.g4` |
| `inputContent` | el programa a analizar | pasa a ser `sourceContent` |

Con eso se van también los tres campos de ruta, las tres funciones `update*`, y
`changeMethod` con `parseWithCachedArtifacts` — 25 líneas cuyo único trabajo era
**re-parsear al cambiar entre LL(1), SLR(1) y LALR(1)**. Ya no hay métodos que elegir.

### Cómo queda `AppState`

```kotlin
class AppState {

    // El programa que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(DEFAULT_PROGRAM)

    // Ruta del archivo abierto, o null si nunca se abrio ni se guardo uno.
    // La escribe el FileMenu al abrir y al guardar; la lee la barra de titulo.
    var sourceFilePath by mutableStateOf<String?>(null)

    // Banner para lo que NO es error del programa del usuario: no se pudo leer el
    // archivo, o el compilador tuvo un bug. Los errores del programa van a la lista
    // de errores, que llega en la Fase 7.
    var errorMessage by mutableStateOf<String?>(null)

    // Este SI queda con `private set`, porque no es un dato: es el estado de una
    // operacion en curso. Lo prende markRunning() y lo apaga el `finally` de
    // onCompile(), y ese par no se puede romper desde afuera.
    var isRunning by mutableStateOf(false)
        private set

    // Deshabilita el boton de inmediato. La GUI llama a esto en el hilo de UI
    // ANTES de despachar la compilacion a un hilo de fondo, asi un doble clic no
    // puede arrancar dos corridas.
    fun markRunning() {
        isRunning = true
    }

    fun onCompile() {
        // Placeholder hasta la Fase 7, cuando exista CompilerPipeline.
    }

    private companion object {
        private val DEFAULT_PROGRAM = """
            let mensaje: string = "Hola Compiscript";
            print(mensaje);
        """.trimIndent()
    }
}
```

Y los sitios de uso quedan directos:

```kotlin
// CodeEditor
onValueChange = { state.sourceContent = it }

// FileMenu, abrir
state.sourceContent = file.readText()
state.sourceFilePath = file.absolutePath
state.errorMessage = null

// FileMenu, guardar como
state.sourceFilePath = file.absolutePath
```

### La regla de `private set`: cuando hay invariante, no por costumbre

Una versión anterior de este ticket ponía `private set` en los cuatro campos, más una
función `updateSource(content, filePath)` y dos alias (`reportFileError`,
`clearError`). Se descartó, y vale documentar por qué, porque la regla aplica al
resto del proyecto.

**El bug real del código actual** es que `WorkspaceScreen` escribe
`state.yalexContent = it` directo, saltándose `updateYalexContent` y su registro de la
ruta. Pero la causa no es que el setter sea público: es que existen **dos** formas y
la GUI usa la equivocada. Borrando la función, la duplicación desaparece igual.

**Y la función forzada tenía su propio olor.** En el `FileMenu` actual, "Save As" solo
cambia la ruta, así que le devuelve el contenido de rebote:

```kotlin
state.updateYalexContent(state.yalexContent, path)
//                       ^^^^^^^^^^^^^^^^^^ el contenido que NO esta cambiando
```

Los tres sitios que escriben necesitan cosas distintas: el editor solo el contenido,
abrir un archivo los dos, y "Save As" solo la ruta. Un par de campos independientes
encaja con los tres; una función que recibe ambos, con uno solo.

`isRunning` es el único con invariante real —se prende antes de despachar y se apaga
en el `finally`—, así que es el único que conserva `private set`.

### Lo que se conserva del patrón actual porque es correcto

- `markRunning()` llamado en el hilo de UI antes de despachar.
- La compilación en `Dispatchers.Default`, para que la ventana no se congele.
- El `catch (throwable: Throwable)` — **no** `Exception`. Un `StackOverflowError` es
  un `Error`, no una `Exception`, y una expresión con 200 paréntesis anidados lo
  produce. Con `Exception` la ventana muere; con `Throwable` sale un banner.

**Por qué:** se necesita el proyecto en verde para avanzar con confianza. Un repo
que no compila hace imposible saber si el siguiente cambio rompió algo.

**Aceptación:**

- `./gradlew build` pasa.
- `./gradlew runGui` abre una ventana con editor, botón y lista de errores vacía.
- `AppStateTest` en verde.
- `grep -rn "yalex\|yalp\|ParserMethod" app/src/main` no devuelve nada: no quedó
  ninguna referencia a los tres editores ni a la elección de método de parsing.

---

## Ticket 0.4 — Configurar ANTLR en Gradle

- **Estado**: pendiente
- **Depende de**: 0.3

**Archivos:**

- `app/build.gradle.kts` (modificar)
- `gradle/libs.versions.toml` (agregar ANTLR)
- `app/src/main/antlr/Compiscript.g4` (copiar el archivo entregado)
- `app/src/test/kotlin/org/compiler/AntlrSmokeTest.kt` (NUEVO)
- `README.md` (documentar el comando)

**Qué es esto, en simple:** ANTLR es un programa que **lee el archivo de gramática
y escribe código Java**. Se le da `Compiscript.g4` y genera cuatro clases:

| Clase generada | Qué hace |
|---|---|
| `CompiscriptLexer` | Parte el texto en tokens |
| `CompiscriptParser` | Construye el árbol de análisis |
| `CompiscriptBaseListener` | Plantilla vacía para recorrer el árbol con eventos |
| `CompiscriptBaseVisitor<T>` | Plantilla vacía para recorrer el árbol devolviendo valores |

Ninguna de las cuatro se escribe a mano. Lo que se configura aquí es que Gradle
corra el generador automáticamente antes de compilar.

**Configuración:**

```kotlin
plugins {
    kotlin("jvm")
    antlr                                      // el plugin viene con Gradle
    // ... los de Compose que ya existen
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")                    // el generador
    implementation("org.antlr:antlr4-runtime:4.13.2")    // lo que el código generado necesita al correr
}

tasks.generateGrammarSource {
    // -visitor y -listener son OBLIGATORIOS:
    // por defecto ANTLR solo genera el listener.
    arguments = arguments + listOf(
        "-visitor",
        "-listener",
        "-package", "org.compiler.parser"
    )
}

// Necesario en proyectos Kotlin: el compilador de Kotlin debe correr DESPUÉS de
// que ANTLR haya generado las clases Java, no antes.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.generateGrammarSource)
}
```

**Por qué el `dependsOn` explícito:** sin él, Gradle a veces intenta compilar
Kotlin antes de que exista `CompiscriptParser.java` y falla con
`unresolved reference` de forma **intermitente** — el peor tipo de error, porque a
veces funciona y parece que el problema es otro.

**Por qué el runtime de Java y no `antlr-kotlin` de Strumenta:** el de Strumenta
es para Kotlin Multiplatform. Este proyecto es JVM puro, y Kotlin consume clases
Java sin fricción. El plugin estándar tiene toda la documentación y cero sorpresas.

**Un cuidado que va a aparecer en toda la Fase 2:** Kotlin ve los tipos de Java
como *platform types*. `ctx.expression()` es `ExpressionContext!` — Kotlin **no
avisa** que puede ser `null`, y en reglas opcionales (`initializer?`,
`typeAnnotation?`, `expression?`) **sí lo es**. Va a ser la fuente número uno de
NPEs. Hay que ser explícito al consumir contextos:

```kotlin
val declaredType: TypeReference? = ctx.typeAnnotation()?.let { buildTypeReference(it.type()) }
```

**Aceptación:**

- `./gradlew generateGrammarSource` produce `CompiscriptLexer.java`,
  `CompiscriptParser.java`, `CompiscriptBaseListener.java` y
  `CompiscriptBaseVisitor.java` en
  `app/build/generated-src/antlr/main/org/compiler/parser/`.
- Este test de humo compila y pasa:

  ```kotlin
  @Test
  fun `ANTLR genera un parser que acepta un programa minimo`() {
      val lexer = CompiscriptLexer(CharStreams.fromString("let x: integer = 1;"))
      val parser = CompiscriptParser(CommonTokenStream(lexer))
      val tree = parser.program()
      assertEquals(0, parser.numberOfSyntaxErrors)
      assertNotNull(tree)
  }
  ```

- `./gradlew clean build` en verde desde cero.

---

## Ticket 0.5 — Extender `Compiscript.g4` con `float`

- **Estado**: pendiente
- **Depende de**: 0.4

**Archivos:**

- `app/src/main/antlr/Compiscript.g4` (modificar)
- `docs/decisiones-gramatica.md` (NUEVO)

**Qué se hace:** tres cambios mínimos y **nada más**.

```antlr
// 1. Agregar 'float' a los tipos base
baseType: 'boolean' | 'integer' | 'float' | 'string' | Identifier;

// 2. Agregar el literal flotante a Literal
Literal
  : IntegerLiteral
  | FloatLiteral
  | StringLiteral
  ;

// 3. Declarar la regla léxica antes de IntegerLiteral
FloatLiteral: [0-9]+ '.' [0-9]+;
IntegerLiteral: [0-9]+;
```

### Dos detalles que importan

**Dígitos obligatorios a ambos lados del punto.** `[0-9]+ '.' [0-9]+` exige
`3.14` y **rechaza** `3.` y `.5`. Es a propósito: si se permitiera `3.`, entonces
`3.foo` sería ambiguo entre "flotante incompleto" y "acceso a propiedad del número
3", y el punto del flotante chocaría con el punto de
`suffixOp: '.' Identifier`. Exigiendo dígitos a los dos lados, `3.14` es un
flotante y `x.foo` es acceso a propiedad, sin ambigüedad posible.

**El orden de las reglas léxicas.** El lexer de ANTLR usa **coincidencia más
larga** — la misma regla de *longest match* que se implementó a mano en el
`Scanner` del proyecto anterior. Ante `3.14`, `IntegerLiteral` coincide con 1
carácter (`3`) y `FloatLiteral` con 4: gana el más largo. El orden solo decide
empates, pero se declara `FloatLiteral` primero por convención y claridad.

### Reglas de tipo que este cambio obliga a implementar

Se implementan en la Fase 4; se anotan aquí para que no se olviden:

1. `float op float → float` para `+ - * /`
2. **Ensanchamiento**: `integer op float → float` y `float op integer → float`
3. Asignar `integer` a variable `float` es **legal** (se ensancha)
4. Asignar `float` a variable `integer` es **error** (habría pérdida de precisión,
   y el lenguaje no tiene casts para autorizarla)
5. `%` (módulo) **solo aplica a `integer`** — decisión 12 del README
6. Comparaciones `< <= > >=` y `== !=` entre `integer` y `float` son legales

### `docs/decisiones-gramatica.md`

Documento corto con cada cambio al `.g4`, la razón, y la cita del enunciado que lo
justifica. Arranca con `float`: el enunciado pide aritmética sobre `integer` o
`float`, y la gramática de ejemplo no tenía `float`. Este es el documento que se
abre si preguntan *"¿por qué modificaste la gramática oficial?"*.

**Aceptación:**

- `let pi: float = 3.14;` parsea sin errores sintácticos.
- `let x: float = 3.;` produce error sintáctico.
- `let y = 1.5 + 2;` parsea (la validación del tipo llega en la Fase 4).
- `docs/decisiones-gramatica.md` existe y explica el cambio con su justificación.

---

## Ticket 0.6 — `Diagnostics`: de singleton a instancia, y los tres niveles de error

- **Estado**: pendiente
- **Depende de**: 0.3

**Archivos:**

- `diagnostics/CompilerError.kt` (modificar)
- `diagnostics/Diagnostics.kt` (NUEVO — reemplaza `DiagnosticsTable.kt`)
- `diagnostics/DiagnosticsTable.kt` (ELIMINAR)
- `app/src/test/kotlin/org/compiler/DiagnosticsTest.kt` (NUEVO)

**Qué es esto, en simple:** hoy hay un `object DiagnosticsTable`. En Kotlin,
`object` significa que **existe una sola copia para todo el programa**, como una
pizarra global. Funcionaba, pero con una condición incómoda: había que acordarse
de **borrarla** al empezar cada compilación, y si dos compilaciones corrían a la
vez se pisaban.

El cambio es hacerla una **clase normal**: cada compilación crea su propia
pizarra, la llena, y la tira. No hay que limpiar nada porque nunca se reusa.

**Cómo queda:**

```kotlin
// Un error del compilador. Tres variantes porque el enunciado exige reportar los
// tres niveles, y la GUI los etiqueta distinto.
sealed interface CompilerError {
    val location: LexemeLocation
    val message: String

    // Los producen el lexer y el parser de ANTLR, vía DiagnosticsErrorListener.
    data class LexerError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    data class ParserError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError

    // Los producen las fases semánticas (3, 4 y 5).
    data class SemanticError(
        override val location: LexemeLocation,
        override val message: String
    ) : CompilerError
}
```

```kotlin
// Colector de errores de UNA compilación.
//
// Es una clase, no un `object`: cada corrida crea la suya, así que no hay estado
// global que limpiar ni que se pise entre corridas. Fue un problema real en el
// proyecto anterior.
class Diagnostics {
    private val errors = mutableListOf<CompilerError>()

    fun report(error: CompilerError) {
        errors.add(error)
    }

    // Ordenados por posición en el fuente: así la lista de la GUI se lee de
    // arriba hacia abajo igual que el código.
    fun all(): List<CompilerError> =
        errors.sortedWith(compareBy({ it.location.line }, { it.location.position }))

    fun lexical(): List<CompilerError.LexerError> = errors.filterIsInstance()
    fun syntactic(): List<CompilerError.ParserError> = errors.filterIsInstance()
    fun semantic(): List<CompilerError.SemanticError> = errors.filterIsInstance()

    val hasErrors: Boolean get() = errors.isNotEmpty()
    val count: Int get() = errors.size
}
```

**Sin `Severity`.** Como no se piden warnings, `CompilerError` tiene un solo
nivel. Fuera el enum `WARNING`/`ERROR` que tenía el viejo `ValidationError`. Esto
simplifica: la GUI no decide iconos ni filtros por severidad, y `hasErrors` es una
pregunta binaria.

El *"código muerto"* que sí pide el enunciado va como **error**, no como warning
(lo produce el `FlowAnalyzer` en la Fase 5). Y la información de vivacidad para el
recolector de basura no va aquí: es otra vista, no un diagnóstico.

**Por qué ahora:** de los problemas detectados en el repo actual, este es el
**único que no se resuelve solo** al borrar código. Los otros (categorías de
whitespace escritas a mano en el scanner, dos representaciones distintas de
épsilon, determinismo apoyado en que `toSet()` devuelve un `LinkedHashSet`)
desaparecen con los módulos que se van. Este se arrastraría al proyecto nuevo, y
en la Fase 3 se va a crear más estado (el árbol de ámbitos, los símbolos):
si copian el patrón de singleton, quedan con el mismo problema multiplicado por
tres.

**Limpieza relacionada:** el `Pipeline` viejo lanzaba `error(...)` cuando la
validación fallaba, aunque su propio plan decía que debía **retornar una lista**
para que la GUI mostrara todos los errores juntos. Con `Diagnostics` como
instancia que se pasa a cada fase, el pipeline nuevo (Fase 7) nunca lanza por
errores del usuario: los acumula y los devuelve. Las excepciones quedan
exclusivamente para bugs del compilador.

**Aceptación:**

- `SemanticError` existe con `location` y `message`.
- Dos instancias distintas de `Diagnostics` no comparten errores (test explícito).
- `all()` devuelve los errores ordenados por línea y luego por columna.
- `grep -rn "object Diagnostics\|DiagnosticsTable" app/src` no devuelve nada.

---

## Resumen de la fase

| Ticket | Qué deja listo |
|---|---|
| 0.1 | Docs del proyecto anterior eliminados, con tag de respaldo |
| 0.2 | 83 archivos `.kt` reducidos a 11 (el proyecto queda roto a propósito) |
| 0.3 | Proyecto compilando; GUI mínima; un solo camino de escritura del editor |
| 0.4 | ANTLR generando lexer, parser, listener y visitor desde el `.g4` |
| 0.5 | `float` en la gramática, con el documento de decisiones iniciado |
| 0.6 | `Diagnostics` como instancia, con los tres niveles de error |
