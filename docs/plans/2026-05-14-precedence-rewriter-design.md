# Diseño del PrecedenceRewriter

**Fecha**: 2026-05-14
**Estado**: aprobado, pendiente de implementación
**Ticket**: 10 (nuevo) en el ROADMAP rediseñado.
**Depende de**: Ticket 6 (modelos Grammar extendidos con PrecedenceTable), Ticket 7 (YalpReader que parsea `%left`, `%right`, `%nonassoc`).

## 1. Motivación

Eliminar recursión por la izquierda **no es suficiente** para desambiguar una gramática que usa operadores binarios. Por ejemplo:

```
expr -> expr OP_PLUS expr
     |  expr OP_TIMES expr
     |  ID
```

aplicar el algoritmo de Dragon Book §4.3.3 produce una gramática equivalente, pero **igual de ambigua**: la cadena `a + b * c` admite dos árboles. La ambigüedad se resuelve declarando precedencia y asociatividad de los operadores y reescribiendo la gramática en niveles.

## 2. Entrada del rewriter

### 2.1 Tabla de precedencia

Se declara en el `.yalp` con sintaxis estilo Yacc/Bison:

```
%left  OP_OR                    /* nivel 0, menor precedencia */
%left  OP_AND                   /* nivel 1 */
%left  OP_EQ OP_NEQ             /* nivel 2 */
%left  OP_LT OP_GT OP_LE OP_GE  /* nivel 3 */
%left  OP_PLUS OP_MINUS         /* nivel 4 */
%left  OP_TIMES OP_DIV OP_MOD   /* nivel 5 */
%right OP_NOT                   /* nivel 6, unario prefijo */
%right OP_ASSIGN                /* nivel 7, asociatividad derecha */
```

- Cada declaración define **un nivel**.
- El **primer** `%left/%right/%nonassoc` corresponde a la **menor** precedencia.
- Operadores en el mismo nivel comparten asociatividad.

### 2.2 Modelo en código

```kotlin
enum class Associativity { LEFT, RIGHT }

data class PrecedenceLevel(
    val level: Int,
    val operators: Set<Symbol.Terminal>,
    val associativity: Associativity
)

data class Grammar(
    val terminals: Set<Symbol.Terminal>,
    val nonTerminals: Set<Symbol.NonTerminal>,
    val productions: List<Production>,
    val startSymbol: Symbol.NonTerminal,
    val ignoredTokens: Set<String>,
    val precedenceTable: List<PrecedenceLevel>
)
```

### 2.3 Gramática de entrada (ejemplo simplificado)

```
expr : expr OP_PLUS  expr
     | expr OP_TIMES expr
     | expr OP_AND   expr
     | OP_NOT expr
     | ID
     ;
```

## 3. Salida esperada del rewriter

```
expr     : or_expr ;
or_expr  : or_expr OP_OR  and_expr | and_expr ;
and_expr : and_expr OP_AND rel_expr | rel_expr ;
rel_expr : rel_expr OP_LT add_expr | add_expr ;
add_expr : add_expr OP_PLUS mul_expr | mul_expr ;
mul_expr : mul_expr OP_TIMES unary  | unary ;
unary    : OP_NOT unary | atom ;
atom     : ID ;
```

Notar:
- Un NT sintético por cada nivel de precedencia.
- La cabecera original (`expr`) queda como redirección al nivel más bajo (mayor recursión, menor precedencia).
- Las producciones no-operador (`ID`) caen al nivel atómico (`atom`).
- Operadores unarios prefijo generan recursión derecha en su nivel: `unary -> OP_NOT unary | atom`.

## 4. Algoritmo

### 4.1 Inputs y outputs

```kotlin
object PrecedenceRewriter {
    fun rewrite(grammar: Grammar): Grammar
}
```

### 4.2 Pasos

Sea `A` el no terminal a reescribir (caso típico: `expr`).

1. **Detectar candidatos**: para cada NT `A` de la gramática, buscar producciones con forma:
   - **Binaria**: `A -> A op A` con `op` en `precedenceTable`.
   - **Unaria prefijo**: `A -> op A` con `op` en `precedenceTable`.
   - **No-operador (atómicas)**: cualquier otra producción de `A` que no caiga en las dos anteriores.

   Si `A` no tiene ninguna producción binaria ni unaria sobre operadores con precedencia, `A` no se reescribe y queda como está.

2. **Generar NTs sintéticos**: para `n` niveles en `precedenceTable`, crear `A_lvl0, A_lvl1, ..., A_lvl(n-1), A_atom`. Los nombres no deben colisionar con NTs existentes (sufijar `_lvl0`, `_lvl1`, etc., y `_atom`).

3. **Emitir producción cabecera**:
   ```
   A -> A_lvl0
   ```

4. **Emitir producciones por nivel** (de nivel 0 hasta n-1):

   Para nivel `k` con operadores `{op_1, ..., op_m}`:

   - **Si `associativity == LEFT`**:
     ```
     A_lvlk -> A_lvlk op_1 A_lvl(k+1)
            |  A_lvlk op_2 A_lvl(k+1)
            |  ...
            |  A_lvlk op_m A_lvl(k+1)
            |  A_lvl(k+1)
     ```

   - **Si `associativity == RIGHT`**:
     - Caso binario (asignación):
       ```
       A_lvlk -> A_lvl(k+1) op_1 A_lvlk
              |  ...
              |  A_lvl(k+1)
       ```
     - Caso unario prefijo (NOT):
       ```
       A_lvlk -> op_1 A_lvlk
              |  ...
              |  A_lvl(k+1)
       ```

   El nivel `n-1` (el último) en lugar de `A_lvl(n)` referencia `A_atom`.

5. **Emitir nivel atómico**: por cada producción no-operador de `A` en la gramática original, reescribirla cambiando la cabecera de `A` a `A_atom` y reemplazando cualquier referencia a `A` dentro del cuerpo por `A` (sí, mantiene la cabecera original, no `A_lvl0`). Ejemplos:
   - `A -> ID` queda `A_atom -> ID`.
   - `A -> LPAREN A RPAREN` queda `A_atom -> LPAREN A RPAREN` (la subexpresión paretizada vuelve al tope: cubre todos los niveles).

6. **Reasignar IDs de producciones**: cada producción nueva recibe un ID secuencial nuevo. Las viejas producciones del NT reescrito se descartan.

7. **Construir y retornar el nuevo `Grammar`** con:
   - `terminals` igual al original.
   - `nonTerminals` igual al original más los sintéticos.
   - `productions` resultado del paso 6.
   - `startSymbol` igual al original.
   - `ignoredTokens` igual al original.
   - `precedenceTable` igual al original (se mantiene para referencia, aunque ya no se usa).

### 4.3 Esqueleto en Kotlin (referencia, no es el código final)

```kotlin
fun rewrite(grammar: Grammar): Grammar {
    val newProductions = mutableListOf<Production>()
    val newNonTerminals = grammar.nonTerminals.toMutableSet()
    var productionId = 1

    for (nonTerminal in grammar.nonTerminals) {
        val originalProductions = grammar.productionsByHead[nonTerminal] ?: continue
        if (!shouldRewrite(nonTerminal, originalProductions, grammar.precedenceTable)) {
            originalProductions.forEach { production ->
                newProductions.add(production.copy(id = productionId++))
            }
            continue
        }

        val syntheticNonTerminals = generateSyntheticNonTerminals(nonTerminal, grammar.precedenceTable.size)
        newNonTerminals.addAll(syntheticNonTerminals)

        newProductions.add(Production(productionId++, nonTerminal, listOf(syntheticNonTerminals[0])))

        for ((index, level) in grammar.precedenceTable.withIndex()) {
            val currentLevel = syntheticNonTerminals[index]
            val nextLevel = syntheticNonTerminals.getOrNull(index + 1) ?: atomNonTerminalFor(nonTerminal)
            newProductions.addAll(buildLevelProductions(currentLevel, nextLevel, level, productionId))
            productionId += /* count */
        }

        val atomNonTerminal = atomNonTerminalFor(nonTerminal)
        val atomicOriginalProductions = filterAtomicProductions(originalProductions, grammar.precedenceTable)
        for (atomic in atomicOriginalProductions) {
            newProductions.add(Production(productionId++, atomNonTerminal, atomic.body))
        }
    }

    return grammar.copy(
        productions = newProductions,
        nonTerminals = newNonTerminals
    )
}
```

## 5. Casos límite

### 5.1 NT no afectado

Un NT que no tiene producciones binarias ni unarias sobre operadores declarados queda intacto. Ejemplo: `stmt -> if_stmt | while_stmt | return_stmt`.

### 5.2 Operador presente en múltiples NTs

Si dos NTs distintos usan el mismo operador (por ejemplo `OP_PLUS` aparece en `expr` y también en `index_expr`), cada NT genera **su propio conjunto** de NTs sintéticos. La tabla de precedencia es global pero la reescritura es local por cabecera.

### 5.3 Paréntesis

Producción `expr -> LPAREN expr RPAREN` cae al nivel atómico:
```
expr_atom -> LPAREN expr RPAREN
```
Notar que la sub-expresión vuelve a `expr` (cabecera original), lo que garantiza que adentro del paréntesis se puede usar cualquier operador.

### 5.4 Operador unario prefijo a la misma altura que un binario derecho

Caso real: el catedrático puede pedir que `OP_NOT` y `OP_ASSIGN` estén en niveles distintos. El algoritmo los trata como niveles separados, así que no hay conflicto.

### 5.5 Operador unario sufijo (no soportado en v1)

`a++` requeriría reglas adicionales. Se deja fuera de alcance para esta iteración. Si se necesita, se agrega un tipo `Associativity.POSTFIX` en una iteración posterior.

### 5.6 Operador binario y unario con el mismo símbolo (resta unaria)

`OP_MINUS` puede ser binario o unario prefijo. **Solución simple**: tener dos categorías léxicas distintas (`OP_MINUS` binario, `OP_UMINUS` unario) y que el lexer o un pre-procesamiento las distinga. **No** se intenta detectar el contexto en el rewriter — eso es responsabilidad del especificador de la gramática.

### 5.7 Ningún operador declarado

Si `precedenceTable` está vacío, `rewrite` retorna la gramática sin tocar. Útil para gramáticas que no involucran operadores binarios.

## 6. Validaciones recomendadas

Antes de invocar el rewriter (en `GrammarValidator`):

1. Cada operador en `precedenceTable` debe estar declarado como `%token` y existir como categoría del lexer.
2. Un operador no puede aparecer en dos niveles distintos (cada terminal está en a lo sumo un `PrecedenceLevel`).
3. Si la gramática **no** tiene producciones binarias y `precedenceTable` está poblada, emitir warning (la tabla no surte efecto).
4. Si la gramática **sí** tiene producciones binarias pero `precedenceTable` está vacía, emitir warning (la gramática quedará ambigua).

## 7. Orden en el pipeline

```
YalpReader -> Grammar (con precedenceTable)
   |
   v
GrammarValidator (valida operadores, advierte si falta precedencia)
   |
   v
PrecedenceRewriter (NUEVO -- introduce niveles)
   |
   v
LeftRecursionRewriter (Ticket 9 actual, renombrado)
   |
   v
FirstSetComputer + FollowSetComputer
   |
   v
LL1TableBuilder | SLR1AutomatonBuilder | LALR1AutomatonMerger
```

El `LeftRecursionRewriter` se aplica DESPUÉS porque la salida del `PrecedenceRewriter` tiene recursión por la izquierda inmediata en cada nivel (para asociatividad izquierda), y LL(1) la necesita eliminada.

## 8. Tests requeridos (Ticket 10 acceptance)

1. Gramática `E -> E + E | E * E | ID` con `%left OP_PLUS` y `%left OP_TIMES` produce la cascada clásica `E -> add_expr; add_expr -> add_expr OP_PLUS mul_expr | mul_expr; mul_expr -> mul_expr OP_TIMES atom | atom; atom -> ID`.
2. Gramática con NOT unario produce `unary -> OP_NOT unary | atom`.
3. Gramática con ASSIGN derecho produce `assign_expr -> atom OP_ASSIGN assign_expr | atom`.
4. NT sin operadores binarios queda inalterado.
5. Cabecera original mantiene su nombre y referencia al primer nivel.
6. Paréntesis caen al nivel atómico y referencian la cabecera original.
7. Gramática con `precedenceTable` vacía pasa intacta.
8. NTs sintéticos no colisionan con NTs existentes (si la gramática ya tiene `add_expr`, se busca un nombre alternativo).

## 9. Ejemplo end-to-end

### Entrada `.yalp`

```
%token ID OP_PLUS OP_TIMES OP_NOT OP_ASSIGN LPAREN RPAREN

%left  OP_PLUS
%left  OP_TIMES
%right OP_NOT
%right OP_ASSIGN

%%
expr : expr OP_PLUS  expr
     | expr OP_TIMES expr
     | OP_NOT expr
     | expr OP_ASSIGN expr
     | LPAREN expr RPAREN
     | ID
     ;
%%
```

### Tras `PrecedenceRewriter`

```
expr           : expr_lvl0 ;
expr_lvl0      : expr_lvl0 OP_PLUS  expr_lvl1 | expr_lvl1 ;       /* left  */
expr_lvl1      : expr_lvl1 OP_TIMES expr_lvl2 | expr_lvl2 ;       /* left  */
expr_lvl2      : OP_NOT expr_lvl2 | expr_lvl3 ;                   /* right unario */
expr_lvl3      : expr_atom OP_ASSIGN expr_lvl3 | expr_atom ;      /* right binario */
expr_atom      : LPAREN expr RPAREN | ID ;
```

### Tras `LeftRecursionRewriter` (Dragon Book §4.3.3, aplicado a `expr_lvl0` y `expr_lvl1`)

```
expr           : expr_lvl0 ;
expr_lvl0      : expr_lvl1 expr_lvl0_prime ;
expr_lvl0_prime: OP_PLUS  expr_lvl1 expr_lvl0_prime | EPSILON ;
expr_lvl1      : expr_lvl2 expr_lvl1_prime ;
expr_lvl1_prime: OP_TIMES expr_lvl2 expr_lvl1_prime | EPSILON ;
expr_lvl2      : OP_NOT expr_lvl2 | expr_lvl3 ;
expr_lvl3      : expr_atom OP_ASSIGN expr_lvl3 | expr_atom ;
expr_atom      : LPAREN expr RPAREN | ID ;
```

Esta forma es LL(1)-compatible y también SLR(1)/LALR(1)-compatible (con la versión sin LeftRecursionRewriter para SLR/LALR si se prefiere, ya que esos métodos manejan recursión por la izquierda sin problemas).

## 10. Decisión pendiente: ¿LeftRecursionRewriter siempre o solo para LL(1)?

LL(1) **requiere** eliminación de recursión por la izquierda. SLR(1) y LALR(1) **no**. Aplicar el rewriter para todos los métodos uniformiza la entrada al cálculo de FIRST/FOLLOW pero hace los autómatas más grandes.

**Recomendación**: el `Pipeline` aplica `LeftRecursionRewriter` **solamente** cuando el método es `LL1`. Para `SLR1` y `LALR1` se pasa la salida directa del `PrecedenceRewriter`. Esto preserva la estructura natural de la gramática para los métodos LR y reduce el número de items en el autómata.

Esta decisión se materializa en el `Pipeline` (Ticket 28 en el roadmap rediseñado).
