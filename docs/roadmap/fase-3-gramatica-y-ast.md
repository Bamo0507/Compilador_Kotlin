# Fase 3 · Gramática y AST

**Objetivo:** que todo el SQL del alcance parsee, que los errores de sintaxis
apunten a la línea correcta, y que salga un AST limpio.

**Es el artefacto individual más grande del proyecto**, por dos razones: la
recursión de las subconsultas hace que una expresión pueda contener una consulta
entera, y la torre de precedencia de expresiones tiene siete niveles.

**Lo que hace única a esta fase:** no toca el disco. **Se puede hacer en paralelo
con la fase 2.**

**Estimación:** tres o cuatro sesiones.

---

## Ticket 3.1 · `Sql.g4` parte 1, léxico y tipos

- **Estado**: pendiente
- **Depende de**: 0.5

**Archivos:**

- `app/src/main/antlr/Sql.g4` (MODIFICA)

### Lo que entra

```antlr
grammar Sql;

script: statement* EOF;

// Tipos -------------------------------------------------------------------

type
  : ('INT' | 'INTEGER')                          # intType
  | 'FLOAT'                                      # floatType
  | ('DECIMAL' | 'NUMERIC') '(' IntegerLit ',' IntegerLit ')'  # decimalType
  | 'CHAR' '(' IntegerLit ')'                     # charType
  | 'VARCHAR' '(' IntegerLit ')'                  # varcharType
  | 'TEXT'                                       # textType
  | 'DATE'                                       # dateType
  | 'TIME'                                       # timeType
  | 'BOOLEAN'                                    # boolType
  ;

// Literales ---------------------------------------------------------------

literal
  : IntegerLit          # intLit
  | DecimalLit         # decimalLit
  | StringLit           # stringLit
  | 'DATE' StringLit    # dateLit
  | 'TIME' StringLit    # timeLit
  | ('TRUE' | 'FALSE') # boolLit
  | 'NULL'             # nullLit
  ;

IntegerLit : [0-9]+ ;
DecimalLit : [0-9]+ '.' [0-9]+ ;
StringLit : '\'' ( ~'\'' | '\'\'' )* '\'' ;
Identifier : [a-zA-Z_][a-zA-Z0-9_]* ;

WS : [ \t\r\n]+ -> skip ;
COMMENT : '--' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
```

### Decisión · palabras clave insensibles a mayúsculas

SQL no distingue `select` de `SELECT`. ANTLR sí, por omisión. La forma limpia es
un `CharStream` que devuelve todo en mayúsculas al lexer pero conserva el texto
original para los mensajes de error. Se implementa en el ticket 3.4 como una
subclase corta de `CharStreams`.

**Por qué ahí y no con reglas del tipo `S E L E C T`:** esa técnica llena la
gramática de ruido y hace ilegible el archivo que es la fuente de verdad del
sintáctico.

### Decisión · literales de fecha con palabra clave

```sql
INSERT INTO eventos VALUES (1, DATE '2026-09-23', TIME '14:30:00');
```

La alternativa es inferir del contexto que `'2026-09-23'` es una fecha. No se
hace, porque entonces `WHERE fecha > '2026-01-01'` y `WHERE nombre > '2026-01-01'`
se parsean igual y significan cosas distintas, y el error saldría hasta el
semántico con un mensaje peor.

### Decisión · la comilla simple se escapa duplicándola

`'dijo ''hola'''`. Es lo que dice el estándar de SQL, y evita tener que decidir
qué hace la barra invertida.

**Aceptación:** `./gradlew generateGrammarSource` genera el lexer, y los 11 tipos
más los 7 literales parsean en un test de sintaxis aislado.

---

## Ticket 3.2 · `Sql.g4` parte 2, DDL y DML

- **Estado**: pendiente
- **Depende de**: 3.1

**Archivos:**

- `app/src/main/antlr/Sql.g4` (MODIFICA)

```antlr
statement
  : createTable ';'  | alterTable ';' | dropTable ';'
  | insert ';'    | update ';'   | delete ';'
  | query ';'
  ;

// El ticket 8.1 le agrega createIndex y dropIndex.

// DDL ---------------------------------------------------------------------

createTable
  : 'CREATE' 'TABLE' Identifier '(' columnDefinition (',' columnDefinition)* ')'
  ;

columnDefinition : Identifier type constraint* ;

constraint
  : 'PRIMARY' 'KEY'                                          # rPrimaryKey
  | 'NOT' 'NULL'                                             # rNotNull
  | 'NULL'                                                   # rNull
  | 'UNIQUE'                                                 # rUnique
  | 'AUTOINCREMENT'                                          # rAutoIncrement
  | 'DEFAULT' literal                                        # rDefault
  | 'REFERENCES' Identifier '(' Identifier ')'         # rForeignKey
  ;

alterTable
  : 'ALTER' 'TABLE' Identifier 'ADD' columnDefinition    # alterAdd
  | 'ALTER' 'TABLE' Identifier 'DROP' 'COLUMN' Identifier  # alterDrop
  ;

dropTable : 'DROP' 'TABLE' Identifier ;

// DML ---------------------------------------------------------------------

insert
  : 'INSERT' 'INTO' Identifier ('(' columnList ')')? 'VALUES' valuesRow (',' valuesRow)*
  ;

valuesRow : '(' expression (',' expression)* ')' ;
columnList : Identifier (',' Identifier)* ;

update
  : 'UPDATE' Identifier 'SET' assignment (',' assignment)* ('WHERE' expression)?
  ;

assignment : Identifier '=' expression ;

delete : 'DELETE' 'FROM' Identifier ('WHERE' expression)? ;
```

**Por qué `INSERT` acepta varias filas:** es gratis en la gramática y evita
repetir la sentencia en la batería de pruebas.

**Por qué las restricciones son `restriccion*` y no un conjunto:** el orden en que
se escriben no importa, pero validar que no se repitan ni se contradigan (por
ejemplo `NOT NULL` junto a `NULL`) es una regla semántica, no sintáctica. Va en la
fase 5.

**Aceptación:** las nueve sentencias de DDL y DML parsean, incluido un `CREATE`
con las siete restricciones en una sola columna.

---

## Ticket 3.3 · `Sql.g4` parte 3, consultas

- **Estado**: pendiente
- **Depende de**: 3.2

**Archivos:**

- `app/src/main/antlr/Sql.g4` (MODIFICA)

```antlr
query
  : 'SELECT' 'DISTINCT'? selectList
    'FROM' source (join)*
    ('WHERE' expression)?
    ('GROUP' 'BY' expressionList)?
    ('HAVING' expression)?
    ('ORDER' 'BY' orderCriterion (',' orderCriterion)*)?
    ('LIMIT' IntegerLit)?
  ;

selectList
  : '*'                                                # selectAll
  | selectItem (',' selectItem)*         # selectListItems
  ;

selectItem
  : Identifier '.' '*'                              # itemTableAll
  | expression ('AS'? Identifier)?                   # itemExpression
  ;

source
  : Identifier ('AS'? Identifier)?               # tableSource
  | '(' query ')' 'AS'? Identifier               # derivedSource
  ;

join : 'INNER'? 'JOIN' source 'ON' expression ;

orderCriterion : expression ('ASC' | 'DESC')? ;
expressionList : expression (',' expression)* ;
```

### La torre de precedencia

```antlr
expression      : orExpression ;
orExpression    : andExpression ('OR' andExpression)* ;
andExpression   : notExpression ('AND' notExpression)* ;
notExpression   : 'NOT'? comparisonExpression ;

comparisonExpression
  : additiveExpression (comparisonOp additiveExpression)?       # cmpBinary
  | additiveExpression 'IS' 'NOT'? 'NULL'                      # cmpIsNull
  | additiveExpression 'NOT'? 'IN' '(' query ')'            # cmpInSubquery
  | additiveExpression 'NOT'? 'IN' '(' expressionList ')'    # cmpInList
  | 'EXISTS' '(' query ')'                                # cmpExists
  ;

additiveExpression       : multiplicativeExpression (('+' | '-' | '||') multiplicativeExpression)* ;
multiplicativeExpression: unaryExpression (('*' | '/') unaryExpression)* ;
unaryExpression        : '-'? primaryExpression ;

primaryExpression
  : literal                                            # primLiteral
  | Identifier '.' Identifier                    # primQualifiedColumn
  | Identifier                                      # primColumn
  | aggregate                                         # primAggregate
  | '(' query ')'                                   # primSubquery
  | '(' expression ')'                                  # primParen
  ;

aggregate
  : 'COUNT' '(' '*' ')'                                # agCountAll
  | ('COUNT'|'SUM'|'AVG'|'MIN'|'MAX') '(' 'DISTINCT'? expression ')'  # agFunction
  ;

comparisonOp : '=' | '<>' | '!=' | '<' | '>' | '<=' | '>=' ;
```

### La recursión que hay que entender

`primaryExpression` puede ser `'(' query ')'`, y `query` contiene
`expression`. Ese ciclo es lo que hace posibles las subconsultas anidadas a
cualquier profundidad, y es lo que más complica esta fase.

ANTLR lo maneja sin ayuda porque los paréntesis desambiguan. El punto de cuidado
es `'(' consulta ')'` contra `'(' expresion ')'`: ANTLR prueba las alternativas en
orden, así que **la subconsulta va primero**. Si se pusiera al revés, nunca se
elegiría.

### Decisión · el orden de las cláusulas es fijo

`WHERE` antes de `GROUP BY` antes de `HAVING` antes de `ORDER BY`. SQL lo exige y
la gramática lo refleja, así que escribirlas al revés es un error de sintaxis con
un mensaje claro en vez de un error semántico confuso.

**Aceptación:**

- parsean: `SELECT *`, con alias, con `JOIN`, con agregación y `GROUP BY`
- parsea un `EXISTS` con tres niveles de anidamiento
- parsea una tabla derivada en el `FROM`
- `a + b * c` agrupa como `a + (b * c)`
- `NOT a = b` agrupa como `NOT (a = b)`

---

## Ticket 3.4 · `SqlSyntaxAnalyzer`

- **Estado**: pendiente
- **Depende de**: 3.3

**Archivos:**

- `frontend/syntax/SqlSyntaxAnalyzer.kt` (NUEVO)
- `frontend/syntax/UpperCaseCharStream.kt` (NUEVO)
- `frontend/ast/models/TreeNodeView.kt` (NUEVO)
- `frontend/syntax/ParseTreeView.kt` (NUEVO)
- `app/src/test/.../SqlSyntaxAnalyzerTest.kt` (NUEVO)

```kotlin
object SqlSyntaxAnalyzer {
    // Devuelve null si hubo error de sintaxis: es la unica etapa que corta.
    fun parse(source: String, diagnostics: Diagnostics): SqlParser.ScriptContext?
}
```

Reusa `DiagnosticsErrorListener` tal cual, quitando los listeners por omisión de
ANTLR, que escriben a la consola.

`UpperCaseCharStream` envuelve el `CharStream` y devuelve mayúsculas en `LA()`,
que es lo que consume el lexer, pero conserva el texto original en `getText()`,
que es lo que sale en los mensajes de error. Así `select * from Users` funciona y
el error sigue diciendo `Users` y no `USERS`.

### `TreeNodeView` y `ParseTreeView` van aquí, no en la fase 8

El pipeline del ticket 3.6 llama `parseTree.toTreeView()`, así que la conversión
tiene que existir desde esta fase. La fase 8 solo agrega el **dibujo**, que es
otra cosa.

```kotlin
// El arbol que la GUI sabe dibujar, sin saber de donde viene.
class TreeNodeView(
    val etiqueta: String,
    val detalle: String? = null,
    val children: List<TreeNodeView> = emptyList()
)

// El ProgramContext de ANTLR se convierte AQUI y no en la GUI, para que
// ningun tipo generado por ANTLR salga de frontend/.
fun ParseTree.toTreeView(): TreeNodeView
```

**Aceptación:**

- `select * from users;` y `SELECT * FROM users;` dan el mismo árbol
- un error de sintaxis reporta línea y columna correctas
- nada se escribe a la salida estándar
- `toTreeView()` sobre un `SELECT` simple da un árbol con la forma del parse tree,
  y ningún tipo de ANTLR aparece en la firma

---

## Ticket 3.5 · Los nodos del AST

- **Estado**: pendiente
- **Depende de**: 3.3, 1.1, **2.1**

`ColumnDefinition` guarda `List<Constraint>`, y `Constraint` nace en el ticket 2.1.
Es el **unico** punto donde la fase 3 toca a la fase 2, asi que el 2.1 se hace
primero y se mergea antes de partir el trabajo.

**Archivos:**

- `frontend/ast/models/Statement.kt`, `Query.kt`, `Expression.kt` (NUEVOS)
- `app/src/test/.../AstModelsTest.kt` (NUEVO)

```kotlin
sealed interface Node { val location: LexemeLocation }

// El nodo RAIZ: lo que devuelve el AstBuilder y lo que recorre todo lo demas.
class Script(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Node

sealed interface Statement : Node
class CreateTable(val name: String, val columns: List<ColumnDefinition>, ...) : Statement
class AlterTableAdd(...) : Statement
class AlterTableDrop(...) : Statement
class DropTable(...) : Statement
class Insert(val table: String, val columns: List<String>?, val rows: List<List<Expression>>, ...) : Statement
class Update(val table: String, val assignments: List<Assignment>, val where: Expression?, ...) : Statement
class Delete(val table: String, val where: Expression?, ...) : Statement

class Query(
    val distinct: Boolean,
    val selection: List<SelectItem>,
    val source: FromSource,
    val joins: List<Join>,
    val where: Expression?,
    val groupBy: List<Expression>,
    val having: Expression?,
    val orderBy: List<OrderCriterion>,
    val limit: Int?,
    override val location: LexemeLocation
) : Statement, Expression {
    // Lo llena la fase 4.
    var scope: Scope? = null
    var correlated: Boolean = false
}
```

### La lista completa de nodos

Arriba van los de sentencia. Estos son los que faltan, y **ninguno se puede
elidir**: el `SqlAstBuilder` del ticket 3.6 construye exactamente esta lista y no
hay otra fuente que la diga.

```kotlin
// Piezas de las sentencias -----------------------------------------------

class ColumnDefinition(val name: String, val type: Type,
                        val constraints: List<Constraint>, ...) : Node
class Assignment(val column: String, val value: Expression, ...) : Node

// Piezas de la consulta ---------------------------------------------------

sealed interface FromSource : Node
class TableSource(val table: String, val alias: String?, ...) : FromSource
class DerivedSource(val query: Query, val alias: String, ...) : FromSource

class Join(val source: FromSource, val condition: Expression, ...) : Node

sealed interface SelectItem : Node
data object SelectAll : SelectItem
class SelectTableAll(val alias: String, ...) : SelectItem
class SelectExpression(val expression: Expression, val alias: String?, ...) : SelectItem

class OrderCriterion(val expression: Expression, val descending: Boolean, ...) : Node

// Expresiones -------------------------------------------------------------

sealed interface Expression : Node {
    // Lo llena la fase 5. Arranca en ErrorType para que un nodo sin tipar
    // no se confunda con uno bien tipado.
    var type: Type
}

class Literal(val value: Value, ...) : Expression
class ColumnReference(...) : Expression          // ver ticket 4.3
class Binary(val operator: BinaryOperator, val izquierda: Expression,
              val derecha: Expression, ...) : Expression
class Unary(val operator: UnaryOperator, val operand: Expression, ...) : Expression
class IsNull(val operand: Expression, val negated: Boolean, ...) : Expression
class InList(val operand: Expression, val values: List<Expression>,
              val negated: Boolean, ...) : Expression
class InSubquery(val operand: Expression, val query: Query,
                    val negated: Boolean, ...) : Expression
class Exists(val query: Query, ...) : Expression
class Aggregate(val function: AggregateFunction, val argument: Expression?,
                 val distinct: Boolean, ...) : Expression

enum class AggregateFunction { COUNT, COUNT_ALL, SUM, AVG, MIN, MAX }
```

`BinaryOperator` y `UnaryOperator` **no se declaran aqui**: viven en
`types/Operators.kt` desde la fase 1, porque `TypeRules` los necesita para decidir
el tipo de una operacion. El AST los usa tal cual, y `BinaryOperator.fromSymbol`
es lo que convierte el texto del arbol de ANTLR en el valor del enum.

**`Aggregate.argumento` es nulable** porque `COUNT(*)` no tiene ninguno. Se
distingue de `COUNT(col)` por `funcion`, no por el nulo, para que el `when` de la
fase 6 sea exhaustivo.

**`ColumnDefinition` guarda un `Type` ya resuelto y no texto**, porque el tipo se
escribe completo en el `CREATE` y no hay nada que resolver después. Es la
diferencia con `TypeReference` de Compiscript, donde un nombre de clase podía
apuntar a algo declarado más abajo.

### Los campos mutables y quién los llena

Igual que en Compiscript: los `val` son lo que se sabe al parsear, los `var` son
lo que se descubre analizando.

| Campo | Lo llena | Se lee en |
|---|---|---|
| `Expression.tipo` | fase 5 | fase 6 |
| `ColumnReference.nivel` e `.indice` | fase 4 | fase 6 |
| `Query.ambito` | fase 4 | fases 5 y 6 |
| `Query.correlacionada` | fase 4 | fase 6, para cachear |

**Que `Query` sea a la vez `Statement` y `Expression`** es lo que permite que
una subconsulta aparezca donde va una expresión sin duplicar el nodo. Es el mismo
truco que en Compiscript hacía que la asignación fuera expresión.

**Aceptación:** los nodos existen, un `when` sobre `Statement` sin `else` compila,
y ningún nodo guarda un tipo de ANTLR.

---

## Ticket 3.6 · `SqlAstBuilder` y el pipeline mínimo

- **Estado**: pendiente
- **Depende de**: 3.4, 3.5

**Archivos:**

- `frontend/ast/SqlAstBuilder.kt` (NUEVO)
- `runtime/DbmsPipeline.kt` (NUEVO)
- `runtime/models/CompilationResult.kt` (MODIFICA)
- `app/src/test/.../SqlAstBuilderTest.kt`, `DbmsPipelineTest.kt` (NUEVOS)

`SqlAstBuilder` hereda de `SqlBaseVisitor<Node>` y tiene un método por etiqueta
`#` de la gramática. El colapso importante es el de la torre de precedencia: siete
reglas de ANTLR producen un solo tipo de nodo binario, con `foldBinaryLeft`, igual
que en Compiscript.

### El pipeline mínimo

```kotlin
object DbmsPipeline {
    fun run(source: String, write: Boolean = true): CompilationResult {
        val diagnostics = Diagnostics()

        val parseTree = SqlSyntaxAnalyzer.parse(source, diagnostics)
            ?: return CompilationResult.failed(diagnostics, source)
        val parseTreeView = parseTree.toTreeView()

        val ast = SqlAstBuilder().visit(parseTree) as Script

        // Las etapas C a F se conectan en las fases 4, 5, 6 y 7.

        return CompilationResult(source, parseTreeView, ast, diagnostics.all(), null, null)
    }
}
```

**Por qué el pipeline se arma ahora y no al final:** cada fase posterior conecta
su etapa y se prueba de punta a punta desde el primer día. Integrar al final es
donde aparecen los problemas caros.

**Aceptación:**

- una sentencia de cada tipo produce el nodo correcto, comparado por estructura
- `a + b * c` produce el árbol con la multiplicación adentro
- el pipeline devuelve resultado con AST y sin errores para SQL válido
- el pipeline devuelve resultado con errores y sin AST para SQL con error de
  sintaxis, y no lanza excepción
