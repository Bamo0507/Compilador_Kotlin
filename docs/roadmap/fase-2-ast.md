# Fase 2 — Del árbol de ANTLR al AST propio

**Objetivo de la fase:** correr ANTLR sobre un archivo `.cps`, capturar sus errores
léxicos y sintácticos en `Diagnostics`, y convertir su árbol de análisis en el AST
propio de la Fase 1.

**Por qué esta fase existe:** el árbol que produce ANTLR es fiel al texto, no al
significado. Incluye paréntesis, puntos y comas, y un nodo por cada nivel de
precedencia aunque no haga nada. Para la expresión `x` sola son **once** nodos
encadenados. Trabajar directamente sobre ese árbol haría que cada regla semántica
tuviera que saltar niveles vacíos, y el código sería ilegible.

**Al terminar:** un archivo `.cps` entra y sale un `Program` limpio, o una lista de
errores con línea y columna.

**Esta fase es el único lugar del proyecto que conoce ANTLR.** Todo lo que viene
después trabaja sobre el AST propio. Es una frontera deliberada: si algún día se
cambia de herramienta, solo esta carpeta se reescribe.

**Estimación:** dos o tres sesiones. El ticket 2.3 es el más largo por cantidad de
casos, no por dificultad.

---

## Ticket 2.1 — `SyntaxAnalyzer` y captura de errores de ANTLR

- **Estado**: terminado 
- **Depende de**: 0.6, 1.5

**Archivos:**

- `frontend/syntax/DiagnosticsErrorListener.kt` (NUEVO)
- `frontend/syntax/SyntaxAnalyzer.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/SyntaxAnalyzerTest.kt` (NUEVO)

**Qué es esto, en simple:** por defecto, cuando ANTLR encuentra un error escribe un
mensaje en la consola y sigue. Eso no sirve: los errores tienen que llegar a la
lista de errores del IDE. Este ticket le quita a ANTLR su reportador por defecto y
le pone uno propio que manda todo a `Diagnostics`.

### `DiagnosticsErrorListener`

```kotlin
// Recibe los errores que ANTLR detecta y los traduce a CompilerError.
//
// ANTLR usa el mismo callback para errores léxicos y sintácticos; se distinguen
// por quién lo reporta: si el recognizer es un Lexer, es un error léxico.
class DiagnosticsErrorListener(
    private val diagnostics: Diagnostics
) : BaseErrorListener() {

    override fun syntaxError(
        // * -> es como un tipo abstracto
        // evitmos casarnos con el formato que viene de ANTLR
      // para errores lexicos (Recognizer<Int, LexerATNSimulator>) o 
        // el de sintactico Recognizer<Token, ParserATNSimulator>
        recognizer: Recognizer<*, *>?,
        // El token que hace la ofensa, null si es lexico
        offendingSymbol: Any?,
        line: Int,                  // ya viene 1-based
        charPositionInLine: Int,    // viene 0-based

        // El mensaje que ANTLR ya armó. Viene en INGLES y con su propio formato:
        //   "token recognition error at: '@'"
        //   "mismatched input ';' expecting {'let', 'var', ...}"
        // Se usa tal cual. Reescribirlo en español seria posible a partir de
        // offendingSymbol, pero es trabajo extra sin ganar precision.
        msg: String,

        // La excepcion con el detalle interno del algoritmo: que regla estaba
        // reconociendo y que esperaba encontrar. Viene null cuando ANTLR se
        // recupero del error sin necesidad de lanzar, que es el caso mas comun.
        // No se usa: line, charPositionInLine y msg ya traen todo lo que el IDE
        // necesita mostrar.
        e: RecognitionException?
    ) {
        // ANTLR cuenta las columnas desde 0; LexemeLocation las cuenta desde 1.
        val location = LexemeLocation(line = line, position = charPositionInLine + 1)

        // El recognizer es la instancia que ANTLR usa para reconocer la entrada y
        // que reporta el error: el lexer o el parser. La verificacion `is Lexer`
        // funciona por la jerarquia de clases de ANTLR:
        //
        //   CompiscriptLexer  -> Lexer  -> Recognizer
        //   CompiscriptParser -> Parser -> Recognizer
        //
        // Los dos son Recognizer, pero solo el lexer hereda de Lexer. Por eso
        // `is Lexer` es verdadero exclusivamente cuando el error lo reporto el
        // lexer, y eso es lo unico que permite separarlos: ANTLR llama a ESTE
        // MISMO metodo para los dos.
        //
        // Este if produce DOS de los tres niveles de error del IDE. El tercero,
        // SemanticError, no pasa por aqui: lo reportan las fases 3, 4 y 5
        // llamando a diagnostics.report(...) directo.
        //
        // Sin el if habria que elegir un solo tipo para los dos casos, y el IDE
        // perderia esta distincion:
        //
        //   let x = @@@;   el @ no es un caracter de Compiscript      -> LexerError
        //   let x = ;      los caracteres son validos, el orden no    -> ParserError
        val error = if (recognizer is Lexer) {
            CompilerError.LexerError(location, msg)
        } else {
            CompilerError.ParserError(location, msg)
        }

        diagnostics.report(error)
    }
}
```

### `SyntaxAnalyzer`

```kotlin
// Corre el lexer y el parser de ANTLR sobre el código fuente.
//
// Devuelve el árbol de análisis, o null si hubo errores sintácticos: sin un árbol
// confiable no tiene sentido seguir a la fase semántica.
object SyntaxAnalyzer {

    fun parse(source: String, diagnostics: Diagnostics): CompiscriptParser.ProgramContext? {
        val errorListener = DiagnosticsErrorListener(diagnostics)

        val lexer = CompiscriptLexer(CharStreams.fromString(source))
        lexer.removeErrorListeners()      // fuera el que escribe a consola
        lexer.addErrorListener(errorListener)

        val parser = CompiscriptParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tree = parser.program()

        return if (diagnostics.hasErrors) null else tree
    }
}
```

**Por qué devolver `null` cuando hay errores sintácticos:** si el árbol está
incompleto o mal formado, el `AstBuilder` va a encontrar contextos nulos donde
esperaba nodos, y el resultado serían excepciones en vez de mensajes útiles. Es más
honesto detenerse: se muestran los errores sintácticos y ya. El IDE seguirá
mostrando el árbol de ANTLR (que sí existe aunque tenga huecos) para que el usuario
vea hasta dónde llegó.

### Aceptación

- `parse("let x: integer = 1;", diagnostics)` devuelve un árbol no nulo y
  `diagnostics.hasErrors` es `false`.
- `parse("let x: integer = ;", diagnostics)` devuelve `null` y produce al menos un
  `ParserError` con línea y columna correctas.
- `parse("let x = @@@;", diagnostics)` produce al menos un `LexerError` (el
  carácter `@` no existe en Compiscript).
- Las columnas reportadas son 1-based: un error en el primer carácter de una línea
  reporta `position = 1`, no `0`.

### Respaldo

Enunciado, *"El analizador léxico y sintáctico (ANTLR) construye el árbol
sintáctico"*. Requisito de reportar los tres niveles de error en el IDE.

---

## Ticket 2.2 — `AstBuilder`: expresiones y el plegado a la izquierda

- **Estado**: pendiente
- **Depende de**: 1.4, 2.1

**Archivos:**

- `frontend/ast/AstBuilder.kt` (NUEVO — parte de expresiones)
- `app/src/test/kotlin/org/compiler/AstBuilderExprTest.kt` (NUEVO)

**Qué es esto, en simple:** el `AstBuilder` es un **Visitor de ANTLR**. ANTLR genera
la plantilla (`CompiscriptBaseVisitor`) con **52 métodos**, uno por regla de la
gramática, todos con un cuerpo por defecto. Aquí se sobrescriben los que producen un
nodo del AST.

```kotlin
// Convierte el arbol de ANTLR en el AST propio.
//
// El <Node> es el tipo de retorno de todos los metodos: cada visitX devuelve el
// nodo del AST equivalente a la regla que visito.
class AstBuilder : CompiscriptBaseVisitor<Node>()
```

**Cuándo se genera la plantilla:** la produce `./gradlew generateGrammarSource`, que
corre automáticamente antes de compilar Kotlin por el `dependsOn` del ticket 0.4.
Sale en `app/build/generated-src/antlr/main/org/compiler/parser/`, así que **no se
commitea**: se regenera.

Y no viene por defecto — la declara la bandera `-visitor` del ticket 0.4. Sin ella
ANTLR genera **solo** el listener y `CompiscriptBaseVisitor` no existe.

### Qué hay dentro de un `*Context`

Referencia para trabajar el AstBuilder. ANTLR genera una clase `Context` por cada
regla del `.g4`, anidada dentro de `CompiscriptParser` y nombrada igual que la regla.

```kotlin
// ── Reglas con repeticion: `*` y `+` dan una LISTA ────────────────────────
//
//   program: statement* EOF;
//
//   class ProgramContext : ParserRuleContext {
//       fun statement(): List<StatementContext>     // el `*` -> lista
//       fun statement(i: Int): StatementContext     // acceso por indice
//       fun EOF(): TerminalNode
//   }
//
// Sobre este programa:
//     1  let x: integer = 5;
//     2  print(x);
//
//   tree.statement()     -> [StatementContext, StatementContext]
//   tree.statement(0)    -> el del `let`


// ── Reglas de ALTERNATIVAS: todos los metodos existen, solo uno no es null ─
//
//   statement: variableDeclaration | assignment | printStatement | ... ;
//
//   class StatementContext : ParserRuleContext {
//       fun variableDeclaration(): VariableDeclarationContext?
//       fun assignment(): AssignmentContext?
//       fun printStatement(): PrintStatementContext?
//       // ... uno por cada alternativa, 18 en total
//   }
//
// Para `let x: integer = 5;`:
//   stmt.variableDeclaration()   -> VariableDeclarationContext   OK
//   stmt.assignment()            -> null
//   stmt.printStatement()        -> null
//
// Por eso conviene usar visit(stmt) y dejar que ANTLR despache al metodo
// correcto, en vez de preguntar cual no es nulo.


// ── La tabla que dice que esperar de cada metodo ───────────────────────────
//
//   En la gramatica        El metodo devuelve
//   ─────────────────      ─────────────────────────────────────
//   x  (a secas)           XContext         nunca null
//   x?                     XContext?        PUEDE ser null
//   x*  o  x+              List<XContext>
//   un token (Identifier)  TerminalNode
//
//   variableDeclaration: ('let'|'var') Identifier typeAnnotation? initializer? ';'
//
//   Para `let x: integer = 5;`      Para `let y;`
//     ctx.Identifier().text -> "x"    ctx.Identifier().text -> "y"
//     ctx.typeAnnotation()  -> ...    ctx.typeAnnotation()  -> null
//     ctx.initializer()     -> ...    ctx.initializer()     -> null
//
// CUIDADO: Kotlin ve estos tipos como platform types, asi que NO avisa que los
// `?` pueden ser null. Compila y revienta en ejecucion. Es la fuente numero uno
// de NPEs en esta fase.


// ── Lo que todos heredan de ParserRuleContext ─────────────────────────────
//
//   ctx.start        Token: el PRIMER token de este nodo
//   ctx.stop         Token: el ULTIMO
//   ctx.text         String: el texto fuente de este nodo
//   ctx.childCount   Int
//   ctx.getChild(i)  ParseTree
//   ctx.parent       el nodo de arriba
//
// `ctx.start` es lo que usa locationOf() para la linea y la columna.
//
// CUIDADO con ctx.text: concatena los tokens SIN espacios, porque WS lleva
// `-> skip` en la gramatica y esos caracteres nunca entraron al stream.
//   ctx.text  ->  "letx:integer=5;"   y no  "let x: integer = 5;"
// Sirve para tokens sueltos (ctx.Identifier().text -> "x"), no para reconstruir
// el codigo fuente.
```

### Los nombres de los métodos no siempre son el de la regla

Es lo que más confunde al empezar. Depende de si la regla tiene **alternativas
etiquetadas** con `#`:

```antlr
additiveExpr: multiplicativeExpr ( ('+'|'-') multiplicativeExpr )*;   // sin etiquetas
```
genera **`visitAdditiveExpr`**, con el nombre de la regla.

```antlr
assignmentExpr
  : lhs=leftHandSide '=' assignmentExpr                 # AssignExpr
  | lhs=leftHandSide '.' Identifier '=' assignmentExpr  # PropertyAssignExpr
  | conditionalExpr                                     # ExprNoAssign
  ;
```
**no** genera `visitAssignmentExpr`. Genera **uno por etiqueta**: `visitAssignExpr`,
`visitPropertyAssignExpr` y `visitExprNoAssign`.

Las cuatro reglas del `.g4` con etiquetas, y lo que hay que escribir:

| Regla | Métodos que existen | Método que NO existe |
|---|---|---|
| `assignmentExpr` | `visitAssignExpr`, `visitPropertyAssignExpr`, `visitExprNoAssign` | `visitAssignmentExpr` |
| `conditionalExpr` | `visitTernaryExpr` | `visitConditionalExpr` |
| `primaryAtom` | `visitIdentifierExpr`, `visitNewExpr`, `visitThisExpr` | `visitPrimaryAtom` |
| `suffixOp` | `visitCallExpr`, `visitIndexExpr`, `visitPropertyAccessExpr` | `visitSuffixOp` |

En total: 46 reglas de parser, de las cuales 4 tienen etiquetas (10 etiquetas entre
las cuatro). **42 + 10 = 52 métodos** en `CompiscriptBaseVisitor`.

Los tres de `suffixOp` **no se sobrescriben**: un sufijo suelto no significa nada
—`()` necesita saber a qué se le está llamando—, así que `visitLeftHandSide` los
inspecciona por tipo con el lado izquierdo ya acumulado.

### Lo que trae cada método por defecto, y por qué hay que sobrescribir

```java
// CompiscriptBaseVisitor.java, generado
@Override public T visitPrintStatement(CompiscriptParser.PrintStatementContext ctx) {
    return visitChildren(ctx);
}
```

Y `visitChildren`, que viene de `AbstractParseTreeVisitor`, hace **una** cosa:

```java
public T visitChildren(RuleNode node) {
    T result = defaultResult();                          // arranca en null
    for (int i = 0; i < node.getChildCount(); i++) {
        T childResult = node.getChild(i).accept(this);
        result = aggregateResult(result, childResult);    // result = childResult
    }
    return result;
}

protected T defaultResult() { return null; }
protected T aggregateResult(T aggregate, T nextResult) { return nextResult; }
```

Cada hijo sobreescribe al anterior, así que **devuelve el resultado del último hijo**.

Y en un árbol de parseo hay exactamente dos tipos de hijo:

| Tipo de hijo | Ejemplo | Qué devuelve al visitarlo |
|---|---|---|
| **Regla** (`RuleNode`) | `expression`, `block` | el nodo del AST |
| **Token** (`TerminalNode`) | `';'`, `'print'`, `'('` | `null`, siempre (`visitTerminal` → `defaultResult()`) |

Un token no tiene nodo del AST: `;` es puntuación, no significado. Ahí está la trampa:

```antlr
printStatement: 'print' '(' expression ')' ';'
```

Para `print(42);`, así evoluciona `result`:

| i | hijo | tipo | `result` queda |
|---|---|---|---|
| 0 | `'print'` | token | `null` |
| 1 | `'('` | token | `null` |
| 2 | `expression` | regla | **`Literal(42)`** |
| 3 | `')'` | token | `null` ← se pierde |
| 4 | `';'` | token | `null` |

El nodo estaba ahí y los dos tokens de después lo tiraron. Por eso se sobrescribe:
no para *construir* el nodo, sino para **quedárselo**.

**Y a veces el default es correcto**, y por eso tres reglas no aparecen en el
`AstBuilder`:

```antlr
expression: assignmentExpr;
statement: variableDeclaration | assignment | printStatement | ... ;
classMember: functionDeclaration | variableDeclaration | constantDeclaration;
```

Un solo hijo, y es una **regla**: `visitChildren` lo visita y propaga su nodo, sin
nada después que lo pise. Es lo que hace que `visit(statementContext)` funcione sin
escribir `visitStatement`.

La regla exacta: **el default sirve si y solo si el último hijo es una regla que ya
devuelve el nodo que querés.** No es "un solo hijo" — `literalExpr` tiene un hijo en
todas sus alternativas y cuatro de las cinco son tokens.

### Los ayudantes, todos juntos

```kotlin
// La ubicacion de un nodo es la de su PRIMER token.
// ANTLR cuenta columnas desde 0; LexemeLocation desde 1.
private fun locationOf(ctx: ParserRuleContext): LexemeLocation =
    LexemeLocation(
        line = ctx.start.line,
        position = ctx.start.charPositionInLine + 1
    )

// Los operadores de una regla con forma  X (op X)*  son los hijos que NO son
// sub-expresiones: los nodos terminales. Se leen en orden de aparicion.
//
//   a + b - c   ->  ["+", "-"]
private fun operatorSymbolsOf(ctx: ParserRuleContext): List<String> =
    (0 until ctx.childCount)
        .map { ctx.getChild(it) }
        .filterIsInstance<TerminalNode>()
        .map { it.text }

// Pliega a la IZQUIERDA una lista plana de operandos con sus operadores.
//
//   [a, b, c] con ["+", "-"]  ->  BinaryOperation(BinaryOperation(a,+,b), -, c)
//
// El `left = result` es todo el secreto: cada vuelta mete lo ACUMULADO a la
// izquierda. Por eso queda ((a+b)-c) y no (a+(b-c)).
private fun foldBinaryLeft(
    operands: List<Expression>,
    operatorSymbols: List<String>
): Expression {
    var result = operands.first()
    for (index in operatorSymbols.indices) {
        result = BinaryOperation(
            left = result,
            operator = BinaryOperator.fromSymbol(operatorSymbols[index]),
            right = operands[index + 1],
            location = result.location
        )
    }
    return result
}

// Las SEIS reglas binarias tienen la misma forma  X (op X)*, asi que comparten
// esto. La unica diferencia entre ellas es el accesor del hijo.
//
// El `if (size == 1)` es la regla que COLAPSA LA TORRE, y vivir aqui garantiza que
// las seis la tengan: no se puede olvidar en una.
private fun foldFlatBinary(
    ctx: ParserRuleContext,
    operands: List<ParserRuleContext>
): Expression {
    val expressions = operands.map { visit(it) as Expression }
    if (expressions.size == 1) return expressions.single()
    return foldBinaryLeft(expressions, operatorSymbolsOf(ctx))
}

// arguments: expression (',' expression)*
//
// El `?` del llamador (`arguments?` en la gramatica) es lo que hace posible `f()`
// sin argumentos, y por eso el parametro es nullable.
private fun buildArguments(ctx: ArgumentsContext?): List<Expression> =
    ctx?.expression()?.map { visit(it) as Expression } ?: emptyList()

// Quita las comillas de un StringLiteral.
//
// No hay escapes que procesar: la gramatica dice
//   StringLiteral: '"' (~["\r\n])* '"'
// o sea cualquier caracter menos comilla y salto de linea. Un backslash es un
// backslash literal, no un escape.
private fun unquote(text: String): String = text.substring(1, text.length - 1)
```

### Los seis niveles binarios: el plegado a la izquierda

Mira la forma de estas reglas:

```antlr
additiveExpr: multiplicativeExpr ( ('+' | '-') multiplicativeExpr )*
```

Eso **no** es recursión izquierda: es un bucle. La expresión `a + b + c` produce
**un solo** nodo `additiveExpr` con **cinco** hijos:

```
additiveExpr
├─ multiplicativeExpr(a)
├─ '+'
├─ multiplicativeExpr(b)
├─ '+'
└─ multiplicativeExpr(c)
```

En el proyecto anterior, la recursión izquierda de la gramática daba la forma del
árbol gratis: `((a+b)+c)`. **Aquí ANTLR entrega una lista plana y la asociatividad
la decide el AstBuilder.**

```
10 - 3 - 2   plegado a la izquierda:  (10-3)-2 = 5   ✓ correcto
10 - 3 - 2   plegado a la derecha:    10-(3-2) = 9   ✗ incorrecto
```

Y si sale al revés el compilador no falla: **calcula mal en silencio.**

Con `foldFlatBinary`, cada una de las seis reglas es **una línea**, y la diferencia
entre ellas queda a la vista:

```kotlin
override fun visitLogicalOrExpr(ctx: LogicalOrExprContext): Node =
    foldFlatBinary(ctx, ctx.logicalAndExpr())

override fun visitLogicalAndExpr(ctx: LogicalAndExprContext): Node =
    foldFlatBinary(ctx, ctx.equalityExpr())

override fun visitEqualityExpr(ctx: EqualityExprContext): Node =
    foldFlatBinary(ctx, ctx.relationalExpr())

override fun visitRelationalExpr(ctx: RelationalExprContext): Node =
    foldFlatBinary(ctx, ctx.additiveExpr())

override fun visitAdditiveExpr(ctx: AdditiveExprContext): Node =
    foldFlatBinary(ctx, ctx.multiplicativeExpr())

override fun visitMultiplicativeExpr(ctx: MultiplicativeExprContext): Node =
    foldFlatBinary(ctx, ctx.unaryExpr())
```

#### Para los lógicos el plegado protege otra cosa: el orden

`&&` y `||` son **asociativos**, igual que `+`: `(p&&q)&&r` y `p&&(q&&r)` dan el mismo
booleano. Entonces, ¿importa el lado? Sí, pero por el **orden de evaluación**.

El intérprete de la Fase 6 tiene que hacer cortocircuito. Con plegado a la izquierda
el árbol queda así:

```
        AND
       /   \
     AND    r
    /   \
   p     q
```

y el recorrido en postorden evalúa **`p`, `q`, `r`** — de izquierda a derecha, que es
lo que espera quien escribió `p && q && r`. Plegado a la derecha evaluaría `q` y `r`
antes de decidir sobre `p`, y si `q` fuera una llamada a función correría cuando no
debía.

**Para las aritméticas el plegado izquierdo protege el valor; para las lógicas
protege el orden.** El mismo código cubre las dos cosas.

#### La precedencia no viene del plegado, viene de la torre

`logicalAndExpr` está **debajo** de `logicalOrExpr`, así que `&&` liga más fuerte, y
eso sale gratis. Para `a || b && c`, el nodo `logicalOrExpr` tiene tres hijos:

```
logicalOrExpr
├─ logicalAndExpr(a)
├─ '||'
└─ logicalAndExpr(b && c)      <- el && se armo ADENTRO, un nivel mas abajo
```

Entonces `operands = [a, (b&&c)]` con `["||"]`, y sale
`BinaryOperation(a, OR, BinaryOperation(b, AND, c))`.

**El `AstBuilder` no sabe nada de precedencia.** Solo pliega lo que cada nivel le
entrega, y la forma correcta sale porque la torre ya separó los niveles.

### Las dos que no necesitan plegado

```antlr
unaryExpr: ('-' | '!') unaryExpr | primaryExpr;         // recursiva por la DERECHA
assignmentExpr: lhs=leftHandSide '=' assignmentExpr;    // recursiva por la DERECHA
```

Estas **sí** son recursivas en la gramática, y hacia la derecha — que es la
asociatividad correcta para ambas. `!!x` y `a = b = c` salen bien sin tocar nada.

Y el ternario también: sus ramas son `expression` completa, así que `a ? b = 1 : c`
es legal y `a ? b : c ? d : e` anida a la derecha naturalmente.

### La cadena de sufijos: llamadas, indexado y acceso a propiedad

```antlr
leftHandSide: primaryAtom (suffixOp)*
suffixOp
  : '(' arguments? ')'      # CallExpr
  | '[' expression ']'      # IndexExpr
  | '.' Identifier          # PropertyAccessExpr
```

Aquí también se pliega a la izquierda, pero cada sufijo **envuelve** lo anterior:

```kotlin
override fun visitLeftHandSide(ctx: LeftHandSideContext): Node {
    var result = visit(ctx.primaryAtom()) as Expression

    // Cada sufijo envuelve el resultado acumulado. Así,
    //   perro.dueno.nombre  ->  PropertyAccess(PropertyAccess(perro, dueno), nombre)
    //   lista[0][1]         ->  IndexAccess(IndexAccess(lista, 0), 1)
    //   perro.hablar()      ->  FunctionCall(PropertyAccess(perro, hablar), [])
    for (suffix in ctx.suffixOp()) {
        result = when (suffix) {
            is CallExprContext -> FunctionCall(
                callee = result,
                arguments = buildArguments(suffix.arguments()),
                location = result.location
            )
            is IndexExprContext -> IndexAccess(
                target = result,
                index = visit(suffix.expression()) as Expression,
                location = result.location
            )
            is PropertyAccessExprContext -> PropertyAccess(
                target = result,
                propertyName = suffix.Identifier().text,
                location = result.location
            )
            else -> error("Sufijo desconocido: ${suffix::class.simpleName}")
        }
    }

    return result
}
```

Fíjate en el último ejemplo: `perro.hablar()` produce
`FunctionCall(callee = PropertyAccess(perro, "hablar"), arguments = [])`. **La llamada a
método no es un nodo aparte: es un `FunctionCall` cuyo `callee` es un `PropertyAccess`.** El
verificador de tipos tiene que manejar ese caso explícitamente (Fase 4, ticket 4.3).

### La asignación como expresión, y el ternario

```kotlin
// lhs=leftHandSide '=' assignmentExpr            # AssignExpr
//
// El `lhs=` de la gramatica es una ETIQUETA DE CAMPO, y genera el accesor ctx.lhs.
// Recursiva por la derecha, asi que `a = b = c` ya agrupa como `a = (b = c)`.
override fun visitAssignExpr(ctx: AssignExprContext): Node =
    AssignmentExpression(
        target = visit(ctx.lhs) as Expression,
        value = visit(ctx.assignmentExpr()) as Expression,
        location = locationOf(ctx)
    )

// lhs=leftHandSide '.' Identifier '=' assignmentExpr   # PropertyAssignExpr
//
// `obj.prop = valor`. El target se arma como PropertyAccess, asi que el resto del
// compilador ve la misma forma que produce la cadena de sufijos.
override fun visitPropertyAssignExpr(ctx: PropertyAssignExprContext): Node {
    val location = locationOf(ctx)
    return AssignmentExpression(
        target = PropertyAccess(
            target = visit(ctx.lhs) as Expression,
            propertyName = ctx.Identifier().text,
            location = location
        ),
        value = visit(ctx.assignmentExpr()) as Expression,
        location = location
    )
}

// conditionalExpr    # ExprNoAssign
//
// Alternativa de puro paso. Habria que dejarla al default, PERO la regla tiene
// etiquetas: sin este metodo, `visitExprNoAssign` no se escribe y el default de
// ANTLR aplica igual. Se escribe explicito para que las tres alternativas de
// assignmentExpr esten a la vista juntas.
override fun visitExprNoAssign(ctx: ExprNoAssignContext): Node =
    visit(ctx.conditionalExpr())

// conditionalExpr
//   : logicalOrExpr ('?' expression ':' expression)?   # TernaryExpr
//
// El metodo se llama visitTernaryExpr, NO visitConditionalExpr: ver la tabla de
// nombres arriba.
override fun visitTernaryExpr(ctx: TernaryExprContext): Node {
    val condition = visit(ctx.logicalOrExpr()) as Expression

    // Sin ternario: colapsa al hijo.
    if (ctx.expression().isEmpty()) return condition

    return TernaryOperation(
        condition = condition,
        ifTrue = visit(ctx.expression(0)) as Expression,
        ifFalse = visit(ctx.expression(1)) as Expression,
        location = locationOf(ctx)
    )
}
```

### El unario, las primarias y los literales

```kotlin
// unaryExpr: ('-' | '!') unaryExpr | primaryExpr
override fun visitUnaryExpr(ctx: UnaryExprContext): Node {
    // Sin operador: colapsa al hijo.
    val inner = ctx.unaryExpr() ?: return visit(ctx.primaryExpr())

    return UnaryOperation(
        operator = UnaryOperator.fromSymbol(ctx.getChild(0).text),
        operand = visit(inner) as Expression,
        location = locationOf(ctx)
    )
}

// primaryExpr: literalExpr | leftHandSide | '(' expression ')'
//
// Las dos primeras alternativas funcionarian con el default (un hijo, es regla),
// pero la tercera no: su ultimo hijo es ')' y devolveria null. Como la regla no
// tiene etiquetas, ANTLR genera UN metodo para las tres, asi que hay que cubrirlas.
override fun visitPrimaryExpr(ctx: PrimaryExprContext): Node = when {
    ctx.literalExpr() != null  -> visit(ctx.literalExpr())
    ctx.leftHandSide() != null -> visit(ctx.leftHandSide())

    // El caso `(expr)`. Los parentesis DESAPARECEN: ya hicieron su trabajo al
    // parsear, le dieron forma al arbol. El AST guarda la forma, no la notacion.
    else -> visit(ctx.expression())
}

// literalExpr: Literal | arrayLiteral | 'null' | 'true' | 'false'
//
// El tipo del literal se decide por la FORMA del texto: la gramatica no tiene
// tokens separados para true/false/null, los declara como literales de cadena.
override fun visitLiteralExpr(ctx: LiteralExprContext): Node {
    if (ctx.arrayLiteral() != null) return visit(ctx.arrayLiteral())

    val text = ctx.text
    val location = locationOf(ctx)

    return when {
        text == "null"        -> Literal(null, NullType, location)
        text == "true"        -> Literal(true, BooleanType, location)
        text == "false"       -> Literal(false, BooleanType, location)
        text.startsWith("\"") -> Literal(unquote(text), StringType, location)
        text.contains(".")    -> Literal(text.toDouble(), FloatType, location)

        // toLong y no toInt: 99999999999 es sintacticamente valido pero no cabe en
        // Int. Guardandolo en Long, la Fase 4 detecta el desborde y lo REPORTA con
        // linea y columna; con toInt el AstBuilder lanzaria antes de llegar ahi.
        else -> Literal(text.toLong(), IntegerType, location)
    }
}

// arrayLiteral: '[' (expression (',' expression)*)? ']'
override fun visitArrayLiteral(ctx: ArrayLiteralContext): Node =
    ArrayLiteral(
        elements = ctx.expression().map { visit(it) as Expression },
        location = locationOf(ctx)
    )
```

**Cuidado con `ctx.text` fuera de este método.** Concatena los tokens **sin
espacios**, porque `WS` lleva `-> skip` en la gramática y esos caracteres nunca
entraron al stream:

```kotlin
ctx.text   // "letx:integer=5;"   y no  "let x: integer = 5;"
```

Aquí funciona porque un literal es siempre **un** token. Para todo lo demás hay que
pedir el token puntual (`ctx.Identifier().text`).

### Los tres átomos

```kotlin
// primaryAtom: Identifier   # IdentifierExpr
override fun visitIdentifierExpr(ctx: IdentifierExprContext): Node =
    Identifier(ctx.Identifier().text, locationOf(ctx))

// primaryAtom: 'new' Identifier '(' arguments? ')'   # NewExpr
override fun visitNewExpr(ctx: NewExprContext): Node =
    ObjectCreation(
        className = ctx.Identifier().text,
        arguments = buildArguments(ctx.arguments()),
        location = locationOf(ctx)
    )

// primaryAtom: 'this'   # ThisExpr
override fun visitThisExpr(ctx: ThisExprContext): Node =
    ThisReference(locationOf(ctx))
```

### Aceptación

- `3 + 5 * 2` produce `BinaryOperation(Literal(3), ADD, BinaryOperation(Literal(5), MULTIPLY, Literal(2)))`.
- `10 - 3 - 2` produce `BinaryOperation(BinaryOperation(10, SUBTRACT, 3), SUBTRACT, 2)`. **Este es el
  test más importante del ticket**: si el plegado sale al revés, el árbol es
  `BinaryOperation(10, SUBTRACT, BinaryOperation(3, SUBTRACT, 2))` y el programa daría 9 en vez de 5.
- `2 / 4 / 8` produce el mismo patrón de plegado a la izquierda.
- `x` produce **un** nodo `Identifier`, no once nodos encadenados.
- `a ? b : c ? d : e` produce `TernaryOperation(a, b, TernaryOperation(c, d, e))` (asociativa derecha).
- `perro.hablar()` produce `FunctionCall(PropertyAccess(Identifier("perro"), "hablar"), [])`.
- `lista[0][1]` produce `IndexAccess(IndexAccess(Identifier("lista"), 0), 1)`.
- `-x` produce `UnaryOperation(NEGATE, Identifier("x"))`.
- `!!x` produce `UnaryOperation(NOT, UnaryOperation(NOT, Identifier("x")))`.
- `3.14` produce `Literal(3.14, FloatType)`; `3` produce `Literal(3L, IntegerType)`.
- `p && q && r` produce `BinaryOperation(BinaryOperation(p, AND, q), AND, r)`. El
  valor sería el mismo plegando a la derecha; **este test protege el orden de
  evaluación** del cortocircuito de la Fase 6.
- `a || b && c` produce `BinaryOperation(a, OR, BinaryOperation(b, AND, c))`: la
  precedencia sale de la torre, no del plegado.
- `(1 + 2) * 3` produce `BinaryOperation(BinaryOperation(1, ADD, 2), MULTIPLY, 3)`.
  **No hay nodo de paréntesis en el AST.**
- `a = b = c` produce `AssignmentExpression(a, AssignmentExpression(b, c))`
  (asociativa derecha, gratis por la gramática).
- `obj.prop = 5` produce `AssignmentExpression(PropertyAccess(obj, "prop"), 5)`.
- `new Perro("Toby")` produce `ObjectCreation("Perro", [Literal("Toby")])`;
  `new Animal()` produce `ObjectCreation("Animal", [])`.
- `this` produce `ThisReference`.
- `[]` produce `ArrayLiteral(emptyList())`.
- Cada nodo tiene la `location` de su primer token, con columna 1-based.

### Respaldo

Dragon Book §5.3.1 (Ejemplo 5.11: construcción de un árbol sintáctico abstracto).
`Compiscript.g4`, reglas de expresión.

---

## Ticket 2.3 — `AstBuilder`: sentencias y declaraciones

- **Estado**: pendiente
- **Depende de**: 1.5, 2.2

**Archivos:**

- `frontend/ast/AstBuilder.kt` (continuar — parte de sentencias)
- `app/src/test/kotlin/org/compiler/AstBuilderStmtTest.kt` (NUEVO)

**Qué se hace:** una función por cada regla de sentencia. Son muchos casos pero
todos parecidos: leer los hijos, descartar la puntuación, construir el nodo.

### `TypeReference`: de la sintaxis escrita al nodo

```antlr
type: baseType ('[' ']')*
baseType: 'boolean' | 'integer' | 'float' | 'string' | Identifier
```

```kotlin
// "integer[][]" -> TypeReference("integer", 2)
//
// Las dimensiones son la cantidad de pares '[' ']'. Como cada par son DOS hijos
// terminales, se divide entre 2.
//
// El /2 funciona porque `baseType` es una REGLA, no un token: los unicos hijos
// terminales de `type` son los corchetes.
private fun buildTypeReference(ctx: TypeContext): TypeReference {
    val bracketPairs = (0 until ctx.childCount)
        .map { ctx.getChild(it) }
        .filterIsInstance<TerminalNode>()
        .count() / 2

    return TypeReference(
        // ctx.baseType().text es seguro aqui: baseType es siempre UN token, sea
        // 'integer' o un Identifier. La advertencia sobre ctx.text aplica a nodos
        // con varios hijos, no a este.
        baseName = ctx.baseType().text,
        arrayDimensions = bracketPairs,
        location = locationOf(ctx)
    )
}

// parameter: Identifier (':' type)?
//
// Es un ayudante y no un visitor porque Parameter no es Statement ni Expression: es
// un Node aparte, y el unico que lo construye es visitFunctionDeclaration.
private fun buildParameter(ctx: ParameterContext): Parameter =
    Parameter(
        name = ctx.Identifier().text,
        declaredType = ctx.type()?.let { buildTypeReference(it) },   // null si no se anoto
        location = locationOf(ctx)
    )
```

**Por qué `TypeReference` guarda lo escrito y no un `Type` resuelto.** La tentación es
resolver aquí mismo. No se puede, porque Compiscript permite esto:

```cps
let p: Perro = new Perro();            // usa Perro...
class Perro { let nombre: string; }    // ...declarada mas abajo
```

Si el `AstBuilder` resolviera `Perro`, tendría que reportar *"el tipo Perro no
existe"* — **y sería falso**: todavía no vio la línea 2. El AST guarda lo escrito y la
Fase 3 lo resuelve, o reporta el error si de verdad no existe. **Esa separación es lo
que habilita las referencias adelantadas.**

### Declaraciones

```kotlin
// variableDeclaration: ('let' | 'var') Identifier typeAnnotation? initializer? ';'
override fun visitVariableDeclaration(ctx: VariableDeclarationContext): Node =
    VariableDeclaration(
        name = ctx.Identifier().text,
        declaredType = ctx.typeAnnotation()?.let { buildTypeReference(it.type()) },
        initializer = ctx.initializer()?.let { visit(it.expression()) as Expression },
        isConstant = false,
        location = locationOf(ctx)
    )

// constantDeclaration: 'const' Identifier typeAnnotation? '=' expression ';'
//
// Mismo nodo que la variable, con isConstant = true. La `const` SIEMPRE tiene
// inicializador: la gramática lo exige, no hace falta validarlo aquí.
override fun visitConstantDeclaration(ctx: ConstantDeclarationContext): Node =
    VariableDeclaration(
        name = ctx.Identifier().text,
        declaredType = ctx.typeAnnotation()?.let { buildTypeReference(it.type()) },
        initializer = visit(ctx.expression()) as Expression,
        isConstant = true,
        location = locationOf(ctx)
    )

// functionDeclaration: 'function' Identifier '(' parameters? ')' (':' type)? block
override fun visitFunctionDeclaration(ctx: FunctionDeclarationContext): Node =
    FunctionDeclaration(
        name = ctx.Identifier().text,
        parameters = ctx.parameters()?.parameter()?.map { buildParameter(it) } ?: emptyList(),
        returnType = ctx.type()?.let { buildTypeReference(it) },
        body = visit(ctx.block()) as Block,
        location = locationOf(ctx)
    )

// classDeclaration: 'class' Identifier (':' Identifier)? '{' classMember* '}'
//
// El segundo Identifier, si existe, es la superclase.
override fun visitClassDeclaration(ctx: ClassDeclarationContext): Node {
    val identifiers = ctx.Identifier()
    return ClassDeclaration(
        name = identifiers[0].text,
        superclassName = identifiers.getOrNull(1)?.text,
        members = ctx.classMember().map { visit(it) as Statement },
        location = locationOf(ctx)
    )
}
```

### El detalle de la asignación: la gramática tiene dos caminos

```antlr
statement: ... | assignment | ... | expressionStatement | ...

assignment
  : Identifier '=' expression ';'
  | expression '.' Identifier '=' expression ';'

expressionStatement: expression ';'
```

`x = 5;` puede parsear por `assignment` **o** por `expressionStatement` conteniendo
un `AssignmentExpression`. ANTLR resuelve a favor de `assignment` porque aparece primero en
la lista de alternativas de `statement`.

Pero `lista[0] = 5;` **no** coincide con `assignment` (exige un `Identifier` pelado
como lado izquierdo, no `lista[0]`), así que cae por `expressionStatement` →
`assignmentExpr` → `AssignmentExpression` con `lhs = lista[0]`.

**Consecuencia: hay que manejar los dos caminos y normalizarlos al mismo nodo.** Si
solo se maneja uno, la asignación indexada o la simple se cae en silencio, y es un
bug que aparece tarde.

```kotlin
// assignment (la regla de statement): dos formas.
override fun visitAssignment(ctx: AssignmentContext): Node {
    val location = locationOf(ctx)

    // Forma 1:  Identifier '=' expression ';'
    //           (un solo `expression` hijo y ningún '.')
    if (ctx.expression().size == 1) {
        return Assignment(
            target = Identifier(ctx.Identifier().text, location),
            value = visit(ctx.expression(0)) as Expression,
            location = location
        )
    }

    // Forma 2:  expression '.' Identifier '=' expression ';'
    return Assignment(
        target = PropertyAccess(
            target = visit(ctx.expression(0)) as Expression,
            propertyName = ctx.Identifier().text,
            location = location
        ),
        value = visit(ctx.expression(1)) as Expression,
        location = location
    )
}

// expressionStatement: expression ';'
//
// Si la expresion resulta ser una asignacion, se DESARMA y se rearma como el mismo
// nodo Assignment que produce la regla `assignment`. Asi el AST tiene una sola forma
// de asignar A NIVEL DE SENTENCIA, sin importar por que camino de la gramatica llego.
//
// AssignmentExpression sigue existiendo para el caso ANIDADO —`let y = (x = 5);`,
// `if (x = 1)`—, asi que la Fase 4 tiene las dos funciones. Las tres reglas comunes
// (lvalue, constante, asignabilidad) viven en un ayudante compartido; ver ticket 4.4.
override fun visitExpressionStatement(ctx: ExpressionStatementContext): Node {
    val expr = visit(ctx.expression()) as Expression
    val location = locationOf(ctx)

    return if (expr is AssignmentExpression) {
        Assignment(target = expr.target, value = expr.value, location = location)
    } else {
        ExpressionStatement(expr = expr, location = location)
    }
}
```

### Control de flujo

```kotlin
// ifStatement: 'if' '(' expression ')' block ('else' block)?
override fun visitIfStatement(ctx: IfStatementContext): Node =
    If(
        condition = visit(ctx.expression()) as Expression,
        thenBranch = visit(ctx.block(0)) as Block,
        elseBranch = ctx.block(1)?.let { visit(it) as Block },
        location = locationOf(ctx)
    )

// whileStatement: 'while' '(' expression ')' block
override fun visitWhileStatement(ctx: WhileStatementContext): Node =
    While(
        condition = visit(ctx.expression()) as Expression,
        body = visit(ctx.block()) as Block,
        location = locationOf(ctx)
    )

// doWhileStatement: 'do' block 'while' '(' expression ')' ';'
override fun visitDoWhileStatement(ctx: DoWhileStatementContext): Node =
    DoWhile(
        body = visit(ctx.block()) as Block,
        condition = visit(ctx.expression()) as Expression,
        location = locationOf(ctx)
    )
```

El `for` merece un comentario, porque su gramática es engañosa:

```antlr
forStatement
  : 'for' '(' (variableDeclaration | assignment | ';') expression? ';' expression? ')' block
```

La primera alternativa (`variableDeclaration` o `assignment`) **ya incluye su
propio `;`**, así que en la regla solo aparece un `';'` explícito. Un `for`
completo tiene dos puntos y comas en total:

```cps
for (let i: integer = 0; i < 3; i = i + 1) { ... }
//                     ^ del variableDeclaration
//                              ^ el explícito de la regla
```

```kotlin
override fun visitForStatement(ctx: ForStatementContext): Node {
    // El inicializador es opcional: si es solo ';', ambos hijos vienen nulos.
    val initializer: Statement? = when {
        ctx.variableDeclaration() != null -> visit(ctx.variableDeclaration()) as Statement
        ctx.assignment() != null          -> visit(ctx.assignment()) as Statement
        else                              -> null
    }

    // Las dos expresiones opcionales son, en orden: condición y actualización.
    // Si solo viene una, la LISTA no dice cuál es. Se resuelve por posición del
    // token: la que está antes del ';' separador es la condición.
    //
    // El ';' separador es SIEMPRE el último ';' hijo directo de forStatement:
    //   for (let i = 0; i < 3; i++)   -> hijos ';': uno (el separador)
    //   for (; i < 3; i++)            -> hijos ';': dos (el del init vacío y el separador)
    val separatorIndex = ctx.children
        .filterIsInstance<TerminalNode>()
        .last { it.text == ";" }
        .symbol.tokenIndex

    val expressions = ctx.expression()

    return For(
        initializer = initializer,
        condition = expressions.firstOrNull { it.start.tokenIndex < separatorIndex }
            ?.let { visit(it) as Expression },
        update = expressions.firstOrNull { it.start.tokenIndex > separatorIndex }
            ?.let { visit(it) as Expression },
        body = visit(ctx.block()) as Block,
        location = locationOf(ctx)
    )
}
```

**Por qué la posición del token y no el índice de la lista:** en `for (;; i = i + 1)`
solo viene una expresión, y es la actualización, no la condición. Si se asumiera
"`expression(0)` es la condición", el programa se interpretaría al revés: el
compilador creería que `i = i + 1` es la condición del ciclo. Comparar contra la
posición del `;` separador lo resuelve sin ambigüedad.

Es el único lugar del AstBuilder donde hay que mirar posiciones de tokens, y existe
porque la gramática mete el `;` del inicializador dentro de la alternativa.

### El resto de las sentencias

```kotlin
// foreachStatement: 'foreach' '(' Identifier 'in' expression ')' block
override fun visitForeachStatement(ctx: ForeachStatementContext): Node =
    ForEach(
        variableName = ctx.Identifier().text,
        iterable = visit(ctx.expression()) as Expression,
        body = visit(ctx.block()) as Block,
        location = locationOf(ctx)
    )

// switchStatement: 'switch' '(' expression ')' '{' switchCase* defaultCase? '}'
override fun visitSwitchStatement(ctx: SwitchStatementContext): Node =
    Switch(
        subject = visit(ctx.expression()) as Expression,
        cases = ctx.switchCase().map { visit(it) as SwitchCase },

        // El `default` NO necesita su propio visitor: aqui se leen sus statements
        // directo. El `?` es lo que distingue "sin default" (null) de "default
        // vacio" (lista vacia), y la Fase 5 usa esa diferencia para decidir si un
        // switch garantiza retorno en todos los caminos.
        defaultBody = ctx.defaultCase()?.statement()?.map { visit(it) as Statement },
        location = locationOf(ctx)
    )

// switchCase: 'case' expression ':' statement*
//
// HACE FALTA sobrescribirlo. Sin este metodo corre el default, y visitChildren
// devuelve el ULTIMO `statement` del cuerpo: un Statement, no un SwitchCase. El
// `as SwitchCase` del llamador lanzaria ClassCastException en ejecucion.
//
// El cuerpo es List<Statement> y no un Block porque la gramatica no le pone llaves
// al case. Por eso un `case` no abre ambito por si mismo: lo abre el TypeChecker de
// la Fase 4, con el nombre "case@N".
override fun visitSwitchCase(ctx: SwitchCaseContext): Node =
    SwitchCase(
        value = visit(ctx.expression()) as Expression,
        body = ctx.statement().map { visit(it) as Statement },
        location = locationOf(ctx)
    )

// tryCatchStatement: 'try' block 'catch' '(' Identifier ')' block
override fun visitTryCatchStatement(ctx: TryCatchStatementContext): Node =
    TryCatch(
        tryBlock = visit(ctx.block(0)) as Block,
        catchParameterName = ctx.Identifier().text,
        catchBlock = visit(ctx.block(1)) as Block,
        location = locationOf(ctx)
    )

// printStatement: 'print' '(' expression ')' ';'
override fun visitPrintStatement(ctx: PrintStatementContext): Node =
    Print(visit(ctx.expression()) as Expression, locationOf(ctx))

// returnStatement: 'return' expression? ';'
override fun visitReturnStatement(ctx: ReturnStatementContext): Node =
    Return(ctx.expression()?.let { visit(it) as Expression }, locationOf(ctx))

override fun visitBreakStatement(ctx: BreakStatementContext): Node =
    Break(locationOf(ctx))

override fun visitContinueStatement(ctx: ContinueStatementContext): Node =
    Continue(locationOf(ctx))

// block: '{' statement* '}'
override fun visitBlock(ctx: BlockContext): Node =
    Block(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))

// program: statement* EOF
override fun visitProgram(ctx: ProgramContext): Node =
    Program(ctx.statement().map { visit(it) as Statement }, locationOf(ctx))
```

### Aceptación

- Se construye el AST completo del programa por defecto del IDE y todos los nodos
  son de la clase esperada.
- `x = 5;` y `lista[0] = 5;` **ambos** producen un nodo `Assignment` (uno con
  `target = Identifier`, el otro con `target = IndexAccess`).
- `perro.nombre = "Toby";` produce `Assignment(PropertyAccess(...), Literal(...))`.
- `for (let i: integer = 0; i < 3; i = i + 1)` produce un `For` con los tres
  campos poblados.
- `for (;; i = i + 1)` produce un `For` con `condition = null` y `update` poblado.
  **Este es el test que atrapa el error de posición de tokens.**
- `for (let i: integer = 0;;)` produce `condition = null` y `update = null`.
- `for (; i < 3; i = i + 1)` produce `initializer = null` con los otros dos
  poblados: verifica que el `;` del inicializador vacío no se confunda con el
  separador.
- `let x;` produce `VariableDeclaration(declaredType = null, initializer = null)`.
- `const PI: integer = 314;` produce `VariableDeclaration(isConstant = true)`.
- `class Perro : Animal { }` produce `ClassDeclaration(superclassName = "Animal")`.
- `class Animal { }` produce `ClassDeclaration(superclassName = null)`.
- `integer[][]` produce `TypeReference("integer", 2)`; `Perro` produce
  `TypeReference("Perro", 0)`.
- `function f(a: integer, b)` produce dos `Parameter`, el segundo con
  `declaredType = null`.
- `switch (x) { case 1: print(1); case 2: }` produce dos `SwitchCase`, el segundo
  con `body` vacío. **Este test atrapa el `visitSwitchCase` faltante**: sin él el
  `as SwitchCase` lanza `ClassCastException`.
- `switch (x) { }` produce `defaultBody = null`;
  `switch (x) { default: }` produce `defaultBody = emptyList()`. La Fase 5 usa esa
  diferencia.
- El AST de un programa de 40 líneas se imprime indentado y se lee como el
  programa original.

### Respaldo

Dragon Book §5.3.1. `Compiscript.g4`, todas las reglas de `statement`.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 2.1 | `.cps` → árbol de ANTLR, con errores léxicos y sintácticos en `Diagnostics` |
| 2.2 | Las 12 expresiones, con plegado a la izquierda y la torre de precedencia colapsada |
| 2.3 | Las 17 sentencias, con los dos caminos de asignación normalizados |

**Al terminar:** `SyntaxAnalyzer.parse(...)` + `AstBuilder().visit(tree)` convierten
un archivo `.cps` en un `Program` limpio. **Es la frontera con ANTLR: nada después
de esta fase importa clases del paquete `org.compiler.parser`.**
