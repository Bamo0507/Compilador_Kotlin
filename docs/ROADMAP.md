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

- **Estado**: completado
- **Depende de**: Ticket 12
- **Archivos**:
  - `frontend/syntaxAnalyzer/ll1/models/LL1Cell.kt` — define `LL1Conflict(nonTerminal: Symbol.NonTerminal, terminal: Symbol, productions: List<Production>)`. Estructura paralela a `SLR1Conflict` de `slr1/models/SLR1Table.kt`. El nombre del archivo es histórico; agrupa los tipos auxiliares de la tabla LL(1).
  - `frontend/syntaxAnalyzer/ll1/models/LL1Table.kt` — define la data class `LL1Table(cells: Map<Pair<Symbol.NonTerminal, Symbol>, Production>, conflicts: List<LL1Conflict> = emptyList())`. Propiedad `isLL1: Boolean get() = conflicts.isEmpty()` y método `lookup(nonTerminal: Symbol.NonTerminal, lookahead: Symbol): Production? = cells[nonTerminal to lookahead]`.
- **Descripción**: estructura paralela a `SLR1Table` pero adaptada al modelo predictivo de LL(1). Ver `slr1/models/SLR1Table.kt` como template: misma idea de "map principal" + lista de conflicts separada, con la propiedad `isLL1`/`isSLR1` derivada. La key del map es `Pair<NonTerminal, Symbol>` porque el lookahead puede ser un Terminal o `Symbol.EndMarker`. El valor es la `Production` a aplicar.
- **Aceptación**: se puede construir manualmente una `LL1Table` con una celda, llamar `lookup(A, a)` y obtener la `Production` o `null`. `isLL1` retorna `true` cuando `conflicts` está vacío, `false` cuando no.
- **Plan**: §5.1

### Ticket 14 -- `LL1TableBuilder`

- **Estado**: completado
- **Depende de**: Tickets 11, 13
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1TableBuilder.kt`
- **Descripción**: `object LL1TableBuilder` con `fun build(grammar: Grammar, firstSets: FirstSets, followSets: FollowSets): LL1Table`.
- **Algoritmo** (Dragon Book §4.4.3, Algoritmo 4.31):
  - Por cada producción `A -> alpha` en la gramática:
    - Computar `FIRST(alpha)` usando `FirstSetComputer.firstOfSequence(production.body, firstSets)`. **Importante**: usar `firstOfSequence` y no `firstSets.firstOf(...)` porque `alpha` es una secuencia, no un único símbolo.
    - Para cada terminal `a` en `FIRST(alpha)` (excluyendo `Epsilon`): asignar `production` a `M[A, a]`.
    - Si `Epsilon` está en `FIRST(alpha)`: para cada `b` en `FOLLOW(A)` (incluyendo `EndMarker` si está): asignar `production` a `M[A, b]`.
  - Si una celda `M[A, X]` ya tenía otra producción asignada cuando se intenta escribir una nueva: registrar el conflicto en `LL1Conflict` y aplicar la **política de resolución**: gana la producción con menor `id` (misma convención que `SLR1TableBuilder` para reduce-reduce, ver el comentario explícito en ese archivo).
- **Entrada esperada**: la gramática que llega a `build` ya viene reescrita por `PrecedenceRewriter` (Ticket 11) y `LeftRecursionRewriter` (Ticket 9). El `Pipeline` (Ticket 27) encadena ambos antes de invocar al builder cuando el método elegido es LL(1).
- **Aceptación**: la tabla generada para la gramática de expresiones LL(1) clásica del Dragon Book (E -> T E', E' -> + T E' | epsilon, T -> F T', T' -> * F T' | epsilon, F -> (E) | id) coincide con la figura 4.17 del libro.
- **Plan**: §5.2, §15.8

### Ticket 15 -- `LL1Parser`

- **Estado**: completado
- **Depende de**: Tickets 14, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/ll1/LL1Parser.kt`
- **Descripción**: `object LL1Parser` (mantener `object` para consistencia con `SLR1Parser` y `LALR1Parser`). Firma: `fun parse(entries: List<TokenEntry>, ignoredCategories: Set<String>, table: LL1Table): ParseResult`.
- **Nota sobre `TokenEntry`**: el parser recibe `List<TokenEntry>` (no `List<Token>`). `TokenEntry` es el wrapper `(token, location)` definido en `frontend/models/TokenEntry.kt`. Al construir `LeafNode` usar el `TokenEntry` completo; al construir `ParseError` extraer `entry.location` y `entry.token`.
- **Algoritmo** (Dragon Book §4.4.4, Algoritmo 4.34, extendido con recovery del §4.4.5):
  - Usar `TokenStream(entries, ignoredCategories)` del módulo runtime para iterar el input (saltea categorías ignoradas como WHITESPACE).
  - Inicializar `symbolStack: MutableList<Symbol> = mutableListOf(Symbol.EndMarker, grammar.startSymbol)`. EndMarker queda en el fondo (`[0]`), startSymbol arriba (`.last()`).
  - Mantener `errors: MutableList<ParseError> = mutableListOf()` para acumular errores durante el parseo.
  - En cada iteración:
    - `top = symbolStack.last()`
    - `lookahead = stream.peek()?.let { Symbol.Terminal(it.token.category) } ?: Symbol.EndMarker`
    - **Caso ACCEPT**: si `top == Symbol.EndMarker` y `lookahead == Symbol.EndMarker`: retornar `ParseResult.Accepted(trace, parseTree, errors)`.
    - **Caso MATCH exitoso**: si `top is Symbol.Terminal` y `top == lookahead`: pop del symbolStack, `stream.consume()`. Registrar `Action.Match(top)` en la traza. Reemplazar el placeholder correspondiente en el árbol con `LeafNode(top, consumedEntry)`.
    - **Caso MATCH con error**: si `top is Symbol.Terminal` pero `top != lookahead`: agregar `ParseError` a la lista (formato descrito abajo). **Recuperación**: pop el terminal del stack (se asume que fue una inserción accidental del usuario) y continuar — NO se consume el lookahead. Equivale a "ignorar el terminal esperado y seguir con la siguiente regla". Registrar el paso en la traza con un `Action` descriptivo del descarte.
    - **Caso EXPAND exitoso**: si `top is Symbol.NonTerminal` y `production = table.lookup(top, lookahead) != null`: pop `top`, push los símbolos de `production.body` en **orden inverso** (para que el primer símbolo quede en el tope). Registrar `Action.Expand(production)` en la traza.
    - **Caso EXPAND con error**: si `top is Symbol.NonTerminal` pero `table.lookup(top, lookahead) == null`: agregar `ParseError` a la lista. **Recuperación con sync set**: descartar tokens del input hasta que el lookahead esté en `FOLLOW(top)` o sea `EndMarker`. Después, pop `top` del symbolStack (se asume `top -> epsilon` para poder seguir). Si en algún punto del descarte se llega a EOF y nada en el stack acepta `EndMarker`, retornar `ParseResult.Rejected(trace, errors, partialTree)`.
- **Construcción del ParseTree top-down**:
  - LL(1) construye el árbol de arriba abajo, distinto a SLR/LALR (bottom-up). Estrategia recomendada:
    - Mantener un mapa `Map<Symbol-instance, InternalNode-mutable-children>` o usar un treeStack paralelo a symbolStack donde cada slot del symbolStack tiene su "espacio reservado" en el árbol.
    - Al inicio, crear `rootInternalNode = InternalNode(startSymbol, null, mutableListOf())`. (La production será conocida cuando se expanda.)
    - Al EXPAND `A -> alpha`: el `InternalNode` correspondiente al `A` arriba del stack gana la producción y sus children: una lista de placeholders por cada símbolo de `alpha` (InternalNode vacío si es NT, "pending leaf" si es Terminal, `EpsilonNode` si es Epsilon).
    - Al MATCH: el "pending leaf" en el árbol se reemplaza por `LeafNode(symbol, consumedToken)`.
  - El detalle exacto depende de la implementación; lo importante es que al ACCEPT, el árbol entero esté formado y `result.parseTree` sea el `rootInternalNode`.
- **Extensión de `Action`** (en `runtime/models/Action.kt`): agregar dos variantes nuevas a la sealed interface existente:
  - `data class Match(val terminal: Symbol.Terminal) : Action`
  - `data class Expand(val production: Production) : Action`
  - `Action.Accept` ya existe (reusar tal cual).
- **`ParseStep`**: para LL(1), el campo `stack: List<Int>` no aplica (no hay estados numerados). Pasar `emptyList()`. El campo `symbols: List<Symbol>` lleva la pila de símbolos. `remainingInput: List<TokenEntry>` lleva los entries no consumidos. `action` se completa con la variante de `Action` correspondiente.
- **Recuperación de errores (panic mode, Dragon Book §4.4.5)** — **requisito de rúbrica**:
  - El parser **NO debe terminar al primer error**. Las dos políticas descritas arriba en "MATCH con error" y "EXPAND con error" implementan el recovery; el parser sigue corriendo hasta agotar el input.
  - **Sync set = `FOLLOW(top)`** para el caso EXPAND. Sale directo del `FollowSetComputer` (Ticket 12). Ejemplo: si la gramática tiene `S -> A B` y estamos en error con `A` arriba del stack, entonces el sync set es `FOLLOW(A) = FIRST(B) - {epsilon}` (más `FOLLOW(S)` si `B` es nullable).
  - **Garantía de progreso**: cada recovery debe avanzar (pop al menos un símbolo del stack O consumir al menos un token). Si el algoritmo se queda atascado sin progreso, hay un bug.
  - **Acumulación**: cada error agregado a `errors` debe traer su `location` (de `entry.location`) y `foundToken` (de `entry.token`).
  - **Retorno final**: si llegamos al ACCEPT con `errors.isEmpty()`, retornar `Accepted(trace, parseTree)`. Si llegamos al ACCEPT con `errors.isNotEmpty()`, retornar `Accepted(trace, parseTree, errors)`. Si nunca llegamos al ACCEPT (recovery se rindió en EOF), retornar `Rejected(trace, errors, partialTree)`.
- **Mensajes descriptivos**: cada `ParseError.message` debe seguir el formato `"Syntax error at line L, column C: unexpected 'X'; expected one of: A, B, C"` para consistencia con `SLR1Parser`. La línea y columna salen de `entry.location`. La lista de "expected" sale de las columnas de la fila de la tabla LL(1) que tienen producción (los terminales para los cuales `table.lookup(top, terminal) != null`), más `FOLLOW(top)` si `top` es un no terminal (porque puede derivar epsilon en contexto).
- **Implementación de referencia**: `SLR1Parser.kt` ya tiene panic mode, acumulación de errores, formato de mensaje y construcción de `ParseError`. **Leerlo antes de empezar** — la lógica de recovery LR y LL es estructuralmente distinta pero el manejo de errores (acumular, no abortar, formatear) es idéntico. Reusar el formato de `buildError(...)` adaptado al contexto LL.
- **Tests**: ubicar en `app/src/test/kotlin/org/compiler/LL1ParserTest.kt`. Seguir el patrón de `SLR1ParserTest`. Mínimo:
  - Acepta `id + id * id` con árbol correcto.
  - Acepta `id` solo.
  - **Recupera de error**: input con un token sobrante (ej. `id + + id`) debe retornar `Accepted` con `errors.size == 1`.
  - **Rechaza cuando recovery falla**: input vacío debe retornar `Rejected`.
- **Aceptación**:
  - Parsear `id + id * id` con la gramática de expresiones LL(1) del Dragon Book retorna `Accepted` con un `ParseTree` cuya raíz tiene la producción `E -> T E'`.
  - Parsear una cadena con un error recuperable retorna `Accepted` con la lista de errores poblada (al menos uno) y cada error con `location` no nula apuntando al token ofensor.
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

- **Estado**: completado
- **Depende de**: Ticket 17
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1AutomatonMerger.kt`
- **Descripción**: `mergeFromSLR1(automaton: SLR1Automaton): SLR1Automaton`. Identificar todos los pares de estados con el mismo core, fusionarlos uniendo los lookaheads de los items correspondientes, y reescribir las transiciones. Aplicar Dragon Book §4.7.4 (Algoritmo 4.59).
- **Aceptación**: aplicado al autómata SLR(1) de la gramática del libro produce un autómata con menos estados (mismo número que el autómata LR(0) clásico). Los lookaheads del estado mergeado son la unión de los originales.
- **Plan**: §9.2, §15.4

### Ticket 22 -- `LALR1Table`

- **Estado**: completado
- **Depende de**: Ticket 18
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/models/LALR1Table.kt`
- **Descripción**: `LALR1Table(action, goto, numStates)` con métodos `isLALR1()` y `conflicts()`. Reusa `Action` y `ConflictType` del módulo SLR(1).
- **Aceptación**: estructura paralela a `SLR1Table` lista para llenarse.
- **Plan**: §9.1

### Ticket 23 -- `LALR1TableBuilder`

- **Estado**: completado
- **Depende de**: Tickets 21, 22
- **Archivos**: `frontend/syntaxAnalyzer/lalr1/LALR1TableBuilder.kt`
- **Descripción**: `build(grammar, mergedAutomaton)`. Idéntico estructuralmente a `SLR1TableBuilder` pero sobre el autómata mergeado. Los lookaheads de los items mergeados ya contienen la unión, así que el algoritmo es el mismo.
- **Aceptación**: la tabla generada resuelve conflictos en gramáticas donde SLR fallaría debido a la separación de cores.
- **Plan**: §9.3, §15.6

### Ticket 24 -- `LALR1Parser`

- **Estado**: completado
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

- **Estado**: completado
- **Depende de**: Tickets 5, 7, 8, 10, 11, 12, 14, 17, 19, 21, 23, 15, 20, 24, 25, 26
- **Archivos**: `frontend/syntaxAnalyzer/runtime/Pipeline.kt`
- **Descripción**: `object Pipeline { fun runFull(yalexContent, yalpContent, inputContent, method): PipelineResult }`. `enum ParserMethod { LL1, SLR1, LALR1 }`. `data class PipelineResult` con todos los artefactos (tokens, grammar original, grammar tras precedence, grammar tras left recursion (solo si LL1), firstSets, followSets, slr1Automaton, lalr1Automaton, ll1Table, slr1Table, lalr1Table, parseResult). Aplica `LeftRecursionRewriter` solo si el método es `LL1`; para SLR/LALR pasa la salida de `PrecedenceRewriter` directamente.
- **Aceptación**: invocar `Pipeline.runFull` con archivos de prueba retorna un `PipelineResult` con todos los campos poblados.
- **Plan**: §10.3, [plans/2026-05-14-precedence-rewriter-design.md §10](./plans/2026-05-14-precedence-rewriter-design.md)

---

## Fase 9 -- Módulo Visualization

### Ticket 28 -- `DotExporter`

- **Estado**: completado
- **Depende de**: Ticket 16
- **Archivos**: `frontend/syntaxAnalyzer/visualization/DotExporter.kt`
- **Descripción**: `object DotExporter` con tres funciones públicas. Genera texto Graphviz para los autómatas SLR(1) y LALR(1), y renderiza un PNG vía el comando `dot` del sistema.
- **Signaturas exactas**:
  ```kotlin
  fun slr1ToDot(automaton: SLR1Automata): String
  fun lalr1ToDot(automaton: SLR1Automata): String   // recibe SLR1Automata porque LALR1AutomatonMerger.mergeFromSLR1 retorna SLR1Automata
  fun renderToImage(dot: String, outputPath: String): Boolean
  ```
- **Tipos a los que se refiere** (ver archivos):
  - `SLR1Automata` en `slr1/models/SLR1Automata.kt` (campos: `states`, `transitions: Map<Pair<Int, Symbol>, Int>`, `initialState`, `augmentedGrammar`).
  - `SLR1State` en `slr1/models/SLR1State.kt` (campos: `id: Int`, `items: Set<SLR1Item>`).
  - `SLR1Item` en `slr1/models/SLR1Item.kt` (campos: `production`, `dotPosition`, `lookaheads`).
- **Formato del output DOT** -- para una gramática mínima `S' -> S`, `S -> a` con 3 estados:
  ```
  digraph SLR1 {
    rankdir=LR;
    node [shape=box, style=rounded, fontname="Courier"];

    start [shape=none, label=""];
    start -> S0;

    S0 [label="State 0\nS' -> . S\nS -> . a"];
    S1 [label="State 1\nS -> a ."];
    S2 [label="State 2\nS' -> S .", peripheries=2];

    S0 -> S1 [label="a"];
    S0 -> S2 [label="S"];
  }
  ```
  Reglas:
  - Un nodo por estado, con label que incluye `State N` en la primera línea y un ítem `A -> α . β` por línea adicional (el `.` indica `dotPosition`).
  - Estado inicial: arrow desde un nodo invisible `start`.
  - Estado aceptador (ítem `S' -> S .` completo del símbolo aumentado): añadir `peripheries=2`.
  - Una arista por transición, etiquetada con el nombre del símbolo (`Terminal.name` o `NonTerminal.name`).
  - Para `lalr1ToDot`: mismo formato pero título `digraph LALR1`. La diferencia conceptual está en cómo se construyó el autómata (estados ya mergeados), no en la estructura del DOT.
- **`renderToImage`**: usa `ProcessBuilder("dot", "-Tpng", "-o", outputPath)`, escribe el DOT al stdin del proceso, espera a que termine y retorna `exitValue == 0`. Si `ProcessBuilder` lanza `IOException` (Graphviz no está en PATH), atrapar y retornar `false`. **No lanza excepción nunca**.
- **Aceptación**:
  - Para un `SLR1Automata` con N estados y M transiciones: `slr1ToDot` produce un `String` con N apariciones de `^S\d+ \[label=` (un nodo por estado) y M apariciones de `^S\d+ -> S\d+ \[label=` (una arista por transición).
  - El estado que contiene el ítem aceptador (símbolo aumentado completo) tiene `peripheries=2` en su línea.
  - `renderToImage(validDot, "/tmp/out.png")` crea el archivo y retorna `true` cuando `dot` está instalado; retorna `false` cuando no lo está, sin lanzar.
  - `lalr1ToDot` arranca con `digraph LALR1`; `slr1ToDot` arranca con `digraph SLR1`.
- **Tests**: en `app/src/test/kotlin/org/compiler/DotExporterTest.kt`. Mínimo:
  - `slr1ToDot includes one node per state and one edge per transition`
  - `slr1ToDot marks the accepting state with peripheries=2`
  - `slr1ToDot labels transitions with the symbol name`
  - `lalr1ToDot uses LALR1 as the graph title`
  - `renderToImage returns false when dot binary is not available` (se puede simular pasando un path inválido como dot binary, o testear vía mock; lo importante es que no lance)
- **Plan**: §11.1

### Ticket 29 -- `ParseTreeExporter`

- **Estado**: completado
- **Depende de**: Ticket 25
- **Archivos**: `frontend/syntaxAnalyzer/visualization/ParseTreeExporter.kt`
- **Descripción**: `object ParseTreeExporter` con dos funciones que convierten un `ParseTree` en string: una versión texto indentado para CLI/consola y una versión DOT para renderizar con Graphviz.
- **Signaturas exactas**:
  ```kotlin
  fun toIndentedText(tree: ParseTree): String
  fun toDot(tree: ParseTree): String
  ```
- **Tipos a los que se refiere**:
  - `ParseTree` en `runtime/models/ParseTree.kt` -- sealed interface con tres variantes:
    - `LeafNode(symbol: Symbol.Terminal, entry: TokenEntry)`
    - `InternalNode(symbol: Symbol.NonTerminal, production: Production, children: List<ParseTree>)`
    - `EpsilonNode` (data object)
  - El recorrido debe manejar las tres variantes vía `when`.
- **Formato del output indentado** -- para `id + id` con la gramática canónica del Dragon Book:
  ```
  E [E -> T E']
  +-- T [T -> F T']
  |   +-- F [F -> id]
  |   |   +-- id "a"
  |   +-- T' [T' -> ε]
  |       +-- ε
  +-- E' [E' -> + T E']
      +-- + "+"
      +-- T [T -> F T']
      |   +-- F [F -> id]
      |   |   +-- id "b"
      |   +-- T' [T' -> ε]
      |       +-- ε
      +-- E' [E' -> ε]
          +-- ε
  ```
  Reglas:
  - `InternalNode` -> linea con nombre del no-terminal seguido de `[head -> body]` mostrando la producción aplicada.
  - `LeafNode` -> `category "lexeme"` (siempre comillas alrededor del lexeme, incluso si coincide con category).
  - `EpsilonNode` -> `ε`.
  - Conectores: `+-- ` antes del nodo, prefijo `|   ` por cada nivel intermedio cuando hay más hermanos abajo, `    ` cuando es el último hermano. La raíz no lleva conector.
- **Formato del output DOT** -- para el mismo árbol:
  ```
  digraph ParseTree {
    rankdir=TB;
    ordering=out;

    n0 [label="E", shape=ellipse];
    n1 [label="T", shape=ellipse];
    n2 [label="F", shape=ellipse];
    n3 [label="id\n\"a\"", shape=box];
    n4 [label="T'", shape=ellipse];
    n5 [label="ε", shape=plaintext];
    n6 [label="E'", shape=ellipse];
    n7 [label="+\n\"+\"", shape=box];
    ...

    n0 -> n1;
    n0 -> n6;
    n1 -> n2;
    n1 -> n4;
    ...
  }
  ```
  Reglas:
  - `rankdir=TB` (raíz arriba, hojas abajo). `ordering=out` para que los hijos salgan en el orden declarado (izquierda a derecha).
  - `InternalNode` -> `shape=ellipse`, label es el nombre del no-terminal.
  - `LeafNode` -> `shape=box`, label es `category\n"lexeme"`.
  - `EpsilonNode` -> `shape=plaintext`, label es `ε`.
  - IDs únicos vía contador incremental durante el recorrido pre-orden (n0, n1, n2, ...).
  - Una arista del padre a cada hijo, sin etiqueta.
- **Aceptación**:
  - Para un `ParseTree` de `id` solo (`E -> T E', T -> F T', T' -> ε, E' -> ε, F -> id`), `toIndentedText` produce un string que se imprime con la estructura exacta del ejemplo de arriba (verificable con un snapshot de string).
  - `toDot` de un árbol con N nodos produce un string con N apariciones de `^n\d+ \[label=` y exactamente N-1 apariciones de `n\d+ -> n\d+;`.
  - `EpsilonNode` aparece como `ε` (con shape `plaintext` en DOT, sin comillas en texto).
- **Tests**: en `app/src/test/kotlin/org/compiler/ParseTreeExporterTest.kt`. Mínimo:
  - `toIndentedText for single id produces expected string` (snapshot exacto).
  - `toDot for single id has N nodes and N-1 edges`.
  - `Epsilon nodes render as ε in both formats`.
  - `Children are listed in declaration order`.
- **Plan**: §11.2

### Ticket 30 -- `TableFormatter`

- **Estado**: completado
- **Depende de**: Tickets 12, 13, 18, 22
- **Archivos**: `frontend/syntaxAnalyzer/visualization/TableFormatter.kt`
- **Descripción**: `object TableFormatter` con funciones que formatean las tablas y conjuntos del análisis sintáctico como strings multilinea alineados, listos para mostrar en un componente monoespaciado (terminal, `TextArea` Swing/Compose con fuente Courier, etc.).
- **Signaturas exactas**:
  ```kotlin
  fun formatLL1Table(table: LL1Table): String
  fun formatSLR1Action(table: SLR1Table): String
  fun formatSLR1Goto(table: SLR1Table): String
  fun formatLALR1Action(table: LALR1Table): String
  fun formatLALR1Goto(table: LALR1Table): String
  fun formatFirstSets(firstSets: FirstSets): String
  fun formatFollowSets(followSets: FollowSets): String
  ```
- **Tipos a los que se refiere**:
  - `LL1Table` en `ll1/models/LL1Table.kt` (campos: `startSymbol`, `cells: Map<Pair<Symbol.NonTerminal, Symbol>, Production>`, `conflicts`, `followSets`).
  - `SLR1Table` en `slr1/models/SLR1Table.kt` (campos: `action: Map<Pair<Int, Symbol>, Action>`, `goto: Map<Pair<Int, Symbol>, Int>`, `numStates`, `conflicts`).
  - `LALR1Table` en `lalr1/models/LALR1Table.kt` (misma estructura que `SLR1Table`).
  - `Action` en `runtime/models/Action.kt` -- sealed interface con `Shift(nextState)`, `Reduce(production)`, `Accept`, `Match`, `Expand`.
  - `FirstSets` / `FollowSets` en `sets/models/` (ambos exponen `results: Map<Symbol.NonTerminal, Set<Symbol>>`).
- **Formato del output -- LL(1) table** -- para la gramática canónica del Dragon Book:
  ```
         | id     | +      | *      | (      | )      | $     
  -------+--------+--------+--------+--------+--------+-------
  E      | E->TE' |        |        | E->TE' |        |       
  E'     |        | +TE'   |        |        | ε      | ε     
  T      | T->FT' |        |        | T->FT' |        |       
  T'     |        | ε      | *FT'   |        | ε      | ε     
  F      | F->id  |        |        | F->(E) |        |       
  ```
  Reglas:
  - Header con `|` como separador de columnas, `+` en las intersecciones del separador horizontal.
  - Columnas: una por terminal/EndMarker que aparezca en `cells`. `$` representa `Symbol.EndMarker`.
  - Filas: una por `NonTerminal` que aparezca en `cells`.
  - Celda con producción: muestra el body, ej. `E->TE'` o `ε` si el body es solo `Epsilon`.
  - Celda vacía: solo espacios.
  - Ancho de columna: `max(longest_content_in_column, 6)` para no quedar demasiado angosto.
- **Formato del output -- SLR1Action / LALR1Action**:
  ```
  State | id   | +    | *    | (    | )    | $   
  ------+------+------+------+------+------+-----
      0 | s5   |      |      | s4   |      |     
      1 |      | s6   |      |      |      | acc 
      2 |      | r2   | s7   |      | r2   | r2  
  ```
  Codificación de acciones:
  - `Action.Shift(N)` -> `sN`
  - `Action.Reduce(production)` -> `r{production.id}`
  - `Action.Accept` -> `acc`
  - Celda vacía -> solo espacios.
- **Formato del output -- SLR1Goto / LALR1Goto**:
  ```
  State | E    | T    | F   
  ------+------+------+-----
      0 |    1 |    2 |    3
      4 |    8 |    2 |    3
  ```
  La celda contiene el número del siguiente estado (sin prefijo) o vacía si no hay transición.
- **Manejo de conflictos**: si una celda de Action tiene más de una acción (mirar `table.conflicts`: cada `Conflict` tiene `state`, `terminal`, `actions: List<Action>`), formatear todas separadas por `/`. Ejemplo: `s5/r3`. Si la `Map<Pair, Action>` sólo guarda la "ganadora" tras resolución, el formatter debe enriquecer con la lista de conflictos para mostrar todas las opciones.
- **Formato del output -- FIRST/FOLLOW sets** -- para la gramática canónica:
  ```
  E      = { (, id }
  E'     = { +, ε }
  T      = { (, id }
  T'     = { *, ε }
  F      = { (, id }
  ```
  Reglas:
  - Una línea por no-terminal, ordenados por nombre alfabético (output determinista).
  - Símbolos dentro del set ordenados alfabéticamente. `ε` representa `Symbol.Epsilon`, `$` representa `Symbol.EndMarker`.
  - Padding: el nombre del no-terminal se justifica a `max(longest_nonterminal_name, 6)` caracteres.
- **Aceptación**:
  - `formatLL1Table` aplicado a la tabla canónica del libro produce un string donde todas las líneas tienen exactamente la misma longitud en caracteres (verificable con `lines().map { it.length }.toSet().size == 1`).
  - `formatSLR1Action` y `formatSLR1Goto` aceptan tablas con conflictos sin lanzar; cada celda en conflicto muestra todas sus acciones separadas por `/`.
  - `formatFirstSets` y `formatFollowSets` producen output determinista: dos llamadas con el mismo input retornan el mismo string.
- **Tests**: en `app/src/test/kotlin/org/compiler/TableFormatterTest.kt`. Mínimo:
  - `formatLL1Table for Dragon Book grammar matches expected snapshot` (multilinea exacto).
  - `formatSLR1Action renders shift, reduce, and accept correctly`.
  - `formatSLR1Action renders conflict cells with / separator`.
  - `formatFirstSets is deterministic across calls`.
  - `all output lines have equal length` para cada función de tabla.
- **Plan**: §11.3

---

## Fase 10 -- Recursos de prueba

### Ticket 31 -- Archivos de prueba

- **Estado**: completado
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

- **Estado**: completado
- **Depende de**: ninguno
- **Archivos**: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/kotlin/org/compiler/GuiApp.kt`, `app/src/main/kotlin/org/compiler/gui/App.kt`, `README.md`
- **Descripción**: plugins `org.jetbrains.kotlin.plugin.compose` y `org.jetbrains.compose` aplicados; dependencias `compose.desktop.currentOs`, `compose.material3` y `compose.materialIconsExtended`; bloque `compose.desktop { application { ... nativeDistributions { Dmg/Msi/Deb } } }` configurado; task `runGui` registrada con `JavaExec`. Versiones: Kotlin 2.1.21, Compose Multiplatform 1.8.2 (combinacion del template oficial de JetBrains).
- **Aceptación**: `./gradlew runGui` arranca la ventana Compose Desktop con un Hello World en Material 3; `./gradlew packageDmg`/`packageMsi`/`packageDeb` empaqueta instaladores nativos.
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
