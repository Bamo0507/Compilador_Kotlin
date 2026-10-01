# Fase 10 · Batería y documentación

**Objetivo:** dejar el proyecto entregable y defendible.

**Estimación:** dos sesiones.

---

## Ticket 10.1 · Scripts válidos

- **Estado**: pendiente
- **Depende de**: 9.6

**Archivos:**

- `app/src/main/resources/scripts/validos/*.sql` (NUEVOS)
- `app/src/test/.../ScriptsValidosTest.kt` (NUEVO)

**El formato de anotaciones**, el mismo que usaban los `.cps`:

```sql
-- NOMBRE: Join con agregacion y HAVING
-- ESPERADO: sin errores
-- FILAS: 3

CREATE TABLE users (
  id    INT PRIMARY KEY AUTOINCREMENT,
  name  VARCHAR(80) NOT NULL,
  depto VARCHAR(40)
);
...
```

**Por qué los `.sql` viven en `main` y no en `test`:** `src/test/resources` no
existe en tiempo de ejecución, así que el selector del IDE no podría leerlos. Los
recursos de `main` sí están en el classpath de los tests, así que hay **una sola
copia** que el IDE muestra y que los tests verifican. Es la misma lección del
proyecto anterior.

### Cobertura mínima

| Grupo | Cuántos | Qué cubre |
|---|---|---|
| DDL | 4 | `CREATE` con las 7 restricciones, los dos `ALTER`, `DROP` |
| DML | 4 | `INSERT` múltiple, con columnas nombradas, `UPDATE`, `DELETE` |
| Query | 5 | `SELECT *`, alias, `ORDER BY`, `DISTINCT`, `LIMIT` |
| Join | 3 | dos tablas, tres tablas, autojoin |
| Agregación | 5 | las 5 funciones, `GROUP BY`, `HAVING`, `COUNT(*)` contra `COUNT(col)` |
| Subconsulta | 5 | escalar, `IN`, `EXISTS`, correlacionada, tabla derivada |
| Tipos | 4 | los 11 tipos, nulos, `DECIMAL` exacto, fechas |
| Índices | 4 | `CREATE INDEX`, `DROP INDEX`, `WHERE` con igualdad, `WHERE` con rango |
| **Total** | **34** | |

**Cada test corre sobre su propio `DataDirectory`**, construido con `@TempDir` de
JUnit, como quedó en el ticket 0.5. Es lo que permite que un script cree tablas y
las llene sin tocar el `data/` real ni depender del orden en que corran los
tests.

El script se copia al temporal, se ejecuta, y se compara el resultado contra las
anotaciones. Ninguna prueba usa `DataDirectory.DEFAULT`.

**Aceptación:** los 30 corren sin errores, el conteo de filas cuadra con la
anotación, y el `data/` del repo queda intacto después de la suite completa.

---

## Ticket 10.2 · Scripts inválidos

- **Estado**: pendiente
- **Depende de**: 10.1

**Archivos:**

- `app/src/main/resources/scripts/invalidos/*.sql` (NUEVOS)
- `app/src/test/.../ScriptsInvalidosTest.kt` (NUEVO)

```sql
-- NOMBRE: Columna del SELECT sin agrupar
-- ESPERADO: error en linea 3
-- MENSAJE: no esta en el GROUP BY

SELECT depto, name, COUNT(*)
  FROM users
 GROUP BY depto;
```

**Uno por cada regla de la fase 5**, que son alrededor de 35, más los casos de
error de ejecución de la fase 7.

| Grupo | Cuántos |
|---|---|
| Sintaxis | 3 |
| Nombres: tabla, columna, alias ambiguo | 5 |
| Tipos | 5 |
| Query: `WHERE` no lógico, alias en `WHERE` | 4 |
| Agregación | 6 |
| Subconsulta | 5 |
| DML y DDL | 10 |
| Índices: inexistente, duplicado, compuesto, nombre reservado | 5 |
| Ejecución: PK, `NOT NULL`, FK, escalar con dos filas | 5 |
| **Total** | **48** |

Aparte van tres scripts de **solo advertencias**, con `= NULL`, `LIMIT` sin
`ORDER BY` y `DELETE` sin `WHERE`. Su anotación es distinta: `ESPERADO: advertencia`
más `EJECUTA: si`, porque lo que prueban es justamente que el script **sí** corre.

**Los tests verifican la línea y un fragmento del mensaje**, no el texto exacto,
para que mejorar la redacción de un error no rompa la suite.

**Aceptación:** cada script reporta al menos el error esperado, en la línea
esperada, ninguno lanza una excepción no atrapada, los tres de advertencia sí se
ejecutan, y **ninguno de los 43 deja el directorio temporal modificado**, que es
la prueba de la decisión 5 a escala de toda la batería.

---

## Ticket 10.3 · README y diagrama

- **Estado**: pendiente
- **Depende de**: 10.2

**Archivos:**

- `README.md` (MODIFICA)
- `docs/arquitectura.excalidraw` (NUEVO)

### El README

- qué es el proyecto y qué hace
- cómo correrlo, con el `JAVA_HOME` de Corretto 21
- el alcance del SQL, con la tabla del roadmap
- el formato de los dos archivos por tabla, con un ejemplo de cada uno
- las nueve decisiones de diseño, en una línea cada una
- las simplificaciones conscientes, que son las que preguntan en la defensa:
  - join por bucles anidados, sin índices
  - `AUTOINCREMENT` reusa números si se borra la última fila
  - el volcado no es atómico entre tablas
  - la escala de `DECIMAL` al multiplicar no sigue el estándar
  - el paquete raíz sigue llamándose `org.compiler`
  - el volcado no usa diario de transacciones, así que es atómico por tabla y no
    entre tablas

**Sin guiones como puntuación**, con comas.

### El diagrama

Las tres fases en vertical, con el catálogo entrando por un lado y el directorio
`data/` por el otro, y los paneles de diagnóstico y de errores de ejecución a un
costado. El de Compiscript sirve de plantilla, cambiando las cajas.

**Aceptación:** alguien que no conoce el proyecto lo clona, lee el README, lo
corre y hace un `SELECT` sin preguntar nada.
