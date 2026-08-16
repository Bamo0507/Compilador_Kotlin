# Roadmap — Analizador Semántico de Compiscript

Plan de desarrollo del Proyecto 2: análisis semántico de Compiscript sobre ANTLR,
con tabla de símbolos, verificación de tipos, ejecución e IDE.

---

## Antes de empezar: qué cambió respecto al proyecto anterior

El proyecto anterior construía a mano un generador de analizadores: expresiones
regulares a AFD para el léxico, y tablas LL(1)/SLR(1)/LALR(1) para el sintáctico.
**Todo eso se reemplaza por ANTLR.**

Esto es importante entenderlo bien porque cambia la percepción del tamaño del
trabajo. El enunciado dice, literal:

> **Analizador Sintáctico:** Basado en la gramática de Compiscript (ANTLR),
> reutilizando o extendiendo el trabajo de la fase anterior.

Y en objetivos específicos: *"Implementar el analizador sintáctico de Compiscript
utilizando ANTLR (u otra herramienta similar)."*

Traducción: **ANTLR genera el lexer y el parser desde el archivo `.g4`.** No se
reimplementa nada a mano. No hay normalización de regex, ni Shunting Yard, ni
construcción de AFD, ni tablas de parsing, ni reescritura de gramática por
precedencia.

El trabajo real de este proyecto es **semántica, tabla de símbolos e IDE**, y eso
es exactamente lo que califica la rúbrica:

| Componente | Puntos | Qué es realmente |
|---|---|---|
| IDE | 15 | Adaptar la GUI Compose que ya existe |
| Analizador Sintáctico y Semántico | 60 | ANTLR (casi gratis) + **el verificador de tipos** (todo el trabajo) |
| Tabla de Símbolos | 25 | Árbol de ámbitos con entornos anidados |
| **Total** | **100** | **85 puntos son semántica y tabla de símbolos** |

---

## Mapa de fases

| Fase | Qué se logra al terminarla | Tickets |
|---|---|---|
| [**0 — Limpieza y base**](./fase-0-limpieza-y-base.md) | Repo limpio, compilando, con ANTLR generando lexer y parser | 6 |
| [**1 — Modelos congelados**](./fase-1-modelos.md) | Las 4 estructuras que los 3 integrantes deben acordar antes de escribir lógica | 5 |
| [**2 — Del árbol de ANTLR al AST propio**](./fase-2-ast.md) | Un árbol limpio, con la torre de precedencia colapsada | 3 |
| [**3 — Pasada 1: declaraciones**](./fase-3-declaraciones.md) | El árbol de ámbitos poblado con todas las declaraciones | 2 |
| [**4 — Pasada 2: tipos**](./fase-4-tipos.md) | Cada expresión con su tipo verificado y su valor plegado | 4 |
| [**5 — Flujo y vivacidad**](./fase-5-flujo-y-vivacidad.md) | `return`/`break`/`continue`, código muerto, metadatos para el GC | 2 |
| [**6 — Ejecución**](./fase-6-ejecucion.md) | El programa corre y `print(3+5)` imprime `8` | 2 |
| [**7 — Pipeline e IDE**](./fase-7-pipeline-e-ide.md) | Todo orquestado y visible en la GUI | 4 |
| [**8 — Pruebas y documentación**](./fase-8-pruebas-y-docs.md) | Casos exitosos y fallidos por regla, más los entregables de docs | 3 |
| | | **31** |

### Dos notas sobre el orden

**La Fase 1 es un cuello de botella deliberado.** Nadie escribe lógica de
análisis hasta que los cuatro modelos estén acordados por los tres y mergeados.
Es lo que permite que desde la Fase 2 trabajen en paralelo sin pisarse, y el
enunciado exige commits individuales por integrante.

**La Fase 6 (ejecución) va después de la 5**, aunque el catedrático la pidió.
El intérprete corre sobre el AST **ya validado**: si ejecutas antes de verificar,
ejecutas código con errores de tipo y obtienes basura en vez de un error. Además,
buena parte de la demo (`print(3+5)` → `8`) ya sale de la Fase 4 con plegado de
constantes, sin intérprete.

---

## Formato de cada ticket

Cada ticket lleva:

- **Estado** — `pendiente` | `en progreso` | `completado`. Se actualiza a mano.
- **Depende de** — tickets previos requeridos.
- **Archivos** — qué crea, modifica o elimina.
- **Qué es esto, en simple** — explicación en lenguaje llano, cuando el concepto
  lo necesita.
- **Qué se hace** — el diseño concreto, con código.
- **Por qué** — la razón de la decisión. Presente siempre que la decisión no sea
  obvia, porque son las que se preguntan en la defensa.
- **Aceptación** — cuándo se considera terminado, en criterios verificables.
- **Respaldo** — la sección del enunciado, del libro o de las notas de clase que
  lo justifica.

---

## Principios de código para todo el proyecto

Estos aplican a cada ticket sin repetirlos:

1. **Simple antes que ingenioso.** Si una solución necesita un comentario para
   entenderse a nivel de mecánica, probablemente hay una más simple. Los
   comentarios son para el *por qué*, no para el *qué*.
2. **Nombres completos.** `currentScope`, no `cs`. `declaredType`, no `dt`.
3. **`sealed interface` / `sealed class` para jerarquías cerradas.** Da `when`
   exhaustivo: si agregas un caso y olvidas manejarlo, Kotlin no compila.
4. **`data object` para constantes únicas**, `data class` para lo que lleva datos.
5. **`enum` en vez de `String`** para conjuntos cerrados (operadores, categorías).
   Misma razón que el punto 3.
6. **Nada de `object` con estado mutable.** Una instancia por compilación. Fue un
   problema real en el proyecto anterior: obligaba a acordarse de limpiar el
   estado global antes de cada corrida.
7. **Los modelos son datos; las reglas son funciones aparte.** `Type.kt` no sabe
   qué se puede sumar con qué; eso vive en `TypeRules.kt`.
8. **Una función por construcción del lenguaje.** Es la forma que impone el
   patrón visitor y es lo que pide el catedrático: al ámbito en el que estoy le
   corresponde una función que procesa toda su información.

---

## Estructura de carpetas al terminar

```
app/src/main/
├── antlr/
│   └── Compiscript.g4                    la gramática: fuente de verdad del sintáctico
│
└── kotlin/org/compiler/
    ├── models/
    │   └── LexemeLocation.kt              línea + columna (sobrevive del proyecto anterior)
    │
    ├── diagnostics/
    │   ├── CompilerError.kt               sealed: LexerError | ParserError | SemanticError
    │   └── Diagnostics.kt                 colector de errores, una instancia por compilación
    │
    ├── frontend/
    │   ├── syntax/
    │   │   ├── DiagnosticsErrorListener.kt errores de ANTLR -> Diagnostics
    │   │   └── SyntaxAnalyzer.kt           .cps -> parse tree de ANTLR
    │   │
    │   ├── ast/
    │   │   ├── models/                     Node, Expression, Statement, TypeReference, operadores
    │   │   └── AstBuilder.kt               Visitor de ANTLR: parse tree -> AST propio
    │   │
    │   └── semantic/
    │       ├── symbols/
    │       │   ├── Type.kt                 jerarquía sellada de tipos
    │       │   ├── Symbol.kt / DeclarationKind.kt
    │       │   └── Scope.kt / ScopeKind.kt  árbol de ámbitos
    │       ├── ScopeDeclaration.kt          declareOrReport: lo usan las dos pasadas
    │       ├── TypeResolver.kt              TypeReference escrito -> Type resuelto
    │       ├── DeclarationCollector.kt      PASADA 1: declaraciones
    │       ├── TypeRules.kt                 las reglas de inferencia, una función por regla
    │       ├── TypeChecker.kt               PASADA 2: verificación y plegado
    │       ├── FlowAnalyzer.kt              return/break/continue, código muerto
    │       └── LivenessReportBuilder.kt     reporte de vivacidad para el GC
    │
    ├── interpreter/
    │   ├── RuntimeValue.kt                  los valores en ejecución
    │   ├── Environment.kt                    ámbitos con valores
    │   └── Interpreter.kt                    ejecuta el AST validado
    │
    ├── runtime/
    │   ├── CompilerPipeline.kt               orquestador: una llamada, un resultado
    │   └── models/CompilationResult.kt
    │
    └── gui/
        ├── state/AppState.kt
        ├── screens/                          Workspace, Trees, Symbols
        └── components/                       CodeEditor, ErrorList, TreeCanvas, Console, ...
```

---

## El pipeline completo, en orden

```
archivo .cps
     │
     ▼  ANTLR Lexer + Parser  (+ DiagnosticsErrorListener)
parse tree de ANTLR ─────────────────────────► errores léxicos y sintácticos
     │                                          vista visual del árbol (requisito)
     ▼  AstBuilder (Visitor de ANTLR)
AST propio  ── colapsa la torre de 11 niveles de precedencia
     │
     ▼  DeclarationCollector          PASADA 1
árbol de ámbitos completo ─────────────────► errores de declaración
     │                                          vista de tabla de símbolos (requisito)
     ▼  TypeChecker                   PASADA 2
AST decorado (cada Expression con su tipo) ────────► errores de tipo
     │  + valores plegados (3+5 = 8)
     ▼  FlowAnalyzer
     │                              ─────────► errores de flujo y código muerto
     ▼  LivenessReportBuilder
     │                              ─────────► reporte de vivacidad para el GC
     ▼  Interpreter  (solo si no hay errores)
salida del programa ─────────────────────────► consola del IDE
```

---

## Reparto sugerido entre los 3 integrantes

El enunciado exige *"commits individuales que evidencien claramente la
contribución de cada integrante"*. Eso condiciona la arquitectura: hay que poder
partir el trabajo en tres frentes que no se pisen.

| Frente | Fases y tickets | Depende de |
|---|---|---|
| **A — Infra, AST e IDE** | 0.4, 0.5, 1.4, 1.5, 2.x, 7.x | nada |
| **B — Símbolos y tipos** | 0.6, 1.1, 1.2, 1.3, 3.x | acordar los modelos con A |
| **C — Verificación y ejecución** | 4.x, 5.x, 6.x | modelos de A y B |

La clave para que no se bloqueen: **congelar los cinco modelos de la Fase 1 el
primer día**, revisados por los tres, antes de escribir cualquier lógica.

La Fase 0 y la Fase 8 se hacen entre todos.

---

## Decisiones ya cerradas

Estas se discutieron y quedaron cerradas antes de escribir los tickets. Cada una
tiene su razón anotada porque van a ser preguntadas.

| # | Decisión | Elegida | Por qué |
|---|---|---|---|
| 1 | `float` en el lenguaje | **Se agrega a la gramática** | El enunciado pide aritmética sobre `integer` o `float`, y la gramática de ejemplo no tenía `float`. Se extiende `baseType` y se agrega `FloatLiteral`. Ver ticket 0.5 y `docs/decisiones-gramatica.md`. |
| 2 | Modificar `Compiscript.g4` | **Sí, documentando cada cambio** | El enunciado dice *"a partir de la gramática oficial y extenderlo"*. Todo cambio queda registrado con su justificación. |
| 3 | Ejecución de código | **Sí, es requisito** | El catedrático espera las tres cosas: árbol sintáctico, árbol validado semánticamente, y el resultado de ejecutar. No está en la rúbrica escrita, pero sí se pidió. |
| 4 | Condición del `switch` | **Comparable con sus `case`, no `boolean`** | Toda condición de control de flujo se resuelve como operación booleana: en `switch (x) { case 1: }` lo que ocurre es `x == 1`, una comparación que produce `boolean`. La regla real es que el sujeto y los `case` sean comparables entre sí. |
| 5 | Fall-through en el `switch` | **No existe** | Se deduce del propio enunciado: `break` solo se permite dentro de bucles, así que no hay forma de expresar caída al siguiente caso. Cada `case` ejecuta su cuerpo y el `switch` termina. |
| 6 | Sobrecarga de funciones | **No existe** | El enunciado pide *"detección de redeclaración de funciones con el mismo nombre"*: dos funciones con el mismo nombre es error. Un ámbito guarda un símbolo por nombre. Elimina toda la resolución de sobrecarga. |
| 7 | Ámbitos al cerrarse | **Árbol permanente, no pila que descarta** | Tres razones independientes: el enunciado pide mostrar *"el estado de la tabla de símbolos por cada entorno"* (25 pts); la herencia necesita el ámbito de la superclase ya cerrado; y los closures capturan su ámbito de definición, que no puede morir. |
| 8 | Equivalencia de tipos | **Nominal para clases, estructural para arreglos** | `class Perro : Animal` te obliga a *declarar* la relación de subtipo, y eso es la marca de un sistema nominal. Los arreglos no tienen nombre, así que se comparan por su tipo de elemento. |
| 9 | Comparación de tipos | **El `==` de Kotlin, sin canonicalización** | Los `data class` ya generan `equals` estructural, y la profundidad máxima real es 2 (`integer[][]`). Canonicalizar añadiría una caché y un riesgo de desincronización para ahorrar nanosegundos. |
| 10 | Warnings | **No existen: un solo nivel de severidad** | No se piden. `CompilerError` no lleva `Severity`. El *"código muerto"* que sí pide el enunciado va como **error**. La información de vivacidad para el GC no es un diagnóstico: es otra vista. |
| 11 | Pasadas semánticas sobre el AST propio, no sobre el parse tree de ANTLR | **AST propio** | Razón técnica decisiva: un Listener de ANTLR recorre **todo** automáticamente y no se le puede impedir entrar a los cuerpos de las funciones. La Pasada 1 necesita justamente *no* entrar (es lo que habilita las referencias adelantadas). Con funciones recursivas sobre el AST simplemente no recurres. Ver ticket 3.2. |
| 12 | Módulo `%` con `float` | **Solo `integer`** | Es lo más simple y lo más común en lenguajes de este perfil. Queda documentado como regla A3. |
| 13 | Tabla de tipos numerada | **No existe** | Se evaluó y se descartó. Un id entero solo puede contestar *"¿son iguales?"*; no puede decir si un tipo es numérico, de qué es un arreglo, o cuáles son los parámetros de una función — que es lo que el verificador pregunta casi siempre. Y comparar tipos ya es tan barato como comparar enteros: los primitivos son `data object` (una sola instancia, se comparan por referencia) y los compuestos tienen profundidad máxima 2. `Symbol` guarda `type: Type` directamente, que en Kotlin **es** la "referencia al tipo" que piden las notas de clase. |
| 14 | Categoría de un símbolo | **`DeclarationKind` (5 valores) + un booleano `isMember`** | Sin `FIELD` ni `METHOD`: eran el producto cruzado de dos ejes independientes (*qué es* × *dónde vive*), y ese cruce dejaba sin categoría clara a un `const` dentro de una clase. Separados, cada regla pregunta una sola cosa: `kind == CONSTANT` para la reasignación, `isMember` para el acceso con `this.`. `Scope.declare` pone `isMember` automáticamente —el ámbito ya sabe si es una clase—, así que no se puede equivocar. No se llama `category` porque ese término ya significa "categoría de lexema", ni `DeclarationType` porque `Type` ya significa "tipo de dato". |
| 15 | Función sin tipo de retorno anotado | **Es `void`; no se infiere del cuerpo** | La gramática lo permite (`(':' type)?`) y el caso normal es una función que solo imprime. Inferir del primer `return` rompería la Pasada 1, que registra la firma **sin entrar al cuerpo** — y el `return` está justamente ahí. Consecuencias: `function f() { return 1; }` es **error** (*"debe devolver 'void', no 'integer'"*), y `return;` pelado dentro de una función void es **legal** como salida temprana. |
| 16 | Herencia del constructor | **Se hereda si la subclase no declara uno propio** | Al revés que en Java, y por una razón concreta: Compiscript **no tiene `super`**, así que una subclase sin constructor propio no tendría ninguna forma de inicializar los campos heredados y la herencia quedaría inutilizable. Además el ejemplo de `Especificaciones.md` lo asume: `class Perro : Animal` sin constructor, invocado como `new Perro("Toby")`. Se implementa con `lookupMember` en vez de `lookupLocal` (tickets 4.3 y 6.2). |

---

## Las cuatro propiedades del sistema de tipos

El catedrático pidió que se apliquen explícitamente. Cada una tiene una decisión
concreta señalable en el código:

| Propiedad | Qué exige | Dónde se cumple |
|---|---|---|
| **Verificable** | Existe un algoritmo que decide si el programa está bien tipado | `TypeChecker` (Fase 4). Cada regla de la gramática tiene su función y **toda** expresión recibe un tipo, aunque sea `ErrorType`. Ninguna construcción queda sin regla. |
| **Decidible** | El algoritmo **termina** con verdadero o falso en tiempo finito | El recorrido es sobre un árbol **finito** y sin unificación recursiva. Con anotaciones explícitas más inferencia local (solo del inicializador y del `foreach`), la terminación es inmediata. |
| **Realizable** | Lo verificable estáticamente se verifica en compilación; **lo que no, dinámicamente en ejecución** | Dos casos con las dos mitades implementadas. **Índices**: `lista[-1]` con literal se rechaza en la Fase 4 gracias al plegado; `lista[i]` con variable va al chequeo dinámico del `Interpreter` (Fase 6). **División entre cero**: `1 / 0` es error de compilación; `1 / x` con `x` variable se verifica en ejecución. De ahí sale el `try/catch` del lenguaje. |
| **Transparente** | El programador puede **predecir** si pasa la validación y **entender por qué** falló | Los mensajes de error no son cosmética, **son un requisito del sistema de tipos**. Cada error lleva línea, columna, tipo esperado, tipo encontrado y la regla violada. Es lo que justifica invertir en el formato de errores. |

Las reglas de inferencia en notación de Cardelli, con su función y su test, viven
en [`docs/reglas-de-tipos.md`](../reglas-de-tipos.md) (se crea en el ticket 4.1).
