# Fase 8 · Interfaz

**Objetivo:** que todo el alcance se pueda hacer sin salir de la ventana.

**Es la fase más grande en líneas y la más fácil en dificultad**, porque es Compose
y no algoritmos. Y es más sustitución que construcción: la ventana que quedó de
Compiscript sirve casi completa.

| Panel de Compiscript | Qué pasa |
|---|---|
| Editor de código | igual, con resaltado de SQL |
| Lista de errores | igual, sin tocar |
| Salida de texto | se vuelve una **rejilla de resultados** |
| Tabla de símbolos | se vuelve **vista del catálogo** |
| Árbol sintáctico y AST | vuelven, ahora del query |
| Reporte de vivacidad | eliminado, no tiene análogo |

**Estimación:** dos o tres sesiones.

---

## Ticket 8.1 · Rejilla de resultados

- **Estado**: pendiente
- **Depende de**: 7.5

**Archivos:**

- `gui/components/ResultGrid.kt` (NUEVO)
- `gui/components/OutputConsole.kt` (ELIMINA)
- `gui/screens/WorkspaceScreen.kt` (MODIFICA)
- `app/src/test/.../ResultGridTest.kt` (NUEVO)

**Qué muestra:** el `ResultSet` de cada `SELECT` del script, con encabezados y
tipos. Si el script tiene tres `SELECT`, salen tres rejillas una debajo de otra,
cada una con su número de sentencia y su conteo de filas.

Para las sentencias que no consultan, una línea de resumen:

```
INSERT INTO users        2 filas insertadas
UPDATE users             5 filas modificadas
DELETE FROM posts        0 filas borradas
CREATE TABLE eventos     tabla creada
```

### Detalles que importan

- **`NULL` se muestra distinto de la cadena vacía**: en gris y en cursiva. Es la
  misma distinción que el CSV guarda con el campo vacío contra `""`, y si la
  rejilla las muestra igual, se pierde
- **alineación por familia**: los numéricos a la derecha, el resto a la izquierda
- **ancho de columna por contenido medido**, con un tope, y desbordamiento con
  elipsis. Es el mismo problema que ya se resolvió en `TreeCanvas`: un ancho fijo
  en píxeles no sobrevive a la densidad de pantalla
- **scroll en los dos ejes**, con el encabezado fijo arriba
- si el `ResultSet` viene vacío, `sin resultados` y no una rejilla en blanco

**Aceptación:**

- un `SELECT` de tres columnas y dos filas se ve completo
- un `NULL` se distingue visualmente de `''`
- 1,000 filas no congelan la ventana
- tres `SELECT` en un script dan tres rejillas

---

## Ticket 8.2 · Vista del catálogo

- **Estado**: pendiente
- **Depende de**: 8.1

**Archivos:**

- `gui/screens/CatalogScreen.kt` (NUEVO)
- `gui/components/CatalogTree.kt` (NUEVO)
- `app/src/test/.../CatalogTreeTest.kt` (NUEVO)

**Es la vista que más valor tiene por lo poco que cuesta.** Un catálogo es un
árbol de dos niveles: tablas arriba, columnas debajo. El patrón de
`ScopeTreeView`, que se borró en la fase 0, sirve tal cual y vale la pena
recuperarlo del historial de `main`.

```
datos/
├── users                    3 columnas · 12 filas
│   ├── id       INT          PRIMARY KEY, AUTOINCREMENT
│   ├── name     VARCHAR(80)  NOT NULL
│   └── edad     INT
└── posts                    3 columnas · 40 filas
    ├── id       INT          PRIMARY KEY
    ├── uid      INT          NOT NULL, -> users.id
    └── titulo   TEXT
```

- el conteo de filas se lee del CSV sin decodificar, contando líneas
- la flecha de una FK es clicable y salta a la columna referenciada
- las tablas con problemas del `CatalogLoader` salen marcadas, con el mensaje
- la vista se refresca al correr un script, porque un `CREATE` la cambia

**Por qué esta pantalla es la que se enseña en la defensa:** demuestra que el
catálogo existe como estructura y no como una lectura improvisada del disco, y
demuestra que las restricciones se guardan de verdad.

**Aceptación:**

- un directorio con dos tablas se ve completo con sus restricciones
- un `CREATE` en el editor y luego correr refresca la vista
- una tabla sin `.json` sale marcada con su error
- directorio vacío muestra un mensaje, no un árbol en blanco

---

## Ticket 8.3 · Árboles del query

- **Estado**: pendiente
- **Depende de**: 8.1

**Archivos:**

- `gui/components/TreeCanvas.kt`, `TreeLayout.kt` (NUEVOS, recuperados de `main`)
- `frontend/ast/AstView.kt` (NUEVO)
- `gui/screens/TreesScreen.kt` (NUEVO)

Se recuperan de `main` los dos archivos de dibujo, que no saben de qué lenguaje
viene el árbol: trabajan sobre `TreeNodeView`.

**`TreeNodeView` y `ParseTreeView` ya existen desde el ticket 3.4**, porque el
pipeline los necesita desde la fase 3. Aquí solo falta `AstView`, la conversión
del AST propio, y el dibujo.

**Lo que ya está resuelto y no hay que volver a resolver**, porque costó en el
proyecto anterior:

- `clipToBounds()`, sin él los árboles se desbordan del panel
- el ancho de columna se deriva del texto medido, no de una constante en píxeles
- el alto del nodo también
- `PreparedTree` precalcula posiciones, texto medido y aristas una sola vez
- recorte por rectángulo visible, y umbral de escala para dejar de dibujar texto

**El AST de una consulta con subconsultas es profundo**, así que el árbol crece
hacia abajo más que los de Compiscript. Vale la pena que el `AstView` colapse los
nodos de expresión triviales, como un literal solo, en una sola etiqueta.

**Aceptación:**

- el parse tree y el AST de un `SELECT` con `JOIN` se dibujan sin desbordarse
- el arrastre y el zoom no recomponen, solo redibujan
- un árbol de 500 nodos se mueve fluido

---

## Ticket 8.4 · Resaltado y selector de scripts

- **Estado**: pendiente
- **Depende de**: 8.1

**Archivos:**

- `gui/components/SqlHighlighter.kt` (NUEVO)
- `gui/components/CodeEditor.kt` (MODIFICA)
- `gui/components/ScriptSelector.kt` (NUEVO, del `ProgramSelector` de `main`)
- `samples/SqlScripts.kt` (NUEVO)

El resaltado se hace con `buildAnnotatedString` sobre el texto del editor: las
palabras clave en un color, los literales de texto en otro, los comentarios en
gris. Se apoya en el lexer de ANTLR, que ya sabe clasificar cada token, así que no
hay que escribir un segundo analizador.

**Cuidado con el medianil de números:** es un solo `Text` con `buildAnnotatedString`
y no un `Text` por línea. Un `Text` por línea se mide por métricas de fuente y no
por el `lineHeight` del campo, y los números se desalinean. Ya pasó una vez.

`ScriptSelector` se recupera del `ProgramSelector` de `main`: mismo diseño, cinco
opciones visibles y scroll. Lee los `.sql` de `src/main/resources/scripts/` con la
anotación `-- NOMBRE:` para el nombre visible.

**Ese directorio está vacío hasta la fase 9**, así que el selector se prueba con
dos o tres scripts de juguete y muestra solo Default y En blanco. No bloquea.

**Aceptación:**

- las palabras clave se resaltan sin importar mayúsculas o minúsculas
- los números del medianil quedan alineados con las líneas
- el selector muestra cinco y hace scroll
- con el directorio de scripts vacío no truena, muestra solo las dos opciones
  fijas
- elegir un script lo carga y limpia el resultado anterior

---

## Ticket 8.5 · Estado y panel de cambios

- **Estado**: pendiente
- **Depende de**: 8.1, 8.2

**Archivos:**

- `gui/state/AppState.kt` (MODIFICA)
- `gui/components/ChangeSummary.kt` (NUEVO)
- `gui/App.kt` (MODIFICA)
- `app/src/test/.../AppStateTest.kt` (NUEVO)

`AppState` gana lo del catálogo y pierde lo de Compiscript:

```kotlin
var catalogo by mutableStateOf<Catalog?>(null)
    private set
var resultados by mutableStateOf<List<ResultSet>>(emptyList())
    private set
```

La lista de errores muestra advertencias y errores juntos, con distinto color,
leyendo `CompilerError.severidad` del ticket 0.6. Un script con solo advertencias
se ejecuta y la rejilla se llena igual: si el usuario no ve la diferencia entre
las dos severidades, la distinción no sirve de nada.

### El panel de cambios

Es donde se ve la decisión 5 en la interfaz, que es lo que pediste: que el aviso
sea evidente.

**Si el script salió bien:**

```
Se escribieron 2 archivos
  datos/users.csv      2 filas agregadas
  datos/eventos.json   tabla creada
```

**Si algo falló:**

```
No se escribio nada
  la sentencia 4 fallo: ya existe una fila con id = 1
  las 3 sentencias anteriores se descartaron
  datos/ quedo sin cambios
```

Ese segundo mensaje es el que importa. Sin él, el usuario ve un error y no sabe si
las primeras tres sentencias se aplicaron o no, que es exactamente la duda que la
decisión 5 resuelve.

**Aceptación:**

- un script con solo advertencias se ejecuta, y las advertencias se ven distintas
  de los errores
- un script bueno lista los archivos escritos
- un script que falla a la mitad dice que no se escribió nada y por qué
- correr no congela la ventana, va en `Dispatchers.Default`
- el botón se deshabilita al primer clic, antes de arrancar el hilo de fondo
