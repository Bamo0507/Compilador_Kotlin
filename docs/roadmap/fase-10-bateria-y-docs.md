# Fase 10 · Batería y documentación

**Objetivo:** dejar el proyecto entregable y defendible.

**Estimación:** dos sesiones.

---

## Ticket 10.1 · Scripts válidos

- **Estado**: completado
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

### Lo que cambió

**Quedaron 40 y no 34.** Se agregó un grupo de condiciones, que el plan no tenía
como tema aparte y es lo que más se consulta en una defensa, y un script de
recorrido completo que ejercita todo el SQL de una sola corrida. Ese último es con
el que abre la ventana.

**Cada script es autosuficiente y se limpia solo.** Crea sus tablas, las llena,
las consulta y termina con `DROP TABLE`. El plan no lo pedía, pero sin eso el
selector del IDE no sirve: una opción del menú tiene que poder correrse en
cualquier orden, las veces que haga falta, y sobre una base vacía.

Sale gratis por la forma del volcado. Una tabla creada y borrada en el mismo
script nunca estuvo en el catálogo inicial, así que `changes()` no la lista ni
entre lo escrito ni entre lo borrado, y el disco no se entera de que existió. La
batería verifica el directorio antes y después, que de paso es otra prueba de la
decisión 5.

**`FILAS` cuenta el último `SELECT`**, no la suma de todos. Un script que ilustra
siete comparaciones tiene siete rejillas, y sumarlas no diría nada; la última es
la que cierra el caso.

**Los `.sql` viven en subcarpetas numeradas**, una por tema, y la carpeta es el
encabezado que agrupa al script en el menú. Con 95 opciones una lista plana no se
puede recorrer. `SqlScripts` pasó de `Files.list` a `Files.walk` y `SqlScript`
ganó un `group`; el selector dibuja un encabezado por grupo y el resto quedó igual.

---

## Ticket 10.2 · Scripts inválidos

- **Estado**: completado
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

### Lo que cambió

**Quedaron 52 inválidos.** Los de sintaxis verifican el texto de ANTLR tal como
sale, `missing 'FROM'` y `extraneous input`, porque esos mensajes no los redacta
el proyecto.

**Las dos suites se arman con `@TestFactory`.** Un caso por archivo, construido al
leer la carpeta, así que agregar un `.sql` agrega su prueba y su opción del menú
sin tocar Kotlin. Cada caso corre sobre su propio subdirectorio de `@TempDir`.

### Un hueco que la batería destapó

El caso de agregar una columna `NOT NULL` sobre una tabla con filas **no
reportaba nada**. La regla de la fase 5 cuenta las filas leyendo el `.csv`, y en
un script que crea la tabla y la llena en la misma corrida ese archivo todavía no
existe, así que contaba cero. El escritor tampoco validaba: rellenaba con el valor
por omisión, que sin `DEFAULT` es nulo, y volcaba nulos a una columna que no los
acepta.

Se cerró en `Writer.addColumn`, que es donde están las filas de verdad. Es el
mismo reparto que ya rige para el largo de un texto: el analizador revisa lo que
puede saber del script, y el motor revisa lo que solo se sabe con los datos
delante.

---

## Ticket 10.3 · README y diagrama

- **Estado**: completado
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

### Lo que cambió

El README quedó reescrito de cero: el que había era el de Compiscript. Lleva las
trece decisiones en una línea cada una, no nueve, porque las fases 7, 8 y 9
agregaron cuatro. Las simplificaciones conscientes también crecieron, con el
`WHERE` que usa un solo índice y el índice que se usa sin medir si conviene.

El ejemplo de los dos archivos por tabla no está escrito a mano: sale de correr el
pipeline sobre una tabla con las cuatro restricciones y copiar lo que dejó en el
disco.

**El diagrama se armó caja por caja**, no desde Mermaid: la conversión de Mermaid
a Excalidraw necesita el lienzo abierto en el navegador, y el servidor no la puede
hacer solo. A cambio quedó con el reparto que el plan pedía, las tres bandas del
pipeline en vertical, el directorio `data/` entrando por la izquierda y saliendo
por abajo, y los diagnósticos a la derecha camino al IDE. Queda en
`docs/arquitectura.excalidraw` para editarlo y en `docs/arquitectura.png` para el
README y el informe.
