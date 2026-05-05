# Roadmap del Proyecto 2 — Tickets de desarrollo

Este documento enlista los tickets en orden secuencial para construir el Proyecto 2 sobre el código existente del Proyecto 1. Para el diseño detallado de cada elemento (data classes, sealed interfaces, firmas de funciones, algoritmos) consultar el documento de plan: [PROJECT_2_PLAN.md](./PROJECT_2_PLAN.md).

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

## Fase 1 — Refactor del proyecto actual

Esta fase prepara el código existente del Proyecto 1 para que el parser pueda consumirlo limpiamente. Es la base de todo lo demás. Hay que terminarla antes de arrancar Fase 2.

### Ticket 1 — Crear `LexemeLocation`

- **Estado**: completado
- **Depende de**: ninguno
- **Archivos**: `frontend/models/LexemeLocation.kt` (nuevo)
- **Descripción**: crear la data class `LexemeLocation(line: Int, position: Int)` en el paquete `org.compiler.frontend.models`. Es el modelo compartido que ubica un lexema dentro del código fuente — lo usan los tokens, los errores del lexer/parser y las hojas del árbol sintáctico. `line` y `position` son ambos 1-based; `position` es la posición horizontal dentro de la línea (equivale a la columna).
- **Aceptación**: el archivo compila y se puede instanciar.
- **Plan**: §2.3

### Ticket 2 — Mover y rediseñar `Token`

- **Estado**: completado
- **Depende de**: Ticket 1
- **Archivos**:
  - `frontend/models/Token.kt` (nuevo, reemplaza al anterior)
  - `frontend/lexicalAnalyzer/scanner/Scanner.kt` (actualizar imports y construcción del Token)
  - `frontend/lexicalAnalyzer/scanner/models/TokenEntrys.kt` (actualizar imports)
  - `LexerApp.kt` (actualizar referencias a campos del Token)
  - `frontend/lexicalAnalyzer/scanner/models/Token.kt` (eliminar el archivo viejo)
- **Descripción**: rediseñar `Token` con campos `category: String`, `lexeme: String`, `location: LexemeLocation`. Quitar los campos viejos `attribute` y `value`. Mover el archivo a `frontend/models/`. La `location` se pasa con un placeholder `LexemeLocation(0, 0)` — el Ticket 3 implementa el tracking real.
- **Aceptación**: el proyecto compila después del rediseño. Los tres archivos consumidores apuntan al nuevo paquete.
- **Plan**: §2.1, §2.2

### Ticket 3 — Tracking de posición en `Scanner`

- **Estado**: completado
- **Depende de**: Ticket 2
- **Archivos**:
  - `frontend/lexicalAnalyzer/scanner/Scanner.kt` (helper `advanceLineAndPosition`, contador `currentPosition`, captura de `LexemeLocation` real)
  - `LexerApp.kt` (mover `SymbolTable.clear()` antes del `scan()`)
- **Descripción**: agregar contador de posición horizontal que se reinicia con cada salto de línea. Pasar `LexemeLocation(currentLine, currentPosition)` al construir cada `Token`. La posición debe corresponder al inicio del lexema, no al final. Eliminar las llamadas a `SymbolTable.addOrGet` y `SymbolTable.clear` del scanner — el `Token` ahora siempre carga el lexema directo y la responsabilidad sobre la tabla de símbolos sale del scanner. El helper `advanceLineAndPosition(line, position, consumed)` recorre la string consumida actualizando ambos contadores y se usa en las tres ramas (panic mode, whitespace/comment, token normal).
- **Aceptación**: correr el lexer sobre `input.java` produce tokens con `location` correctamente populada. La tabla de símbolos ya no se toca desde el scanner.
- **Plan**: §2.4

### Ticket 4 — Lookup de `SymbolTable` al escribir output en `LexerApp`

- **Estado**: completado
- **Depende de**: Ticket 3
- **Archivos**: `LexerApp.kt`
- **Descripción**: mover la lógica de resolución de índice de tabla de símbolos al momento de escribir `tokens.txt`. Para cada token, si la categoría es `KEYWORD`, escribir `<KEYWORD, lexema>`; si no, hacer `SymbolTable.addOrGet(lexeme)` y escribir `<categoría, índice>`. También se revierte el formato temporal con `line:position` que se usó en Ticket 3 para verificación.
- **Aceptación**: el archivo `tokens.txt` generado por `LexerApp` tiene el mismo formato que antes del refactor.
- **Plan**: §2.5

### Ticket 5 — API pública del lexer (`Lexer.kt`)

- **Estado**: completado
- **Depende de**: Tickets 2, 3, 4
- **Archivos**:
  - `frontend/lexicalAnalyzer/lexer/Lexer.kt` (nuevo)
  - `frontend/lexicalAnalyzer/manageGrammar/utils/YalexReader.kt` (refactor mínimo: expone `parse(content)` y `read(filePath)` lo invoca)
  - `frontend/lexicalAnalyzer/manageGrammar/models/CategoryAutomataIndex.kt` (agregar `clear()`)
  - `app/src/test/kotlin/org/compiler/LexerTest.kt` (nuevo, valida que `tokenize` produce el mismo output que `LexerApp`)
- **Descripción**: crear `object Lexer { fun tokenize(yalexContent: String, source: String): LexerResult }` que arme los DFAs en memoria a partir del contenido del `.yalex` (sin pasar por YAMLs) y scanee el `source`. Crear la data class `LexerResult(tokens, errors, automata)`. Internamente reusa `YalexReader.parse`, `normalizeRegex`, `infixToPostfix`, `buildSyntaxTree`, `computeFollowPos`, `buildTransitionTable`, `minimizeDFA`, `buildMinimizedDFA`, y `scan`.
- **Aceptación**: invocar `Lexer.tokenize(File("java_lang.yal").readText(), File("input.java").readText())` retorna los mismos tokens que `LexerApp` escribe a disco. Verificado por test JUnit que rearma el formato de `tokens.txt` y lo compara byte por byte contra el archivo de referencia.
- **Plan**: §2.6

---

## Fase 2 — Módulo Grammar

Modelos de la gramática y lectura del archivo `.yalp`. Se puede arrancar en cuanto Fase 1 termine; los tickets 6–8 son independientes entre sí (paralelizables).

### Ticket 6 — Modelos del módulo Grammar

- **Estado**: completado
- **Depende de**: ninguno (puede arrancar en paralelo con Fase 1)
- **Archivos**:
  - `frontend/syntaxAnalyzer/grammar/models/Symbol.kt`
  - `frontend/syntaxAnalyzer/grammar/models/Production.kt`
  - `frontend/syntaxAnalyzer/grammar/models/Grammar.kt`
- **Descripción**: crear `Symbol` como sealed interface con `Terminal`, `NonTerminal`, `Epsilon` (data object), `EndMarker` (data object). Crear `Production(id, head, body)` y `Grammar(terminals, nonTerminals, productions, productionsByHead, startSymbol, ignoredTokens)`.
- **Aceptación**: se puede construir manualmente una `Grammar` para `E → E + T | T` y consultarla.
- **Plan**: §3.1

### Ticket 7 — `YalpReader`

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**: `frontend/syntaxAnalyzer/grammar/YalpReader.kt`
- **Descripción**: implementar `object YalpReader { fun read(filePath: String): Grammar }`. Eliminar comentarios `/* */`, separar secciones por `%%`, parsear `%token` y `IGNORE` en la sección de tokens, parsear producciones con sintaxis `nombre: cuerpo1 | cuerpo2 | ... ;`. Convención: minúsculas son no terminales, MAYÚSCULAS son terminales.
- **Aceptación**: leer el `parser.yalp` de prueba (Ticket 31) produce una `Grammar` con todos los terminales, no terminales y producciones correctos.
- **Plan**: §3.2

### Ticket 8 — `GrammarValidator`

- **Estado**: pendiente
- **Depende de**: Ticket 7
- **Archivos**: `frontend/syntaxAnalyzer/grammar/GrammarValidator.kt`
- **Descripción**: implementar `validate(grammar, lexerCategories): List<ValidationError>`. Validar que cada `%token` exista en las categorías del lexer, que ningún no terminal usado quede sin declarar, y reportar advertencias por no terminales inalcanzables, no usados, o producciones duplicadas.
- **Aceptación**: validar la `parser.yalp` de prueba retorna lista vacía. Inyectar un token inexistente o una referencia rota produce un error apropiado.
- **Plan**: §3.3

### Ticket 9 — `GrammarRewriter` (eliminación de recursión por la izquierda)

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**: `frontend/syntaxAnalyzer/grammar/GrammarRewriter.kt`
- **Descripción**: implementar `eliminateLeftRecursion(grammar): Grammar` siguiendo Dragon Book §4.3.3. Generar no terminales auxiliares con sufijo `_prime`.
- **Aceptación**: aplicar la función a `E → E + T | T; T → T * F | F; F → (E) | id` produce la versión sin recursión por la izquierda equivalente.
- **Plan**: §3.4, §15.1

---

## Fase 3 — Módulo Sets

Migración del trabajo independiente de FIRST/FOLLOW con ajustes para alinearse con el resto del proyecto.

### Ticket 10 — Migrar módulo Sets

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/sets/models/FirstSets.kt`
  - `frontend/syntaxAnalyzer/sets/models/FollowSets.kt`
  - `frontend/syntaxAnalyzer/sets/FirstSetComputer.kt`
  - `frontend/syntaxAnalyzer/sets/FollowSetComputer.kt`
- **Descripción**: migrar `FirstResults` → `FirstSets`, `FollowResults` → `FollowSets`, `computeFirst` → `FirstSetComputer.compute`, `computeFollow` → `FollowSetComputer.compute`. Cambiar `Symbol.Terminal("$")` por `Symbol.EndMarker`. Promover `firstOfSequence` a función pública dentro de `FirstSetComputer`.
- **Aceptación**: los conjuntos FIRST y FOLLOW computados sobre `E → E + T | T; T → T * F | F; F → (E) | id` coinciden con los del Dragon Book.
- **Plan**: §4.1, §4.2, §4.3, §15.1

---

## Fase 4 — Módulo LL(1)

Construcción de tabla LL(1) y driver predictivo. Migra parte del trabajo independiente.

### Ticket 11 — Modelos LL(1)

- **Estado**: pendiente
- **Depende de**: Ticket 10
- **Archivos**:
  - `frontend/syntaxAnalyzer/ll1/models/LL1Cell.kt`
  - `frontend/syntaxAnalyzer/ll1/models/LL1Table.kt`
- **Descripción**: crear `LL1Conflict(nonTerminal, terminal, productions)` y `LL1Table(cells)` con métodos `isLL1()`, `conflicts()`, `lookup()`.
- **Aceptación**: se puede construir manualmente una `LL1Table` y consultar `lookup(A, a)`.
- **Plan**: §5.1

### Ticket 12 — `LL1TableBuilder`

- **Estado**: pendiente
- **Depende de**: Ticket 11
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1TableBuilder.kt`
- **Descripción**: migrar la lógica de `buildSyntaxTable` del trabajo independiente. Implementar Dragon Book §4.4.3 (Algoritmo 4.31).
- **Aceptación**: la tabla generada para una gramática LL(1) clásica (ej. la de expresiones aritméticas factorizada) coincide con la del libro.
- **Plan**: §5.2, §15.8

### Ticket 13 — `LL1Driver`

- **Estado**: pendiente
- **Depende de**: Tickets 12, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1Driver.kt`
- **Descripción**: implementar `class LL1Driver(grammar, table) { fun parse(tokens): ParseResult }`. Stack inicia con `[EndMarker, startSymbol]`. Aplicar Dragon Book §4.4.4 (Algoritmo 4.34). Construir el `ParseTree` mientras parsea.
- **Aceptación**: parsear `id + id * id` con la gramática de expresiones LL(1) retorna `Accepted` con el árbol correcto.
- **Plan**: §5.3, §15.9

---

## Fase 5 — Módulo LR(0)

Autómata LR(0). Es la base que SLR(1) consume.

### Ticket 14 — Modelos LR(0)

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/lr0/models/LR0Item.kt`
  - `frontend/syntaxAnalyzer/lr0/models/LR0ItemSet.kt`
  - `frontend/syntaxAnalyzer/lr0/models/LR0Automaton.kt`
- **Descripción**: crear `LR0Item(production, dotPosition)` con propiedades `isComplete`, `symbolAfterDot`, `advance()`. Crear `LR0ItemSet(id, items)` y `LR0Automaton(states, transitions, initialState, augmentedGrammar)`.
- **Aceptación**: se puede construir manualmente un `LR0Item` y avanzar el punto.
- **Plan**: §6.1

### Ticket 15 — `LR0AutomatonBuilder`

- **Estado**: pendiente
- **Depende de**: Ticket 14
- **Archivos**: `frontend/syntaxAnalyzer/lr0/LR0AutomatonBuilder.kt`
- **Descripción**: implementar `build(grammar)`, `closure(items, grammar)`, `goto(items, symbol, grammar)`. Aumentar la gramática con `S' → S` internamente. Aplicar Dragon Book §4.6.2 (Algoritmo 4.32).
- **Aceptación**: aplicado a la gramática de expresiones del libro produce los mismos `I0..I11` que aparecen en §4.6.2 figura 4.31.
- **Plan**: §6.2, §15.2

---

## Fase 6 — Módulo SLR(1)

Tabla SLR(1) y driver shift-reduce. Toma el autómata de Fase 5 más los conjuntos de Fase 3.

### Ticket 16 — `Action` sealed interface

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**: `frontend/syntaxAnalyzer/slr1/models/Action.kt`
- **Descripción**: crear sealed interface con `Shift(nextState)`, `Reduce(production)`, `Accept` (data object). Esta es la representación que comparten SLR y LALR.
- **Aceptación**: se pueden instanciar las tres variantes y compararlas con `is`.
- **Plan**: §7.1

### Ticket 17 — Modelos SLR(1)

- **Estado**: pendiente
- **Depende de**: Ticket 16
- **Archivos**: `frontend/syntaxAnalyzer/slr1/models/SLR1Table.kt`
- **Descripción**: crear `SLR1Table(action, goto, numStates)` con métodos `isSLR1()` y `conflicts()`. Crear `SLR1Conflict` y enum `ConflictType { SHIFT_REDUCE, REDUCE_REDUCE }`.
- **Aceptación**: se puede consultar `action[(0, terminal)]` y obtener una `Action`.
- **Plan**: §7.1

### Ticket 18 — `SLR1TableBuilder`

- **Estado**: pendiente
- **Depende de**: Tickets 15, 17, 10
- **Archivos**: `frontend/syntaxAnalyzer/slr1/SLR1TableBuilder.kt`
- **Descripción**: implementar `build(grammar, automaton, followSets)`. Aplicar Dragon Book §4.6.4 (Algoritmo 4.46). Detectar conflictos shift-reduce y reduce-reduce.
- **Aceptación**: la tabla generada para la gramática de expresiones del libro coincide con la figura 4.37.
- **Plan**: §7.2, §15.5

### Ticket 19 — `SLR1Driver`

- **Estado**: pendiente
- **Depende de**: Tickets 18, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/slr1/SLR1Driver.kt`
- **Descripción**: implementar el parser shift-reduce siguiendo Dragon Book §4.5.3 (Algoritmo 4.44). Mantener stack de estados + stack paralelo de subárboles. Construir el `ParseTree` en cada Reduce combinando subárboles popped.
- **Aceptación**: parsear `id + id * id` con la gramática de expresiones retorna `Accepted` con el árbol correcto. Parsear `id + +` retorna `Rejected` con el error en la posición correcta.
- **Plan**: §7.3, §15.7

---

## Fase 7 — Módulo LR(1)

Autómata LR(1) con items con lookahead. Es base para LALR.

### Ticket 20 — Modelos LR(1)

- **Estado**: pendiente
- **Depende de**: Ticket 14
- **Archivos**:
  - `frontend/syntaxAnalyzer/lr1/models/LR1Item.kt`
  - `frontend/syntaxAnalyzer/lr1/models/LR1ItemSet.kt`
  - `frontend/syntaxAnalyzer/lr1/models/LR1Automaton.kt`
- **Descripción**: crear `LR1Item(production, dotPosition, lookahead)` con propiedad `core: LR0Item`. Crear `LR1ItemSet(id, items)` con propiedad `core: Set<LR0Item>`. Crear `LR1Automaton(states, transitions, initialState, augmentedGrammar)`.
- **Aceptación**: se pueden construir items LR(1) con distintos lookaheads y comparar sus cores.
- **Plan**: §8.1

### Ticket 21 — `LR1AutomatonBuilder`

- **Estado**: pendiente
- **Depende de**: Tickets 20, 10
- **Archivos**: `frontend/syntaxAnalyzer/lr1/LR1AutomatonBuilder.kt`
- **Descripción**: implementar `build(grammar, firstSets)`, `closure(items, grammar, firstSets)`, `goto(items, symbol, grammar, firstSets)`. La diferencia clave con LR(0) es que `closure` propaga lookaheads usando `FIRST(βa)`. Aplicar Dragon Book §4.7.2 (Algoritmo 4.53).
- **Aceptación**: el autómata generado para la gramática del libro tiene los items LR(1) esperados con sus lookaheads correctos.
- **Plan**: §8.2, §15.3

---

## Fase 8 — Módulo LALR(1)

Merge de cores del LR(1), tabla y driver.

### Ticket 22 — `LALR1AutomatonBuilder` (merge de cores)

- **Estado**: pendiente
- **Depende de**: Ticket 21
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1AutomatonBuilder.kt`
- **Descripción**: implementar `mergeFromLR1(automaton): LR1Automaton`. Identificar todos los pares de estados con el mismo core, fusionarlos uniendo sus lookaheads, y reescribir las transiciones. Aplicar Dragon Book §4.7.4 (Algoritmo 4.59).
- **Aceptación**: aplicado al autómata LR(1) de la gramática del libro produce un autómata con menos estados (igual cantidad que LR(0)).
- **Plan**: §9.2, §15.4

### Ticket 23 — Modelo LALR(1)

- **Estado**: pendiente
- **Depende de**: Ticket 16
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/models/LALR1Table.kt`
- **Descripción**: crear `LALR1Table(action, goto, numStates)` con métodos `isLALR1()` y `conflicts()`. Crear `LALR1Conflict` (puede reusar `ConflictType` del módulo SLR).
- **Aceptación**: estructura paralela a `SLR1Table` lista para llenarse.
- **Plan**: §9.1

### Ticket 24 — `LALR1TableBuilder`

- **Estado**: pendiente
- **Depende de**: Tickets 22, 23
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1TableBuilder.kt`
- **Descripción**: implementar `build(grammar, mergedAutomaton)`. Recorrer estados del autómata mergeado: para cada item completo `[A → α•, b]` asignar `Reduce(A → α)` en `ACTION[i, b]`. La diferencia clave con SLR es que los lookaheads vienen del item, no de FOLLOW. Aplicar Dragon Book §4.7.4 (Algoritmo 4.56).
- **Aceptación**: la tabla generada resuelve conflictos en gramáticas donde SLR fallaría.
- **Plan**: §9.3, §15.6

### Ticket 25 — `LALR1Driver`

- **Estado**: pendiente
- **Depende de**: Tickets 24, 26, 27
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1Driver.kt`
- **Descripción**: implementar el parser shift-reduce, idéntico estructuralmente a `SLR1Driver` pero recibiendo `LALR1Table`. Construir el `ParseTree` igual que en SLR.
- **Aceptación**: parsear las cadenas de prueba con la tabla LALR retorna los mismos resultados que SLR cuando ambos métodos son aplicables.
- **Plan**: §9.4, §15.7

---

## Fase 9 — Módulo Runtime

Modelos compartidos por los tres drivers. Algunos tickets de esta fase deben terminar antes de los drivers (LL1Driver, SLR1Driver, LALR1Driver).

### Ticket 26 — Modelos del árbol y resultado

- **Estado**: pendiente
- **Depende de**: Ticket 6
- **Archivos**:
  - `frontend/syntaxAnalyzer/runtime/models/ParseTree.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseResult.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseStep.kt`
  - `frontend/syntaxAnalyzer/runtime/models/ParseError.kt`
- **Descripción**: crear `ParseTree` como sealed interface con `Leaf(symbol, token)`, `Internal(symbol, production, children)`, `EpsilonLeaf`. Crear `ParseResult` como sealed interface con `Accepted(trace, parseTree)` y `Rejected(trace, error, partialTree)`. Crear `ParseStep(stack, remainingInput, action)` y `ParseError(message, location, foundToken, expectedTokens)`.
- **Aceptación**: se puede construir manualmente un `ParseTree` y serializarlo a string indentado.
- **Plan**: §10.1

### Ticket 27 — `TokenStream`

- **Estado**: pendiente
- **Depende de**: Ticket 2
- **Archivos**: `frontend/syntaxAnalyzer/runtime/TokenStream.kt`
- **Descripción**: crear `class TokenStream(tokens, ignored)` con `peek()`, `consume()`, `hasNext()`, `position()`. Filtrar tokens cuya categoría está en `ignored` al hacer peek/consume.
- **Aceptación**: dado `[KEYWORD, WS, ID, WS, OPERATOR]` con `ignored = {WS}`, `peek` y `consume` saltan los whitespace transparentemente.
- **Plan**: §10.2

### Ticket 28 — `Pipeline` orquestador

- **Estado**: pendiente
- **Depende de**: Tickets 5, 7, 8, 10, 12, 15, 18, 21, 22, 24, 13, 19, 25, 26, 27
- **Archivos**: `frontend/syntaxAnalyzer/runtime/Pipeline.kt`
- **Descripción**: crear `object Pipeline { fun runFull(yalexContent, yalpContent, inputContent, method): PipelineResult }`. Crear enum `ParserMethod { LL1, SLR1, LALR1 }` y data class `PipelineResult` con todos los artefactos (tokens, grammar, sets, autómatas, tres tablas, parseResult).
- **Aceptación**: invocar `Pipeline.runFull` con archivos de prueba retorna un `PipelineResult` con todos los campos populados.
- **Plan**: §10.3

---

## Fase 10 — Módulo Visualization

Utilidades de presentación. Se pueden hacer en paralelo.

### Ticket 29 — `DotExporter`

- **Estado**: pendiente
- **Depende de**: Tickets 14, 20
- **Archivos**: `frontend/syntaxAnalyzer/visualization/DotExporter.kt`
- **Descripción**: implementar `lr0ToDot(automaton)`, `lr1ToDot(automaton)`, `renderToImage(dot, outputPath)`. Las funciones de DOT generan texto Graphviz; `renderToImage` invoca el comando `dot` vía `ProcessBuilder` y retorna boolean.
- **Aceptación**: el archivo PNG generado se abre y muestra el autómata correctamente. Si Graphviz no está instalado, retorna `false` sin lanzar excepción.
- **Plan**: §11.1

### Ticket 30 — `ParseTreeExporter`

- **Estado**: pendiente
- **Depende de**: Ticket 26
- **Archivos**: `frontend/syntaxAnalyzer/visualization/ParseTreeExporter.kt`
- **Descripción**: implementar `toDot(tree)` y `toIndentedText(tree)`. La versión DOT genera un grafo dirigido con orden de izquierda a derecha. La versión texto usa caracteres de árbol ASCII (`├─`, `└─`).
- **Aceptación**: dado un `ParseTree` para `id + id`, la versión texto se imprime correctamente y la versión DOT se renderiza a PNG.
- **Plan**: §11.2

### Ticket 31 — `TableFormatter`

- **Estado**: pendiente
- **Depende de**: Tickets 11, 17, 23, 10
- **Archivos**: `frontend/syntaxAnalyzer/visualization/TableFormatter.kt`
- **Descripción**: implementar `formatLL1Table`, `formatSLR1Action`, `formatSLR1Goto`, `formatLALR1Action`, `formatLALR1Goto`, `formatFirstSets`, `formatFollowSets`. Cada una retorna string multilinea alineada en columnas. Acciones SLR/LALR se imprimen como `s5`, `r3`, `acc`.
- **Aceptación**: las strings se ven alineadas en un componente monoespaciado.
- **Plan**: §11.3

---

## Fase 11 — Recursos de prueba

### Ticket 32 — Archivos de prueba

- **Estado**: pendiente
- **Depende de**: ninguno (puede arrancar en cualquier momento)
- **Archivos**:
  - `app/src/main/resources/parser_test.yal` (lexer dedicado para los tests del parser)
  - `app/src/main/resources/parser.yalp` (gramática de prueba)
  - `app/src/main/resources/cadenas.txt` (cadenas a analizar)
- **Descripción**: crear los tres archivos según la especificación de la sección 14 del plan. La gramática debe cubrir clases, funciones, recursión y listas. Las cadenas deben incluir casos válidos y al menos uno inválido.
- **Aceptación**: los tres archivos existen y son leíbles. La gramática parsea con `YalpReader` sin errores.
- **Plan**: §14.1, §14.3, §14.4

---

## Fase 12 — CLI

### Ticket 33 — `YaparApp` punto de entrada CLI

- **Estado**: pendiente
- **Depende de**: Ticket 28
- **Archivos**: `YaparApp.kt`
- **Descripción**: implementar `fun main(args)` que procese argumentos `parser.yalp -l lexer.yal -i cadenas.txt -o theparser -m <método>`. Invocar `Pipeline.runFull` con los contenidos leídos y reportar resultados a stdout.
- **Aceptación**: ejecutar el CLI con los archivos de prueba produce el reporte esperado en consola.
- **Plan**: §13.1

### Ticket 34 — `TheParserExporter`

- **Estado**: pendiente
- **Depende de**: Tickets 31, 33
- **Archivos**: `frontend/syntaxAnalyzer/visualization/TheParserExporter.kt`
- **Descripción**: implementar `export(pipelineResult, outputPath)`. Escribir un archivo de texto plano legible con FIRST/FOLLOW, las tres tablas, y los autómatas.
- **Aceptación**: el archivo generado es legible y contiene toda la información del análisis.
- **Plan**: §13.2

### Ticket 35 — Tareas de Gradle

- **Estado**: pendiente
- **Depende de**: Tickets 33, 36 (opcional, ver Fase 13)
- **Archivos**: `app/build.gradle.kts`
- **Descripción**: registrar tasks `runYapar` y `runGui` con `JavaExec`, apuntando a los `mainClass` correspondientes. Mantener intactas las tasks de `PreprocessorApp` y `LexerApp`.
- **Aceptación**: `./gradlew runYapar` y `./gradlew runGui` ejecutan correctamente.
- **Plan**: §13.3

---

## Fase 13 — GUI (Compose Desktop)

### Ticket 36 — Setup de Compose Desktop

- **Estado**: pendiente
- **Depende de**: ninguno (puede arrancar en cualquier momento)
- **Archivos**: `app/build.gradle.kts`
- **Descripción**: agregar el plugin `org.jetbrains.compose` al `build.gradle.kts` y declarar las dependencias necesarias (`compose.desktop.currentOs`, `compose.material`, `compose.foundation`).
- **Aceptación**: un Hello World mínimo en Compose Desktop compila y ejecuta.
- **Plan**: §12.1

### Ticket 37 — `AppState`

- **Estado**: pendiente
- **Depende de**: Tickets 28, 36
- **Archivos**: `gui/state/AppState.kt`
- **Descripción**: crear `class AppState` con todos los campos `mutableStateOf`. Implementar `onPlay()` que invoca `Pipeline.runFull` y guarda el resultado. Implementar `changeMethod(newMethod)` que solo re-ejecuta el driver del nuevo método sobre los tokens ya construidos.
- **Aceptación**: se puede instanciar `AppState`, llamar `onPlay()`, y el campo `pipelineResult` queda populado.
- **Plan**: §12.2

### Ticket 38 — Componentes básicos de GUI

- **Estado**: pendiente
- **Depende de**: Ticket 36
- **Archivos**:
  - `gui/components/CodeEditor.kt`
  - `gui/components/MethodDropdown.kt`
  - `gui/components/PlayButton.kt`
- **Descripción**: implementar los tres composables. `CodeEditor` envuelve un `BasicTextField` con fuente monoespaciada y números de línea. `MethodDropdown` es un dropdown con las tres opciones de `ParserMethod`. `PlayButton` es un botón con icono que se deshabilita cuando `isRunning`.
- **Aceptación**: cada componente se puede previsualizar de forma aislada y reacciona a interacciones.
- **Plan**: §12.5

### Ticket 39 — Componentes de resultados

- **Estado**: pendiente
- **Depende de**: Tickets 36, 26, 30
- **Archivos**:
  - `gui/components/TokenList.kt`
  - `gui/components/ParseTreeView.kt`
  - `gui/components/ErrorList.kt`
- **Descripción**: implementar los tres composables. `TokenList` muestra una tabla de tokens con categoría, lexema, posición. `ParseTreeView` muestra la imagen del árbol o el texto indentado. `ErrorList` muestra errores con icono, ubicación, y mensaje.
- **Aceptación**: cada componente renderiza correctamente datos de prueba.
- **Plan**: §12.5

### Ticket 40 — `WorkspaceScreen`

- **Estado**: pendiente
- **Depende de**: Tickets 37, 38, 39
- **Archivos**: `gui/screens/WorkspaceScreen.kt`
- **Descripción**: layout principal con toolbar superior (dropdown + play), centro con tabs entre los tres editores, y panel derecho con sub-pestañas Tokens / Parse Tree / Errores.
- **Aceptación**: el usuario puede editar la cadena, escoger método, presionar Play, y ver los tres paneles de resultados poblarse.
- **Plan**: §12.3

### Ticket 41 — Pantallas secundarias

- **Estado**: pendiente
- **Depende de**: Tickets 29, 31, 37
- **Archivos**:
  - `gui/screens/AutomatonScreen.kt`
  - `gui/screens/TablesScreen.kt`
- **Descripción**: `AutomatonScreen` muestra la imagen del LR(0) o LR(1) con un toggle entre ambos. `TablesScreen` tiene cuatro sub-pestañas (FIRST/FOLLOW, LL(1), SLR(1), LALR(1)) con el texto formateado por `TableFormatter`.
- **Aceptación**: las pantallas se abren desde el menú View y muestran contenido cuando hay un `pipelineResult`.
- **Plan**: §12.4

### Ticket 42 — Menús File y View

- **Estado**: pendiente
- **Depende de**: Ticket 37
- **Archivos**:
  - `gui/components/FileMenu.kt`
  - `gui/components/ViewMenu.kt`
- **Descripción**: `FileMenu` con Open .yalex / Open .yalp / Open Input / Save All / Save As. `ViewMenu` con toggles para autómata LR(0), LR(1), tablas, y volver al workspace.
- **Aceptación**: los menús abren diálogos de archivo y actualizan el `AppState` correctamente.
- **Plan**: §12.5

### Ticket 43 — Punto de entrada GUI

- **Estado**: pendiente
- **Depende de**: Tickets 40, 41, 42
- **Archivos**:
  - `gui/App.kt`
  - `GuiApp.kt`
- **Descripción**: `App.kt` define la ventana Compose Desktop con menú superior y `WorkspaceScreen` como contenido principal. `GuiApp.kt` es el `main()` runnable que invoca `App`.
- **Aceptación**: `./gradlew runGui` abre la ventana del IDE funcional.
- **Plan**: §12.1

---

## Fase 14 — Integración y verificación

### Ticket 44 — Verificación end-to-end

- **Estado**: pendiente
- **Depende de**: todos los anteriores
- **Archivos**: ninguno (solo verificación manual)
- **Descripción**: verificar que el flujo completo funcione con los tres métodos sobre las cadenas de prueba. Verificar que: (1) los tokens se identifican correctamente, (2) el árbol sintáctico se genera y visualiza, (3) los errores se reportan con línea y columna, (4) el dropdown permite cambiar de método sin re-correr todo, (5) las pantallas secundarias muestran autómatas y tablas.
- **Aceptación**: cada uno de los cinco puntos verificable por inspección visual en la GUI.

---

## Resumen por fase

| Fase | Cantidad | Tickets |
|---|---|---|
| 1 — Refactor proyecto actual | 5 | 1–5 |
| 2 — Módulo Grammar | 4 | 6–9 |
| 3 — Módulo Sets | 1 | 10 |
| 4 — Módulo LL(1) | 3 | 11–13 |
| 5 — Módulo LR(0) | 2 | 14–15 |
| 6 — Módulo SLR(1) | 4 | 16–19 |
| 7 — Módulo LR(1) | 2 | 20–21 |
| 8 — Módulo LALR(1) | 4 | 22–25 |
| 9 — Módulo Runtime | 3 | 26–28 |
| 10 — Módulo Visualization | 3 | 29–31 |
| 11 — Recursos de prueba | 1 | 32 |
| 12 — CLI | 3 | 33–35 |
| 13 — GUI | 8 | 36–43 |
| 14 — Integración | 1 | 44 |
| **Total** | **44** | |

## Sugerencia de paralelización inicial

Mientras una persona arranca **Fase 1** (refactor del Token), otra puede arrancar **Fase 2 Tickets 6, 9** (modelos Grammar, GrammarRewriter) y otra **Ticket 32** (recursos de prueba). Eso desbloquea el resto de fases lo más rápido posible.

Una vez Fase 1 termina, Fase 4 (LL1) y Fase 5 (LR0) son completamente independientes, así que se pueden asignar a personas distintas. Lo mismo Fase 7 (LR1) puede arrancar en paralelo con Fase 6 (SLR1) en cuanto Fase 5 esté lista.

La GUI (Fase 13) puede arrancar tan temprano como Ticket 36 (setup de Compose), y sus componentes (Tickets 38, 39) se pueden trabajar mientras los módulos del parser todavía no están terminados — solo hace falta tener los modelos del Runtime (Ticket 26) para empezar a renderizar resultados de prueba.
