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

script: sentencia* EOF;

// Tipos -------------------------------------------------------------------

tipo
  : ('INT' | 'INTEGER')                          # tipoEntero
  | 'FLOAT'                                      # tipoFlotante
  | ('DECIMAL' | 'NUMERIC') '(' EnteroLit ',' EnteroLit ')'  # tipoDecimal
  | 'CHAR' '(' EnteroLit ')'                     # tipoChar
  | 'VARCHAR' '(' EnteroLit ')'                  # tipoVarchar
  | 'TEXT'                                       # tipoTexto
  | 'DATE'                                       # tipoFecha
  | 'TIME'                                       # tipoHora
  | 'BOOLEAN'                                    # tipoLogico
  ;

// Literales ---------------------------------------------------------------

literal
  : EnteroLit          # litEntero
  | DecimalLit         # litDecimal
  | TextoLit           # litTexto
  | 'DATE' TextoLit    # litFecha
  | 'TIME' TextoLit    # litHora
  | ('TRUE' | 'FALSE') # litLogico
  | 'NULL'             # litNulo
  ;

EnteroLit : [0-9]+ ;
DecimalLit : [0-9]+ '.' [0-9]+ ;
TextoLit : '\'' ( ~'\'' | '\'\'' )* '\'' ;
Identificador : [a-zA-Z_][a-zA-Z0-9_]* ;

WS : [ \t\r\n]+ -> skip ;
COMENTARIO : '--' ~[\r\n]* -> skip ;
COMENTARIO_BLOQUE : '/*' .*? '*/' -> skip ;
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
sentencia
  : crearTabla ';'  | alterarTabla ';' | borrarTabla ';'
  | insertar ';'    | actualizar ';'   | borrar ';'
  | consulta ';'
  ;

// DDL ---------------------------------------------------------------------

crearTabla
  : 'CREATE' 'TABLE' Identificador '(' definicionColumna (',' definicionColumna)* ')'
  ;

definicionColumna : Identificador tipo restriccion* ;

restriccion
  : 'PRIMARY' 'KEY'                                          # rPrimaryKey
  | 'NOT' 'NULL'                                             # rNotNull
  | 'NULL'                                                   # rNull
  | 'UNIQUE'                                                 # rUnique
  | 'AUTOINCREMENT'                                          # rAutoIncrement
  | 'DEFAULT' literal                                        # rDefault
  | 'REFERENCES' Identificador '(' Identificador ')'         # rForeignKey
  ;

alterarTabla
  : 'ALTER' 'TABLE' Identificador 'ADD' definicionColumna    # alterAdd
  | 'ALTER' 'TABLE' Identificador 'DROP' 'COLUMN' Identificador  # alterDrop
  ;

borrarTabla : 'DROP' 'TABLE' Identificador ;

// DML ---------------------------------------------------------------------

insertar
  : 'INSERT' 'INTO' Identificador ('(' listaColumnas ')')? 'VALUES' filaValores (',' filaValores)*
  ;

filaValores : '(' expresion (',' expresion)* ')' ;
listaColumnas : Identificador (',' Identificador)* ;

actualizar
  : 'UPDATE' Identificador 'SET' asignacion (',' asignacion)* ('WHERE' expresion)?
  ;

asignacion : Identificador '=' expresion ;

borrar : 'DELETE' 'FROM' Identificador ('WHERE' expresion)? ;
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
consulta
  : 'SELECT' 'DISTINCT'? listaSeleccion
    'FROM' origen (join)*
    ('WHERE' expresion)?
    ('GROUP' 'BY' listaExpresiones)?
    ('HAVING' expresion)?
    ('ORDER' 'BY' criterioOrden (',' criterioOrden)*)?
    ('LIMIT' EnteroLit)?
  ;

listaSeleccion
  : '*'                                                # seleccionTodo
  | elementoSeleccion (',' elementoSeleccion)*         # seleccionLista
  ;

elementoSeleccion
  : Identificador '.' '*'                              # elemTablaTodo
  | expresion ('AS'? Identificador)?                   # elemExpresion
  ;

origen
  : Identificador ('AS'? Identificador)?               # origenTabla
  | '(' consulta ')' 'AS'? Identificador               # origenDerivado
  ;

join : 'INNER'? 'JOIN' origen 'ON' expresion ;

criterioOrden : expresion ('ASC' | 'DESC')? ;
listaExpresiones : expresion (',' expresion)* ;
```

### La torre de precedencia

```antlr
expresion      : expresionOr ;
expresionOr    : expresionAnd ('OR' expresionAnd)* ;
expresionAnd   : expresionNot ('AND' expresionNot)* ;
expresionNot   : 'NOT'? expresionComparacion ;

expresionComparacion
  : expresionAditiva (opComparacion expresionAditiva)?       # compBinaria
  | expresionAditiva 'IS' 'NOT'? 'NULL'                      # compEsNulo
  | expresionAditiva 'NOT'? 'IN' '(' consulta ')'            # compEnSubconsulta
  | expresionAditiva 'NOT'? 'IN' '(' listaExpresiones ')'    # compEnLista
  | 'EXISTS' '(' consulta ')'                                # compExiste
  ;

expresionAditiva       : expresionMultiplicativa (('+' | '-' | '||') expresionMultiplicativa)* ;
expresionMultiplicativa: expresionUnaria (('*' | '/') expresionUnaria)* ;
expresionUnaria        : '-'? expresionPrimaria ;

expresionPrimaria
  : literal                                            # primLiteral
  | Identificador '.' Identificador                    # primColumnaCalificada
  | Identificador                                      # primColumna
  | agregacion                                         # primAgregacion
  | '(' consulta ')'                                   # primSubconsulta
  | '(' expresion ')'                                  # primParentesis
  ;

agregacion
  : 'COUNT' '(' '*' ')'                                # agCountTodo
  | ('COUNT'|'SUM'|'AVG'|'MIN'|'MAX') '(' 'DISTINCT'? expresion ')'  # agFuncion
  ;

opComparacion : '=' | '<>' | '!=' | '<' | '>' | '<=' | '>=' ;
```

### La recursión que hay que entender

`expresionPrimaria` puede ser `'(' consulta ')'`, y `consulta` contiene
`expresion`. Ese ciclo es lo que hace posibles las subconsultas anidadas a
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
- `frontend/syntax/MayusculasCharStream.kt` (NUEVO)
- `frontend/ast/models/TreeNodeView.kt` (NUEVO)
- `frontend/syntax/ParseTreeView.kt` (NUEVO)
- `app/src/test/.../SqlSyntaxAnalyzerTest.kt` (NUEVO)

```kotlin
object SqlSyntaxAnalyzer {
    // Devuelve null si hubo error de sintaxis: es la unica etapa que corta.
    fun parse(fuente: String, diagnostics: Diagnostics): SqlParser.ScriptContext?
}
```

Reusa `DiagnosticsErrorListener` tal cual, quitando los listeners por omisión de
ANTLR, que escriben a la consola.

`MayusculasCharStream` envuelve el `CharStream` y devuelve mayúsculas en `LA()`,
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
    val hijos: List<TreeNodeView> = emptyList()
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
- **Depende de**: 3.3, 1.1

**Archivos:**

- `frontend/ast/models/Sentencia.kt`, `Consulta.kt`, `Expresion.kt` (NUEVOS)
- `app/src/test/.../AstModelsTest.kt` (NUEVO)

```kotlin
sealed interface Nodo { val location: LexemeLocation }

// El nodo RAIZ: lo que devuelve el AstBuilder y lo que recorre todo lo demas.
class Script(
    val sentencias: List<Sentencia>,
    override val location: LexemeLocation
) : Nodo

sealed interface Sentencia : Nodo
class CrearTabla(val nombre: String, val columnas: List<DefinicionColumna>, ...) : Sentencia
class AlterarTablaAgregar(...) : Sentencia
class AlterarTablaQuitar(...) : Sentencia
class BorrarTabla(...) : Sentencia
class Insertar(val tabla: String, val columnas: List<String>?, val filas: List<List<Expresion>>, ...) : Sentencia
class Actualizar(val tabla: String, val asignaciones: List<Asignacion>, val donde: Expresion?, ...) : Sentencia
class Borrar(val tabla: String, val donde: Expresion?, ...) : Sentencia

class Consulta(
    val distinto: Boolean,
    val seleccion: List<ElementoSeleccion>,
    val origen: Origen,
    val joins: List<Join>,
    val donde: Expresion?,
    val agruparPor: List<Expresion>,
    val teniendo: Expresion?,
    val ordenarPor: List<CriterioOrden>,
    val limite: Int?,
    override val location: LexemeLocation
) : Sentencia, Expresion {
    // Lo llena la fase 4.
    var ambito: Scope? = null
    var correlacionada: Boolean = false
}
```

### La lista completa de nodos

Arriba van los de sentencia. Estos son los que faltan, y **ninguno se puede
elidir**: el `SqlAstBuilder` del ticket 3.6 construye exactamente esta lista y no
hay otra fuente que la diga.

```kotlin
// Piezas de las sentencias -----------------------------------------------

class DefinicionColumna(val nombre: String, val tipo: Type,
                        val restricciones: List<Restriccion>, ...) : Nodo
class Asignacion(val columna: String, val valor: Expresion, ...) : Nodo

// Piezas de la consulta ---------------------------------------------------

sealed interface Origen : Nodo
class OrigenTabla(val tabla: String, val alias: String?, ...) : Origen
class OrigenDerivado(val consulta: Consulta, val alias: String, ...) : Origen

class Join(val origen: Origen, val condicion: Expresion, ...) : Nodo

sealed interface ElementoSeleccion : Nodo
data object SeleccionTodo : ElementoSeleccion
class SeleccionTablaTodo(val alias: String, ...) : ElementoSeleccion
class SeleccionExpresion(val expresion: Expresion, val alias: String?, ...) : ElementoSeleccion

class CriterioOrden(val expresion: Expresion, val descendente: Boolean, ...) : Nodo

// Expresiones -------------------------------------------------------------

sealed interface Expresion : Nodo {
    // Lo llena la fase 5. Arranca en ErrorType para que un nodo sin tipar
    // no se confunda con uno bien tipado.
    var tipo: Type
}

class Literal(val valor: Valor, ...) : Expresion
class ReferenciaColumna(...) : Expresion          // ver ticket 4.3
class Binaria(val operador: OperadorBinario, val izquierda: Expresion,
              val derecha: Expresion, ...) : Expresion
class Unaria(val operador: OperadorUnario, val operando: Expresion, ...) : Expresion
class EsNulo(val operando: Expresion, val negado: Boolean, ...) : Expresion
class EnLista(val operando: Expresion, val valores: List<Expresion>,
              val negado: Boolean, ...) : Expresion
class EnSubconsulta(val operando: Expresion, val consulta: Consulta,
                    val negado: Boolean, ...) : Expresion
class Existe(val consulta: Consulta, ...) : Expresion
class Agregacion(val funcion: FuncionAgregada, val argumento: Expresion?,
                 val distinto: Boolean, ...) : Expresion

enum class FuncionAgregada { COUNT, COUNT_TODO, SUM, AVG, MIN, MAX }
enum class OperadorBinario { SUMA, RESTA, MULT, DIV, CONCAT, IGUAL, DISTINTO,
                             MENOR, MAYOR, MENOR_IGUAL, MAYOR_IGUAL, Y, O }
enum class OperadorUnario { NEGATIVO, NO }
```

**`Agregacion.argumento` es nulable** porque `COUNT(*)` no tiene ninguno. Se
distingue de `COUNT(col)` por `funcion`, no por el nulo, para que el `when` de la
fase 6 sea exhaustivo.

**`DefinicionColumna` guarda un `Type` ya resuelto y no texto**, porque el tipo se
escribe completo en el `CREATE` y no hay nada que resolver después. Es la
diferencia con `TypeReference` de Compiscript, donde un nombre de clase podía
apuntar a algo declarado más abajo.

### Los campos mutables y quién los llena

Igual que en Compiscript: los `val` son lo que se sabe al parsear, los `var` son
lo que se descubre analizando.

| Campo | Lo llena | Se lee en |
|---|---|---|
| `Expresion.tipo` | fase 5 | fase 6 |
| `ReferenciaColumna.nivel` e `.indice` | fase 4 | fase 6 |
| `Consulta.ambito` | fase 4 | fases 5 y 6 |
| `Consulta.correlacionada` | fase 4 | fase 6, para cachear |

**Que `Consulta` sea a la vez `Sentencia` y `Expresion`** es lo que permite que
una subconsulta aparezca donde va una expresión sin duplicar el nodo. Es el mismo
truco que en Compiscript hacía que la asignación fuera expresión.

**Aceptación:** los nodos existen, un `when` sobre `Sentencia` sin `else` compila,
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

`SqlAstBuilder` hereda de `SqlBaseVisitor<Nodo>` y tiene un método por etiqueta
`#` de la gramática. El colapso importante es el de la torre de precedencia: siete
reglas de ANTLR producen un solo tipo de nodo binario, con `foldBinaryLeft`, igual
que en Compiscript.

### El pipeline mínimo

```kotlin
object DbmsPipeline {
    fun ejecutar(fuente: String, escribir: Boolean = true): CompilationResult {
        val diagnostics = Diagnostics()

        val parseTree = SqlSyntaxAnalyzer.parse(fuente, diagnostics)
            ?: return CompilationResult.fallida(diagnostics, fuente)
        val parseTreeView = parseTree.toTreeView()

        val ast = SqlAstBuilder().visit(parseTree) as Script

        // Las etapas C a F se conectan en las fases 4, 5, 6 y 7.

        return CompilationResult(fuente, parseTreeView, ast, diagnostics.all(), null, null)
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
