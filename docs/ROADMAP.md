# Roadmap del Proyecto 2 -- Tickets de desarrollo

Este documento enlista los tickets en orden secuencial para construir el Proyecto 2 sobre el código existente del Proyecto 1. Para el diseño detallado de cada elemento (data classes, sealed interfaces, firmas de funciones, algoritmos) consultar el documento de plan: [PROJECT_2_PLAN.md](./PROJECT_2_PLAN.md).

> **Cambios respecto a la versión inicial (2026-05-14)**:
> - Se agregó la Fase 3 (soporte para precedencia y `PrecedenceRewriter`) tras revisión con el catedrático.
> - Se rediseñaron las fases de parseo: se eliminó el módulo LR(0) puro y el módulo LR(1) separado. En este proyecto **SLR(1) = LR(1) canónico (Dragon Book §4.7.2) sin FOLLOW**, y **LALR(1) = SLR(1) + merge de cores (§4.7.4)**. Ver [docs/plans/2026-05-14-slr1-lalr1-terminology.md](./plans/2026-05-14-slr1-lalr1-terminology.md).
> - Se eliminó la Fase de CLI. La única interfaz al cierre del proyecto es la GUI.
> - Diseño del `PrecedenceRewriter` en [docs/plans/2026-05-14-precedence-rewriter-design.md](./plans/2026-05-14-precedence-rewriter-design.md).

## Cómo se usa este documento

Los tickets están agrupados en fases. Cada fase asume que las anteriores están terminadas. Dentro de una fase, los tickets que no tienen dependencia explícita entre sí se pueden distribuir entre integrantes en paralelo.

Cada ticket lleva:
- **Archivos** que crea o modifica
- **Descripción** corta de lo que hay que hacer
- **Aceptación**: cuándo se considera terminado
- **Plan**: sección del documento de plan que lo respalda
- **Depende de**: tickets previos requeridos

El campo **Estado** se actualiza manualmente conforme se avanza: `pendiente`, `en progreso`, `completado`.

---

## Fase 1 -- Refactor del proyecto actual

Esta fase prepara el código existente del Proyecto 1 para que el parser pueda consumirlo limpiamente. Es la base de todo lo demás.

### Ticket 1 -- Crear `LexemeLocation`

- **Estado**: completado
- **Depende de**: ninguno
- **Archivos**: `org/compiler/models/LexemeLocation.kt`
- **Descripción**: `LexemeLocation(line: Int, position: Int)` en `org.compiler.models` (por encima de `frontend/`). Vive a ese nivel porque la consumen `SymbolTableEntry`, `CompilerError` y el árbol sintáctico -- estructuras de distintas fases. `line` y `position` son 1-based.
- **Plan**: §2.3

### Ticket 2 -- Rediseñar `Token` y establecer paquetes globales

- **Estado**: completado
- **Depende de**: Ticket 1
- **Archivos**:
  - `frontend/models/Token.kt` -- `category`, `lexeme`, `symbolIndex: Int?`
  - `org/compiler/symbolTable/SymbolTable.kt` -- sube de `frontend/lexicalAnalyzer/lexer/models/`
  - `org/compiler/symbolTable/SymbolTableEntry.kt` -- `index`, `name`, `location`
  - `org/compiler/diagnostics/CompilerError.kt` -- sealed interface: `LexerError` | `ParserError`
  - `org/compiler/diagnostics/DiagnosticsTable.kt` -- colector global de errores
- **Descripción**: `Token` sigue el modelo Dragon Book §2.6: `symbolIndex` apunta a la entrada de la tabla de símbolos para cualquier categoría que no sea `KEYWORD`; para keywords el índice es `null` y se usa el lexema. La ubicación en el fuente vive en `SymbolTableEntry`, no en `Token`. Los errores (léxicos y sintácticos) van a `DiagnosticsTable` como `CompilerError`.
- **Plan**: §2.1, §2.2

### Ticket 3 -- Tracking de posición en `Scanner`

- **Estado**: completado
- **Depende de**: Ticket 2
- **Archivos**: `frontend/lexicalAnalyzer/scanner/Scanner.kt`
- **Descripción**: el scanner lleva `currentLine` y `currentPosition`. Al reconocer un token que no es KEYWORD, llama `SymbolTable.addOrGet(lexeme, location)` y guarda el índice en `symbolIndex`. En panic mode reporta `CompilerError.LexerError` a `DiagnosticsTable`. El helper `advanceLineAndPosition` actualiza ambos contadores en las tres ramas.
- **Plan**: §2.4

### Ticket 4 -- Output en `LexerApp`

- **Estado**: completado
- **Depende de**: Ticket 3
- **Archivos**: `LexerApp.kt`
- **Descripción**: `tokens.txt` usa `token.symbolIndex` directamente. `errors.txt` lee de `DiagnosticsTable.lexerErrors()`. `symbolTable.txt` incluye `index|name|line:position` por entrada.
- **Plan**: §2.5

### Ticket 5 -- API pública del lexer (`Lexer.kt`)

- **Estado**: completado
- **Depende de**: Tickets 2, 3, 4
- **Archivos**:
  - `frontend/lexicalAnalyzer/lexer/Lexer.kt`
  - `frontend/lexicalAnalyzer/manageGrammar/utils/YalexReader.kt` (expone `parse(content)`)
  - `frontend/lexicalAnalyzer/manageGrammar/models/CategoryAutomataIndex.kt` (agrega `clear()`)
  - `app/src/test/kotlin/org/compiler/LexerTest.kt`
- **Descripción**: `object Lexer { fun tokenize(yalexContent, source): LexerResult }`. `LexerResult` tiene `tokens`, `errors: List<CompilerError.LexerError>` (de `DiagnosticsTable`), y `automata`. El scanner limpia `SymbolTable` y `DiagnosticsTable` al inicio de cada llamada.
- **Plan**: §2.6

---

## Fase 2 -- Módulo Grammar (base)

Modelos de la gramática y lectura del archivo `.yalp`. Los tickets 6-9 ya están listos; la Fase 3 los extiende para soportar precedencia.

### Ticket 6 -- Modelos del módulo Grammar

- **Estado**: completado
- **Depende de**: ninguno
- **Archivos**:
  - `frontend/syntaxAnalyzer/grammar/models/Symbol.kt`
  - `frontend/syntaxAnalyzer/grammar/models/Production.kt`
  - `frontend/syntaxAnalyzer/grammar/models/Grammar.kt`
- **Descripción**: `Symbol` como sealed interface con `Terminal`, `NonTerminal`, `Epsilon`, `EndMarker`. `Production(id, head, body)`. `Grammar(terminals, nonTerminals, productions, productionsByHead, startSymbol, ignoredTokens)`.
- **Plan**: §3.1

### Ticket 7 -- `YalpReader`

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**: `frontend/syntaxAnalyzer/grammar/YalpReader.kt`
- **Descripción**: `object YalpReader { fun read(filePath: String): Grammar }`. Elimina comentarios `/* */`, separa secciones por `%%`, parsea `%token` e `IGNORE` en la sección de tokens, parsea producciones con sintaxis `nombre: cuerpo1 | cuerpo2 | ... ;`. Convención: minúsculas son no terminales, MAYÚSCULAS son terminales.
- **Plan**: §3.2

### Ticket 8 -- `GrammarValidator`

- **Estado**: completado
- **Depende de**: Ticket 7
- **Archivos**: `frontend/syntaxAnalyzer/grammar/GrammarValidator.kt`
- **Descripción**: `validate(grammar, lexerCategories): List<ValidationError>`. Valida que cada `%token` exista en las categorías del lexer, que ningún no terminal usado quede sin declarar, y reporta advertencias por no terminales inalcanzables, no usados, o producciones duplicadas.
- **Plan**: §3.3

### Ticket 9 -- `GrammarRewriter` (eliminación de recursión por la izquierda)

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**: `frontend/syntaxAnalyzer/grammar/GrammarRewriter.kt`
- **Descripción**: `eliminateLeftRecursion(grammar): Grammar` siguiendo Dragon Book §4.3.3. Genera no terminales auxiliares con sufijo `_prime`.
- **Plan**: §3.4, §15.1
- **Nota**: en la Fase 3 (Ticket 10) este archivo se **renombra** a `LeftRecursionRewriter.kt`. Su lógica no cambia.

---

## Fase 3 -- Soporte para precedencia de operadores

Tras la revisión con el catedrático se confirmó que la desambiguación de la gramática debe hacerse por **precedencia de operadores** generando nuevas producciones, no solo eliminando recursión por la izquierda. Esta fase extiende el módulo Grammar para declarar precedencia y agrega el módulo que reescribe la gramática.

### Ticket 10 -- Extender módulo Grammar para precedencia

- **Estado**: completado
- **Depende de**: Tickets 6, 7, 8, 9
- **Archivos**:
  - `frontend/syntaxAnalyzer/grammar/models/Associativity.kt` (NUEVO)
  - `frontend/syntaxAnalyzer/grammar/models/PrecedenceLevel.kt` (NUEVO)
  - `frontend/syntaxAnalyzer/grammar/models/Grammar.kt` (modificar: agregar `precedenceTable: List<PrecedenceLevel>`)
  - `frontend/syntaxAnalyzer/grammar/YalpReader.kt` (modificar: parsear `%left`, `%right`)
  - `frontend/syntaxAnalyzer/grammar/GrammarValidator.kt` (modificar: validar que operadores en precedence table existan como tokens; que ningún operador aparezca en dos niveles)
  - `frontend/syntaxAnalyzer/grammar/GrammarRewriter.kt` -> renombrar a `LeftRecursionRewriter.kt` (clase y referencias)
  - `app/src/test/kotlin/org/compiler/YalpReaderTest.kt` (tests del parseo de precedencia)
- **Descripción**: Crear `enum class Associativity { LEFT, RIGHT }` y `data class PrecedenceLevel(level: Int, operators: Set<Symbol.Terminal>, associativity: Associativity)`. Extender `Grammar` con `precedenceTable`. Extender `YalpReader` para parsear declaraciones `%left X Y`, `%right X` antes de `%%` (el orden de aparición define el nivel: el primer `%left/%right` corresponde a la **menor** precedencia). Extender `GrammarValidator` con las dos validaciones nuevas. Renombrar `GrammarRewriter` a `LeftRecursionRewriter` (file rename + class rename + actualizar imports y tests).
- **Aceptación**:
  - Un `.yalp` con `%left OP_PLUS\n%left OP_TIMES` produce un `precedenceTable` con dos niveles, OP_PLUS en nivel 0 y OP_TIMES en nivel 1, ambos LEFT.
  - Declarar un operador en la precedence table que no está en `%token` produce un error de validación.
  - `LeftRecursionRewriter` se invoca sin errores donde antes se invocaba `GrammarRewriter`.
- **Plan**: §3.1, §3.2, §3.3, [plans/2026-05-14-precedence-rewriter-design.md §2](./plans/2026-05-14-precedence-rewriter-design.md)

### Ticket 11 -- `PrecedenceRewriter`

- **Estado**: completado
- **Depende de**: Ticket 10
- **Archivos**:
  - `frontend/syntaxAnalyzer/grammar/PrecedenceRewriter.kt`
  - `app/src/test/kotlin/org/compiler/PrecedenceRewriterTest.kt`
- **Descripción**: Implementar `object PrecedenceRewriter { fun rewrite(grammar: Grammar): Grammar }` siguiendo el algoritmo de [plans/2026-05-14-precedence-rewriter-design.md §4](./plans/2026-05-14-precedence-rewriter-design.md). Para cada NT con producciones binarias o unarias sobre operadores con precedencia declarada, generar NTs sintéticos por nivel (`A_lvl0, A_lvl1, ..., A_atom`), emitir las producciones encadenadas según asociatividad, y bajar las producciones no-operador al nivel atómico. NTs sin operadores con precedencia quedan intactos.
- **Aceptación**:
  - Gramática `expr -> expr OP_PLUS expr | expr OP_TIMES expr | ID` con `%left OP_PLUS` (nivel 0) y `%left OP_TIMES` (nivel 1) produce la cascada `expr -> expr_lvl0; expr_lvl0 -> expr_lvl0 OP_PLUS expr_lvl1 | expr_lvl1; expr_lvl1 -> expr_lvl1 OP_TIMES expr_atom | expr_atom; expr_atom -> ID`.
  - NOT unario (`%right OP_NOT`) produce `expr_lvlN -> OP_NOT expr_lvlN | expr_lvl(N+1)`.
  - ASSIGN derecho (`%right OP_ASSIGN`) produce recursión derecha binaria.
  - `precedenceTable` vacía retorna la gramática sin tocar.
  - Paréntesis (`expr -> LPAREN expr RPAREN`) caen al nivel atómico referenciando la cabecera original.
- **Plan**: [plans/2026-05-14-precedence-rewriter-design.md](./plans/2026-05-14-precedence-rewriter-design.md)

---

## Fase 4 -- Módulo Sets

Cálculo de FIRST y FOLLOW. FOLLOW se mantiene porque LL(1) lo necesita; SLR(1) y LALR(1) **no** lo usan (ver [plans/2026-05-14-slr1-lalr1-terminology.md](./plans/2026-05-14-slr1-lalr1-terminology.md)).

### Ticket 12 -- Módulo Sets

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/sets/models/FirstSets.kt`
  - `frontend/syntaxAnalyzer/sets/models/FollowSets.kt`
  - `frontend/syntaxAnalyzer/sets/FirstSetComputer.kt`
  - `frontend/syntaxAnalyzer/sets/FollowSetComputer.kt`
- **Descripción**: `FirstSetComputer.compute(grammar)` y `FollowSetComputer.compute(grammar, firstSets)`. `firstOfSequence` es función pública dentro de `FirstSetComputer`.
- **Plan**: §4.1, §4.2, §4.3, §15.1

---

## Fase 5 -- Módulo LL(1)

Construcción de tabla LL(1) y parser predictivo.

### Ticket 13 -- Modelos LL(1)

- **Estado**: pendiente
- **Depende de**: Ticket 12
- **Archivos**:
  - `frontend/syntaxAnalyzer/ll1/models/LL1Cell.kt`
  - `frontend/syntaxAnalyzer/ll1/models/LL1Table.kt`
- **Descripción**: `LL1Conflict(nonTerminal, terminal, productions)` y `LL1Table(cells)` con métodos `isLL1()`, `conflicts()`, `lookup()`.
- **Aceptación**: se puede construir manualmente una `LL1Table` y consultar `lookup(A, a)`.
- **Plan**: §5.1

### Ticket 14 -- `LL1TableBuilder`

- **Estado**: pendiente
- **Depende de**: Tickets 11, 13
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1TableBuilder.kt`
- **Descripción**: Construir la tabla LL(1) sobre la gramática **ya reescrita por `PrecedenceRewriter` y `LeftRecursionRewriter`**. Implementar Dragon Book §4.4.3 (Algoritmo 4.31).
- **Aceptación**: la tabla generada para la gramática de expresiones aritméticas factorizada coincide con la del libro.
- **Plan**: §5.2, §15.8

### Ticket 15 -- `LL1Parser`

- **Estado**: pendiente
- **Depende de**: Tickets 14, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1Parser.kt`
- **Descripción**: `class LL1Parser(grammar, table) { fun parse(tokens): ParseResult }`. Stack inicia con `[EndMarker, startSymbol]`. Aplicar Dragon Book §4.4.4 (Algoritmo 4.34). Construir el `ParseTree` mientras parsea.
- **Nota para integración con Runtime**: las acciones específicas del parser predictivo (Match, Expand, etc.) deben agregarse como variantes de la sealed interface `Action` en `frontend/syntaxAnalyzer/runtime/models/Action.kt`. Esto permite que `ParseStep.action: Action` siga sirviendo para los tres parsers sin necesidad de tipos paralelos. La sealed interface ya contiene `Shift`, `Reduce` y `Accept` (usados por SLR/LALR); agregar las variantes LL1 al mismo archivo.
- **Aceptación**: parsear `id + id * id` con la gramática de expresiones LL(1) retorna `Accepted` con el árbol correcto.
- **Plan**: §5.3, §15.9

---

## Fase 6 -- Módulo SLR(1)

**Nota de terminología**: en este proyecto SLR(1) = LR(1) canónico del Dragon Book §4.7.2, con lookaheads propagados desde el closure vía `FIRST(beta a)`. **No usa FOLLOW**. Ver [plans/2026-05-14-slr1-lalr1-terminology.md](./plans/2026-05-14-slr1-lalr1-terminology.md).

### Ticket 16 -- Modelos SLR(1)

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/slr1/models/SLR1Item.kt`
  - `frontend/syntaxAnalyzer/slr1/models/SLR1State.kt`
  - `frontend/syntaxAnalyzer/slr1/models/SLR1Automata.kt`
- **Descripción**: `SLR1Item(production, dotPosition, lookaheads: Set<Symbol>)` con propiedades `isComplete`, `symbolAfterDot`, `advance()`, `core: Pair<Production, Int>`. Representación compacta: los lookaheads se agrupan por core. `SLR1State(id, items)` con propiedad `core: Set<Pair<Production, Int>>`. `SLR1Automata(states, transitions, initialState, augmentedGrammar)`.
- **Aceptación**: se pueden construir items SLR(1) con distintos lookaheads, comparar sus cores, y avanzar el punto.
- **Plan**: §8.1 (reutilizado para SLR en este proyecto)

### Ticket 17 -- `SLR1AutomatonBuilder`

- **Estado**: completado
- **Depende de**: Tickets 11, 12, 16
- **Archivos**: `frontend/syntaxAnalyzer/slr1/SLR1AutomatonBuilder.kt`
- **Descripción**: `build(grammar, firstSets)`, `closure(items, grammar, firstSets)`, `goto(items, symbol, grammar, firstSets)`. El closure propaga lookaheads vía `FIRST(beta a)` siguiendo Dragon Book §4.7.2 (Algoritmo 4.53). La gramática se aumenta con `S' -> S` internamente y el item inicial es `[S' -> .S, $]`.
- **Aceptación**: el autómata generado para la gramática de expresiones del Dragon Book tiene los items con lookaheads correctos según figura 4.41.
- **Plan**: §8.2, §15.3

### Ticket 18 -- `Action` y `SLR1Table`

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/runtime/models/Action.kt`
  - `frontend/syntaxAnalyzer/slr1/models/SLR1Table.kt`
- **Descripción**: `sealed interface Action` con `Shift(nextState)`, `Reduce(production)`, `Accept` (data object) vive en `runtime/models/` porque es compartido por SLR(1), LALR(1) y LL(1). `SLR1Table(action, goto, numStates)` con métodos `isSLR1()` y `conflicts()`. `SLR1Conflict` y `enum ConflictType { SHIFT_REDUCE, REDUCE_REDUCE }`.
- **Aceptación**: se puede consultar `action[(0, terminal)]` y obtener una `Action`.
- **Plan**: §7.1

### Ticket 19 -- `SLR1TableBuilder`

- **Estado**: completado
- **Depende de**: Tickets 17, 18
- **Archivos**: `frontend/syntaxAnalyzer/slr1/SLR1TableBuilder.kt`
- **Descripción**: `build(grammar, automaton)`. Recorre estados del autómata; para cada item completo `[A -> alpha., a]` asigna `Reduce(A -> alpha)` en `ACTION[i, a]`. Para cada item con `[A -> alpha.X beta, ...]` y `goto(I_i, X) = I_j`, asigna `Shift(j)` si X es terminal. Detecta conflictos shift-reduce y reduce-reduce. **No usa `FollowSets`** -- los lookaheads vienen del item.
- **Aceptación**: la tabla generada para la gramática de expresiones canónica del Dragon Book es completa y sin conflictos.
- **Plan**: §9.3, §15.6 (algoritmo del libro 4.56 aplicado sin merge)

### Ticket 20 -- `SLR1Parser`

- **Estado**: completado
- **Depende de**: Tickets 19, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/slr1/SLR1Parser.kt`
- **Descripción**: Parser shift-reduce siguiendo Dragon Book §4.5.3 (Algoritmo 4.44). Mantiene stack de estados + stack paralelo de subárboles. Construye el `ParseTree` en cada Reduce combinando subárboles popped.
- **Aceptación**: parsear `id + id * id` con la gramática de expresiones retorna `Accepted` con el árbol correcto. Parsear `id + +` retorna `Rejected` con error en la posición correcta.
- **Plan**: §7.3, §15.7

---

## Fase 7 -- Módulo LALR(1)

LALR(1) en este proyecto = SLR(1) con un paso adicional de **merge por core** uniendo lookaheads. Comparte casi todo el código de Fase 6.

### Ticket 21 -- `LALR1AutomatonMerger`

- **Estado**: pendiente
- **Depende de**: Ticket 17
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1AutomatonMerger.kt`
- **Descripción**: `mergeFromSLR1(automaton: SLR1Automaton): SLR1Automaton`. Identificar todos los pares de estados con el mismo core, fusionarlos uniendo los lookaheads de los items correspondientes, y reescribir las transiciones. Aplicar Dragon Book §4.7.4 (Algoritmo 4.59).
- **Aceptación**: aplicado al autómata SLR(1) de la gramática del libro produce un autómata con menos estados (mismo número que el autómata LR(0) clásico). Los lookaheads del estado mergeado son la unión de los originales.
- **Plan**: §9.2, §15.4

### Ticket 22 -- `LALR1Table`

- **Estado**: pendiente
- **Depende de**: Ticket 18
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/models/LALR1Table.kt`
- **Descripción**: `LALR1Table(action, goto, numStates)` con métodos `isLALR1()` y `conflicts()`. Reusa `Action` y `ConflictType` del módulo SLR(1).
- **Aceptación**: estructura paralela a `SLR1Table` lista para llenarse.
- **Plan**: §9.1

### Ticket 23 -- `LALR1TableBuilder`

- **Estado**: pendiente
- **Depende de**: Tickets 21, 22
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1TableBuilder.kt`
- **Descripción**: `build(grammar, mergedAutomaton)`. Idéntico estructuralmente a `SLR1TableBuilder` pero sobre el autómata mergeado. Los lookaheads de los items mergeados ya contienen la unión, así que el algoritmo es el mismo.
- **Aceptación**: la tabla generada resuelve conflictos en gramáticas donde SLR fallaría debido a la separación de cores.
- **Plan**: §9.3, §15.6

### Ticket 24 -- `LALR1Parser`

- **Estado**: pendiente
- **Depende de**: Tickets 23, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1Parser.kt`
- **Descripción**: Parser shift-reduce idéntico estructuralmente a `SLR1Parser` pero recibe `LALR1Table`. Construye el `ParseTree` igual que en SLR.
- **Aceptación**: parsear las cadenas de prueba con la tabla LALR retorna los mismos resultados que SLR cuando ambos métodos son aplicables.
- **Plan**: §9.4, §15.7

---

## Fase 8 -- Módulo Runtime

Modelos compartidos por los tres parsers. Algunos tickets de esta fase deben terminar antes de los parsers (LL1Parser, SLR1Parser, LALR1Parser).

### Ticket 25 -- Modelos del árbol y resultado

- **Estado**: completado
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/runtime/models/Action.kt` (movido desde `slr1/models/` en este ticket)
  - `frontend/syntaxAnalyzer/runtime/models/ParseTree.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseResult.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseStep.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseError.kt`
- **Descripción**: `ParseTree` como sealed interface con `LeafNode(symbol, token)`, `InternalNode(symbol, production, children)`, `EpsilonNode`. `ParseResult` como sealed interface con `Accepted(trace, parseTree)` y `Rejected(trace, error, partialTree)`. `ParseStep(stack, remainingInput, action)`. `ParseError(message, location, foundToken, expectedTokens)`. `Action` se mueve a `runtime/models/` porque es compartida por SLR(1), LALR(1) y LL(1) (ver nota en Ticket 15).
- **Aceptación**: se puede construir manualmente un `ParseTree` y serializarlo a string indentado.
- **Plan**: §10.1

### Ticket 26 -- `TokenStream`

- **Estado**: completado
- **Depende de**: Ticket 2
- **Archivos**: `frontend/syntaxAnalyzer/runtime/TokenStream.kt`
- **Descripción**: `class TokenStream(tokens, ignored)` con `peek()`, `consume()`, `hasNext()`, `position()`. Filtra tokens cuya categoría está en `ignored` al hacer peek/consume.
- **Aceptación**: dado `[KEYWORD, WS, ID, WS, OPERATOR]` con `ignored = {WS}`, `peek` y `consume` saltan los whitespace transparentemente.
- **Plan**: §10.2

### Ticket 27 -- `Pipeline` orquestador

- **Estado**: pendiente
- **Depende de**: Tickets 5, 7, 8, 10, 11, 12, 14, 17, 19, 21, 23, 15, 20, 24, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/runtime/Pipeline.kt`
- **Descripción**: `object Pipeline { fun runFull(yalexContent, yalpContent, inputContent, method): PipelineResult }`. `enum ParserMethod { LL1, SLR1, LALR1 }`. `data class PipelineResult` con todos los artefactos (tokens, grammar original, grammar tras precedence, grammar tras left recursion (solo si LL1), firstSets, followSets, slr1Automaton, lalr1Automaton, ll1Table, slr1Table, lalr1Table, parseResult). Aplica `LeftRecursionRewriter` solo si el método es `LL1`; para SLR/LALR pasa la salida de `PrecedenceRewriter` directamente.
- **Aceptación**: invocar `Pipeline.runFull` con archivos de prueba retorna un `PipelineResult` con todos los campos poblados.
- **Plan**: §10.3, [plans/2026-05-14-precedence-rewriter-design.md §10](./plans/2026-05-14-precedence-rewriter-design.md)

---

## Fase 9 -- Módulo Visualization

### Ticket 28 -- `DotExporter`

- **Estado**: pendiente
- **Depende de**: Ticket 16
- **Archivos**: `frontend/syntaxAnalyzer/visualization/DotExporter.kt`
- **Descripción**: `slr1ToDot(automaton)`, `lalr1ToDot(automaton)`, `renderToImage(dot, outputPath)`. Las funciones de DOT generan texto Graphviz; `renderToImage` invoca el comando `dot` vía `ProcessBuilder` y retorna boolean.
- **Aceptación**: el archivo PNG generado se abre y muestra el autómata correctamente. Si Graphviz no está instalado, retorna `false` sin lanzar excepción.
- **Plan**: §11.1

### Ticket 29 -- `ParseTreeExporter`

- **Estado**: pendiente
- **Depende de**: Ticket 25
- **Archivos**: `frontend/syntaxAnalyzer/visualization/ParseTreeExporter.kt`
- **Descripción**: `toDot(tree)` y `toIndentedText(tree)`. La versión DOT genera un grafo dirigido con orden de izquierda a derecha. La versión texto usa caracteres ASCII de árbol (`+-`, `|-`).
- **Aceptación**: dado un `ParseTree` para `id + id`, la versión texto se imprime correctamente y la versión DOT se renderiza a PNG.
- **Plan**: §11.2

### Ticket 30 -- `TableFormatter`

- **Estado**: pendiente
- **Depende de**: Tickets 12, 13, 18, 22
- **Archivos**: `frontend/syntaxAnalyzer/visualization/TableFormatter.kt`
- **Descripción**: `formatLL1Table`, `formatSLR1Action`, `formatSLR1Goto`, `formatLALR1Action`, `formatLALR1Goto`, `formatFirstSets`, `formatFollowSets`. Cada una retorna string multilinea alineada en columnas. Acciones se imprimen como `s5`, `r3`, `acc`.
- **Aceptación**: las strings se ven alineadas en un componente monoespaciado.
- **Plan**: §11.3

---

## Fase 10 -- Recursos de prueba

### Ticket 31 -- Archivos de prueba

- **Estado**: pendiente
- **Depende de**: Ticket 10 (necesita la sintaxis `%left`/`%right`/`%nonassoc`)
- **Archivos**:
  - `app/src/main/resources/parser_test.yal` (lexer dedicado para los tests del parser, categorías por operador: `OP_PLUS`, `OP_MINUS`, `OP_TIMES`, `OP_DIV`, `OP_MOD`, `OP_LT`, `OP_GT`, `OP_LE`, `OP_GE`, `OP_EQ`, `OP_NEQ`, `OP_AND`, `OP_OR`, `OP_NOT`, `OP_ASSIGN`)
  - `app/src/main/resources/parser.yalp` (gramática de prueba con declaraciones de precedencia)
  - `app/src/main/resources/cadenas.txt` (cadenas a analizar, casos válidos e inválidos)
- **Descripción**: Tabla de precedencia confirmada (de menor a mayor):
  - `%left OP_OR`
  - `%left OP_AND`
  - `%left OP_EQ OP_NEQ`
  - `%left OP_LT OP_GT OP_LE OP_GE`
  - `%left OP_PLUS OP_MINUS`
  - `%left OP_TIMES OP_DIV OP_MOD`
  - `%right OP_NOT` (unario prefijo)
  - `%right OP_ASSIGN`

  Convención AND/OR: estilo C/Java. `&&` mayor precedencia que `||`. NOT es unario prefijo asociativo a la derecha. ASSIGN asociativo a la derecha. La gramática debe cubrir clases, funciones, recursión y listas además de expresiones.
- **Aceptación**: los tres archivos existen y son leíbles. La gramática parsea con `YalpReader` sin errores. La tabla de precedencia resultante tiene 8 niveles.
- **Plan**: §14.1, §14.3, §14.4

---

## Fase 11 -- GUI (Compose Desktop)

La GUI es la **única interfaz** al usuario final. No hay CLI.

### Ticket 32 -- Setup de Compose Desktop

- **Estado**: pendiente
- **Depende de**: ninguno
- **Archivos**: `app/build.gradle.kts`
- **Descripción**: agregar el plugin `org.jetbrains.compose` y declarar `compose.desktop.currentOs`, `compose.material`, `compose.foundation`. Registrar la task `runGui` con `JavaExec`.
- **Aceptación**: un Hello World mínimo en Compose Desktop compila y `./gradlew runGui` lo ejecuta.
- **Plan**: §12.1, §13.3

### Ticket 33 -- `AppState`

- **Estado**: pendiente
- **Depende de**: Tickets 27, 32
- **Archivos**: `gui/state/AppState.kt`
- **Descripción**: `class AppState` con todos los campos `mutableStateOf`. `onPlay()` invoca `Pipeline.runFull` y guarda el resultado. `changeMethod(newMethod)` solo re-ejecuta el parser del nuevo método sobre los tokens ya construidos.
- **Aceptación**: se puede instanciar `AppState`, llamar `onPlay()`, y el campo `pipelineResult` queda populado.
- **Plan**: §12.2

### Ticket 34 -- Componentes básicos de GUI

- **Estado**: pendiente
- **Depende de**: Ticket 32
- **Archivos**:
  - `gui/components/CodeEditor.kt`
  - `gui/components/MethodDropdown.kt`
  - `gui/components/PlayButton.kt`
- **Descripción**: `CodeEditor` envuelve `BasicTextField` con fuente monoespaciada y números de línea. `MethodDropdown` muestra las tres opciones de `ParserMethod`. `PlayButton` con icono que se deshabilita cuando `isRunning`.
- **Aceptación**: cada componente se puede previsualizar de forma aislada y reacciona a interacciones.
- **Plan**: §12.5

### Ticket 35 -- Componentes de resultados

- **Estado**: pendiente
- **Depende de**: Tickets 25, 29, 32
- **Archivos**:
  - `gui/components/TokenList.kt`
  - `gui/components/ParseTreeView.kt`
  - `gui/components/ErrorList.kt`
- **Descripción**: `TokenList` muestra tabla de tokens con categoría, lexema, posición. `ParseTreeView` muestra la imagen del árbol o el texto indentado. `ErrorList` muestra errores con icono, ubicación y mensaje.
- **Aceptación**: cada componente renderiza correctamente datos de prueba.
- **Plan**: §12.5

### Ticket 36 -- `WorkspaceScreen`

- **Estado**: pendiente
- **Depende de**: Tickets 33, 34, 35
- **Archivos**: `gui/screens/WorkspaceScreen.kt`
- **Descripción**: layout principal con toolbar superior (dropdown + play), centro con tabs entre los tres editores, y panel derecho con sub-pestañas Tokens / Parse Tree / Errores.
- **Aceptación**: el usuario puede editar la cadena, escoger método, presionar Play, y ver los tres paneles de resultados poblarse.
- **Plan**: §12.3

### Ticket 37 -- Pantallas secundarias

- **Estado**: pendiente
- **Depende de**: Tickets 28, 30, 33
- **Archivos**:
  - `gui/screens/AutomatonScreen.kt`
  - `gui/screens/TablesScreen.kt`
- **Descripción**: `AutomatonScreen` muestra el autómata con un toggle "SLR(1) sin merge / LALR(1) con merge". `TablesScreen` tiene cuatro sub-pestañas (FIRST/FOLLOW, LL(1), SLR(1), LALR(1)) con el texto formateado por `TableFormatter`.
- **Aceptación**: las pantallas se abren desde el menú View y muestran contenido cuando hay un `pipelineResult`.
- **Plan**: §12.4

### Ticket 38 -- Menús File y View

- **Estado**: pendiente
- **Depende de**: Ticket 33
- **Archivos**:
  - `gui/components/FileMenu.kt`
  - `gui/components/ViewMenu.kt`
- **Descripción**: `FileMenu` con Open .yalex / Open .yalp / Open Input / Save All / Save As. `ViewMenu` con toggles para autómata (SLR/LALR), tablas, y volver al workspace.
- **Aceptación**: los menús abren diálogos de archivo y actualizan el `AppState` correctamente.
- **Plan**: §12.5

### Ticket 39 -- Punto de entrada GUI

- **Estado**: pendiente
- **Depende de**: Tickets 36, 37, 38
- **Archivos**:
  - `gui/App.kt`
  - `GuiApp.kt`
- **Descripción**: `App.kt` define la ventana Compose Desktop con menú superior y `WorkspaceScreen` como contenido principal. `GuiApp.kt` es el `main()` runnable que invoca `App`.
- **Aceptación**: `./gradlew runGui` abre la ventana del IDE funcional.
- **Plan**: §12.1

---

## Fase 12 -- Integración y verificación

### Ticket 40 -- Verificación end-to-end

- **Estado**: pendiente
- **Depende de**: todos los anteriores
- **Archivos**: ninguno (solo verificación manual)
- **Descripción**: verificar que el flujo completo funcione con los tres métodos sobre las cadenas de prueba. Verificar que: (1) los tokens se identifican correctamente, (2) la gramática se reescribe por precedencia y se ve sin ambigüedad, (3) el árbol sintáctico se genera y visualiza, (4) los errores se reportan con línea y columna, (5) el dropdown permite cambiar de método sin re-correr todo, (6) las pantallas secundarias muestran autómatas (SLR sin merge / LALR con merge) y tablas.
- **Aceptación**: cada uno de los seis puntos verificable por inspección visual en la GUI.

---

## Resumen por fase

| Fase | Cantidad | Tickets |
|---|---|---|
| 1 -- Refactor proyecto actual | 5 | 1-5 |
| 2 -- Módulo Grammar (base) | 4 | 6-9 |
| 3 -- Soporte para precedencia | 2 | 10-11 |
| 4 -- Módulo Sets | 1 | 12 |
| 5 -- Módulo LL(1) | 3 | 13-15 |
| 6 -- Módulo SLR(1) | 5 | 16-20 |
| 7 -- Módulo LALR(1) | 4 | 21-24 |
| 8 -- Módulo Runtime | 3 | 25-27 |
| 9 -- Módulo Visualization | 3 | 28-30 |
| 10 -- Recursos de prueba | 1 | 31 |
| 11 -- GUI | 8 | 32-39 |
| 12 -- Integración | 1 | 40 |
| **Total** | **40** | |

## Sugerencia de paralelización inicial

Con Fase 1 y Fase 2 ya completas y Ticket 12 (Sets) listo, los próximos frentes paralelos son:

- **Ticket 10** (Extender Grammar para precedencia) -- desbloquea las demás fases que dependen de la gramática reescrita.
- **Ticket 25** (Modelos Runtime) y **Ticket 26** (TokenStream) -- no dependen de la precedencia, se pueden arrancar en paralelo.
- **Ticket 32** (Setup Compose) y **Ticket 34** (Componentes básicos GUI) -- arrancables en paralelo, no dependen de la precedencia.
- **Ticket 31** (Recursos de prueba) -- arrancable en cuanto Ticket 10 esté listo (necesita la sintaxis nueva del `.yalp`).

Una vez Ticket 11 (PrecedenceRewriter) cierra, las Fases 5 (LL1), 6 (SLR1), 7 (LALR1) son completamente independientes y paralelizables.
