# Fase 0: Preparación

**Objetivo de la fase:** dejar el terreno listo para generar código intermedio. El
roadmap del analizador semántico queda archivado con sus estados reales, los bugs
del semántico que afectan la generación quedan corregidos, la deuda de convenciones
y el repositorio quedan limpios, y el documento del lenguaje intermedio arranca con
su primera sección.

**Por qué va primero:** el generador de TAC confía en el AST decorado igual que el
intérprete. Dos de los bugs encontrados (el constructor de una subclase y los
miembros sin `this.`) dan hoy un resultado incorrecto sobre clases, y traducir
clases es parte de lo que se califica. Corregirlos después obligaría a corregir
también el generador.

**Al terminar:** un roadmap nuevo con su índice, el semántico corrigiendo los casos
encontrados con un test por cada uno, cero referencias a fases o tickets en el
código, y `docs/lenguaje-intermedio.md` con la sección 1.

**Teoría que la sostiene:** punto 1, por qué generar una representación intermedia
(presentación 06, diapositivas 7 a 9; Dragon Book 1.2 y 6.0).

---

## Ticket 0.1: Archivar el roadmap del analizador semántico

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `docs/roadmap/README.md` y `docs/roadmap/fase-0-limpieza-y-base.md` a
  `fase-8-pruebas-y-docs.md` (MOVER a `docs/roadmap/semantico/`)
- `docs/roadmap/semantico/README.md` (MODIFICAR: nota de archivo)
- `docs/roadmap/semantico/fase-6-ejecucion.md`, `fase-7-pipeline-e-ide.md`,
  `fase-8-pruebas-y-docs.md` (MODIFICAR: estados)
- `docs/roadmap/README.md` (NUEVO: índice de esta etapa)

### Qué se hace

**Mover con `git mv`**, no copiar y borrar, para que `git log --follow` conserve la
historia de cada documento:

```bash
mkdir -p docs/roadmap/semantico
git mv docs/roadmap/README.md docs/roadmap/semantico/README.md
for f in fase-0-limpieza-y-base fase-1-modelos fase-2-ast fase-3-declaraciones \
         fase-4-tipos fase-5-flujo-y-vivacidad fase-6-ejecucion \
         fase-7-pipeline-e-ide fase-8-pruebas-y-docs; do
  git mv "docs/roadmap/$f.md" docs/roadmap/semantico/
done
```

Los nombres van explícitos y no con un patrón, porque las fases de esta etapa también
empiezan con `fase-`.

**Corregir los estados** contra lo que existe en el código:

| Ticket | Estado anterior | Estado real | Evidencia |
|---|---|---|---|
| 6.1 | pendiente | completado | `RuntimeValue.kt`, `Environment.kt`, `EnvironmentTest` (8 tests) |
| 6.2 | pendiente | completado | `Interpreter.kt`, `InterpreterTest` (34 tests) |
| 7.4 | pendiente | completado | `SymbolTableScreen.kt`, `ScopeTreeView.kt`, `GarbageCollectorReportView.kt` |
| 8.1 | pendiente | completado | 38 `.cps`, `ProgramasValidosTest`, `ProgramasInvalidosTest` |
| 8.2 | pendiente | en progreso | README reescrito; falta `docs/arquitectura.md` |
| 8.3 | pendiente | pendiente | verificación manual no realizada |

**La nota de archivo**, al inicio de `semantico/README.md`:

- Este roadmap corresponde a la etapa del analizador semántico y está cerrado.
- Las decisiones 1 a 16 siguen vigentes; las nuevas se numeran desde la 17 en el
  README de esta etapa.
- Correcciones posteriores: la decisión 16 se complementa con la 20 (una subclase
  puede declarar un constructor con otra firma); la fila *"`nombre` sin `this.`
  dentro de un método"* del ticket 4.3 queda reemplazada por la decisión 20;
  `Especificaciones.md`, citado en varias fases, no está en el repositorio.
- 8.1 se desvió del plan: los `.cps` viven en `src/main/resources` y no en
  `src/test/resources`, para que el IDE los lea. La razón está en el `README.md`
  del proyecto.

**El README nuevo** lleva, en este orden:

1. Qué se entrega en esta etapa y la rúbrica (Diseño de CI 25, Generación de TAC 65,
   Tabla de Símbolos 10).
2. La dinámica de trabajo: cada punto de teoría se estudia primero, después se
   estructura su fase y sus tickets, y al cerrarla se pasa al siguiente punto.
3. El mapa de fases de esta etapa, con lo que deja cada una y la parte de la rúbrica que
   expone:

   | Fase | Qué se logra | Rúbrica |
   |---|---|---|
   | [0, Preparación](./fase-0-preparacion.md) | Roadmap archivado, semántico corregido, repo limpio | prerrequisito |
   | [1, Diseño del lenguaje intermedio](./fase-1-diseno-del-lenguaje-intermedio.md) | `Quadruple`, su sintaxis y su documento | Diseño de CI, 25 |
   | [2, Expresiones y temporales](./fase-2-expresiones-y-temporales.md) | El generador, el GDA y el pool de temporales | Generación de TAC, 65 |
   | [3, Control de flujo](./fase-3-control-de-flujo.md) | Condiciones con caída, sentencias, `switch` y `try/catch` | Generación de TAC, 65 |
   | [4, Tabla de símbolos y funciones](./fase-4-tabla-de-simbolos-y-funciones.md) | Zonas, tamaños, desplazamientos, registros de activación y funciones | Tabla de Símbolos, 10 |
   | [5, Objetos y listas](./fase-5-objetos-y-listas.md) | Objetos, métodos con despacho, listas y chequeos | Generación de TAC, 65 |
   | [6, El IDE](./fase-6-ide.md) | La pantalla de código intermedio | entregable |
   | [7, Batería y documentación](./fase-7-bateria-y-documentacion.md) | Los `.tac` dorados y los documentos finales | Generación de TAC, 65, y Diseño de CI, 25 |

   Y el mapa de los puntos de teoría, con la fase que salió de cada uno:

   | # | Punto de teoría | Fuente | Fase |
   |---|---|---|---|
   | 1 | Por qué una representación intermedia | 06 | Fase 0 |
   | 2 | GDA y número de valor | 06 | Fase 2 |
   | 3 | Código de tres direcciones: direcciones e instrucciones | 06 | Fase 1 |
   | 4 | Cuádruplos y tripletas | 06 | Fase 1 |
   | 5 | Temporales: asignación y reciclaje | Dragon Book 6 | Fase 2 |
   | 6 | Traducción de control de flujo | Dragon Book 6.6 a 6.8 | Fase 3 |
   | 7 | Subdivisión de la memoria en ejecución | 07 | Fase 4 |
   | 8 | Árboles de activación y la pila durante las llamadas | 07 | Fase 4 |
   | 9 | Registros de activación, secuencia de llamadas y enlaces de acceso | 07, Dragon Book 7.2 y 7.3 | Fase 4 |
   | 10 | Objetos, métodos y listas en memoria | Dragon Book 6.4 | Fase 5 |
   | 11 | Montículo, administrador de memoria y recolección de basura | 07, 08 | Fase 5 |

4. El formato de ticket y los principios de código, iguales a los del roadmap
   archivado.
5. La tabla de decisiones de esta etapa, desde la 17. Las que se tomen al estudiar
   los puntos de teoría se agregan aquí al momento de tomarlas:

   | # | Decisión | Elegida | Por qué |
   |---|---|---|---|
   | 17 | Dónde vive el generador | `frontend/intermediate/` | En la teoría, generar código intermedio es el último paso del front-end (Dragon Book 1.2). `backend/` queda para la fase de assembler. |
   | 18 | Cuándo corre | Etapa G, solo si no hubo errores | Paso 4 del enunciado: *"Si no existen errores, el generador recorre el árbol"*. Es la misma regla que ya sigue el intérprete. |
   | 19 | El intérprete | Se queda y convive | No estorba, la GUI ya muestra su salida, y la decisión de validar el TAC ejecutándolo se toma en el punto 3. |
   | 20 | Miembros de clase sin `this.` | No son visibles por nombre suelto | Es la regla de TypeScript, del que Compiscript es subconjunto. Hoy el verificador los aceptaba y el intérprete fallaba. |
   | 21 | Rango de `integer` | 32 bits, con recorte | El TAC va a declarar que un `integer` mide 4 bytes; el plegado y el intérprete calculaban en 64. |
   | 22 | Cuerpos del nivel superior | Se revisan al final de la Pasada 2 | Así una función puede usar una global declarada más abajo. Limitación: llamarla antes de la declaración da error en ejecución, igual que el `ReferenceError` de TypeScript. |
   | 23 | Qué cuenta como uso en la vivacidad | Solo las lecturas | Para un recolector de basura importa la última lectura. Una variable que solo se escribe nunca necesitó su memoria. |
   | 24 | Destino de los saltos | Etiquetas simbólicas, sin backpatching | El backpatching existe para traducir en una sola pasada dentro de un parser ascendente, donde la condición se genera antes de conocer su destino. Aquí el generador recorre el AST completo y pasa las etiquetas a los hijos como atributo heredado. Además, un número de instrucción del TAC no sobrevive al assembler: cada instrucción se vuelve varias, y el ensamblador trabaja con etiquetas de forma nativa. Se documenta en `docs/lenguaje-intermedio.md`. |
   | 25 | Representación de las instrucciones | Cuádruplos, como `sealed interface Quadruple` con una `data class` por familia | El enunciado pide temporales y su reciclaje, y las tripletas no tienen temporales. La ventaja de las tripletas indirectas, reordenar barato, no se usa porque no se mueven instrucciones después de generarlas. Una clase por familia, y no un registro genérico `(op, arg1, arg2, result)`, hace que cada instrucción traiga exactamente sus campos y que el `when` sea exhaustivo. La tabla de cuatro columnas de la teoría se obtiene con `toRow()`. |
   | 26 | `print` | Instrucción propia, `print_<tipo> x` | Se lee directo en el TAC. El sufijo es obligatorio porque imprimir un entero y un string son llamadas al sistema distintas en assembler. |
   | 27 | Tipos en las operaciones | Sufijo de tipo en el operador (`+`, `+f`, `<s`), `concat` para strings y conversión explícita `inttofloat` | En assembler, sumar enteros y flotantes son instrucciones distintas. La conversión explícita deja visible el ensanchamiento implícito del lenguaje (Dragon Book 6.5.2). |
   | 28 | `try/catch` | Instrucciones `try L`, `endtry` y `throw x`, con semántica de manejador | Se pidió en la etapa anterior. Es la idea de `setjmp`/`longjmp`: el `throw` encuentra el manejador aunque esté varias llamadas abajo, y descarta los registros de activación de por medio. Cómo se descartan lo implementa la fase de assembler. |
   | 29 | Errores en ejecución | Chequeos emitidos en el TAC, en línea, que disparan `throw` con la línea del fuente | Sin chequeos, la máquina lee memoria basura en silencio, y el `catch` nunca tendría nada que atrapar. División entre cero en la Fase 2; índice y `null` con los objetos y las listas; la recursión demasiado profunda queda fuera de alcance, documentada. |
   | 30 | GDA | Uno por expresión, construido desde el AST, sin compartir en expresiones con llamadas o asignaciones anidadas | Es lo que muestran las diapositivas 12 y 13. Una llamada puede cambiar una global, y una asignación anidada cambia una variable: en esos casos dos subexpresiones iguales no son el mismo valor. |
   | 31 | Reciclaje de temporales | Pool con conteo de usos, que entrega el libre de índice más bajo | Con un GDA un temporal tiene varios lectores y deja de morir en orden de pila, y el contador clásico generaría código que pisa valores vivos. En un árbol el pool entrega los mismos nombres que el contador. |
   | 32 | Plegado de constantes en el TAC | Se usa: una expresión con `constantValue` se emite como constante | El `TypeChecker` ya calculó el valor y garantiza que es correcto; recalcularlo en ejecución sería trabajo repetido. |
   | 33 | La copia al destino | Se conserva: `t1 = a + b` y después `x = t1` | Es la forma de la diapositiva 24. Escribir directo en `x` es una optimización que la teoría no muestra. |
   | 34 | Condiciones con caída | Sí: la condición solo salta hacia el lado que no sigue, con la relación invertida | Es la técnica del Dragon Book 6.6.5. Ahorra un `goto` por condición, que sin ella aparecería en cada `if` y cada bucle. |
   | 35 | Traducción del `switch` | Cadena de comparaciones | La tabla de saltos (Dragon Book 6.8) exige `case` enteros constantes y cercanos, y en Compiscript un `case` puede ser un string o una variable. |
   | 36 | Subexpresiones con saltos adentro (ternario, `&&` y `\|\|` como valor) | Entran al GDA como nodo `Opaque`, y la expresión que las contiene no comparte nodos | Extiende la decisión 30: entre dos ramas solo se ejecuta una, y un nodo compartido entre ellas no estaría calculado en la otra. Las llamadas y los accesos a campos y elementos entran por el mismo nodo. |
   | 37 | Variable del `catch` | `try L, e`: la instrucción nombra la variable que recibe el mensaje | Hace explícito en el TAC el flujo del mensaje del `throw`, en vez de dejarlo en la semántica de la instrucción. |
   | 38 | Funciones como valores | Prohibidas: una función solo se puede llamar. `let g = f;` es error semántico | Una función anidada guardada y llamada después de que retorna la que la contiene seguiría su enlace de acceso hasta un registro de activación ya liberado. Con la regla, una pila y los enlaces de acceso alcanzan siempre, y el TAC solo necesita llamadas indirectas para los métodos (decisión 43), que no capturan variables de ninguna hoja. Llamar en cualquier posición de valor (`r = sumar(2, 3)`) sigue permitido. Ningún programa de la batería lo usaba. |
   | 39 | El código del nivel superior | Se envuelve en un `main` implícito, con su propio registro de activación | Así todo temporal vive en un registro de activación, y la fase de assembler maneja los temporales de una sola forma. Las globales siguen en datos estáticos. |
   | 40 | Variables de otro registro de activación | La dirección lleva los saltos de enlace de acceso: `cuenta^1` | El TAC queda legible y el número de saltos visible. Expandirlo a instrucciones que siguen el enlace es trabajo mecánico de la fase de assembler, igual que cualquier acceso a una local. El documento muestra la expansión como ejemplo. |
   | 41 | Variables con el mismo nombre en el TAC | Sufijo con la línea de la declaración, solo cuando hay ambigüedad: `x` y `x@3`; la columna se agrega si dos chocan en la misma línea | El sufijo dice dónde está declarada sin abrir la tabla de símbolos. El nombre del ámbito no sirve porque no es único, y un contador no informa nada. Una variable sin otra del mismo nombre que la pueda confundir, como el parámetro `n` de dos funciones distintas, no lleva sufijo. |
   | 42 | Tamaños y alineación | `integer` 4, `float` 8, `boolean` 1, referencias 4, cada temporal 8; alineación natural y registro redondeado a 8 | El objetivo de assembler es ARM de 32 bits (Raspberry Pi): las direcciones miden 4 bytes y la pila se alinea a 8. El `float` en doble precisión coincide con el intérprete. Un temporal mide 8 porque el pool recicla nombres sin mirar el tipo. La alineación natural (Dragon Book 6.3.4) es una sola función y deja visibles los tamaños que pide el enunciado. |
   | 43 | Despacho de métodos sobrescritos | Tabla de métodos (vtable) por clase en datos estáticos; la casilla 0 de cada objeto apunta a la de su clase, y la llamada es indirecta | Con `let a: Animal = new Perro()`, el compilador no sabe la clase real, y llamar a `Animal.hablar` imprimiría lo incorrecto. La tabla cuesta tres instrucciones por llamada sin importar cuántas subclases haya, y es la técnica estándar. Los métodos heredados ocupan en la tabla la misma posición que en la del padre, igual que los campos en el objeto. |
   | 44 | Inicialización de campos | Una rutina `$init` por clase, separada del constructor, que primero llama a la de la superclase | Si una subclase hereda el constructor del padre, ese constructor no conoce los campos de la subclase. Con `$init` aparte, todos los campos se inicializan siempre. Es la traducción de los inicializadores que pueden usar `this`. |
   | 45 | Chequeo de `null` | Antes de cada acceso a un campo, a un elemento y de cada llamada a método, salvo sobre `this` | Completa la decisión 29: sin el chequeo, la máquina lee la dirección 0 en vez de disparar un error que el `catch` pueda atrapar. `this` nunca es `null`. |
   | 46 | Ubicación de las tablas de métodos | Al inicio del TAC, antes de `$main` | Son datos estáticos, no código. La fase de assembler las traduce a su sección de datos. |
   | 47 | Strings | Referencias de 4 bytes; los literales viven en datos estáticos y un `concat` pide su bloque en el montículo | El texto de un literal se conoce al compilar; el de una concatenación solo al ejecutar. |
   | 48 | Liberación del montículo | No se libera: `alloc` es la única operación, y se documenta como limitación | El enunciado de esta etapa pide el TAC, no un recolector. Los programas de la batería son pequeños y terminan pronto. El diseño deja posible un recolector por rastreo más adelante: las referencias apuntan al inicio del objeto y la casilla 0 identifica su clase, las dos suposiciones de la presentación 08. |
   | 49 | Cómo verifica la batería el TAC | Archivos dorados: cada `.cps` válido lleva su `.tac` esperado; los inválidos no deben producir TAC | Agregar un caso sigue siendo agregar archivos, y los `.tac` sirven de ejemplos para el documento y para quien califica. Un intérprete de TAC verificaría además que el código calcula bien, pero es una pieza grande que el enunciado no pide. Los `.tac` se regeneran con `./gradlew test -DupdateGolden=true` y el cambio se revisa en el `git diff`. |

6. Decisiones pendientes: ninguna por ahora. La de las funciones que escapan se
   cerró con la decisión 38, al estudiar los enlaces de acceso.

### Por qué

**Archivar y no borrar:** las 16 decisiones del semántico siguen mandando sobre el
generador, y en la defensa se pueden preguntar. **Archivar y no seguir agregando
fases:** las fases 0 a 8 ya tienen números, y una "Fase 9" mezclaría dos entregas en
un mismo índice.

### Aceptación

- `docs/roadmap/` contiene solo `README.md`, las fases de esta etapa y `semantico/`.
- `git log --follow docs/roadmap/semantico/fase-4-tipos.md` muestra la historia
  previa al movimiento.
- Ningún ticket archivado dice `pendiente` si su código existe.
- El README nuevo tiene las seis secciones, con las decisiones 17 a 49.

---

## Ticket 0.2: Corregir el analizador semántico

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `frontend/semantic/symbols/Scope.kt` (MODIFICAR: `lookup`)
- `frontend/semantic/TypeChecker.kt` (MODIFICAR)
- `frontend/semantic/TypeRules.kt` (MODIFICAR: `wrapToInteger`)
- `frontend/semantic/FlowAnalyzer.kt` (MODIFICAR: `alwaysReturns`)
- `interpreter/Interpreter.kt` (MODIFICAR: recorte a 32 bits, `initializeFields`)
- `resources/programas/validos/` (NUEVOS: `clases_constructor_subclase.cps`,
  `flujo_try_retorno.cps`, `tipos_desborde_entero.cps`, `ambito_global_posterior.cps`,
  `clases_inicializador_this.cps`)
- `resources/programas/invalidos/miembro_sin_this.cps` (NUEVO)
- Tests: `TypeCheckerCallsTest`, `TypeCheckerExprTest`, `TypeCheckerStmtTest`,
  `FlowAnalyzerTest`, `InterpreterTest`, `LivenessReportTest` (AMPLIAR)
- `README.md` (MODIFICAR: conteo de programas, 21 válidos y 23 inválidos)

Son siete correcciones independientes. Cada una lleva su test, y las cinco que
cambian lo que el usuario ve llevan además su `.cps`.

### 1. El constructor de una subclase puede tener otra firma

Hoy `checkOverride` trata al `constructor` como un método más:

```cps
class Animal { function constructor(n: string) { ... } }
class Perro : Animal { function constructor(n: string, r: string) { ... } }
// error: "sobrescribe el de la superclase con otra firma"
```

```kotlin
private fun checkOverride(declaration: FunctionDeclaration, classScope: Scope) {
    // El constructor no se invoca a traves de una referencia a la superclase, asi
    // que su firma no participa del subtipado.
    if (declaration.name == CONSTRUCTOR_NAME) return

    val inherited = classScope.superclass?.lookupMember(declaration.name) ?: return
    ...
}
```

**Por qué no abre el agujero que `checkOverride` cierra:** ese agujero es
`let a: Animal = new Perro(); a.hablar()`, donde el verificador consulta `Animal` y
en ejecución corre `Perro`. Con el constructor eso no puede pasar: `new Perro(...)`
siempre nombra la clase exacta, y el `TypeChecker` valida los argumentos contra el
constructor de esa clase.

### 2. Los miembros de clase solo se alcanzan con `this.`

```cps
class Contador {
  let cuenta: integer = 0;
  function incrementar() { cuenta = cuenta + 1; }   // hoy pasa y revienta al ejecutar
}
```

Un nombre suelto **nunca** encuentra un miembro de clase, ni siquiera en el
inicializador de un campo. `lookup` salta los ámbitos de clase al subir, y la
rama de superclases desaparece de `lookup` porque solo la necesitaba ese caso:

```kotlin
fun lookup(name: String): Symbol? {
    // Los miembros de una clase solo se alcanzan con `this.`, como en TypeScript:
    // el nombre suelto sigue de largo hacia el ambito que contiene a la clase.
    if (kind == ScopeKind.CLASS) return parent?.lookup(name)
    return symbols[name] ?: parent?.lookup(name)
}
```

`checkFunctionDeclaration` busca hoy la firma con `currentScope.lookup(decl.name)`.
Con `currentScope` en una clase, eso ya no encontraría el método, así que pasa a
`lookupLocal`. Es correcto en los tres casos: una función anidada recién declarada,
una función del nivel superior y un método viven todos en `currentScope`.

El error lleva la pista cuando el nombre existe como miembro:

```kotlin
if (symbol == null) {
    val member = currentScope.enclosingClass()?.lookupMember(expr.name)
    val hint = if (member != null) ". ¿Quisiste decir 'this.${expr.name}'?" else ""
    report(expr, "La variable '${expr.name}' no está declarada$hint")
    return decorate(expr, TypedValue(ErrorType))
}
```

**Por qué en `lookup` y no en el `TypeChecker`:** `lookup` es la definición de qué
ve un nombre suelto. Ponerlo ahí hace que la regla sea imposible de saltar desde
cualquier llamador.

### 3. Un `try/catch` donde las dos ramas retornan garantiza retorno

```cps
function f(): integer { try { return 1; } catch (e) { return 2; } }
// hoy: "hay caminos que no retornan"
```

```kotlin
// Si el try termina, retorno; si falla, el catch tambien retorna.
is TryCatch -> alwaysReturns(stmt.tryBlock.statements) &&
    alwaysReturns(stmt.catchBlock.statements)
```

### 4. `integer` es de 32 bits

Hoy `2147483647 + 1` imprime `2147483648`: el literal pasa el chequeo de rango, pero
el plegado y el intérprete operan en `Long` sin recortar.

```kotlin
// TypeRules.kt, junto a las reglas: es la definicion del rango de integer.
// Recorta como Java: 2147483647 + 1 da -2147483648.
fun wrapToInteger(value: Long): Long = value.toInt().toLong()
```

Se aplica a todo resultado entero:

- `TypeChecker.foldBinaryOperation`: suma, resta, multiplicación, división y módulo
  cuando el resultado es `IntegerType`.
- `TypeChecker.checkUnaryOperation`: la negación entera, porque
  `-(-2147483648)` también desborda.
- `Interpreter.applyLong` y `evaluateUnary`: los mismos casos en ejecución.

Los enteros siguen guardándose como `Long`: el recorte es sobre el valor, no sobre
el tipo de Kotlin, así que ninguna otra parte cambia.

### 5. Las escrituras no cuentan como uso

```cps
let x: integer = 0;
x = 5;      // hoy: "2 usos, último uso en la línea 3"
x = 10;     // x nunca se lee
```

`checkAssignmentRules` ya prende `checkingAssignmentTarget` para el destino
`Identifier`. Se extiende a `PropertyAccess`, porque `this.cuenta = 0` tampoco es
una lectura de `cuenta`:

```kotlin
// En checkIdentifier: la captura se marca igual, porque una funcion anidada que
// ESCRIBE una variable externa tambien depende de ella.
if (!checkingAssignmentTarget) {
    symbol.useCount += 1
    symbol.lastUseLine = maxOf(symbol.lastUseLine ?: 0, expr.location.line)
}

// En checkPropertyAccess: el objeto de `obj.campo = 5` SI se lee.
val isWrite = checkingAssignmentTarget
checkingAssignmentTarget = false
val targetType = checkExpression(expr.target).type
...
if (!isWrite) {
    member.useCount += 1
    member.lastUseLine = maxOf(member.lastUseLine ?: 0, expr.location.line)
}
```

**El `maxOf` no es opcional:** con la corrección 6, el cuerpo de una función se
revisa al final aunque esté más arriba en el archivo. Sin `maxOf`, un uso dentro de
la función en la línea 2 pisaría un uso global en la línea 10, y el reporte diría
que el último uso es la línea 2.

`lista[0] = 5` sigue contando como uso de `lista`: se lee la referencia para
modificar su contenido.

### 6. Las funciones y clases del nivel superior se revisan al final

```cps
function mostrar() { print(contador); }   // hoy: "'contador' no está declarada"
let contador: integer = 5;
mostrar();
```

```kotlin
// Los cuerpos van al final para que vean todas las globales, declaradas antes o
// despues. Las firmas ya las registro la Pasada 1.
fun check(program: Program) {
    val (declarations, rest) = program.statements.partition {
        it is FunctionDeclaration || it is ClassDeclaration
    }
    rest.forEach { checkStatement(it) }
    declarations.forEach { checkStatement(it) }
}
```

El intérprete no cambia: ya registra las funciones antes de ejecutar y busca la
global en el momento de la llamada.

**Lo que esto deja pasar, documentado:** `mostrar();` escrito **antes** de
`let contador` compila y falla en ejecución con *"no tiene valor"*. Detectarlo
exigiría seguir las llamadas de forma transitiva. Es el comportamiento de
TypeScript.

**Efecto visible:** en la tabla de símbolos, los ámbitos de las funciones y clases
aparecen después de los bloques del nivel superior, porque se abren en ese orden.

### 7. Los inicializadores de campo pueden usar `this`

```cps
class A {
  let x: integer = 1;
  let y: integer = this.x + 1;
}
print(new A().y);   // hoy compila y falla: "'this' no está disponible aquí"
```

El `TypeChecker` ya lo acepta, porque `enclosingClass()` encuentra la clase. El que
falla es el intérprete: `initializeFields` evalúa los inicializadores en el entorno
de **quien llamó** a `new`, donde no hay `this`.

```kotlin
private fun initializeFields(declaration: ClassDeclaration, instance: ObjectValue) {
    declaration.superclassName
        ?.let { classDeclarations[it] }
        ?.let { initializeFields(it, instance) }

    // Los inicializadores corren sobre la instancia que se esta construyendo, igual
    // que el constructor, y cuelgan del global y no del entorno de quien hizo `new`.
    val fieldEnvironment = globalEnvironment.child()
    fieldEnvironment.define("this", instance)

    val previous = environment
    environment = fieldEnvironment
    try {
        declaration.members.filterIsInstance<VariableDeclaration>().forEach { field ->
            instance.fields[field.name] =
                field.initializer?.let { evaluate(it) } ?: defaultValueFor(field)
        }
    } finally {
        environment = previous
    }
}
```

**Colgar del global corrige un segundo bug que hoy no se ve:** evaluar en el entorno
de quien llama es alcance dinámico. Si una función declara una local `g` y hace
`new A()`, un inicializador que lee la global `g` estaría leyendo la local.

**Por qué soportarlo y no prohibirlo:** es lo que permite TypeScript. Además, al
traducir clases a TAC, los inicializadores de campo se van a emitir como parte del
constructor, donde `this` existe. Prohibirlo ahora sería quitar algo que después
igual hay que soportar.

Un campo que lee otro declarado **más abajo** recibe su valor por defecto, porque
los campos se inicializan en orden. Queda documentado.

### Aceptación

| Programa | Resultado esperado |
|---|---|
| `Perro : Animal` con `constructor(n, r)` sobre `constructor(n)` | válido, y `new Perro("Toby", "x")` corre |
| `cuenta = cuenta + 1` dentro de un método | **error** con *"¿Quisiste decir 'this.cuenta'?"* |
| `this.cuenta = this.cuenta + 1` | válido |
| Método que llama a otro sin `this.` | **error** con la pista |
| Una global llamada igual que un campo, usada sin `this.` en un método | encuentra la global |
| `try { return 1; } catch (e) { return 2; }` en una función `integer` | válido |
| `try { return 1; } catch (e) { }` | **error**: hay caminos que no retornan |
| `print(2147483647 + 1)` plegado y con variable | `-2147483648` en los dos |
| `let x = 0; x = 5;` | vivacidad: 0 usos |
| `this.cuenta = 0;` sin lecturas | vivacidad: `cuenta` con 0 usos |
| Global usada en la línea 10 y dentro de una función en la línea 2 | último uso: línea 10 |
| Función que usa una global declarada más abajo | válido, y ejecuta |
| `let y: integer = this.x + 1;` con `x = 1` | `new A().y` vale `2` |
| Inicializador que lee una global `g`, con `new A()` llamado desde una función que tiene su propia local `g` | lee la global |

- Los 38 `.cps` existentes siguen pasando. Si alguno usaba un miembro sin `this.`,
  se corrige el programa, no la regla.
- `./gradlew test` en verde.

---

## Ticket 0.3: Deuda de convenciones

- **Estado**: pendiente
- **Depende de**: 0.2, para no editar los mismos archivos en paralelo

**Archivos:** solo cambios de forma, sin cambiar comportamiento.

| Qué | Dónde |
|---|---|
| Comentarios que nombran fases, tickets o decisiones | `TypeResolver.kt`, `FlowAnalyzer.kt`, `Interpreter.kt`, `TypeChecker.kt`, `LivenessReportBuilder.kt`, `AstBuilder.kt`, `AstView.kt`, `ScopeTreeView.kt`, y los tests `DiagnosticsTest`, `AstBuilderExprTest`, `TypeCheckerStmtTest`, `AntlrSmokeTest`, `TypeRulesTest`, `SyntaxAnalyzerTest`, `TypeResolverTest`, `ProgramasInvalidosTest` |
| Texto visible en la GUI que nombra una fase | `TreesScreen.kt`: *"AST propio, con los tipos de la Fase 4"* pasa a *"AST decorado con tipos"* |
| Identificadores en español | `TypeRules.kt` (`ancestrosDeLeft`); en los tests, las clases `ProgramasValidosTest` y `ProgramasInvalidosTest` pasan a `ValidProgramsTest` e `InvalidProgramsTest`, más sus `val`, `fun` y `data class` (`ErrorEsperado`, `leerAnotacionEsperada`, `resultado`, `errores`, `recolectar`, `mensajes`...) |
| Imports con comodín | `TypeRules.kt`, `Interpreter.kt` |
| `!!` donde el nulo es imposible por construcción | `TypeChecker.withScope`: `checkNotNull(currentScope.parent)` |
| Warning de deprecación | `ScopeTreeView.kt:141`: `Icons.AutoMirrored.Filled.KeyboardArrowRight` |

**Lo que no cambia:** los nombres de test entre backticks siguen en español, porque
son la descripción del caso y no un identificador que se use desde el código. Las
carpetas `programas/validos` e `invalidos` tampoco, porque son datos y forman parte
del id que muestra el selector.

**Lo que se deja a propósito:** el parámetro `offset` de `TypeChecker.declare`, que
`Scope.declare` sobrescribe. Los desplazamientos se rediseñan con la tabla de
símbolos extendida (puntos 5 a 7), y limpiarlo ahora sería tocarlo dos veces.

### Aceptación

- `grep -rnE '[Tt]icket|[Ff]ase [0-9]|decisi[oó]n [0-9]+' app/src` no devuelve nada.
- Compilar no produce warnings.
- `./gradlew test` en verde con el mismo número de tests que dejó 0.2.

---

## Ticket 0.4: Limpieza del repositorio

- **Estado**: pendiente
- **Depende de**: ninguno

**Archivos:**

- `.DS_Store`, `_to_delete/`, `extras/` (ELIMINAR)
- `Informe_Proyecto_Analisis_Semantico (1).pdf` (MOVER a
  `docs/informe-analisis-semantico.pdf`)
- `.gitignore` (MODIFICAR: agregar `.DS_Store`)
- `README.md` (MODIFICAR: quitar las dos referencias a `docs/arquitectura.excalidraw`,
  que no existe)

**Por qué cada uno:** `_to_delete/` es una copia vieja de `TypeRulesTest`;
`extras/example.yaml` es un autómata de otro proyecto; `.DS_Store` es metadata de
macOS. Un evaluador que abre el repositorio no debería preguntarse qué son.

### Aceptación

- `git ls-files | grep -E 'DS_Store|_to_delete|extras'` no devuelve nada.
- La raíz del repositorio no tiene PDFs.
- El README no menciona archivos que no existen.

---

## Ticket 0.5: Sección 1 del documento del lenguaje intermedio

- **Estado**: pendiente
- **Depende de**: 0.1

**Archivos:**

- `docs/lenguaje-intermedio.md` (NUEVO)

Es el documento que vale 25 puntos: *"documentación detallada del lenguaje
intermedio diseñado, con ejemplos de traducción y los supuestos considerados"*. Se
escribe por partes, una sección por punto de teoría cerrado, para que nunca describa
algo que todavía no está decidido.

### Sección 1: qué es y dónde encaja

1. **Qué es:** una representación del programa, independiente de la máquina, que
   produce el front-end y consume el back-end.
2. **Dónde está:** la cadena de representaciones del compilador, con esta etapa
   marcada:

   ```
   texto .cps -> árbol de ANTLR -> AST -> TAC -> (assembler, la etapa siguiente)
   ```

3. **Por qué existe si no es indispensable:** el intérprete demuestra que se puede
   ejecutar sin IR. Se usa porque divide el problema (`m + n` piezas en vez de
   `m × n`), acorta el salto entre el lenguaje fuente y la máquina, y permite
   optimizar una sola vez.
4. **Alto y bajo nivel:** el AST conserva la estructura y los tipos, y sirve para
   verificar; el TAC solo tiene instrucciones simples y saltos, y sirve para acercarse
   a la máquina. El ejemplo `lista[i] = x + 1` en las dos representaciones, mostrando
   que el desplazamiento `i * tamaño` aparece en el TAC y no en el AST. El tamaño
   concreto se remite a la sección de la tabla de símbolos, que se escribe después.

Las secciones siguientes se listan al final como *pendiente*, con el punto de teoría
que las va a llenar.

### Aceptación

- La sección 1 cubre los cuatro apartados.
- No afirma nada de la sintaxis del TAC ni de tamaños que no esté decidido.
- Cita la presentación 06 y el Dragon Book para cada afirmación teórica.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 0.1 | El roadmap del semántico archivado con estados reales, y el índice de esta etapa con las decisiones 17 a 49 |
| 0.2 | Siete correcciones del semántico y del intérprete, cada una con su test |
| 0.3 | El código sin referencias al plan, en inglés y sin warnings |
| 0.4 | El repositorio sin archivos sueltos |
| 0.5 | `docs/lenguaje-intermedio.md` con la sección 1 |
