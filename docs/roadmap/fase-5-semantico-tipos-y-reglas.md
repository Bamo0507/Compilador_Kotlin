# Fase 5 · Semántico II, tipos y reglas

**Objetivo:** que cada expresión quede con su tipo verificado, y que se cumplan
todas las reglas propias de SQL.

**Es la fase con más reglas del proyecto.** Cada una tiene su test, y los errores
salen **todos juntos**: uno no corta el análisis.

**Estimación:** tres sesiones.

---

## El orden real de evaluación, que es lo que explica casi todo

```
FROM      abre el ambito con los alias
  |
JOIN      combina y evalua el ON
  |
WHERE     filtra rows
  |
GROUP BY  agrupa
  |
HAVING    filtra grupos
  |
SELECT    proyecta columns y calcula alias
  |
DISTINCT  quita repetidos
  |
ORDER BY  ordena
  |
LIMIT     corta
```

No es el orden en que se escribe. La consecuencia concreta: **un alias definido en
el `SELECT` no se puede usar en el `WHERE`**, porque cuando corre el `WHERE` ese
alias todavía no existe. En `ORDER BY` sí, porque va después.

---

## Ticket 5.1 · Tipado de expresiones

- **Estado**: completado
- **Depende de**: 1.5, 4.3, 4.5

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (NUEVO)
- `app/src/test/.../ExpressionTypingTest.kt` (NUEVO)

**Qué es esto, en simple:** recorrer cada expresión de abajo hacia arriba
preguntándole a `TypeRules` si lo que se hace tiene sentido, y dejar el tipo
pegado en el nodo.

```kotlin
private fun typeOf(expression: Expression): Type {
    val type = when (expression) {
        is Literal -> expression.literalType
        is ColumnReference -> expression.symbol?.type ?: ErrorType
        is Binary -> {
            val left = typeOf(expression.left)
            val right = typeOf(expression.right)
            TypeRules.binaryResultType(expression.operator, left, right)
                ?: fail(expression.location, "no se puede aplicar ...")
        }
        // ...
    }
    expression.type = type
    return type
}
```

**Este es el atributo sintetizado del proyecto:** el tipo sube de los hijos al
padre. El ámbito, en cambio, es heredado: baja del padre a los hijos como campo
mutable con guardar y restaurar, igual que en Compiscript.

`ErrorType` corta cascadas: cualquier operación con un `ErrorType` da `ErrorType`
sin reportar de nuevo, así que `(1 + 'a') * 2` reporta **un** error.

**Los mensajes salen todos con la misma forma:**
`no se puede aplicar + entre INT y VARCHAR(1)`, con los dos tipos nombrados. Los
unarios tienen el suyo, porque "aplicar" suena raro con un solo operando:
`no se puede negar VARCHAR(80), se esperaba un tipo numerico`.

**Aceptación:**

- cada nodo de expresión queda con `type` distinto de `ErrorType` en SQL válido
- `1 + 'a'` reporta exactamente un error, no dos
- `DATE '2026-01-01' > TIME '10:00:00'` reporta incompatibilidad de familias
- una columna que no se resolvió no genera errores de tipo encima

---

## Ticket 5.2 · Reglas de la consulta

- **Estado**: completado
- **Depende de**: 5.1, 4.5

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `frontend/semantic/NameResolver.kt` (MODIFICA, la regla del alias)
- `app/src/test/.../QueryRulesTest.kt` (NUEVO)

| Regla | Mensaje |
|---|---|
| `WHERE` da `BOOLEAN` | `WHERE espera BOOLEAN, recibio INT` |
| `ON` da `BOOLEAN` | `la condicion del JOIN espera BOOLEAN` |
| `HAVING` da `BOOLEAN` | `HAVING espera BOOLEAN` |
| un alias del `SELECT` no se usa en `WHERE` | `'total' se define en el SELECT y no esta disponible en WHERE` |
| un alias del `SELECT` **sí** se usa en `ORDER BY` | |
| `LIMIT` sin `ORDER BY` | advertencia: `LIMIT sin ORDER BY no garantiza que filas salen` |
| `LIMIT` es un entero positivo | `LIMIT espera un entero mayor que cero` |
| alias de salida repetidos | `hay dos columnas llamadas 'total' en el resultado` |
| `= NULL` en vez de `IS NULL` | advertencia: `= NULL nunca es verdadero, se esperaba IS NULL` |

**La regla del alias terminó en el `NameResolver`, no aquí.** El checker no puede
hacerla cumplir porque no sabe qué nombres fallaron: cuando le llega la expresión,
la referencia ya quedó sin símbolo. Quien lo sabe es el resolutor, en el momento
exacto en que la búsqueda no encuentra nada, así que el mensaje se arma ahí. Cada
cláusula se resuelve sabiendo su nombre y los alias que el `SELECT` declara, y de
ahí salen los dos mensajes:

```
'doble' se define en el SELECT y no esta disponible en WHERE
no existe la columna 'doble'
```

El segundo es el de `SELECT id + 1 AS doble, doble + 1 FROM users`: dentro de la
misma lista el alias todavía no existe, así que ahí el nombre no se "define
después", simplemente no está.

### Las advertencias no detienen el script

Tres reglas de esta fase y la siguiente reportan con severidad `WARNING`, que
el ticket 0.6 agregó a `CompilerError`: `= NULL`, `LIMIT` sin `ORDER BY`, y
`UPDATE` o `DELETE` sin `WHERE`.

`Diagnostics.hasErrors` cuenta **solo** las de severidad `ERROR`, así que el
pipeline ejecuta igual. Si contara las advertencias, serían errores con otro
nombre.

**Sobre la advertencia de `= NULL`:** en SQL, `x = NULL` da `NULL`, no `true`, así
que la condición nunca se cumple. Es un error que la gente escribe todo el tiempo
y que el motor no puede detectar en ejecución, porque técnicamente es válido.

**Sobre `LIMIT` sin `ORDER BY`:** el resultado es no determinista. Aquí las filas
salen en el orden del CSV, así que en la práctica es estable, pero depender de eso
es depender de un detalle de implementación. La advertencia lo dice.

**Por qué la regla del alias del `SELECT` es la que mejor demuestra el orden de
evaluación:** es la pregunta obvia de la defensa, y la respuesta es el diagrama de
arriba.

**La regla de los alias repetidos mira solo los `AS` escritos.** Que un
`SELECT *` sobre un join repita un nombre de columna es normal y no se toca.

**Aceptación:** una prueba por fila de la tabla, más

- un script con solo advertencias **se ejecuta**, y `hasErrors` es `false`
- la advertencia aparece igual en la lista de errores, con su severidad
- `SELECT *` sobre un join con un nombre repetido no reporta

---

## Ticket 5.3 · Reglas de agregación

- **Estado**: completado
- **Depende de**: 5.2

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `frontend/ast/models/Expression.kt` (MODIFICA, la etiqueta de cada función)
- `app/src/test/.../AggregationRulesTest.kt` (NUEVO)

**Es la regla semántica más interesante del proyecto**, porque no se resuelve
mirando un nodo aislado: necesita el contexto de toda la consulta.

### La regla principal

En una consulta con agregación, **toda columna del `SELECT` debe estar en el
`GROUP BY` o dentro de una función de agregación**.

```sql
-- correcto
SELECT depto, COUNT(*) FROM users GROUP BY depto;

-- ERROR: name no esta agrupado ni agregado
SELECT depto, name, COUNT(*) FROM users GROUP BY depto;
```

Por qué es un error y no una elección arbitraria: si un departamento tiene cinco
empleados, hay **un** grupo y **cinco** nombres distintos. La consulta no dice
cuál de los cinco debería salir, así que no tiene respuesta.

### Las demás

| Regla | Mensaje |
|---|---|
| Una consulta es agregada si tiene `GROUP BY` o alguna función de agregación | |
| `HAVING` sin agregación | `HAVING solo tiene sentido con GROUP BY` |
| Agregación dentro de agregación | `no se puede anidar COUNT dentro de SUM` |
| Agregación en el `WHERE` | `WHERE no puede usar funciones de agregacion, se esperaba HAVING` |
| `SUM` y `AVG` sobre no numérico | `SUM espera un tipo numerico, recibio VARCHAR(80)` |
| `MIN` y `MAX` sobre lógico | `MIN no acepta BOOLEAN` |

### Tipos de retorno

| Función | Sobre | Devuelve |
|---|---|---|
| `COUNT` | cualquiera | `INT` |
| `SUM` | `INT` | `INT` |
| `SUM` | `DECIMAL(p,s)` | `DECIMAL(p,s)` |
| `SUM` | `FLOAT` | `FLOAT` |
| `AVG` | cualquier numérico | `FLOAT` |
| `MIN` y `MAX` | numérico, carácter o temporal | el mismo tipo |

**Por qué `AVG` siempre da `FLOAT`:** el promedio de enteros casi nunca es entero,
y devolver `INT` truncando sería una sorpresa silenciosa.

### Dos cosas que este ticket no listaba

**La regla alcanza también al `HAVING` y al `ORDER BY`.** `HAVING name = 'ana'`
sobre una consulta agrupada por `city` tiene el mismo problema que ponerlo en el
`SELECT`: hay un grupo y cinco nombres. Es el mismo chequeo y la misma función, así
que cubrirlos fue gratis. Las referencias a un alias del `SELECT` quedan fuera,
porque esas nombran la fila de salida.

**`SELECT *` con `GROUP BY`.** El `*` se expande a todas las columnas y casi
ninguna está agrupada, así que en vez de un error por columna sale uno solo:
`'*' no se puede usar en una consulta agrupada, nombre las columnas`.

`COUNT(*)` y `COUNT(x)` son dos nodos distintos del AST pero la misma función para
quien lee el error, así que `AggregateFunction` ganó una etiqueta y los dos dicen
`COUNT`.

**Aceptación:** una prueba por regla, más el caso de que `SELECT COUNT(*) FROM
users` sin `GROUP BY` es válido, porque agrupa todo en un solo grupo.

---

## Ticket 5.4 · Reglas de subconsulta

- **Estado**: completado
- **Depende de**: 5.2

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `frontend/semantic/symbols/Scope.kt` (MODIFICA, `redeclare`)
- `app/src/test/.../SubqueryRulesTest.kt` (NUEVO)

| Forma | Regla | Mensaje |
|---|---|---|
| escalar `(SELECT ...)` | exactamente **una columna** | `la subconsulta debe devolver una sola columna, devuelve 3` |
| `IN (SELECT ...)` | una columna, de tipo comparable con el lado izquierdo | `no se puede comparar INT con VARCHAR(80)` |
| `EXISTS (SELECT ...)` | cualquier forma | |
| `FROM (SELECT ...) x` | alias obligatorio, ya lo exige la gramática | |
| `FROM (SELECT ...) x` | toda columna con nombre | `la columna 2 de la subconsulta no tiene nombre, use AS` |

**Sobre "a lo sumo una fila" en la escalar:** eso **no** se puede verificar en el
análisis, porque depende de los datos. Se verifica en ejecución (fase 6) y el
mensaje es `la subconsulta devolvio 4 filas, se esperaba a lo sumo una`.

Es un buen ejemplo de la división entre las dos etapas: la **forma** se valida sin
datos, la **cantidad** necesita leerlos.

**Sobre la columna sin nombre en una tabla derivada:** si escribes
`FROM (SELECT edad * 2 FROM users) x`, la segunda columna no tiene cómo llamarse,
así que `x.algo` no puede funcionar. El `AS` es obligatorio para expresiones, no
para referencias simples a columna, que heredan su nombre.

**Refrescar el esquema de una tabla derivada:** la fase 4 declara sus columnas con
`schemaOf`, pero una columna calculada queda en `ErrorType` porque todavía no se
tipaba nada. Al terminar de tipar una subconsulta del `FROM`, este ticket vuelve a
pedirle el esquema y reemplaza la `Table` del símbolo del origen, con
`Scope.redeclare`.

Reemplazar el símbolo no alcanza: las referencias de la consulta de afuera ya
quedaron pegadas al esquema viejo. Por eso, después de refrescar, se repasan las
referencias de esa consulta que siguen en `ErrorType` y se vuelven a resolver
contra el ámbito, que ahora sí tiene los tipos. Solo se tocan esas, así que una
referencia que ya estaba bien no se mueve.

**Aceptación:** una prueba por fila, una subconsulta de tres niveles válida pasa
sin errores, y `FROM (SELECT edad * 2 AS doble FROM users) x` deja `x.doble` con
tipo.

---

## Ticket 5.5 · Reglas de DML y DDL

- **Estado**: completado
- **Depende de**: 5.1

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `frontend/semantic/CatalogEvolution.kt` (NUEVO)
- `frontend/semantic/NameResolver.kt` (MODIFICA, el WHERE del DML)
- `app/src/test/.../DmlDdlRulesTest.kt` (NUEVO)

### Dos huecos de la fase 4 que este ticket destapó

**El catálogo tiene que avanzar con el script.** `CREATE TABLE t (...);` seguido de
`INSERT INTO t ...` es lo primero que escribe cualquiera, y con un catálogo fijo la
segunda línea reportaba que la tabla no existe. De ahí sale `Catalog.after`, que
devuelve el catálogo como queda después de una sentencia y que usan por igual el
resolutor y el checker:

```kotlin
fun Catalog.after(statement: Statement): Catalog
```

**El `WHERE` de un `UPDATE` o un `DELETE` nombra columnas.** La fase 4 solo
recorría los `SELECT`, así que esas referencias llegaban sin símbolo y no había
nada que tipar. Ahora el resolutor abre un ámbito con la tabla del `UPDATE` o el
`DELETE` y resuelve su `WHERE` y sus asignaciones, igual que haría con un `FROM` de
una sola tabla. Si la tabla no existe no resuelve nada, para que un nombre mal
escrito no dé además un error por cada columna.

La guarda de cordura estructural del ticket 4.2 pasó a correr también sobre estas
tablas, que es donde dice el propio 4.2 que haría falta. Una tabla creada en el
mismo script todavía no tiene archivo, así que la guarda la salta.

### `INSERT`

| Regla | Mensaje |
|---|---|
| La tabla existe | `la tabla 'usuarios' no existe` |
| La cantidad de valores cuadra | `se esperaban 3 valores, se recibieron 2` |
| Cada valor es asignable a su columna | `no se puede guardar VARCHAR en la columna 'edad' de tipo INT` |
| Un literal de texto cabe en el largo | `'abcdefgh' no cabe en VARCHAR(5)` |
| Columnas omitidas tienen `DEFAULT`, `AUTOINCREMENT` o aceptan nulos | `la columna 'name' no acepta nulos y no tiene valor por omision` |
| Las columnas nombradas existen y no se repiten | `la columna 'id' se nombra dos veces` |
| Un `NULL` explícito en una columna que no los acepta | `la columna 'name' no acepta nulos` |

El chequeo de largo se hace **solo sobre literales**, porque una expresión como
`name || apellido` no tiene largo conocido hasta ejecutarla. Ese caso lo atrapa la
validación de escritura de la fase 7.

### `UPDATE` y `DELETE`

| Regla | Mensaje |
|---|---|
| La tabla existe | |
| Cada columna asignada existe y no se repite | |
| El valor asignado es del tipo de la columna | |
| No se asigna a una columna `AUTOINCREMENT` | `'id' es AUTOINCREMENT y no se puede asignar` |
| El `WHERE` da `BOOLEAN` | |
| `UPDATE` o `DELETE` sin `WHERE` | advertencia: `esto afecta todas las filas de 'users'`, y no detiene el script |

### `CREATE TABLE`

| Regla | Mensaje |
|---|---|
| La tabla no existe ya | `la tabla 'users' ya existe` |
| Nombres de columna no repetidos | |
| A lo sumo una `PRIMARY KEY` | `'users' declara dos claves primarias` |
| `NOT NULL` y `NULL` no juntos | `'name' se declara NOT NULL y NULL a la vez` |
| `AUTOINCREMENT` solo sobre `INT` | `AUTOINCREMENT solo aplica a INT` |
| `DEFAULT` del tipo de la columna | `el valor por omision de 'edad' no es INT` |
| `DEFAULT` y `AUTOINCREMENT` no juntos | `'id' no puede tener DEFAULT y AUTOINCREMENT a la vez` |
| `REFERENCES` apunta a tabla y columna que existen | |
| La columna referenciada es `PRIMARY KEY` o `UNIQUE` | `'posts.uid' referencia 'users.name', que no es unica` |
| Los tipos de la FK coinciden | `'posts.uid' es VARCHAR(5) y 'users.id' es INT` |
| `DECIMAL(p,s)` con `s <= p` y ambos positivos | `DECIMAL(2,5) no es valido: la escala excede la precision` |
| `CHAR(n)` y `VARCHAR(n)` con `n > 0` | `CHAR(0) no es valido: el largo debe ser mayor que cero` |

### `ALTER TABLE`

| Regla | Mensaje |
|---|---|
| La tabla existe | |
| `ADD` de columna que ya existe | `'users' ya tiene una columna 'edad'` |
| **`ADD` de `NOT NULL` sin `DEFAULT` sobre tabla con filas** | `agregar 'edad' NOT NULL dejaria 12 filas invalidas, use DEFAULT` |
| `DROP` de columna que no existe | |
| `DROP` de columna referenciada por una FK | `'users.id' es referenciada por 'posts.uid'` |
| `DROP` de la última columna | `una tabla no puede quedarse sin columnas` |

**La regla del `ADD NOT NULL` es la única del DDL que necesita mirar los datos**,
porque depende de si la tabla tiene filas. Se resuelve contando las líneas del
CSV, sin decodificar nada.

### `DROP TABLE`

| Regla | Mensaje |
|---|---|
| La tabla existe | |
| Ninguna otra tabla la referencia | `'users' es referenciada por 'posts.uid'` |

### `CREATE INDEX` y `DROP INDEX`

Sus reglas viven en el **ticket 8.5**, junto al resto de los índices. Dos de ellas
tocan sentencias de esta fase: `ALTER TABLE ... DROP COLUMN` sobre una columna
indexada falla, y `DROP TABLE` se lleva los `.idx` de la tabla.

**Una tabla no se estorba a sí misma.** Si una tabla se referencia a sí misma, esa
FK no impide borrarla ni quitarle la columna: las referencias que bloquean son las
de las demás.

**Aceptación:** una prueba por fila de las cinco tablas, más que una tabla creada
arriba se pueda usar abajo y que una borrada arriba ya no exista abajo. Son 45
casos y es la batería más grande de la fase, pero cada uno es de tres líneas.
