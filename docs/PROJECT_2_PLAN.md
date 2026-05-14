# Plan del Proyecto 2 — Generador de Analizadores Sintácticos

Este documento detalla los elementos de código que se construirán para el Proyecto 2 (YAPar) sobre el proyecto existente del Proyecto 1 (YALex). Cada sección lista los archivos, modelos, y funciones que se trabajarán, junto con su responsabilidad.

---

## Tabla de contenidos

1. Estructura de carpetas y archivos
2. Cambios al proyecto actual
3. Módulo Grammar
4. Módulo Sets
5. Módulo LL(1)
6. Módulo LR(0)
7. Módulo SLR(1)
8. Módulo LR(1)
9. Módulo LALR(1)
10. Módulo Runtime
11. Módulo Visualization
12. Módulo GUI
13. CLI: comando `yapar`
14. Archivo de gramática de prueba
15. Detalle algorítmico y glosario

---

## 1. Estructura de carpetas y archivos

La estructura del proyecto refleja la separación entre análisis léxico y análisis sintáctico. Ambas fases viven bajo una carpeta común `frontend/`. Por encima de `frontend/` viven tres paquetes globales que todas las fases del compilador comparten: `models/` (tipos de valor básicos), `symbolTable/` (tabla de símbolos con atributos) y `diagnostics/` (colector de errores léxicos y sintácticos). Esta separación evita que el parser importe desde el lexer y sigue la teoría del Dragon Book donde la tabla de símbolos y el reporte de errores son estructuras globales, no pertenecientes a una sola fase.

### Árbol completo

```
app/src/main/kotlin/org/compiler/
│
├── models/                                [tipos de valor compartidos por todas las fases]
│   └── LexemeLocation.kt                 [línea + posición en el fuente]
│
├── symbolTable/                           [tabla de símbolos global — Dragon Book §2.7]
│   ├── SymbolTable.kt                    [singleton; addOrGet(name, location): Int]
│   └── SymbolTableEntry.kt               [index, name, location]
│
├── diagnostics/                           [colector global de errores del compilador]
│   ├── CompilerError.kt                  [sealed interface: LexerError | ParserError]
│   └── DiagnosticsTable.kt              [singleton; report(), lexerErrors(), parserErrors()]
│
├── frontend/                              [techo común del compilador]
│   │
│   ├── models/                            [modelos compartidos entre fases del frontend]
│   │   └── Token.kt                       [category, lexeme, symbolIndex?]
│   │
│   ├── lexicalAnalyzer/                   [Proyecto 1, casi sin cambios]
│   │   ├── manageGrammar/                 [intacto]
│   │   ├── scanner/                       [usa SymbolTable y DiagnosticsTable globales]
│   │   └── lexer/
│   │
│   └── syntaxAnalyzer/                    [NUEVO — Proyecto 2]
│       │
│       ├── grammar/
│       │   ├── models/
│       │   │   ├── Symbol.kt              [migrado y ampliado]
│       │   │   ├── Production.kt          [migrado y ampliado]
│       │   │   └── Grammar.kt             [migrado y ampliado]
│       │   ├── YalpReader.kt              [nuevo]
│       │   ├── GrammarValidator.kt        [nuevo]
│       │   └── GrammarRewriter.kt         [nuevo]
│       │
│       ├── sets/
│       │   ├── models/
│       │   │   ├── FirstSets.kt           [migrado, renombrado]
│       │   │   └── FollowSets.kt          [migrado, renombrado]
│       │   ├── FirstSetComputer.kt        [migrado, renombrado]
│       │   └── FollowSetComputer.kt       [migrado, renombrado]
│       │
│       ├── ll1/
│       │   ├── models/
│       │   │   ├── LL1Cell.kt             [nuevo]
│       │   │   └── LL1Table.kt            [migrado, renombrado]
│       │   ├── LL1TableBuilder.kt         [migrado, renombrado]
│       │   └── LL1Driver.kt               [nuevo]
│       │
│       ├── lr0/
│       │   ├── models/
│       │   │   ├── LR0Item.kt             [nuevo]
│       │   │   ├── LR0ItemSet.kt          [nuevo]
│       │   │   └── LR0Automaton.kt        [nuevo]
│       │   └── LR0AutomatonBuilder.kt     [nuevo]
│       │
│       ├── slr1/
│       │   ├── models/
│       │   │   ├── Action.kt              [nuevo, sealed interface — compartido con LALR]
│       │   │   └── SLR1Table.kt           [nuevo]
│       │   ├── SLR1TableBuilder.kt        [nuevo]
│       │   └── SLR1Driver.kt              [nuevo]
│       │
│       ├── lr1/                           [NUEVO — items con lookahead]
│       │   ├── models/
│       │   │   ├── LR1Item.kt             [nuevo]
│       │   │   ├── LR1ItemSet.kt          [nuevo]
│       │   │   └── LR1Automaton.kt        [nuevo]
│       │   └── LR1AutomatonBuilder.kt     [nuevo]
│       │
│       ├── lalr1/                         [NUEVO]
│       │   ├── models/
│       │   │   └── LALR1Table.kt          [nuevo]
│       │   ├── LALR1AutomatonBuilder.kt   [nuevo — mergea cores del LR(1)]
│       │   ├── LALR1TableBuilder.kt       [nuevo]
│       │   └── LALR1Driver.kt             [nuevo]
│       │
│       ├── runtime/
│       │   ├── models/
│       │   │   ├── ParseResult.kt         [nuevo, sealed interface]
│       │   │   ├── ParseTree.kt           [nuevo, sealed interface]
│       │   │   ├── ParseStep.kt           [nuevo]
│       │   │   └── ParseError.kt          [nuevo]
│       │   └── TokenStream.kt             [nuevo]
│       │
│       └── visualization/
│           ├── DotExporter.kt             [nuevo — autómatas y árbol]
│           ├── ParseTreeExporter.kt       [nuevo]
│           └── TableFormatter.kt          [nuevo]
│
├── gui/                                   [NUEVO — Compose Desktop]
│   ├── App.kt
│   ├── state/
│   │   └── AppState.kt
│   ├── screens/
│   │   ├── EditorScreen.kt
│   │   ├── AutomatonScreen.kt
│   │   ├── TablesScreen.kt
│   │   └── ResultsScreen.kt
│   └── components/
│       ├── FileMenu.kt
│       └── CodeEditor.kt
│
├── PreprocessorApp.kt                     [existente]
├── LexerApp.kt                            [existente]
├── YaparApp.kt                            [NUEVO — entrada CLI]
└── GuiApp.kt                              [NUEVO — entrada GUI]
```

### Recursos

```
app/src/main/resources/
├── java_lang.yal                          [existente, lexer del Proyecto 1]
├── input.java                             [existente, fuente de prueba del lexer]
├── *.yaml                                 [existente, DFAs serializados]
├── parser.yalp                            [NUEVO — gramática de prueba]
└── cadenas.txt                            [NUEVO — cadenas a analizar]
```

### Cómo se lee el árbol

`frontend/` es el techo común del compilador. Adentro viven el lexer (Proyecto 1), el parser (Proyecto 2) y los modelos que ambas fases comparten.

`frontend/lexicalAnalyzer/` queda como está. Solo se modifica el manejo de `Token` (ver Sección 2), porque el parser necesita ubicar los errores en la fuente y compartir el modelo con el lexer.

`frontend/models/` agrupa los modelos que ambas fases del compilador comparten. Por ahora son únicamente `Token` y `LexemeLocation`. La regla para esta carpeta: solo entra aquí lo que de verdad se comparte entre fases. No se llena de "utilidades varias".

`frontend/syntaxAnalyzer/` es donde vive todo lo nuevo del Proyecto 2. Cada subcarpeta es un módulo independiente con responsabilidad única:

- **`grammar/`** — todo lo relacionado a la gramática como objeto: leer el `.yalp`, validar que los tokens declarados coincidan con los del lexer, y reescribir cuando se requiera para LL(1).
- **`sets/`** — cálculo de FIRST y FOLLOW. Es un módulo aislado porque LL(1) y SLR(1) lo consumen (LALR(1) usa lookaheads propios de los items LR(1)).
- **`ll1/`** — construcción de la tabla LL(1) y el driver predictivo.
- **`lr0/`** — el autómata LR(0) (closure, goto, colección canónica). Vive separado de SLR porque el documento del proyecto pide explícitamente "implementar la construcción de un Autómata LR(0) en su totalidad" como objetivo específico, y mantenerlo aparte facilita explicarlo en preguntas teóricas.
- **`slr1/`** — toma el autómata de `lr0/` y los conjuntos de `sets/` y construye la tabla SLR + el driver shift-reduce.
- **`lr1/`** — items con lookahead (extensión de LR(0) donde cada item carga un terminal de anticipación). Es la base que LALR mergea.
- **`lalr1/`** — toma el autómata LR(1), mergea estados con el mismo core, y construye la tabla LALR + el driver. Es el método más potente que se implementa.
- **`runtime/`** — objetos que los tres drivers comparten: el resultado del parsing (incluyendo el árbol sintáctico), la traza paso a paso, la estructura de errores y el adaptador de tokens.
- **`visualization/`** — utilidades de presentación: exporta autómatas y árboles sintácticos a DOT/Graphviz, y formatea tablas para mostrarse en la GUI.

`models/` dentro de cada módulo sigue la convención que ya existe en `frontend/lexicalAnalyzer/scanner/models/` y `frontend/lexicalAnalyzer/manageGrammar/models/`. Las `data class` y `sealed interface` viven ahí; las funciones y objetos que ejecutan algo viven en la raíz del módulo. Esto hace que al abrir cualquier módulo veas en `models/` qué es lo que se manipula y en la raíz qué se le hace.

`gui/` vive a la par de `frontend/`, no dentro. La GUI no es parte del compilador, es solo una interfaz que lo consume. Si mañana se quitara la GUI, el compilador seguiría funcionando vía CLI.

### Puntos de entrada

Hay cuatro `main()` separados. Cada uno tiene un propósito único:

| Archivo | Para qué sirve |
|---|---|
| `PreprocessorApp.kt` | Genera los YAMLs del lexer (Proyecto 1) |
| `LexerApp.kt` | Corre solo el lexer sobre un archivo fuente (Proyecto 1) |
| `YaparApp.kt` | Comando CLI `yapar parser.yalp -l lexer.yal -o theparser` |
| `GuiApp.kt` | Lanza la interfaz Compose Desktop |

Mantener los `main()` separados permite que cada uno haga una cosa y se pueda invocar desde Gradle con un task distinto, sin colgar lógica en flags.

---

## 2. Cambios al proyecto actual

El Proyecto 1 queda funcional como está, pero hay cinco cambios necesarios para que el parser pueda consumirlo limpiamente. Todos son cambios pequeños y locales — no se altera la arquitectura del lexer.

### 2.1 — Mover `Token.kt` a `frontend/models/`

**Archivo afectado**: `frontend/lexicalAnalyzer/lexer/models/Token.kt` → `frontend/models/Token.kt`

El `Token` es la frontera lexer → parser. Hoy vive dentro del módulo del lexer, lo que obligaría al `syntaxAnalyzer` a importar desde `lexicalAnalyzer`. Moviéndolo a `frontend/models/`, ambos analizadores importan desde un paquete neutral, sin acoplamiento direccional.

Imports a actualizar en tres archivos:
- `frontend/lexicalAnalyzer/scanner/Scanner.kt`
- `frontend/lexicalAnalyzer/scanner/models/TokenEntrys.kt`
- `LexerApp.kt`

### 2.2 — Rediseñar `Token`

**Archivo**: `frontend/models/Token.kt`

```kotlin
data class Token(
    val category: String,
    val lexeme: String,
    val symbolIndex: Int?   // null para KEYWORD; índice en SymbolTable para todo lo demás
)
```

`Token` es la frontera lexer → parser. Siguiendo el Dragon Book (§2.6), el atributo de un identificador o literal es un puntero a la tabla de símbolos. Para keywords el atributo es el lexema mismo. La ubicación en el fuente no vive en el token — vive en la entrada correspondiente de `SymbolTable` (para símbolos) y en `DiagnosticsTable` cuando hay un error.

### 2.3 — Crear `LexemeLocation`

**Archivo**: `org/compiler/models/LexemeLocation.kt`

```kotlin
data class LexemeLocation(val line: Int, val position: Int)
```

`line` y `position` son 1-based. Vive en `org.compiler.models/` (por encima de `frontend/`) porque la consumen `SymbolTableEntry`, `CompilerError`, y eventualmente el árbol sintáctico — tres estructuras que pertenecen a niveles distintos del compilador.

### 2.4 — Modificar `Scanner.kt`

**Archivo**: `frontend/lexicalAnalyzer/scanner/Scanner.kt`

El scanner lleva contadores `currentLine` y `currentPosition`. Al reconocer un token:
- Si la categoría es `KEYWORD`: crea `Token(category, lexeme, symbolIndex = null)`
- Para cualquier otra categoría: llama `SymbolTable.addOrGet(lexeme, location)` y guarda el índice en `symbolIndex`
- En panic mode: reporta `CompilerError.LexerError` a `DiagnosticsTable` con la ubicación y la secuencia inválida

La ubicación se captura al **inicio del lexema** antes de avanzar los contadores.

### 2.5 — Modificar `LexerApp.kt`

**Archivo**: `LexerApp.kt`

El output a `tokens.txt` usa `token.symbolIndex` directamente (ya calculado durante el scan):

```
Para cada token:
    si la categoría es KEYWORD → escribir <KEYWORD, lexema>
    si no → escribir <categoría, symbolIndex>
```

`errors.txt` lee de `DiagnosticsTable.lexerErrors()`. `symbolTable.txt` lee de `SymbolTable.getAll()` — cada entrada ahora incluye `name` y `location` (línea:posición de primera aparición).

### 2.6 — Exponer una API pública del lexer para el parser

**Archivo nuevo**: `frontend/lexicalAnalyzer/lexer/Lexer.kt`

Hoy para correr el lexer hay que ejecutar `LexerApp.main()`, que escribe a archivos y depende de YAMLs precargados en disco. El parser y la GUI necesitan tokens en memoria sin pasar por disco. Se introduce una función pública que arma los DFAs y escanea en una sola llamada:

```kotlin
object Lexer {
    fun tokenize(yalexContent: String, source: String): LexerResult
}

data class LexerResult(
    val tokens: List<Token>,
    val errors: List<ErrorEntry>,
    val automata: CategoryAutomataIndex
)
```

`tokenize` recibe el contenido crudo del `.yalex` (no la ruta — eso queda a quien llame), construye los DFAs minimizados en memoria, scanea el `source` con esos DFAs, y devuelve tokens + errores + el índice de autómatas (por si la GUI los quiere mostrar).

Internamente reúsa las funciones existentes del Proyecto 1 (`YalexReader`, `normalizeRegex`, `infixToPostfix`, `buildSyntaxTree`, `computeFollowPos`, `buildTransitionTable`, `minimizeDFA`, `buildMinimizedDFA`, `scan`) sin escribir YAMLs intermedios. El patrón two-program de Proyecto 1 (PreprocessorApp + LexerApp) sigue funcionando intacto para uso CLI, pero el flujo IDE no pasa por ahí.

`LexerResult` agrupa todo lo que el consumidor necesita en un solo retorno. La frontera entre módulos queda limpia aunque por dentro sigan usándose los singletons del Proyecto 1 para acumular tokens y errores.

### Resumen de impacto

| Cambio | Tamaño |
|---|---|
| Mover `Token.kt` a `frontend/` | 3 imports a actualizar |
| Rediseñar `Token` | reescribir 1 archivo, ajustar 2 más |
| Crear `LexemeLocation.kt` | 1 archivo nuevo |
| Agregar posición en Scanner | ~5 líneas de cambio |
| Mover lookup de SymbolTable a output | ~10 líneas de cambio |
| Crear `Lexer.kt` API pública | 1 archivo nuevo |

---

## 3. Módulo Grammar

Este módulo agrupa todo lo relacionado con la gramática como objeto de datos: los modelos que la representan, el lector que la construye desde un archivo `.yalp`, el validador que verifica su consistencia, y el reescritor que la transforma cuando hace falta para LL(1).

### 3.1 — Modelos

#### `Symbol.kt` — los símbolos de la gramática

Un símbolo es lo que aparece en el lado derecho de una producción. Hay cuatro tipos disjuntos, así que se modela con `sealed interface`:

```kotlin
sealed interface Symbol {
    val name: String

    data class Terminal(override val name: String) : Symbol
    data class NonTerminal(override val name: String) : Symbol
    data object Epsilon : Symbol { override val name = "ε" }
    data object EndMarker : Symbol { override val name = "$" }
}
```

`Terminal` y `NonTerminal` cargan un nombre arbitrario (`ID`, `INT_LIT`, `expr`, `program`...). `Epsilon` y `EndMarker` no — son constantes únicas en toda la gramática, por eso se modelan como `data object`.

`EndMarker` se trata como caso aparte y no como `Terminal("$")` porque si una gramática real declarara un token llamado `$`, habría colisión. Tener `EndMarker` como caso dedicado lo hace inequívoco — cuando se lee código de SLR y aparece `EndMarker`, queda inmediatamente claro que es el centinela de fin de entrada.

#### `Production.kt` — una regla de producción

Una producción tiene la forma `A → β` donde `A` es un no terminal y `β` es una secuencia de símbolos.

```kotlin
data class Production(
    val id: Int,
    val head: Symbol.NonTerminal,
    val body: List<Symbol>
)
```

El `id` es necesario porque en SLR las acciones de reducción se identifican por número de producción (`reduce by production 5`). Tenerlo evita comparaciones por igualdad estructural sobre listas de símbolos, y permite imprimir las trazas de parsing de forma legible.

El `head` explícito es necesario porque los items LR(0) son objetos `Production + dotPosition` que circulan por el autómata sin tener el mapa de la gramática a mano. Cada producción debe poder responder por sí misma a quién pertenece.

El `body` como `List<Symbol>` mantiene la representación más simple posible: una lista vacía representa una producción epsilon.

#### `Grammar.kt` — la gramática completa

La gramática agrupa todas las producciones más los metadatos que ambos parsers necesitan.

```kotlin
data class Grammar(
    val terminals: Set<Symbol.Terminal>,
    val nonTerminals: Set<Symbol.NonTerminal>,
    val productions: List<Production>,
    val productionsByHead: Map<Symbol.NonTerminal, List<Production>>,
    val startSymbol: Symbol.NonTerminal,
    val ignoredTokens: Set<Symbol.Terminal>
)
```

Tener `terminals` y `nonTerminals` precalculados como conjuntos hace que muchas operaciones sean más legibles: "construir tabla SLR para todos los terminales" se vuelve `for (t in grammar.terminals)`.

`productions` como lista plana es necesario para los items LR(0) (cada item referencia una producción por identidad). La lista preserva el orden de declaración del archivo `.yalp`, que importa para resolución de conflictos en SLR.

`productionsByHead` es una vista alternativa redundante con `productions`, pero precalcularla evita escanear la lista en cada iteración del cómputo de FIRST y FOLLOW.

`startSymbol` se almacena explícitamente para evitar depender del orden de inserción del map (que es frágil). El símbolo inicial es la primera producción declarada en el archivo.

`ignoredTokens` corresponde a la directiva `IGNORE WS` del `.yalp`. Estos tokens deben filtrarse del stream antes de parsear, y se cargan acá para que el `TokenStream` los consulte.

### 3.2 — `YalpReader.kt`

Lee un archivo `.yalp` y construye una `Grammar`.

```kotlin
object YalpReader {
    fun read(filePath: String): Grammar
}
```

El flujo interno es: leer el archivo, eliminar comentarios `/* ... */` (incluyendo multilínea), separar el contenido en dos secciones usando `%%` como divisor, parsear la sección de tokens (líneas con `%token` registran terminales, líneas con `IGNORE` los marcan como ignorados), parsear la sección de producciones (cada bloque `nombre: cuerpo1 | cuerpo2 | ... ;`), y construir la `Grammar` con el primer no terminal declarado como `startSymbol`.

Por convención del documento, los nombres en minúscula son no terminales y los identificadores en MAYÚSCULA son terminales (que deben coincidir con los `%token` declarados).

Los helpers privados siguen el patrón de `YalexReader`:

```kotlin
private fun stripComments(content: String): String
private fun parseTokensSection(lines: List<String>): TokensSectionResult
private fun parseProductionsSection(lines: List<String>, declaredTokens: Set<String>): List<Production>
private fun classifySymbol(token: String, nonTerminalNames: Set<String>): Symbol
```

Igual que `YalexReader`, el `YalpReader` es un `object` con una sola función pública `read`. Mantener la simetría facilita encontrar las cosas.

### 3.3 — `GrammarValidator.kt`

Verifica que la gramática es consistente y que sus terminales coinciden con los tokens que produce YALex.

```kotlin
object GrammarValidator {
    fun validate(
        grammar: Grammar,
        lexerCategories: Set<String>
    ): List<ValidationError>
}

data class ValidationError(
    val message: String,
    val location: LexemeLocation? = null
)
```

Las validaciones que se ejecutan son: cada `%token X` del `.yalp` debe corresponder a una categoría que YALex puede producir; ningún no terminal puede aparecer en un cuerpo sin estar declarado como cabeza de alguna producción; se reporta como advertencia (no bloqueante) cuando hay no terminales declarados pero nunca usados; se reporta como advertencia cuando hay no terminales inalcanzables desde el símbolo inicial; y se reportan producciones duplicadas como indicio de error humano.

`ValidationError` lleva `location` opcional. Cuando `YalpReader` puede llevar el rastreo de líneas, se popula. Cuando no, se deja `null`.

La función retorna lista en lugar de lanzar excepción porque la GUI quiere mostrar todos los errores juntos al usuario, no fallar al primero. Una lista vacía indica gramática válida.

### 3.4 — `GrammarRewriter.kt`

Transforma una gramática para que sea apta para LL(1). SLR(1) no requiere reescritura porque maneja recursión por la izquierda nativamente.

```kotlin
object GrammarRewriter {
    fun eliminateLeftRecursion(grammar: Grammar): Grammar
}
```

`eliminateLeftRecursion` aplica el algoritmo del Dragon Book §4.3.3. Si encuentra `A → A α | β`, lo reescribe como:

```
A → β A_prime
A_prime → α A_prime | ε
```

Se aplica de forma sistemática a toda la gramática. Los no terminales auxiliares se nombran con sufijo `_prime` (sin caracteres especiales, fácil de imprimir y leer).

La factorización por la izquierda (Dragon Book §4.3.4) se deja fuera del plan inicial. Las gramáticas de prueba que se usarán están escritas para evitarla, y el tiempo se prioriza para módulos del núcleo. Si surge la necesidad, se agrega como una segunda función en este mismo archivo.

El flujo en la GUI es: construir tabla LL(1) sobre la gramática original, y si tiene conflictos, ofrecer al usuario aplicar reescritura y reconstruir la tabla sobre la gramática reescrita. La reescritura cambia el árbol de derivación, así que las trazas mostradas tendrán los no terminales auxiliares — esto se puede documentar en la GUI para que no sorprenda.

---

## 4. Módulo Sets

Este módulo computa los conjuntos FIRST y FOLLOW de una gramática. Vive aislado porque ambos parsers (LL(1) y SLR(1)) lo consumen y conviene tenerlo como dependencia única.

La mayor parte del código de este módulo se migra del trabajo independiente que ya está hecho. Los cambios son: renombres de archivos para que se alineen con la convención del proyecto, ajuste para usar `Symbol.EndMarker` en lugar de `Terminal("$")`, y promoción del helper `firstOfSequence` a función pública.

### 4.1 — Modelos

#### `FirstSets.kt`

```kotlin
data class FirstSets(
    val sets: Map<Symbol.NonTerminal, Set<Symbol>>
) {
    fun firstOf(nonTerminal: Symbol.NonTerminal): Set<Symbol> =
        sets[nonTerminal] ?: emptySet()
}
```

#### `FollowSets.kt`

```kotlin
data class FollowSets(
    val sets: Map<Symbol.NonTerminal, Set<Symbol>>
) {
    fun followOf(nonTerminal: Symbol.NonTerminal): Set<Symbol> =
        sets[nonTerminal] ?: emptySet()
}
```

Ambos son wrappers inmutables sobre un map. El método de conveniencia `firstOf` / `followOf` evita que el código consumidor tenga que escribir `?: emptySet()` repetidamente.

### 4.2 — `FirstSetComputer.kt`

```kotlin
object FirstSetComputer {
    fun compute(grammar: Grammar): FirstSets
    fun firstOfSequence(symbols: List<Symbol>, firstSets: FirstSets): Set<Symbol>
}
```

`compute` sigue Dragon Book §4.4.2: precomputa el conjunto de no terminales nullables (los que pueden derivar ε), y luego itera punto fijo recorriendo cada producción `A → X1 X2 ... Xn`, agregando FIRST(X1) - {ε} a FIRST(A), y si X1 es nullable, continuando con FIRST(X2), y así sucesivamente. Si todos los Xi son nullables, se agrega ε a FIRST(A).

`firstOfSequence` calcula FIRST sobre una secuencia de símbolos. Hoy es helper privado del trabajo independiente; se promueve a público porque `LL1TableBuilder` lo necesita directamente para computar FIRST del cuerpo de cada producción.

### 4.3 — `FollowSetComputer.kt`

```kotlin
object FollowSetComputer {
    fun compute(grammar: Grammar, firstSets: FirstSets): FollowSets
}
```

Sigue Dragon Book §4.4.2: FOLLOW(startSymbol) recibe `EndMarker`, luego se itera punto fijo: para cada producción `A → α B β`, se agrega FIRST(β) - {ε} a FOLLOW(B); si β es nullable o vacío, se agrega FOLLOW(A) a FOLLOW(B).

Cambio frente al trabajo independiente: donde antes se usaba `Symbol.Terminal("$")`, ahora se usa `Symbol.EndMarker`.

---

## 5. Módulo LL(1)

Este módulo construye la tabla LL(1) y ejecuta el parser predictivo no recursivo. La construcción se migra casi tal cual del trabajo independiente; el driver es nuevo.

### 5.1 — Modelos

#### `LL1Cell.kt`

```kotlin
data class LL1Conflict(
    val nonTerminal: Symbol.NonTerminal,
    val terminal: Symbol.Terminal,
    val productions: List<Production>
)
```

Solo se modela el conflicto como data class. La celda de la tabla en sí es simplemente una `List<Production>` — si tiene cero elementos, es entrada vacía; si tiene uno, está bien; si tiene más, hay conflicto. No se necesita una clase wrapper porque las operaciones sobre la lista son directas y legibles.

#### `LL1Table.kt`

```kotlin
data class LL1Table(
    val cells: Map<Symbol.NonTerminal, Map<Symbol.Terminal, List<Production>>>
) {
    fun isLL1(): Boolean
    fun conflicts(): List<LL1Conflict>
    fun lookup(nonTerminal: Symbol.NonTerminal, terminal: Symbol.Terminal): Production?
}
```

El diseño es el mismo que ya está en `SyntaxTable`. Se renombra a `LL1Table` para distinguir de `SLR1Table` cuando ambas convivan en el código.

`isLL1()` retorna true si todas las celdas tienen cero o una producción. `conflicts()` retorna la lista de celdas con más de una producción para que la GUI las muestre. `lookup` retorna la única producción de la celda, o null si no hay (la celda es entrada vacía o conflicto).

### 5.2 — `LL1TableBuilder.kt`

```kotlin
object LL1TableBuilder {
    fun build(
        grammar: Grammar,
        firstSets: FirstSets,
        followSets: FollowSets
    ): LL1Table
}
```

Sigue Dragon Book §4.4.3 (Algoritmo 4.31): para cada producción `A → α`, para cada terminal `a` en FIRST(α), agregar `A → α` a `M[A, a]`; si ε está en FIRST(α), para cada terminal `b` en FOLLOW(A), agregar `A → α` a `M[A, b]`; si además EndMarker está en FOLLOW(A), agregar también a `M[A, EndMarker]`.

Si alguna celda termina con más de una producción, hay conflicto y la gramática no es LL(1).

Este código se migra del trabajo independiente con los renombres correspondientes.

### 5.3 — `LL1Driver.kt`

```kotlin
class LL1Driver(
    private val grammar: Grammar,
    private val table: LL1Table
) {
    fun parse(tokens: List<Token>): ParseResult
}
```

Implementa el parser predictivo no recursivo del Dragon Book §4.4.4 (Algoritmo 4.34). El stack inicia con `[EndMarker, startSymbol]` y se procesa así: si la cima del stack es terminal, se compara con el token actual (si coincide se hace pop y se avanza; si no, error); si la cima es no terminal, se consulta la tabla en `[cima, tokenActual]` y se reemplaza la cima por el cuerpo de la producción en orden inverso (para que el primer símbolo del cuerpo quede en la cima); cuando el stack solo contiene `EndMarker` y la entrada está consumida, se acepta.

En cada iteración se construye un `ParseStep` con snapshot del stack, la entrada restante y la acción tomada. Esto alimenta la traza que la GUI muestra al usuario.

---

## 6. Módulo LR(0)

Este módulo construye el autómata LR(0) que sirve de base para SLR(1). Es uno de los objetivos específicos del documento del proyecto, por eso vive en su propio módulo y no como detalle interno de SLR.

### 6.1 — Modelos

#### `LR0Item.kt`

Un item LR(0) es una producción con un punto que indica hasta dónde se ha reconocido el cuerpo.

```kotlin
data class LR0Item(
    val production: Production,
    val dotPosition: Int
) {
    val isComplete: Boolean
        get() = dotPosition == production.body.size

    val symbolAfterDot: Symbol?
        get() = production.body.getOrNull(dotPosition)

    fun advance(): LR0Item =
        copy(dotPosition = dotPosition + 1)
}
```

`dotPosition = 0` significa el punto al inicio del cuerpo, `dotPosition = body.size` significa el punto al final (item completo, listo para reducción).

`symbolAfterDot` retorna el símbolo inmediatamente después del punto, o null si el item está completo. Es el método más usado en `closure` y `goto`.

`advance` retorna un nuevo item con el punto un paso adelante. Se usa en `goto`.

#### `LR0ItemSet.kt`

Un item set es un conjunto de items que conforma un estado del autómata.

```kotlin
data class LR0ItemSet(
    val id: Int,
    val items: Set<LR0Item>
)
```

El `id` numera los estados (I0, I1, I2...) según el orden en que se descubren durante la construcción.

#### `LR0Automaton.kt`

```kotlin
data class LR0Automaton(
    val states: List<LR0ItemSet>,
    val transitions: Map<Pair<Int, Symbol>, Int>,
    val initialState: Int,
    val augmentedGrammar: Grammar
)
```

`states` lista los item sets en orden de id. `transitions` mapea pares `(estado, símbolo)` al estado destino. `augmentedGrammar` guarda la gramática con la producción aumentada `S' → S` agregada al inicio — el constructor SLR la necesita para reconocer la condición de aceptación.

### 6.2 — `LR0AutomatonBuilder.kt`

```kotlin
object LR0AutomatonBuilder {
    fun build(grammar: Grammar): LR0Automaton
    fun closure(items: Set<LR0Item>, grammar: Grammar): Set<LR0Item>
    fun goto(items: Set<LR0Item>, symbol: Symbol, grammar: Grammar): Set<LR0Item>
}
```

`build` orquesta toda la construcción siguiendo Dragon Book §4.6.2. Aumenta la gramática con `S' → S`, calcula el item set inicial como `closure({S' → •S})`, y luego itera: para cada item set, para cada símbolo de la gramática, computa `goto`. Si el resultado no es vacío y no existe ya, se agrega como nuevo estado. Se repite hasta que no se descubren estados nuevos.

`closure` (Dragon Book §4.6.2) toma un conjunto de items y agrega: para cada item `A → α•Bβ` donde B es no terminal, agrega `B → •γ` para cada producción de B. Se repite hasta estabilizar.

`goto` (Dragon Book §4.6.2) toma un conjunto de items y un símbolo X. Avanza el punto en cada item de la forma `A → α•Xβ` para producir `A → αX•β`, y luego retorna el closure de esos items avanzados.

Las tres funciones son públicas porque facilitan el testing aislado: se puede verificar `closure` y `goto` sobre item sets pequeños antes de probar `build` sobre una gramática completa.

---

## 7. Módulo SLR(1)

Este módulo toma el autómata LR(0) y los conjuntos FOLLOW para construir la tabla SLR(1) y ejecutar el parser shift-reduce.

### 7.1 — Modelos

#### `Action.kt`

Cada celda de la tabla ACTION contiene una de cuatro acciones posibles. Se modelan como `sealed interface` porque cada una carga datos distintos.

```kotlin
sealed interface Action {
    data class Shift(val nextState: Int) : Action
    data class Reduce(val production: Production) : Action
    data object Accept : Action
}
```

`Shift(s)` significa "consumir el token actual y pasar al estado s". `Reduce(p)` significa "aplicar la producción p para reducir la pila". `Accept` significa "fin exitoso del parsing".

No se modela un caso `Error` explícito. La ausencia de entrada en la tabla (la celda no existe en el map) ya indica error. Esto evita ruido visual cuando se imprime la tabla.

#### `SLR1Table.kt`

```kotlin
data class SLR1Table(
    val action: Map<Pair<Int, Symbol>, Action>,
    val goto: Map<Pair<Int, Symbol.NonTerminal>, Int>,
    val numStates: Int
) {
    fun isSLR1(): Boolean
    fun conflicts(): List<SLR1Conflict>
}

data class SLR1Conflict(
    val state: Int,
    val symbol: Symbol,
    val actions: List<Action>,
    val type: ConflictType
)

enum class ConflictType {
    SHIFT_REDUCE, REDUCE_REDUCE
}
```

`action` está indexada por `(estado, terminal)` donde el terminal puede incluir EndMarker. `goto` está indexada por `(estado, no terminal)`. `numStates` se mantiene para iterar la tabla al imprimirla.

`SLR1Conflict` distingue conflictos shift-reduce (un Shift y un Reduce sobre el mismo símbolo) de reduce-reduce (dos Reduce sobre el mismo símbolo). Es útil tanto para el reporte como para entender qué tan severa es la ambigüedad.

### 7.2 — `SLR1TableBuilder.kt`

```kotlin
object SLR1TableBuilder {
    fun build(
        grammar: Grammar,
        automaton: LR0Automaton,
        followSets: FollowSets
    ): SLR1Table
}
```

Sigue Dragon Book §4.6.4 (Algoritmo 4.46): para cada estado Ii del autómata, recorrer sus items. Si un item es de la forma `A → α•aβ` con `a` terminal, y existe transición de Ii con `a` hacia Ij, entonces `ACTION[i, a] = Shift(j)`. Si un item está completo (`A → α•`) y A no es el símbolo inicial aumentado, entonces para cada terminal `b` en FOLLOW(A), `ACTION[i, b] = Reduce(A → α)`. Si un item está completo y A es el símbolo inicial aumentado, entonces `ACTION[i, EndMarker] = Accept`. Para cada no terminal A, si existe transición de Ii con A hacia Ij, entonces `GOTO[i, A] = j`.

Cuando dos asignaciones distintas caerían en la misma celda, se registra el conflicto en lugar de sobrescribir. La gramática es SLR(1) si y solo si no hay conflictos.

### 7.3 — `SLR1Driver.kt`

```kotlin
class SLR1Driver(
    private val grammar: Grammar,
    private val table: SLR1Table
) {
    fun parse(tokens: List<Token>): ParseResult
}
```

Implementa el parser shift-reduce del Dragon Book §4.5.3 (Algoritmo 4.44). El stack contiene estados (no símbolos), iniciando con `[0]`. En cada iteración: tomar el estado en la cima, consultar `ACTION[cima, tokenActual]`. Si es `Shift(t)`, empujar t y avanzar al siguiente token. Si es `Reduce(A → α)`, hacer pop de `|α|` estados y luego empujar `GOTO[nuevaCima, A]`. Si es `Accept`, retornar éxito. Si la entrada no existe, retornar error con el token encontrado y los terminales esperados.

Mientras parsea, el driver mantiene un stack paralelo de subárboles. En cada `Shift` empuja una hoja `ParseTree.Leaf` con el token consumido. En cada `Reduce(A → α)` saca `|α|` subárboles, los combina como hijos de un nuevo `ParseTree.Internal(A, producción, hijos)`, y empuja ese nodo. Al aceptar, queda un único subárbol en el stack — ese es el árbol sintáctico que se retorna en `ParseResult.Accepted`.

Igual que en LL(1), cada paso construye un `ParseStep` para la traza.

---

## 8. Módulo LR(1)

Este módulo construye el autómata LR(1), una extensión del LR(0) donde cada item carga un terminal de **lookahead** que restringe cuándo puede aplicarse una reducción. Es la base sobre la que se construye LALR(1).

LR(1) por sí solo produce muchísimos estados y rara vez se usa como tabla final. Su valor en este proyecto es servir como insumo para que LALR(1) mergee estados con el mismo core y obtenga una tabla más compacta sin perder potencia.

### 8.1 — Modelos

#### `LR1Item.kt`

Un item LR(1) extiende un item LR(0) agregándole un terminal de anticipación.

```kotlin
data class LR1Item(
    val production: Production,
    val dotPosition: Int,
    val lookahead: Symbol
) {
    val isComplete: Boolean
        get() = dotPosition == production.body.size

    val symbolAfterDot: Symbol?
        get() = production.body.getOrNull(dotPosition)

    val core: LR0Item
        get() = LR0Item(production, dotPosition)

    fun advance(): LR1Item =
        copy(dotPosition = dotPosition + 1)
}
```

El `lookahead` puede ser un `Terminal` o `EndMarker`. La propiedad `core` retorna el item LR(0) correspondiente (la misma producción y posición del punto, sin el lookahead) — esto es lo que LALR usa para decidir qué estados mergear.

#### `LR1ItemSet.kt`

```kotlin
data class LR1ItemSet(
    val id: Int,
    val items: Set<LR1Item>
) {
    val core: Set<LR0Item>
        get() = items.map { it.core }.toSet()
}
```

`core` retorna el conjunto de items LR(0) subyacentes ignorando lookaheads. Dos estados LR(1) con el mismo core son candidatos a merge en la construcción LALR.

#### `LR1Automaton.kt`

```kotlin
data class LR1Automaton(
    val states: List<LR1ItemSet>,
    val transitions: Map<Pair<Int, Symbol>, Int>,
    val initialState: Int,
    val augmentedGrammar: Grammar
)
```

La estructura es paralela a `LR0Automaton`. Lo único que cambia es que los estados son `LR1ItemSet` con items que cargan lookahead.

### 8.2 — `LR1AutomatonBuilder.kt`

```kotlin
object LR1AutomatonBuilder {
    fun build(grammar: Grammar, firstSets: FirstSets): LR1Automaton
    fun closure(items: Set<LR1Item>, grammar: Grammar, firstSets: FirstSets): Set<LR1Item>
    fun goto(items: Set<LR1Item>, symbol: Symbol, grammar: Grammar, firstSets: FirstSets): Set<LR1Item>
}
```

`build` aumenta la gramática con `S' → S`, comienza con `closure({[S' → •S, $]})`, y procede igual que en LR(0): para cada estado, para cada símbolo, computa `goto`. El resultado tiene típicamente muchos más estados que el autómata LR(0) sobre la misma gramática.

`closure` (Dragon Book §4.7.2) sigue una regla más rica que LR(0): si un item es `[A → α•Bβ, a]`, agregar `[B → •γ, b]` para cada producción `B → γ` y cada `b` en `FIRST(βa)`. Es decir, el lookahead del nuevo item es el primer terminal que puede aparecer después de B en el contexto del item original. Por eso `LR1AutomatonBuilder.closure` recibe `FirstSets` como parámetro: lo necesita para computar `FIRST(βa)`.

`goto` funciona igual que en LR(0): avanza el punto sobre el símbolo X y retorna el closure del resultado. La única diferencia es que arrastra los lookaheads de los items originales (no los modifica).

---

## 9. Módulo LALR(1)

LALR(1) toma el autómata LR(1) y mergea todos los estados que tengan el mismo core (los mismos items LR(0) subyacentes), uniendo sus lookaheads. El resultado es una tabla con la misma cantidad de estados que el LR(0) (mucho más compacta que LR(1) canónico) pero con la potencia que dan los lookaheads para resolver muchos conflictos que SLR no puede.

Este es el método "más fuerte" del trío: cuando una gramática genera conflictos en SLR, LALR muchas veces los resuelve.

### 9.1 — Modelos

#### `LALR1Table.kt`

La tabla LALR(1) tiene la misma forma estructural que la tabla SLR(1) — una tabla `ACTION` y una tabla `GOTO`. Por eso reusa el mismo `Action` (sealed interface) del módulo SLR.

```kotlin
data class LALR1Table(
    val action: Map<Pair<Int, Symbol>, Action>,
    val goto: Map<Pair<Int, Symbol.NonTerminal>, Int>,
    val numStates: Int
) {
    fun isLALR1(): Boolean
    fun conflicts(): List<LALR1Conflict>
}

data class LALR1Conflict(
    val state: Int,
    val symbol: Symbol,
    val actions: List<Action>,
    val type: ConflictType
)
```

El `Action` (Shift, Reduce, Accept) y `ConflictType` (SHIFT_REDUCE, REDUCE_REDUCE) son los mismos que SLR. No tiene sentido duplicarlos.

### 9.2 — `LALR1AutomatonBuilder.kt`

```kotlin
object LALR1AutomatonBuilder {
    fun mergeFromLR1(automaton: LR1Automaton): LR1Automaton
}
```

Toma un `LR1Automaton` y produce otro `LR1Automaton` donde se han agrupado los estados con el mismo core. Sigue Dragon Book §4.7.4: identifica todos los estados con el mismo conjunto de items LR(0) subyacentes, los reemplaza por un único estado cuyo lookahead set es la unión de los lookaheads originales, y reescribe las transiciones para apuntar al nuevo estado consolidado.

El resultado se mantiene como `LR1Automaton` (no un tipo nuevo) porque estructuralmente es lo mismo: items con lookahead, transiciones, estados. Solo cambió la cantidad y la composición de los lookaheads.

### 9.3 — `LALR1TableBuilder.kt`

```kotlin
object LALR1TableBuilder {
    fun build(grammar: Grammar, mergedAutomaton: LR1Automaton): LALR1Table
}
```

Llena la tabla recorriendo los estados del autómata mergeado (Dragon Book §4.7.4): para cada estado Ii, si tiene un item `[A → α•aβ, b]` con `a` terminal y existe transición a Ij con `a`, entonces `ACTION[i, a] = Shift(j)`. Si tiene un item completo `[A → α•, b]` y A no es el símbolo aumentado, entonces `ACTION[i, b] = Reduce(A → α)` (notar que el lookahead `b` viene del item, no de FOLLOW como en SLR). Si tiene `[S' → S•, $]`, entonces `ACTION[i, EndMarker] = Accept`. `GOTO` se llena igual que en SLR a partir de las transiciones por no terminales.

La diferencia clave frente a SLR está en quién decide cuándo reducir: SLR usa `FOLLOW(A)`, LALR usa los lookaheads que vienen propagados en cada item. Por eso LALR es más preciso y resuelve conflictos donde SLR fallaría.

### 9.4 — `LALR1Driver.kt`

```kotlin
class LALR1Driver(
    private val grammar: Grammar,
    private val table: LALR1Table
) {
    fun parse(tokens: List<Token>): ParseResult
}
```

El algoritmo es **idéntico al de SLR(1)**: un parser shift-reduce manejado por la tabla `ACTION + GOTO`. Lo único que cambia es la tabla que se le pasa. Por la simetría podría reutilizar internamente el código del `SLR1Driver`, pero por legibilidad y para que cada integrante pueda explicar "su" driver de cabo a rabo se mantiene como clase separada con la implementación duplicada (son ~50 líneas, no es costo significativo).

Igual que SLR(1), construye el árbol sintáctico empujando hojas en cada `Shift` y combinando subárboles en cada `Reduce`.

---

## 10. Módulo Runtime

Este módulo agrupa los objetos compartidos por los tres drivers (LL(1), SLR(1), LALR(1)): el resultado del parsing con su árbol sintáctico, la traza paso a paso, los errores, y el adaptador del stream de tokens.

### 10.1 — Modelos

#### `ParseTree.kt`

El árbol sintáctico es la representación visual de cómo el parser derivó la cadena de entrada. Cada nodo tiene asociado un símbolo de la gramática. Hay dos tipos disjuntos: hojas (terminales con su token) e internos (no terminales con la producción aplicada y los hijos).

```kotlin
sealed interface ParseTree {
    val symbol: Symbol

    data class Leaf(
        override val symbol: Symbol.Terminal,
        val token: Token
    ) : ParseTree

    data class Internal(
        override val symbol: Symbol.NonTerminal,
        val production: Production,
        val children: List<ParseTree>
    ) : ParseTree

    data class EpsilonLeaf(
        override val symbol: Symbol = Symbol.Epsilon
    ) : ParseTree
}
```

Cada `Leaf` carga el token original (con su lexema y posición), lo que permite a la GUI mostrar el texto exacto en el árbol final. Cada `Internal` carga la producción aplicada para que se pueda imprimir como `expr → expr + term`. `EpsilonLeaf` representa una expansión a vacío en LL(1) (una hoja con símbolo `Epsilon`).

Los tres drivers construyen este árbol de forma distinta pero el resultado tiene la misma estructura. Esto permite que el `ParseTreeExporter` no sepa qué método lo generó.

#### `ParseResult.kt`

El resultado de un parsing es uno de dos casos disjuntos. Se modela como `sealed interface`.

```kotlin
sealed interface ParseResult {
    val trace: List<ParseStep>

    data class Accepted(
        override val trace: List<ParseStep>,
        val parseTree: ParseTree
    ) : ParseResult

    data class Rejected(
        override val trace: List<ParseStep>,
        val error: ParseError,
        val partialTree: ParseTree?
    ) : ParseResult
}
```

`Accepted` carga el árbol sintáctico completo. `Rejected` carga el error y opcionalmente un `partialTree` con lo que el parser logró construir antes de fallar (útil para mostrar al usuario hasta dónde llegó).

Ambos casos cargan la traza completa. La razón: la GUI quiere mostrar la traza incluso en caso de rechazo, hasta el punto donde el parser falló.

#### `ParseStep.kt`

```kotlin
data class ParseStep(
    val stack: String,
    val remainingInput: String,
    val action: String
)
```

Las tres columnas se almacenan como `String` ya formateadas porque la GUI las muestra en una tabla de texto. Mantenerlas como string evita una capa de formato adicional.

`action` es un texto descriptivo: `"shift 5"`, `"reduce by E -> E + T"`, `"match id"`. Suficiente para que cualquier persona leyendo la traza entienda qué hizo el parser en cada paso.

#### `ParseError.kt`

```kotlin
data class ParseError(
    val message: String,
    val location: LexemeLocation?,
    val foundToken: String?,
    val expectedTokens: Set<String>
)
```

`message` es texto descriptivo en lenguaje natural. `location` ubica el error en la fuente (línea y columna del token problemático). `foundToken` es el lexema o categoría que recibió el parser. `expectedTokens` es el conjunto de tokens que el parser esperaba en ese punto, derivado de la tabla.

Tener `expectedTokens` permite mensajes como `"se esperaba ; o }, se encontró if"`. Es la diferencia entre un error útil y uno frustrante.

### 10.2 — `TokenStream.kt`

```kotlin
class TokenStream(
    private val tokens: List<Token>,
    private val ignored: Set<String>
) {
    fun peek(): Token?
    fun consume(): Token?
    fun hasNext(): Boolean
    fun position(): Int
}
```

Adaptador entre la salida del lexer y la entrada del parser. Es una clase (no función) porque los parsers necesitan estado de posición para hacer peek y consume.

`peek` retorna el siguiente token sin consumirlo. `consume` retorna el siguiente y avanza. Ambos saltan tokens cuya categoría está en `ignored` (los `IGNORE` declarados en el `.yalp`).

`position` retorna el índice actual, útil para guardar y restaurar estado en estrategias avanzadas de recuperación de errores.

### 10.3 — `Pipeline.kt`

Orquestador de alto nivel que el IDE consume con una sola llamada. Encadena lexer → grammar → sets → tablas → driver, manteniendo todo en memoria.

```kotlin
object Pipeline {
    fun runFull(
        yalexContent: String,
        yalpContent: String,
        inputContent: String,
        method: ParserMethod
    ): PipelineResult
}

enum class ParserMethod { LL1, SLR1, LALR1 }

data class PipelineResult(
    val tokens: List<Token>,
    val lexerErrors: List<ErrorEntry>,
    val grammar: Grammar,
    val validationErrors: List<ValidationError>,
    val firstSets: FirstSets,
    val followSets: FollowSets,
    val lr0Automaton: LR0Automaton,
    val lr1Automaton: LR1Automaton,
    val ll1Table: LL1Table,
    val slr1Table: SLR1Table,
    val lalr1Table: LALR1Table,
    val parseResult: ParseResult,
    val methodUsed: ParserMethod
)
```

`runFull` siempre construye **las tres tablas y los dos autómatas**, no solo el del método activo. Razón: cuando el usuario cambia de método en el dropdown del IDE, no hay que re-correr todo el pipeline — basta con re-ejecutar el driver correspondiente sobre la tabla ya construida. Adicionalmente, las pestañas de visualización (autómata LR(0), tablas LL(1)/SLR(1)/LALR(1)) están siempre disponibles independientemente de qué método se eligió para parsear.

El driver que se ejecuta sobre los tokens depende del `method` recibido. Eso queda capturado en `parseResult` y `methodUsed`.

Vive en `frontend/syntaxAnalyzer/runtime/` porque coordina todos los módulos pero no aporta lógica nueva — solo los enlaza.

---

## 11. Módulo Visualization

Este módulo agrupa las utilidades de presentación que la GUI consume. Convierte las estructuras internas (autómatas, árbol sintáctico, tablas, conjuntos) en texto formateado o imágenes.

### 11.1 — `DotExporter.kt`

```kotlin
object DotExporter {
    fun lr0ToDot(automaton: LR0Automaton): String
    fun lr1ToDot(automaton: LR1Automaton): String
    fun renderToImage(dot: String, outputPath: String): Boolean
}
```

`lr0ToDot` y `lr1ToDot` generan texto en formato DOT de Graphviz. Cada item set se imprime como una caja etiquetada con su id y la lista de items adentro (cada item LR(0) con la forma `A → α • β`, cada item LR(1) con la forma `[A → α • β, lookahead]`). Las transiciones son flechas etiquetadas con el símbolo correspondiente.

`renderToImage` invoca el comando `dot` de Graphviz vía `ProcessBuilder` para convertir el texto DOT en una imagen PNG. Retorna `true` si tuvo éxito, `false` si Graphviz no está instalado o falló la conversión. La GUI consulta este boolean y muestra un fallback (texto DOT en bruto) si no se pudo renderizar.

### 11.2 — `ParseTreeExporter.kt`

```kotlin
object ParseTreeExporter {
    fun toDot(tree: ParseTree): String
    fun toIndentedText(tree: ParseTree): String
}
```

`toDot` convierte el árbol sintáctico al formato DOT de Graphviz para que `renderToImage` produzca una imagen visualizable. Cada nodo `Internal` se imprime como una caja etiquetada con el nombre del no terminal; cada nodo `Leaf` como una caja etiquetada con la categoría y el lexema del token. Las aristas conectan padres con hijos manteniendo el orden de izquierda a derecha.

`toIndentedText` retorna una representación textual con indentación (formato árbol ASCII tradicional con `├─` y `└─`) para mostrar como fallback si Graphviz no está disponible, o como vista alternativa para usuarios que prefieran texto sobre gráfico.

Vive en su propio archivo (no dentro de `DotExporter`) porque el árbol sintáctico es conceptualmente distinto de los autómatas — los autómatas son grafos cíclicos con estados; el árbol es un grafo acíclico con orden de hijos.

### 11.3 — `TableFormatter.kt`

```kotlin
object TableFormatter {
    fun formatLL1Table(table: LL1Table, grammar: Grammar): String
    fun formatSLR1Action(table: SLR1Table, grammar: Grammar): String
    fun formatSLR1Goto(table: SLR1Table, grammar: Grammar): String
    fun formatLALR1Action(table: LALR1Table, grammar: Grammar): String
    fun formatLALR1Goto(table: LALR1Table, grammar: Grammar): String
    fun formatFirstSets(sets: FirstSets): String
    fun formatFollowSets(sets: FollowSets): String
}
```

Cada formatter retorna una string multilinea alineada en columnas, apta para mostrarse en un componente de texto monoespaciado. La GUI las usa directamente sin más procesamiento.

Las acciones SLR/LALR se imprimen abreviadas: `s5` para Shift(5), `r3` para Reduce(producción 3), `acc` para Accept. Esta convención es la misma del Dragon Book, lo cual ayuda en preguntas teóricas.

---

## 12. Módulo GUI (Compose Desktop)

La interfaz gráfica es un IDE estilo workspace: el usuario carga `.yalex` y `.yalp`, escribe la cadena a analizar en un editor central, escoge el método de análisis en un dropdown, y presiona Play para ejecutar. Los resultados (lista de tokens y árbol sintáctico) aparecen en paneles laterales. Las visualizaciones de autómatas y tablas son pestañas secundarias accesibles desde el menú View.

### 12.1 — `App.kt`

Punto de entrada de Compose Desktop. Crea la ventana principal y define el layout general.

```kotlin
fun main() = application {
    val state = remember { AppState() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "YAPar IDE",
        state = WindowState(width = 1280.dp, height = 800.dp)
    ) {
        MenuBar {
            FileMenu(state)
            ViewMenu(state)
        }
        AppContent(state)
    }
}
```

`AppContent` es el composable raíz que coloca la `WorkspaceScreen` como pantalla principal y permite abrir las pantallas secundarias (autómata, tablas) en pestañas adicionales o ventanas flotantes.

### 12.2 — `AppState.kt`

Holder centralizado del estado de la aplicación. Vive en `gui/state/`.

```kotlin
class AppState {
    var yalexFilePath: String? by mutableStateOf(null)
    var yalpFilePath: String? by mutableStateOf(null)
    var inputFilePath: String? by mutableStateOf(null)

    var yalexContent: String by mutableStateOf("")
    var yalpContent: String by mutableStateOf("")
    var inputContent: String by mutableStateOf("")

    var selectedMethod: ParserMethod by mutableStateOf(ParserMethod.SLR1)
    var pipelineResult: PipelineResult? by mutableStateOf(null)
    var isRunning: Boolean by mutableStateOf(false)

    var lr0ImagePath: String? by mutableStateOf(null)
    var lr1ImagePath: String? by mutableStateOf(null)
    var parseTreeImagePath: String? by mutableStateOf(null)

    fun onPlay() {
        isRunning = true
        pipelineResult = Pipeline.runFull(yalexContent, yalpContent, inputContent, selectedMethod)
        // regenerar imágenes (autómatas, árbol sintáctico) vía DotExporter
        isRunning = false
    }

    fun changeMethod(newMethod: ParserMethod) {
        selectedMethod = newMethod
        // re-ejecutar solo el driver, no todo el pipeline
        pipelineResult?.let { current ->
            pipelineResult = current.copy(
                parseResult = runDriverFor(newMethod, current),
                methodUsed = newMethod
            )
        }
    }
}
```

Todos los campos son `mutableStateOf` para que Compose recomponga automáticamente cuando cambien.

`onPlay` corre el pipeline completo. `changeMethod` permite cambiar de método sin re-correr todo: si las tablas ya están construidas, basta con re-ejecutar el driver del nuevo método sobre los mismos tokens.

### 12.3 — `WorkspaceScreen` — la pantalla principal

Esta es la pantalla que el usuario ve al abrir el IDE. Layout en tres áreas:

**Top bar (toolbar):**
- Dropdown con las tres opciones (`LL(1)`, `SLR(1)`, `LALR(1)`)
- Botón Play (icono ▶) que dispara `onPlay()`
- Indicador de estado (loading spinner mientras `isRunning`)

**Centro:**
- Editor de cadenas — donde el usuario escribe el programa fuente que quiere analizar. Es el área más grande de la pantalla.
- Tabs superiores que permiten alternar entre el editor de cadenas, el editor de `.yalex`, y el editor de `.yalp` (los tres siempre disponibles, el usuario edita el que necesite).

**Panel derecho (resultados):**
- Sub-pestañas: **Tokens**, **Parse Tree**, **Errores**
- Tokens: lista de los tokens identificados por el lexer en orden, mostrando categoría, lexema, y posición
- Parse Tree: imagen del árbol sintáctico (renderizada por `ParseTreeExporter` + Graphviz). Si Graphviz no está disponible, muestra el árbol como texto indentado
- Errores: lista de errores del parser con línea, columna, mensaje, token encontrado y tokens esperados

El panel derecho solo muestra contenido cuando `pipelineResult` no es null. Mientras tanto, muestra un placeholder "Presiona Play para analizar".

### 12.4 — Pantallas secundarias

Accesibles desde el menú View > Mostrar autómata / Mostrar tablas. Cada una abre una pestaña adicional:

`AutomatonScreen` muestra el autómata seleccionado (LR(0) o LR(1)) como imagen. Tiene un toggle entre ambos. La imagen se carga desde `lr0ImagePath` o `lr1ImagePath` del estado.

`TablesScreen` tiene cuatro sub-pestañas: **FIRST/FOLLOW**, **LL(1)**, **SLR(1)**, **LALR(1)**. Cada una muestra el texto formateado por `TableFormatter` en un componente monoespaciado.

Estas pantallas existen porque el documento del proyecto las exige como entregable visual. No son parte del flujo principal del usuario, pero deben estar disponibles para revisión y para preguntas del catedrático durante la evaluación.

### 12.5 — Componentes

`FileMenu` define la sección File del menú superior con las opciones: Open .yalex / Open .yalp / Open Input / Save All / Save As.

`ViewMenu` define la sección View con: Mostrar autómata LR(0) / Mostrar autómata LR(1) / Mostrar tablas / Volver al workspace.

`CodeEditor` envuelve un `BasicTextField` con fuente monoespaciada y una columna lateral con números de línea. Es el componente que reusan los tres editores (yalex, yalp, cadenas).

`MethodDropdown` envuelve un `DropdownMenu` con las tres opciones de `ParserMethod`. Llama `state.changeMethod` al seleccionar.

`PlayButton` es un `Button` con icono y label "Analizar". Llama `state.onPlay`. Se deshabilita cuando `isRunning` es true.

`TokenList` toma `pipelineResult.tokens` y los muestra en una lista vertical con tres columnas (categoría, lexema, posición).

`ParseTreeView` muestra la imagen del árbol o el texto indentado según disponibilidad de Graphviz.

`ErrorList` muestra los errores del parser con icono de alerta, línea/columna, y mensaje descriptivo. Hacer click en un error mueve el cursor del editor de cadenas a la posición correspondiente.

---

## 13. CLI: comando `yapar`

### 13.1 — `YaparApp.kt`

Punto de entrada CLI. Procesa argumentos y ejecuta el flujo completo de análisis usando el `Pipeline`.

```kotlin
fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val yalexContent = File(parsed.lexerFile ?: DEFAULT_YALEX).readText()
    val yalpContent = File(parsed.yalpFile).readText()
    val inputContent = parsed.inputFile?.let { File(it).readText() } ?: ""

    val result = Pipeline.runFull(
        yalexContent = yalexContent,
        yalpContent = yalpContent,
        inputContent = inputContent,
        method = parsed.method
    )

    if (parsed.outputFile != null) {
        TheParserExporter.export(result, parsed.outputFile)
    }

    println("Análisis completo.")
    if (!result.ll1Table.isLL1()) println("La gramática NO es LL(1)")
    if (!result.slr1Table.isSLR1()) println("La gramática NO es SLR(1)")
    if (!result.lalr1Table.isLALR1()) println("La gramática NO es LALR(1)")

    when (val pr = result.parseResult) {
        is ParseResult.Accepted -> println("Cadena aceptada por ${result.methodUsed}")
        is ParseResult.Rejected -> println("Cadena rechazada: ${pr.error.message}")
    }
}

private data class CliArgs(
    val yalpFile: String,
    val lexerFile: String?,
    val inputFile: String?,
    val outputFile: String?,
    val method: ParserMethod
)

private fun parseArgs(args: Array<String>): CliArgs
```

El reconocedor de argumentos identifica: el primer positional es el archivo `.yalp`; `-l <archivo>` indica la especificación del lexer; `-i <archivo>` indica las cadenas a analizar; `-o <archivo>` indica el archivo de salida; `-m <método>` indica el método (`ll1`, `slr1`, `lalr1`), default `slr1`.

### 13.2 — Exportador opcional

`TheParserExporter` (vive en `frontend/syntaxAnalyzer/visualization/`) serializa las tres tablas + autómatas + sets a un archivo de texto plano legible. No es deserializable — su único propósito es satisfacer el flag `-o` del CLI con un artefacto inspeccionable.

### 13.3 — Tarea de Gradle

```kotlin
tasks.register<JavaExec>("runYapar") {
    mainClass.set("org.compiler.YaparAppKt")
    args = listOf(
        "src/main/resources/parser.yalp",
        "-l", "src/main/resources/java_lang.yal",
        "-o", "src/main/resources/theparser"
    )
}

tasks.register<JavaExec>("runGui") {
    mainClass.set("org.compiler.GuiAppKt")
}
```

Cada `main()` tiene su propia tarea Gradle. Esto evita pasarle banderas a un binario único, mantiene la convención del Proyecto 1, y deja claro al leer el `build.gradle.kts` qué puntos de entrada existen.

---

## 14. Archivo de gramática de prueba

El documento del proyecto exige que el sistema acepte el análisis sintáctico de clases, funciones, recursión y listas. La gramática de prueba debe cubrir esos cuatro patrones para soportar la evaluación.

### 14.1 — `parser.yalp`

```
/* Gramática de prueba para YAPar */

%token CLASS FUN ID INT_LIT
%token LBRACE RBRACE LPAREN RPAREN LBRACKET RBRACKET
%token COMMA SEMI ASSIGN
%token RETURN
%token PLUS TIMES
%token WS COMMENT

IGNORE WS
IGNORE COMMENT

%%

program:
    declarationList
  ;

declarationList:
    declarationList declaration
  | declaration
  ;

declaration:
    classDecl
  | functionDecl
  ;

classDecl:
    CLASS ID LBRACE memberList RBRACE
  ;

memberList:
    memberList functionDecl
  | functionDecl
  ;

functionDecl:
    FUN ID LPAREN paramList RPAREN LBRACE statementList RBRACE
  ;

paramList:
    paramList COMMA ID
  | ID
  ;

statementList:
    statementList statement
  | statement
  ;

statement:
    exprStmt
  | returnStmt
  ;

returnStmt:
    RETURN expr SEMI
  ;

exprStmt:
    expr SEMI
  ;

expr:
    expr PLUS term
  | term
  ;

term:
    term TIMES factor
  | factor
  ;

factor:
    LPAREN expr RPAREN
  | functionCall
  | listExpr
  | ID
  | INT_LIT
  ;

functionCall:
    ID LPAREN argList RPAREN
  ;

argList:
    argList COMMA expr
  | expr
  ;

listExpr:
    LBRACKET elemList RBRACKET
  ;

elemList:
    elemList COMMA expr
  | expr
  ;
```

Esta gramática cubre los cuatro patrones requeridos: clases con `classDecl`, funciones con `functionDecl` y `functionCall`, recursión a través de `declarationList`, `statementList`, `expr` y `term` (todas recursivas por la izquierda), y listas con `listExpr` y `elemList`.

### 12.2 — Compatibilidad con LL(1)

La gramática tal como está NO es LL(1) debido a la recursión por la izquierda en múltiples no terminales. Para probar LL(1), la GUI ofrece aplicar `GrammarRewriter.eliminateLeftRecursion`, que produce versiones con sufijo `_prime`.

Para SLR(1) la gramática se usa tal cual: SLR maneja recursión por la izquierda nativamente y de hecho prefiere esa forma.

### 14.2 — Compatibilidad con LL(1)

(continúa de la subsección anterior)

### 14.3 — Adecuación del lexer

Los nombres de los `%token` deben coincidir con las categorías que produce YALex. El `java_lang.yal` actual produce categorías como `KEYWORD`, `INT`, `ID`, `OPERATOR`, `PUNCTUATION` — más generales que lo que el `parser.yalp` necesita.

Se crea un archivo dedicado `parser_test.yal` con regex específicas para cada token del `parser.yalp` (CLASS, FUN, RETURN como keywords distintos; LBRACE, RBRACE, etc. como puntuaciones distintas). Esto deja el `java_lang.yal` original intacto para el Proyecto 1 y aísla los tests del Proyecto 2.

### 14.4 — `cadenas.txt`

Archivo de cadenas a analizar. Debe contener al menos un caso de cada patrón requerido:

```
class Foo {
    fun bar(x, y) {
        return x + y;
    }
}

fun main() {
    return [1, 2, 3];
}

fun rec(n) {
    return rec(n);
}
```

El sistema debe aceptar cada uno de estos como sintácticamente correcto. Adicionalmente se incluyen casos negativos (sintaxis incorrecta) para verificar el reporte de errores.

---

## 15. Detalle algorítmico y glosario

Esta sección describe paso a paso cada algoritmo del Proyecto 2 con la terminología exacta del Dragon Book y un glosario de operaciones primitivas. El objetivo es que cualquier integrante del equipo pueda explicar su módulo a partir de aquí, citando la sección del libro de referencia.

### 15.1 — Glosario de operaciones primitivas

**Shift (desplazamiento).** Acción de un parser bottom-up que consume el token actual de la entrada y empuja el estado destino al stack. Se aplica cuando la tabla `ACTION[estadoCima, tokenActual] = Shift(s)`. Avanza el cursor de entrada en una posición.

**Reduce (reducción).** Acción de un parser bottom-up que aplica una producción `A → α` "al revés": saca `|α|` estados del stack, consulta `GOTO[nuevoEstadoCima, A]` para obtener el siguiente estado, y empuja ese estado. La reducción NO consume tokens — el token actual sigue siendo el mismo después de reducir. Es el momento donde se construye un nodo interno del árbol sintáctico tomando los `|α|` subárboles que estaban en el stack paralelo.

**Accept (aceptación).** Acción que indica que el parser ha consumido toda la entrada exitosamente. En SLR/LALR ocurre cuando el item `[S' → S•]` es completo y el token actual es `EndMarker`. Termina el parsing con éxito.

**Lookahead (anticipación).** En LL(1) y LR(1)/LALR(1), el lookahead es el siguiente terminal de la entrada que se inspecciona sin consumirlo, para decidir qué producción aplicar. En LR(1) los items cargan un terminal de lookahead que restringe cuándo puede ejecutarse una reducción. En LL(1) el lookahead es simplemente el token actual usado para indexar la tabla `M[A, lookahead]`.

**Closure (cerradura).** Operación sobre un conjunto de items que lo expande: para cada item con un no terminal `B` después del punto, agrega los items iniciales de todas las producciones de `B`. En LR(0) esto significa agregar `B → •γ`. En LR(1) esto significa agregar `[B → •γ, b]` para cada `b` en `FIRST(βa)` donde el contexto era `[A → α•Bβ, a]`.

**Goto (transición).** Operación sobre un conjunto de items y un símbolo `X`. Avanza el punto en cada item que tenga `X` después del punto, y retorna el closure del resultado. Define las transiciones del autómata.

**Handle (mango).** En el contexto de un parser bottom-up, el handle es la subcadena en la cima del stack que coincide con el cuerpo de una producción y debería reducirse en este momento para reconstruir la derivación derecha-más en reverso. Identificar handles correctamente es lo que el autómata LR hace por nosotros.

**Viable prefix (prefijo viable).** Cualquier prefijo de una forma de oración derecha que pueda aparecer en el stack durante un parsing válido. Los autómatas LR reconocen exactamente los viable prefixes — cada estado del autómata representa el conjunto de items posibles para algún prefijo viable.

**Item LR(0).** Una producción con un punto en alguna posición del cuerpo: `A → α•β`. Significa "estoy intentando reconocer A, ya he visto α, espero ver β".

**Item LR(1).** Un item LR(0) extendido con un terminal de lookahead: `[A → α•β, a]`. Significa lo mismo que el item LR(0) más "y solo aplicaré la reducción cuando termine si lo que sigue es a".

**FIRST(α).** Conjunto de terminales con los que pueden empezar las cadenas derivables de α. Si α puede derivar la cadena vacía, ε también está en FIRST(α). Calculado por punto fijo (Dragon Book §4.4.2).

**FOLLOW(A).** Conjunto de terminales que pueden aparecer inmediatamente después de A en alguna forma de oración. EndMarker está en FOLLOW del símbolo inicial. Calculado por punto fijo (Dragon Book §4.4.2).

### 15.2 — Algoritmo: Construcción del autómata LR(0)

Referencia: Dragon Book §4.6.2, Algoritmo 4.32.

**Entrada:** una gramática `G`.

**Salida:** un autómata `LR0Automaton` con la colección canónica de conjuntos de items LR(0).

**Pasos:**

1. **Aumentar la gramática:** agregar la producción `S' → S` donde `S` es el símbolo inicial original. `S'` se vuelve el nuevo símbolo inicial.
2. **Estado inicial:** `I0 = closure({S' → •S})`.
3. **Iterar hasta estabilizar:** para cada estado `Ii` ya descubierto, para cada símbolo `X` de la gramática, calcular `J = goto(Ii, X)`. Si `J` no es vacío y no existe ya en la colección, agregarlo como nuevo estado. Registrar la transición `(Ii, X) → J`.
4. Repetir hasta que ninguna iteración descubra estados nuevos.

**`closure(I)`:**

1. Inicializar `J = I`.
2. Para cada item `A → α•Bβ` en `J` donde `B` es no terminal, para cada producción `B → γ`, agregar `B → •γ` a `J` si no estaba ya.
3. Repetir hasta que no se agreguen items nuevos.
4. Retornar `J`.

**`goto(I, X)`:**

1. Sea `J = { A → αX•β | A → α•Xβ está en I }` (avanzar el punto sobre X en todos los items aplicables).
2. Retornar `closure(J)`.

### 15.3 — Algoritmo: Construcción del autómata LR(1)

Referencia: Dragon Book §4.7.2, Algoritmo 4.53.

Mismo esqueleto que LR(0) pero los items cargan lookahead y `closure` cambia.

**Estado inicial:** `I0 = closure({[S' → •S, $]})` donde `$` es EndMarker.

**`closure(I)` para LR(1):**

1. Inicializar `J = I`.
2. Para cada item `[A → α•Bβ, a]` en `J`, para cada producción `B → γ`, para cada terminal `b` en `FIRST(βa)`, agregar `[B → •γ, b]` a `J` si no estaba.
3. Repetir hasta estabilizar.

`FIRST(βa)` significa `FIRST` aplicado a la concatenación de la cadena `β` seguida del terminal `a`. Si `β` es nullable, entonces `a` aparece en el resultado.

**`goto(I, X)` para LR(1):** igual que en LR(0) — avanza el punto sobre X arrastrando los lookaheads sin modificarlos, y retorna el closure del resultado.

### 15.4 — Algoritmo: Construcción de LALR(1) por merge de cores

Referencia: Dragon Book §4.7.4, Algoritmo 4.59.

**Entrada:** un `LR1Automaton` ya construido.

**Salida:** un `LR1Automaton` con menos estados (el "automatón LALR(1)").

**Pasos:**

1. Para cada par de estados `Ii, Ij` que tengan el mismo `core` (el mismo conjunto de items LR(0) subyacentes ignorando lookaheads), marcarlos para merge.
2. Crear un nuevo estado `Iij` cuyos items son los items LR(0) del core, cada uno con un lookahead que es la unión de los lookaheads originales en `Ii` y `Ij`.
3. Reescribir todas las transiciones que apuntaban a `Ii` o `Ij` para que ahora apunten a `Iij`.
4. Repetir hasta que no haya más estados con cores duplicados.

El resultado tiene la misma cantidad de estados que el autómata LR(0) original. Los lookaheads son menos precisos que en LR(1) puro (porque se unieron) pero más precisos que FOLLOW (que es lo que SLR usa). Por eso LALR queda en el medio del trío de potencia: SLR < LALR < LR(1) canónico.

### 15.5 — Algoritmo: Construcción de la tabla SLR(1)

Referencia: Dragon Book §4.6.4, Algoritmo 4.46.

**Entrada:** una gramática aumentada `G'`, su autómata LR(0), y los conjuntos FOLLOW.

**Salida:** las tablas `ACTION` y `GOTO`.

**Pasos:** para cada estado `Ii`:

1. Para cada item `A → α•aβ` en `Ii` donde `a` es terminal, si `goto(Ii, a) = Ij`, entonces `ACTION[i, a] = Shift(j)`.
2. Para cada item `A → α•` en `Ii` (item completo) donde `A ≠ S'`, para cada terminal `b` en `FOLLOW(A)`, `ACTION[i, b] = Reduce(A → α)`.
3. Si `[S' → S•]` está en `Ii`, entonces `ACTION[i, $] = Accept`.
4. Para cada no terminal `A`, si `goto(Ii, A) = Ij`, entonces `GOTO[i, A] = j`.

Si en algún paso una celda `ACTION[i, X]` recibe dos asignaciones distintas, hay conflicto y la gramática no es SLR(1).

### 15.6 — Algoritmo: Construcción de la tabla LALR(1)

Referencia: Dragon Book §4.7.4, Algoritmo 4.56.

Idéntica a SLR pero con dos diferencias clave:

1. Se trabaja sobre el autómata LALR (LR(1) mergeado), no sobre LR(0).
2. En el paso de reducciones, en lugar de usar `FOLLOW(A)`, se usa el conjunto de lookaheads del item completo: para cada item `[A → α•, b]` en `Ii`, `ACTION[i, b] = Reduce(A → α)`.

Por usar lookaheads que vienen del propio item (más restrictivos que FOLLOW), LALR resuelve conflictos donde SLR fallaría.

### 15.7 — Algoritmo: Driver shift-reduce (SLR/LALR)

Referencia: Dragon Book §4.5.3, Algoritmo 4.44.

**Entrada:** una tabla `ACTION` + `GOTO` y una secuencia de tokens.

**Salida:** un `ParseResult` con árbol sintáctico o error.

**Estado:**
- `stateStack`: pila de estados, inicializada con `[0]`.
- `treeStack`: pila paralela de subárboles, inicialmente vacía.
- `cursor`: posición actual en la entrada.

**Loop principal:**

1. Sea `s` el estado en la cima de `stateStack` y `a` el token actual.
2. Consultar `ACTION[s, a]`:
   - **Si `Shift(t)`:** empujar `t` a `stateStack`, empujar `Leaf(a)` a `treeStack`, avanzar el cursor.
   - **Si `Reduce(A → α)`:** sacar `|α|` estados de `stateStack` y `|α|` subárboles de `treeStack`. Sea `s'` la nueva cima. Empujar `GOTO[s', A]` a `stateStack`. Empujar `Internal(A, producción, [los subárboles sacados en orden])` a `treeStack`.
   - **Si `Accept`:** retornar `Accepted(trace, treeStack.top)`.
   - **Si no hay entrada:** retornar `Rejected(trace, error con expectedTokens, treeStack.top)`.

En cada iteración registrar un `ParseStep` con el snapshot de `stateStack`, la entrada restante, y la acción tomada.

### 15.8 — Algoritmo: Construcción de la tabla LL(1)

Referencia: Dragon Book §4.4.3, Algoritmo 4.31.

**Entrada:** una gramática y sus conjuntos FIRST y FOLLOW.

**Salida:** la tabla `M[A, a]`.

**Pasos:** para cada producción `A → α`:

1. Para cada terminal `a` en `FIRST(α)` donde `a ≠ ε`, agregar `A → α` a `M[A, a]`.
2. Si `ε` está en `FIRST(α)`, entonces para cada terminal `b` en `FOLLOW(A)`, agregar `A → α` a `M[A, b]`. Si además `EndMarker` está en `FOLLOW(A)`, agregar también a `M[A, $]`.

Si alguna celda `M[A, a]` recibe más de una producción, hay conflicto y la gramática no es LL(1).

### 15.9 — Algoritmo: Driver predictivo LL(1)

Referencia: Dragon Book §4.4.4, Algoritmo 4.34.

**Entrada:** una tabla `M`, una gramática, y una secuencia de tokens.

**Salida:** un `ParseResult` con árbol sintáctico o error.

**Estado:**
- `stack`: pila de símbolos, inicializada con `[EndMarker, S]` donde `S` es el símbolo inicial.
- `treeRoot`: nodo raíz del árbol que se va construyendo.
- `cursor`: posición actual en la entrada.

**Loop principal:**

1. Sea `X` la cima del stack y `a` el token actual.
2. Caso `X` es terminal:
   - Si `X == a`: hacer pop de `X`, asociar `Leaf(a)` al nodo correspondiente del árbol, avanzar cursor.
   - Si no: error.
3. Caso `X` es `EndMarker`:
   - Si `a` es EndMarker: aceptar.
   - Si no: error.
4. Caso `X` es no terminal:
   - Consultar `M[X, a]`. Si hay producción `X → Y1 Y2 ... Yk`: hacer pop de `X`, empujar `Yk, Yk-1, ..., Y1` (en orden inverso para que `Y1` quede en la cima). Crear un nodo `Internal(X, producción)` con `k` hijos pendientes, asociarlo al árbol, y mantener punteros a los hijos para irlos llenando conforme se reconozcan.
   - Si no hay producción: error.

En cada iteración registrar un `ParseStep`.

### 15.10 — Convenciones de nomenclatura

Para que el código sea legible y coincida con la teoría:

- Estados del autómata: `i`, `j`, `s`, `t` (igual que el libro).
- Item sets: `Ii` con i numérico (`I0`, `I1`, `I2`, ...).
- Producción: `A → α` donde `A` es head no terminal, `α` es body lista de símbolos.
- Lookahead: variable `a` o `b` cuando es un terminal específico, `lookahead` en código.
- Acciones abreviadas en tablas impresas: `s5` (shift 5), `r3` (reduce by 3), `acc` (accept), celda vacía (error).
- Items LR(0) impresos: `A → α • β` con espacios alrededor del punto.
- Items LR(1) impresos: `[A → α • β, a]` con corchetes y lookahead separado por coma.

Estas convenciones se usan tanto en mensajes de la GUI como en comentarios del código y en este documento.
