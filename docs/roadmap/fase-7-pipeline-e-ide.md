# Fase 7 — Pipeline e IDE

**Objetivo de la fase:** conectar todas las fases en una sola llamada, y mostrar
todo lo que producen en la interfaz.

**Cuánto vale:** el IDE son **15 puntos directos** de la rúbrica, y la pantalla de
tabla de símbolos es la cara visible de los **25 puntos** de esa componente. Es
decir: esta fase expone 40 de los 100 puntos. No es el adorno del final.

**Estimación:** tres o cuatro sesiones.

---

## Ticket 7.1 — `CompilerPipeline` y `CompilationResult`

- **Estado**: completado
- **Depende de**: 6.2

**Archivos:**

- `runtime/CompilerPipeline.kt` (NUEVO)
- `runtime/models/CompilationResult.kt` (NUEVO)
- `frontend/ast/models/TreeNodeView.kt` (NUEVO — adelantado del 7.3)
- `frontend/syntax/ParseTreeView.kt` (NUEVO — adelantado del 7.3)
- `app/src/test/kotlin/org/compiler/CompilerPipelineTest.kt` (NUEVO)

`TreeNodeView` y la conversión del árbol de ANTLR estaban descritas en el ticket
7.3, pero el pipeline las necesita: sin ellas el `ProgramContext` saldría de esta
función y el criterio de la frontera no se podría cumplir. `Program.toTreeView()`
—la conversión del AST— sí se queda en el 7.3, porque `CompilationResult` guarda el
`Program` directo.

**Qué es esto, en simple:** una sola función que recibe el texto del programa y
devuelve **todo**: el árbol, la tabla de símbolos, los errores y la salida de
ejecutar. La GUI llama a esto una vez y ya tiene todo lo que necesita
mostrar; no orquesta nada.

```kotlin
// Encadena todas las fases del compilador.
//
// Se detiene en cuanto una fase produce errores: no tiene sentido verificar tipos
// sobre un árbol que no se pudo construir, ni ejecutar un programa con errores.
// Pero SIEMPRE devuelve lo que alcanzó a producir, para que la GUI pueda mostrar
// resultados parciales.
object CompilerPipeline {

    fun compile(source: String, execute: Boolean = true): CompilationResult {
        val diagnostics = Diagnostics()

        // ── Etapa A: sintaxis (ANTLR) ─────────────────────────────────
        val parseTree = SyntaxAnalyzer.parse(source, diagnostics)
        if (parseTree == null) {
            return CompilationResult.failed(diagnostics, source)
        }

        // El arbol de ANTLR se convierte a TreeNodeView AQUI y no en la GUI, para
        // que el tipo CompiscriptParser.ProgramContext no salga de esta funcion.
        // Es lo que sostiene la frontera declarada en la Fase 2: nada despues del
        // AstBuilder importa clases de org.compiler.parser.
        val parseTreeView = parseTree.toTreeView()

        // ── Etapa B: AST propio ───────────────────────────────────────
        val ast = AstBuilder().visit(parseTree) as Program

        // ── Etapa C: pasada 1, declaraciones ──────────────────────────
        val collector = DeclarationCollector(diagnostics)
        collector.collect(ast)

        // ── Etapa D: pasada 2, tipos ──────────────────────────────────
        // Se corre AUNQUE la etapa C haya reportado errores: así el usuario ve
        // todos sus problemas de una vez y no de uno en uno.
        TypeChecker(collector.globalScope, diagnostics).check(ast)

        // ── Etapa E: flujo y vivacidad ────────────────────────────────
        FlowAnalyzer(diagnostics).analyze(ast)
        val garbageCollectorReport = LivenessReportBuilder().build(collector.globalScope)

        // ── Etapa F: ejecución ────────────────────────────────────────
        // SOLO si no hay ningún error. Ejecutar código mal tipado da basura.
        val execution =
            if (execute && !diagnostics.hasErrors) Interpreter().run(ast)
            else null

        return CompilationResult(
            source = source,
            parseTreeView = parseTreeView,
            ast = ast,
            globalScope = collector.globalScope,
            garbageCollectorReport = garbageCollectorReport,
            errors = diagnostics.all(),
            execution = execution
        )
    }
}
```

### Por qué la etapa D corre aunque la C haya fallado

Si el pipeline se detuviera al primer error de declaración, un archivo con una
variable mal declarada y cinco errores de tipo mostraría **un** error. El usuario lo
arreglaría, recompilaría, y vería el siguiente. Corriendo las dos etapas se muestran
los seis de una vez.

Esto funciona porque `ErrorType` corta las cascadas: un símbolo que no se pudo
declarar bien tiene tipo `ErrorType`, y cualquier operación con `ErrorType` no genera
errores nuevos. Sin ese mecanismo, seguir después de un error produciría una lluvia
de mensajes falsos.

### El resultado

```kotlin
// Todo lo que produce una compilación. La GUI lee de aquí y no llama a nada más.
//
// Fijate en lo que NO aparece: ningun tipo de org.compiler.parser. El arbol de ANTLR
// entra como TreeNodeView, ya convertido por el pipeline.
data class CompilationResult(
    val source: String,
    val parseTreeView: TreeNodeView?,   // el árbol de ANTLR, ya neutralizado
    val ast: Program?,                  // el árbol propio, decorado
    val globalScope: Scope?,
    val garbageCollectorReport: GarbageCollectorReport?,
    val errors: List<CompilerError>,
    val execution: ExecutionResult?
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    val lexicalErrors: List<CompilerError> get() = errors.filterIsInstance<CompilerError.LexerError>()
    val syntaxErrors: List<CompilerError> get() = errors.filterIsInstance<CompilerError.ParserError>()
    val semanticErrors: List<CompilerError> get() = errors.filterIsInstance<CompilerError.SemanticError>()

    companion object {
        // Cuando la sintaxis falla no hay AST ni tabla de símbolos, pero SÍ hay
        // errores que mostrar.
        fun failed(diagnostics: Diagnostics, source: String) = CompilationResult(
            source = source,
            parseTreeView = null, ast = null, globalScope = null,
            garbageCollectorReport = null,
            errors = diagnostics.all(), execution = null
        )
    }
}
```

**Todos los campos de resultado son nullables a propósito.** Si el archivo no
parsea, no hay AST. La GUI tiene que saber mostrar *"no disponible"* en vez de
reventar, y los tipos nullables la obligan a manejarlo.

### Aceptación

- Un programa válido devuelve un `CompilationResult` con **todos** los campos
  poblados y `errors` vacía.
- Un programa con error sintáctico devuelve `ast = null` y al menos un
  `ParserError`.
- **`grep -rn "org.compiler.parser" app/src/main/kotlin/org/compiler/gui` no devuelve
  nada**: la GUI no conoce ANTLR.
- Un programa con error de tipos devuelve `ast` poblado, `execution = null`, y al
  menos un `SemanticError`.
- Un programa con **un error de declaración y tres de tipos** devuelve **los
  cuatro** en la misma corrida. *Test que justifica el diseño de arriba.*
- `compile(source, execute = false)` no ejecuta aunque no haya errores.
- El pipeline **nunca lanza** por errores del usuario: solo por bugs del compilador.

---

## Ticket 7.2 — GUI: `AppState`, editor, errores y consola

- **Estado**: completado
- **Depende de**: 7.1

**Archivos:**

- `gui/state/AppState.kt` (ampliar el de 0.3)
- `gui/components/ErrorList.kt` (adaptar)
- `gui/components/OutputConsole.kt` (NUEVO)
- `gui/components/CodeEditor.kt` (adaptar: línea resaltada)
- `gui/screens/WorkspaceScreen.kt` (ampliar)
- `gui/components/FileMenu.kt` (adaptar: extensión `.cps`)
- `app/src/test/kotlin/org/compiler/AppStateTest.kt` (ampliar)

**Cómo se resolvió el salto al error.** Mover el cursor exige un `TextFieldValue`,
y eso obligaría a cambiar `AppState.sourceContent`, el `FileMenu` y los tests. En su
lugar `AppState` lleva un `highlightedLine: Int?`, y el `CodeEditor` resalta ese
número en el margen y hace scroll hasta él. El texto sigue siendo un `String` y no
hay dos fuentes de verdad.

### `AppState`

Amplía el del ticket 0.3 con **un solo campo nuevo**: el resultado de compilar. La
regla de `private set` sigue siendo la del 0.3 —solo donde hay un invariante que
proteger—, así que los tres campos que un solo lugar escribe siguen públicos.

```kotlin
class AppState {

    // El programa que se esta editando. Lo escribe el CodeEditor.
    var sourceContent by mutableStateOf(DEFAULT_PROGRAM)

    // Ruta del archivo abierto. La escribe el FileMenu; la lee la barra de titulo.
    var sourceFilePath by mutableStateOf<String?>(null)

    // Banner para lo que NO es error del programa del usuario: no se pudo leer el
    // archivo, o el compilador tuvo un bug.
    var errorMessage by mutableStateOf<String?>(null)

    // NUEVO en esta fase: todo lo que produjo la ultima compilacion.
    //
    // `private set` porque solo onCompile() lo escribe, y siempre en par con
    // isRunning. La GUI solo lee.
    var result by mutableStateOf<CompilationResult?>(null)
        private set

    // Estado de una operacion en curso, no un dato. Lo prende markRunning() y lo
    // apaga el `finally` de onCompile(): ese par no se puede romper desde afuera.
    var isRunning by mutableStateOf(false)
        private set

    // Deshabilita el boton de inmediato. La GUI llama a esto en el hilo de UI ANTES
    // de despachar la compilacion a un hilo de fondo, asi un doble clic no puede
    // arrancar dos corridas.
    fun markRunning() {
        isRunning = true
    }

    fun onCompile() {
        isRunning = true
        errorMessage = null
        try {
            result = CompilerPipeline.compile(sourceContent)
        } catch (throwable: Throwable) {
            // Throwable y no Exception: un StackOverflowError de un programa muy
            // anidado debe salir como banner, no matar la ventana.
            //
            // El pipeline NUNCA lanza por errores del usuario: esos van a la lista de
            // errores. Si algo llega aqui, es un bug del compilador.
            result = null
            errorMessage = throwable.message
                ?: throwable::class.simpleName
                ?: "Error desconocido del compilador"
        } finally {
            isRunning = false
        }
    }
}
```

Se conserva el patrón que ya funcionaba: `markRunning()` en el hilo de UI antes de
despachar, compilación en `Dispatchers.Default`, y `catch (Throwable)`.

### La lista de errores con los tres niveles

El enunciado exige reportar errores léxicos, sintácticos **y** semánticos. La lista
los muestra etiquetados y ordenados por posición:

```
[léxico]     línea 3, col 12   Carácter no reconocido: '@'
[sintáctico] línea 5, col 8    Se esperaba ';'
[semántico]  línea 9, col 5    No se puede asignar 'string' a 'x', declarada como 'integer'
[semántico]  línea 14, col 3   Código inalcanzable: nunca se ejecuta porque 'return' de la línea 13 corta el flujo
```

Tres cosas que hay que hacer bien:

1. **Ordenados por línea y columna**, no por fase. El usuario lee su código de
   arriba hacia abajo; la lista debe seguir el mismo orden. `Diagnostics.all()` ya
   los devuelve así.
2. **Clic en un error salta a esa línea del editor.** Es la diferencia entre una
   lista útil y una decorativa.
3. **Un color por nivel**, pero legible: la etiqueta de texto es lo que informa, el
   color solo acompaña.

### La consola de salida

```kotlin
// Muestra lo que imprimió print() y, si lo hubo, el error de ejecución.
@Composable
fun OutputConsole(execution: ExecutionResult?, modifier: Modifier = Modifier) { ... }
```

Tres estados que hay que distinguir con mensajes distintos, porque significan cosas
distintas:

| Estado | Qué muestra |
|---|---|
| `execution == null` y hay errores | *"El programa no se ejecutó porque tiene errores."* |
| `execution == null` y no hay errores | *"Presiona compilar para ejecutar."* |
| `execution != null` | las líneas de salida, y el error de ejecución al final si lo hubo |

### El programa por defecto

El IDE arranca con un programa que **ejercita todo lo visible**, para que al abrir la
aplicación ya haya algo que mostrar en las cuatro pestañas:

```cps
// Programa de demostración de Compiscript

class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }
}

class Perro : Animal {
  function hablar(): string {
    return this.nombre + " ladra.";
  }
}

function factorial(n: integer): integer {
  if (n <= 1) { return 1; }
  return n * factorial(n - 1);
}

let perro: Perro = new Perro("Toby");
print(perro.hablar());

let notas: integer[] = [90, 85, 100];
foreach (nota in notas) {
  if (nota < 60) { continue; }
  print(nota);
}

print(factorial(5));
print(3 + 5 * 2);
```

Toca clases, herencia, `this`, constructor, recursión, arreglos, `foreach`,
`continue`, y una expresión que el plegado resuelve. Y **debe compilar y ejecutar sin
un solo error**: es la demostración del día de la presentación.

### Aceptación

- Escribir código y presionar compilar puebla la lista de errores.
- La ventana no se congela mientras compila.
- Un doble clic en compilar no arranca dos compilaciones.
- Los tres niveles de error aparecen etiquetados, en orden de línea y columna.
- Clic en un error salta a esa línea del editor.
- `print(3 + 5)` muestra `8` en la consola.
- Abrir y guardar archivos `.cps` funciona; el título muestra la ruta.
- Un programa con recursión infinita muestra un mensaje y **no mata la ventana**.
- El programa por defecto compila con cero errores y ejecuta.

---

## Ticket 7.3 — GUI: árbol sintáctico y AST

- **Estado**: pendiente
- **Depende de**: 7.2

**Archivos:**

- `gui/components/TreeCanvas.kt` (NUEVO — reusa la lógica de layout del proyecto anterior)
- `gui/screens/TreesScreen.kt` (NUEVO)
- `gui/components/TreeNodeAdapter.kt` (NUEVO)

**Qué es esto, en simple:** dibujar los dos árboles, lado a lado o con un
interruptor. **La representación visual del árbol sintáctico es un requisito
explícito del enunciado.**

### Un adaptador para dibujar los dos árboles con el mismo componente

El árbol de ANTLR y el AST propio son tipos distintos. En vez de escribir dos
dibujantes, se define una vista mínima común:

```kotlin
// Lo mínimo que el dibujante necesita saber de un nodo. Así el mismo componente
// dibuja el árbol de ANTLR y el AST propio sin conocer ninguno de los dos.
//
// Vive en frontend/ast/models/ y NO en gui/: es lo que permite que el pipeline
// convierta el arbol de ANTLR antes de devolverlo, y que la GUI nunca importe
// org.compiler.parser.
data class TreeNodeView(
    val label: String,
    val detail: String?,        // el tipo decorado, para los nodos del AST
    val children: List<TreeNodeView>
)
```

Y **dos conversiones, en dos paquetes distintos**:

```kotlin
// frontend/syntax/ParseTreeView.kt  -- hecho en el 7.1, que es quien lo llama.
fun CompiscriptParser.ProgramContext.toTreeView(): TreeNodeView = ...

// frontend/ast/AstView.kt  -- el AST propio, con su decoracion
fun Program.toTreeView(): TreeNodeView = ...
```

En el AST, `detail` lleva el **tipo decorado** de cada expresión. Eso convierte la
pantalla en la evidencia visual del trabajo de la Fase 4: se ve `3 + 5` con la
etiqueta `integer = 8`.

### El punto didáctico: comparar los dos árboles

Poner los dos árboles a la vista muestra de un vistazo lo que hace el `AstBuilder`:

| Expresión | Árbol de ANTLR | AST propio |
|---|---|---|
| `x` | 11 nodos encadenados | 1 nodo `Identifier` |
| `3 + 5 * 2` | ~20 nodos | 5 nodos, con la precedencia ya en la forma |

Es la mejor forma de explicar la fase en la presentación, y sale casi gratis del
mismo componente.

### Aceptación

- El árbol de ANTLR se dibuja completo para el programa por defecto.
- El AST se dibuja y es **visiblemente más chico**.
- `TreeCanvas` recibe un `TreeNodeView` y **no sabe** de dónde vino: el mismo
  componente dibuja los dos árboles.
- Cada nodo de expresión del AST muestra su tipo; los constantes muestran su valor.
- Con un programa que no parsea, la pantalla dice *"no disponible"* y no revienta.
- Los árboles se pueden desplazar y hacer zoom (un programa real no cabe en
  pantalla).

---

## Ticket 7.4 — GUI: tabla de símbolos y reporte de vivacidad

- **Estado**: pendiente
- **Depende de**: 7.2

**Archivos:**

- `gui/screens/SymbolTableScreen.kt` (NUEVO)
- `gui/components/ScopeTreeView.kt` (NUEVO)
- `gui/components/GarbageCollectorReportView.kt` (NUEVO)

**Cuánto vale:** el enunciado pide como salida *"estado de la tabla de símbolos por
cada entorno (global, función, clase, bloque)"*, y la componente vale **25 puntos**.
Esta pantalla es su cara visible.

### La tabla de símbolos, navegable por ámbito

Dos paneles: a la izquierda el árbol de ámbitos, a la derecha los símbolos del
ámbito seleccionado.

```
Ámbitos                          Símbolos de: Animal (CLASS)
─────────────────────            ──────────────────────────────────────────
▼ global                         Nombre       Categoría  Tipo            Ofs  Lín
  ▼ Animal              <─       nombre       Campo      string           0    4
      constructor                constructor  Método     (string)->void   1    6
      hablar                     hablar       Método     ()->string       2   10
  ▼ Perro
      hablar
  ▼ factorial
      if@20
  block@25
```

Las cinco columnas salen directo de `Symbol`: nombre, categoría, tipo, offset y
línea. La columna de tipo muestra `symbol.type.name`, que ya viene en el formato en
que el programador lo escribió (`integer[]`, `Perro`, `(string) -> void`).

**La columna "Categoría" se calcula**, combinando los dos ejes de `Symbol`
(ticket 1.2). El `DeclarationKind` dice *qué es* y `isMember` dice *dónde vive*:

```kotlin
private fun categoryLabel(symbol: Symbol): String = when {
    symbol.kind == DeclarationKind.FUNCTION && symbol.isMember -> "Método"
    symbol.kind == DeclarationKind.VARIABLE && symbol.isMember -> "Campo"
    symbol.kind == DeclarationKind.CONSTANT && symbol.isMember -> "Constante de clase"
    symbol.kind == DeclarationKind.VARIABLE -> "Variable"
    symbol.kind == DeclarationKind.CONSTANT -> "Constante"
    symbol.kind == DeclarationKind.PARAMETER -> "Parámetro"
    symbol.kind == DeclarationKind.FUNCTION -> "Función"
    else -> "Clase"
}
```

Se sigue leyendo "Campo" y "Método", que es lo natural — pero como **texto de
presentación**, no como estado que se pueda contradecir con el ámbito donde vive.

**No hay columna de número de tipo** (decisión 13): `Symbol` guarda el `Type`
directamente, no un id. Dos variables del mismo tipo muestran el mismo texto en la
columna `Tipo`, que es la misma evidencia visual sin necesidad de un catálogo aparte.

**Que el árbol de ámbitos se pueda recorrer entero es la demostración visual de la
decisión 7** (los ámbitos no se descartan al cerrarse). Si se hubieran descartado,
este panel mostraría solo `global`.

### El reporte de vivacidad

```
Símbolo    Ámbito       Declarado  Último uso  Usos  Usada en función anidada
─────────  ───────────  ─────────  ──────────  ────  ────────────────────────
nombre     Animal            4          11       3              no
perro      global           25          26       1              no
temporal   if@30            30          —        0              no
cuenta     crearContador     8          12       1              sí
```

Es la respuesta concreta a lo que pidió el catedrático: *"dejar un tipo de meta que
me diga cuándo algo ya no se va a utilizar"*. Cada fila dice hasta cuándo importa un
símbolo.

- **`temporal`** con 0 usos: nunca hizo falta reservarle memoria.
- **`nombre`** con último uso en la línea 11: desde ahí ya se podría liberar, sin
  esperar a que cierre el ámbito.
- **`cuenta`** usada dentro de una función anidada: la dependencia existe y se
  reporta. En Compiscript **no impide liberarla**, porque la función anidada no
  puede sobrevivir a la de afuera (ver ticket 5.2); sería un impedimento real en un
  lenguaje donde las funciones se pueden devolver.

**No hay columna "¿liberable?"** a propósito: en Compiscript la respuesta sería
siempre "sí", y una columna con un solo valor no informa nada.

### Aceptación

- El árbol de ámbitos muestra `global` y todos sus descendientes, incluidos los
  bloques ya cerrados.
- Seleccionar un ámbito muestra sus símbolos con las cinco columnas.
- Un campo heredado se ve en el ámbito de la subclase o se indica de dónde viene.
- **Dos variables del mismo tipo muestran el mismo texto en la columna `Tipo`.**
- El reporte de vivacidad marca las variables no usadas y las capturadas.
- Con un programa que no compila, las dos pantallas dicen *"no disponible"* y no
  revientan.

---

## Resumen de la fase

| Ticket | Deja listo | Puntos que expone |
|---|---|---|
| 7.1 | Una llamada, un resultado con todo | — |
| 7.2 | Editor, errores de los tres niveles, consola de salida | 15 (IDE) |
| 7.3 | Árbol sintáctico y AST dibujados | requisito de salida |
| 7.4 | Tabla de símbolos navegable por ámbito, reporte de vivacidad | 25 (Tabla) |
