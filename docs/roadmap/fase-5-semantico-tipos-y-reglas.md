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

- **Estado**: pendiente
- **Depende de**: 1.5, 4.3, 4.5

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (NUEVO)
- `app/src/test/.../TipadoExpresionesTest.kt` (NUEVO)

**Qué es esto, en simple:** recorrer cada expresión de abajo hacia arriba
preguntándole a `TypeRules` si lo que se hace tiene sentido, y dejar el tipo
pegado en el nodo.

```kotlin
private fun tipar(expression: Expression): Type {
    val type = when (expression) {
        is Literal -> expression.tipoLiteral
        is ColumnReference -> expression.symbol?.type ?: ErrorType
        is Binary -> {
            val izq = tipar(expression.izquierda)
            val der = tipar(expression.derecha)
            TypeRules.binaryResultType(expression.operator, izq, der)
                ?: reportar(expression, "no se puede aplicar ...")
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

**Aceptación:**

- cada nodo de expresión queda con `type` distinto de `ErrorType` en SQL válido
- `1 + 'a'` reporta exactamente un error, no dos
- `WHERE fecha > TIME '10:00:00'` reporta incompatibilidad de familias

---

## Ticket 5.2 · Reglas de la consulta

- **Estado**: pendiente
- **Depende de**: 5.1, 4.5

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `app/src/test/.../ReglasConsultaTest.kt` (NUEVO)

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

**Las dos reglas de alias las hace cumplir `schemaOf`**, del ticket 4.5: él es
quien sabe qué nombres produce el `SELECT` y qué cláusulas pueden verlos.

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

**Aceptación:** una prueba por fila de la tabla, más

- un script con solo advertencias **se ejecuta**, y `hasErrors` es `false`
- la advertencia aparece igual en la lista de errores, con su severidad

---

## Ticket 5.3 · Reglas de agregación

- **Estado**: pendiente
- **Depende de**: 5.2

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `app/src/test/.../ReglasAgregacionTest.kt` (NUEVO)

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

**Aceptación:** una prueba por regla, más el caso de que `SELECT COUNT(*) FROM
users` sin `GROUP BY` es válido, porque agrupa todo en un solo grupo.

---

## Ticket 5.4 · Reglas de subconsulta

- **Estado**: pendiente
- **Depende de**: 5.2

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `app/src/test/.../ReglasSubconsultaTest.kt` (NUEVO)

| Forma | Regla | Mensaje |
|---|---|---|
| escalar `(SELECT ...)` | exactamente **una columna** | `la subconsulta debe devolver una sola columna, devuelve 3` |
| `IN (SELECT ...)` | una columna, de tipo comparable con el lado izquierdo | `no se puede comparar INT con VARCHAR(80)` |
| `EXISTS (SELECT ...)` | cualquier forma | |
| `FROM (SELECT ...) x` | alias obligatorio | `la subconsulta del FROM necesita un alias` |
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

**Aceptación:** una prueba por fila, y una subconsulta de tres niveles válida pasa
sin errores.

---

## Ticket 5.5 · Reglas de DML y DDL

- **Estado**: pendiente
- **Depende de**: 5.1

**Archivos:**

- `frontend/semantic/SqlChecker.kt` (MODIFICA)
- `app/src/test/.../ReglasDmlDdlTest.kt` (NUEVO)

### `INSERT`

| Regla | Mensaje |
|---|---|
| La tabla existe | `la tabla 'usuarios' no existe` |
| La cantidad de valores cuadra | `se esperaban 3 valores, se recibieron 2` |
| Cada valor es asignable a su columna | `no se puede guardar VARCHAR en la columna 'edad' de tipo INT` |
| Un literal de texto cabe en el largo | `'abcdefgh' no cabe en VARCHAR(5)` |
| Columnas omitidas tienen `DEFAULT`, `AUTOINCREMENT` o aceptan nulos | `la columna 'name' no acepta nulos y no tiene valor por omision` |
| Las columnas nombradas existen y no se repiten | |

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
| `DEFAULT` y `AUTOINCREMENT` no juntos | |
| `REFERENCES` apunta a tabla y columna que existen | |
| La columna referenciada es `PRIMARY KEY` o `UNIQUE` | `'posts.uid' referencia 'users.name', que no es unica` |
| Los tipos de la FK coinciden | |
| `DECIMAL(p,s)` con `s <= p` y ambos positivos | `DECIMAL(2,5) no es valido: la escala excede la precision` |
| `CHAR(n)` y `VARCHAR(n)` con `n > 0` | |

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

**Aceptación:** una prueba por fila de las cinco tablas. Son alrededor de 35
casos y es la batería más grande de la fase, pero cada uno es de tres líneas.
