# Roadmap · DBMS sobre CSV

Plan de desarrollo del manejador de base de datos: un motor SQL que pasa por las
mismas etapas de un compilador (léxico, sintáctico, semántico, ejecución) y que
guarda las tablas como archivos CSV legibles.

---

## Qué es este proyecto respecto a Compiscript

Esta rama **no es una evolución de Compiscript, es un proyecto hermano**.
Compiscript queda congelado en `main`. Aquí se reaprovechan los cimientos, no el
lenguaje.

| Se conserva | Se elimina |
|---|---|
| El andamiaje de ANTLR en Gradle, con su `dependsOn` ya resuelto | La gramática `Compiscript.g4` |
| `LexemeLocation`, `Diagnostics`, `CompilerError` | Clases, herencia, closures |
| La forma del pipeline: seis etapas, y solo la sintaxis corta | `FlowAnalyzer` y el reporte de vivacidad |
| `Scope` y `Symbol`, ahora para alias de tablas | El `TypeChecker` y el `Interpreter` |
| El IDE: editor, lista de errores, vista de árboles | Los 38 programas `.cps` |

Y aparece una etapa que Compiscript no tenía: **el catálogo**, que se lee del
disco antes del análisis semántico, porque sin él el semántico no puede saber si
una tabla existe ni de qué tipo es una columna.

---

## Alcance

| | Incluye |
|---|---|
| DDL | `CREATE TABLE`, `ALTER TABLE` (add y drop column), `DROP TABLE` |
| DML | `INSERT`, `UPDATE`, `DELETE` |
| Query | `SELECT`, `FROM`, alias, `JOIN ... ON`, `WHERE`, `ORDER BY`, `DISTINCT`, `LIMIT` |
| Agregación | `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, `GROUP BY`, `HAVING` |
| Subconsultas | escalares, `IN`, `EXISTS`, correlacionadas, y en el `FROM` |
| Tipos | `INT`, `FLOAT`, `DECIMAL`/`NUMERIC`, `CHAR`, `VARCHAR`, `TEXT`, `DATE`, `TIME`, `BOOLEAN`, `NULL` |
| Restricciones | `PRIMARY KEY`, `NOT NULL`, `NULL`, `UNIQUE`, `FOREIGN KEY`, `AUTOINCREMENT`, `DEFAULT` |

Queda fuera, y es deliberado: índices, transacciones explícitas, vistas,
disparadores, `LEFT`/`RIGHT JOIN`, `UNION`, `CHECK` y usuarios.

---

## Mapa de fases

| Fase | Qué se logra al terminarla | Tickets | Peso |
|---|---|---|---|
| [**0 · Limpieza**](./fase-0-limpieza.md) | Repo sin rastro de Compiscript, compilando y abriendo ventana | 7 | bajo |
| [**1 · Tipos y valores**](./fase-1-tipos-y-valores.md) | Los 11 tipos, los 8 valores y las reglas de compatibilidad | 5 | medio |
| [**2 · Catálogo y almacenamiento**](./fase-2-catalogo-y-almacenamiento.md) | El directorio `data/` se lee y se escribe sin perder nada | 6 | medio |
| [**3 · Gramática y AST**](./fase-3-gramatica-y-ast.md) | Todo el SQL del alcance parsea y produce un AST limpio | 6 | **alto** |
| [**4 · Semántico I, nombres**](./fase-4-semantico-nombres.md) | Tablas, alias y columnas resueltos, con nivel e índice | 5 | medio |
| [**5 · Semántico II, tipos y reglas**](./fase-5-semantico-tipos-y-reglas.md) | Cada expresión tipada y cada regla de SQL verificada | 5 | **alto** |
| [**6 · Motor I, consultas**](./fase-6-motor-consultas.md) | Un `SELECT` del alcance devuelve el `ResultSet` correcto | 7 | **alto** |
| [**7 · Motor II, escritura**](./fase-7-motor-escritura.md) | Se escribe a disco, o no se escribe nada | 5 | medio |
| [**8 · Interfaz**](./fase-8-interfaz.md) | Todo el alcance se hace sin salir de la ventana | 5 | medio |
| [**9 · Batería y documentación**](./fase-9-bateria-y-docs.md) | Casos válidos e inválidos, README y diagrama | 3 | medio |
| | | **54** | |

**Estimación total: 24 sesiones de trabajo.**

### Orden y paralelismo

```
Fase 0  limpieza
   |
Fase 1  types y values
   |
   +---------------------+
   |                     |
Fase 2  catalog      Fase 3  gramatica y AST
   |                     |
   +----------+----------+
              |
        Fase 4  nombres
              |
        Fase 5  types y constraints
              |
        Fase 6  motor consultas
              |
        Fase 7  motor escritura
              |
        Fase 8  interfaz
              |
        Fase 9  bateria y docs
```

**La fase 1 es un cuello de botella deliberado.** Todo lo demás lee `Type` y
`Value`. Si cambian a mitad de camino, hay que rehacer trabajo en varios frentes
al mismo tiempo.

**Las fases 2 y 3 son el único tramo paralelizable**, y es el más grande del
proyecto. La fase 2 no toca ANTLR y la fase 3 no toca el disco, así que no se
pisan. Si son dos personas, ahí se parte.

**El pipeline se arma en la fase 3 en su versión mínima** y cada fase posterior le
conecta su etapa. No hay una fase de integración al final, porque integrar al
final es donde aparecen los problemas caros.

**La fase 0 deja el build rojo entre sus tickets 0.2 y 0.7, y es normal.** Delete
el AST rompe el `TypeChecker`, que rompe el pipeline, que rompe la GUI. El ticket
0.7 reconstruye el runtime y cierra el build.

---

## Las nueve decisiones de diseño

Estas ya están cerradas. Si alguna se reabre, hay que revisar las fases que
dependen de ella.

**1. Proyecto hermano, no evolución.** Compiscript queda congelado en `main`. En
esta rama se reemplaza en sitio, con una fase dedicada a la limpieza.

**2. El directorio es el catálogo.** Si existe `users.csv`, existe la tabla
`users`. No hay archivo global de catálogo, porque sería información derivada de
algo que el sistema de archivos ya sabe, y toda copia se puede quedar vieja.
SQLite necesita `sqlite_master` porque toda la base es un archivo y no tiene
directorio al cual preguntarle. Aquí sí lo hay.

**3. Dos archivos por tabla.** `users.csv` guarda el header y las filas;
`users.json` guarda tipo y restricciones por columna. El CSV no puede cargar
tipos, y el JSON no debería cargar datos.

**4. El DBMS es el único que escribe.** El CSV se eligió por ser legible, no por
ser editable. El chequeo de que el header cuadra con el JSON existe como guarda de
cordura, con un mensaje que dice que alguien tocó el archivo por fuera, no como
mecanismo de reconciliación. Lo que se valida de verdad es lo que **entra**.

**5. Se escribe al final, con vuelta atrás implícita.** Las sentencias se aplican
en memoria y el volcado ocurre una sola vez al terminar. Si cualquier sentencia
falla, no se escribe nada y el directorio queda intacto. Es atomicidad por script
sin agregar `BEGIN` ni `COMMIT` a la gramática.

**6. Las restricciones son una jerarquía sellada, no un enum.** Los estados
imposibles no compilan, y `FOREIGN_KEY` y `DEFAULT` pueden cargar datos mientras
las otras cinco no.

**7. El JSON es uniforme.** Cada regla es un objeto con discriminador `constraint`. Es
lo que `kotlinx.serialization` produce sin serializador a mano.

**8. El semántico decora, el motor no vuelve a preguntar.** Cada referencia a
columna queda con su `level` y su `index` resueltos en el análisis. El motor
indexa un arreglo, no busca un nombre por fila.

**9. `CHAR` se guarda sin relleno.** El largo lo dice el JSON, así que el relleno
se aplica al leer. El CSV no lleva espacios invisibles y sigue siendo legible, que
era la razón de elegirlo.

**10. Una `Session` por corrida es la dueña del estado.** El caché de lectura y el
almacén de escritura son **el mismo mapa**, así que un `SELECT` después de un
`INSERT` ve la fila nueva. Es también quien recorre las sentencias del script y
quien le dice a `flush` qué cambió. Nada de `object` con estado.

**11. Las advertencias son una severidad, no un colector aparte.** `= NULL`,
`LIMIT` sin `ORDER BY` y `UPDATE` sin `WHERE` reportan con severidad
`WARNING`, y `hasErrors` cuenta solo los `ERROR`, así que el script se ejecuta
igual. Los errores de ejecución son una cuarta variante de `CompilerError`, no una
excepción, para que el motor pueda seguir reportando y la lista del IDE los
muestre sin saber de dónde vienen.

---

## El pipeline completo, en orden

```
source SQL
   |
   v  ANTLR Lexer + Parser  (+ DiagnosticsErrorListener)
parse tree  ------------------------> errores lexicos y sintacticos
   |                                  vista visual del arbol
   |        UNICA ETAPA QUE CORTA
   v  SqlAstBuilder
AST propio
   |
   v  CatalogLoader        lee data/*.json, NO los .csv
Catalog  ---------------------------> el esquema completo de la base
   |
   v  SqlChecker           nombres, niveles, types, constraints
AST decorado  ----------------------> errores y ADVERTENCIAS, todos juntos
   |                                  las advertencias no detienen nada
   v  Session.run       recorre las sentencias del script
   |  Engine  -> consultas   lee data/*.csv solo de lo que se toca
   |  Writer  -> escrituras  modifica rows EN MEMORIA
ResultSet + changes pendientes  ----> errores de ejecucion, CORTAN
   |
   v  Flush.flush         solo si nada fallo
data/ actualizado  ----------------> rejilla de resultados en el IDE
```

**Esquema ansioso, datos perezosos.** En la etapa del catálogo se leen todos los
`.json`, porque para decir que una tabla no existe hay que saber qué existe, y
pesan nada. Los `.csv` se abren en la ejecución y solo los de las tablas que el
script menciona.

### Dónde cae cada error

| Error | Etapa | Mensaje |
|---|---|---|
| Sintaxis | sintáctica | `se esperaba FROM` |
| Tabla inexistente | semántica | `la tabla 'usuarios' no existe` |
| Columna inexistente | semántica | `'users' no tiene columna 'email'` |
| Alias ambiguo | semántica | `'name' existe en 'users' y en 'posts'` |
| Tipo incompatible | semántica | `WHERE espera BOOLEAN, recibio INT` |
| Header descuadrado | semántica | `users.csv fue editado fuera del DBMS` |
| `= NULL` en vez de `IS NULL` | semántica | **advertencia**, no detiene |
| `UPDATE` sin `WHERE` | semántica | **advertencia**, no detiene |
| PK repetida | ejecución | `ya existe una fila con id = 1` |
| NOT NULL violado | ejecución | `la columna 'name' no acepta nulos` |
| FK rota | ejecución | `posts.uid = 7 no existe en users.id` |

Las semánticas salen **todas juntas**: un error no corta el análisis. Las de
ejecución sí cortan, y por la decisión 5 eso significa que el disco no se toca.

---

## Formato de cada ticket

- **Estado**: `pendiente` | `en progreso` | `completado`. Se actualiza a mano.
- **Depende de**: tickets previos requeridos.
- **Archivos**: qué crea, modifica o elimina.
- **Qué es esto, en simple**: explicación llana, cuando el concepto la necesita.
- **Qué se hace**: el diseño concreto, con código.
- **Por qué**: la razón de la decisión, cuando no es obvia. Son las que se
  preguntan en la defensa.
- **Aceptación**: cuándo se considera terminado, en criterios verificables.

---

## Principios de código

1. **Simple antes que ingenioso.** Si una solución necesita un comentario para
   entenderse a nivel de mecánica, probablemente hay una más simple. Los
   comentarios son para el porqué, no para el qué.
2. **Comentarios breves y directos.** Una línea por variable como máximo, y si es
   sencillo, sin comentario.
3. **Nombres completos.** `currentScope`, no `cs`.
4. **Imports al inicio**, nunca en línea.
5. **`sealed interface` para jerarquías cerradas.** Da `when` exhaustivo: si
   agregas un caso y olvidas manejarlo, Kotlin no compila.
6. **`data object` para constantes únicas**, `data class` para lo que lleva datos.
7. **Nada de `object` con estado mutable.** Una instancia por corrida.
8. **Los modelos son datos, las reglas son funciones aparte.** `Type.kt` no sabe
   qué se puede sumar con qué; eso vive en `TypeRules.kt`.
9. **Preferir `?.` y `?: return` sobre `!!`** cuando haya ambigüedad.
10. **El código va en inglés, los comentarios en español.** Clases, funciones,
    parámetros, campos, valores de enum, reglas de la gramática y claves del JSON
    se escriben en inglés. Los comentarios, los mensajes de error que ve el
    usuario y esta documentación van en español.

### Nombres que ya están fijados

Para que dos personas no inventen dos nombres para lo mismo:

| Concepto | Nombre |
|---|---|
| raíz del AST | `Script`, con `statements` |
| una sentencia | `Statement`; una consulta es `Query` y además es `Expression` |
| origen de un `FROM` | `FromSource`, con `TableSource` y `DerivedSource` |
| una restricción | `Constraint`, jerarquía sellada |
| un valor de celda | `Value` |
| estado de una corrida | `Session`, con `rowsOf`, `markModified`, `applyCatalog`, `changes` |
| correr el script | `Session.run(script)` |
| correr una consulta | `Engine.execute(query)` |
| aplicar una escritura | `Writer.execute(statement)`, nunca `apply`, que choca con Kotlin |
| volcar a disco | `Flush.flush(changes, directory)` |
| esquema de salida | `OutputSchema.schemaOf(query)` |

---

## Estructura de carpetas al terminar

```
app/src/main/
├── antlr/
│   └── Sql.g4                          la gramatica: source de verdad del sintactico
│
├── resources/scripts/                  la bateria de pruebas .sql
│
└── kotlin/org/compiler/
    ├── models/LexemeLocation.kt         linea y column
    │
    ├── diagnostics/                     CompilerError, Diagnostics
    │
    ├── types/
    │   ├── Type.kt                      los 11 types, tres con parametros
    │   ├── Value.kt                     los 8 values en execution
    │   ├── TypeRules.kt                 torre numerica, familias, operadores
    │   └── ValueCodec.kt                texto del CSV <-> Value
    │
    ├── catalog/
    │   ├── Constraint.kt               jerarquia sellada de las 7 constraints
    │   ├── Catalog.kt                   Catalog, Table, Column
    │   ├── CatalogLoader.kt             recorre data/, lee los .json
    │   ├── CatalogWriter.kt             escribe los .json
    │   └── CatalogExtensions.kt         addConstraint y companiia
    │
    ├── storage/
    │   ├── CsvReader.kt                 RFC 4180, nulos y comillas
    │   ├── CsvWriter.kt
    │   └── DataDirectory.kt             la raiz data/, inyectable para tests
    │
    ├── frontend/
    │   ├── syntax/                      SqlSyntaxAnalyzer, ParseTreeView
    │   ├── ast/                          nodos SQL, SqlAstBuilder, AstView
    │   └── semantic/
    │       ├── symbols/Scope.kt         arbol de ambitos, ahora de consultas
    │       ├── NameResolver.kt          tables, alias, columns, level e index
    │       ├── OutputSchema.kt         que columnas devuelve un SELECT
    │       └── SqlChecker.kt            types y constraints de SQL
    │
    ├── engine/
    │   ├── Session.kt                   EL estado de una corrida, y el bucle
    │   ├── ResultSet.kt                 columns y rows
    │   ├── RowContext.kt                la pila de filas para el anidamiento
    │   ├── Evaluator.kt                 expresiones sobre una fila
    │   ├── Operators.kt                 scan, filter, project, join, sort, ...
    │   ├── Aggregator.kt                GROUP BY y las cinco funciones
    │   ├── Writer.kt                    INSERT, UPDATE, DELETE y el DDL
    │   └── Flush.kt                     escribe solo lo que cambio
    │
    ├── runtime/
    │   ├── DbmsPipeline.kt              orquestador: una llamada, un resultado
    │   └── models/CompilationResult.kt
    │
    └── gui/                             estado, pantallas y componentes
```

---

## Reparto sugerido

| Frente | Fases | Depende de |
|---|---|---|
| Almacenamiento | 1, 2, y el 7.1 al 7.4 | nada, arranca de una |
| Frontend | 3, 4 | la fase 1 |
| Semántica y motor | 5, 6 | las fases 3 y 4 |
| Interfaz | 8 | la fase 6 |

Las fases 0 y 9 se hacen entre todos.
