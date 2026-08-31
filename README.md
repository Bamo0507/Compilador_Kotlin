# Analizador Semántico de Compiscript

Compilador para Compiscript, un lenguaje basado en un subconjunto de TypeScript,
implementado en Kotlin sobre la JVM. Toma un archivo `.cps`, lo analiza léxica,
sintáctica y semánticamente, y lo ejecuta, todo desde un IDE de escritorio escrito
en Compose Multiplatform.

Proyecto 2 del curso de Construcción de Compiladores, Universidad del Valle de
Guatemala.

---

## Qué hace

El compilador no es una sola pasada que lo hace todo, sino una cadena de fases
donde cada una agrega información sobre el mismo AST en vez de construir uno
nuevo. Es decir, el árbol se construye una única vez y de ahí en adelante las
fases lo van decorando, primero con los ámbitos y los símbolos, luego con los
tipos, después con los valores constantes que se pueden resolver desde
compilación, y al final el intérprete lo recorre confiando en todo eso. Bajo esta
idea cada fase queda pequeña y se puede probar por separado, ya que no tiene que
volver a hacer el trabajo de la anterior.

| Fase | Componentes | Produce |
|---|---|---|
| 1, léxico y sintáctico | `CompiscriptLexer`, `CompiscriptParser` (ANTLR), `AstBuilder` | el árbol de análisis y el AST propio |
| 2, semántico, pasada 1 | `DeclarationCollector` | el árbol de ámbitos y símbolos |
| 2, semántico, pasada 2 | `TypeChecker`, `TypeRules`, `TypeResolver` | el AST decorado con tipos y constantes plegadas |
| 2, flujo y vivacidad | `FlowAnalyzer`, `LivenessReportBuilder` | errores de flujo y el reporte de vivacidad |
| 3, ejecución | `Interpreter`, `Environment`, `RuntimeValue` | la salida del programa |

Todo se orquesta desde una sola llamada, `CompilerPipeline.compile(fuente)`, que
devuelve un `CompilationResult` con el árbol de ANTLR, el AST, la tabla de
símbolos, la vivacidad, los errores y la salida. Se hizo de esta forma para que la
interfaz gráfica no tuviera que orquestar nada, es decir, la GUI llama una vez y
de ahí en adelante solo lee campos.

El diagrama completo de la implementación está en `docs/arquitectura.excalidraw`.

### Lo que valida el analizador semántico

- **Tipos**, aritmética sobre `integer` y `float`, operadores lógicos sobre
  `boolean`, comparaciones, asignaciones, `const` inicializada, listas e índices.
- **Ámbitos**, resolución local y global, variables no declaradas, redeclaración,
  bloques anidados, y un entorno nuevo por cada función, clase y bloque.
- **Funciones**, cantidad y tipo de argumentos, tipo de retorno, recursión,
  funciones anidadas con closures, y redeclaración.
- **Control de flujo**, condiciones booleanas, `break` y `continue` dentro de
  bucles, `return` dentro de funciones, código inalcanzable, y que todos los
  caminos de una función retornen.
- **Clases**, atributos y métodos accedidos con `.`, invocación del constructor,
  herencia, uso de `this`, y ciclos de herencia.

---

## Requisitos

### JDK 21

El proyecto fija el toolchain en Java 21, así que cualquier distribución sirve, ya
sea Amazon Corretto, Eclipse Temurin u Oracle.

```bash
java -version
# debe imprimir "21.x.x"
```

Si tienes varias versiones instaladas, exporta `JAVA_HOME` apuntando al JDK 21:

```bash
# macOS Corretto
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home

# macOS Temurin (Adoptium)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

# Linux (ajusta el path según tu distro)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
```

Si prefieres no exportarlo cada vez, copia `gradle.properties.local.template` a
`gradle.properties.local` y ajusta el path local, como se explica en la sección de
overrides.

### Gradle

No es necesario instalarlo, dado que el proyecto incluye el wrapper (`./gradlew`),
y la versión se resuelve automáticamente desde
`gradle/wrapper/gradle-wrapper.properties`.

### ANTLR

Tampoco es necesario instalarlo. El plugin `antlr` de Gradle genera el lexer y el
parser a partir de `app/src/main/antlr/Compiscript.g4` en cada build, de tal forma
que basta con clonar el repositorio y compilar.

---

## Ejecutar

```bash
./gradlew run
```

Abre el IDE, y equivale a `./gradlew runGui`.

Al arrancar se carga un programa de demostración que ejercita clases, herencia,
`this`, constructor, recursión, arreglos, `foreach` y `continue`. Asimismo, el
selector que está sobre el editor permite cargar cualquiera de los 38 programas de
la batería de pruebas, o empezar en blanco.

### Las tres vistas del IDE

Se cambia entre ellas desde el menú **Vista**.

- **Editor**, el código, la lista de errores etiquetados por nivel (léxico,
  sintáctico o semántico) y la consola de salida. Los errores salen ordenados por
  línea y columna en vez de agrupados por fase, ya que el usuario lee su código de
  arriba hacia abajo, y al hacer clic sobre uno el editor salta a esa línea.
- **Árboles**, el árbol sintáctico de ANTLR y el AST propio, lado a lado. En el
  AST cada expresión muestra el tipo que le asignó la fase semántica y, si es
  constante, su valor ya calculado, de tal forma que esta pantalla es la evidencia
  visual del trabajo del analizador.
- **Tabla de símbolos**, el árbol de ámbitos navegable con los símbolos de cada
  entorno, y el reporte de vivacidad.

---

## Pruebas

```bash
./gradlew test
```

El reporte queda en `app/build/reports/tests/test/index.html`.

La batería tiene dos niveles. Por un lado están las pruebas unitarias sobre cada
componente por separado, y por otro, 38 programas `.cps` reales que atraviesan el
compilador completo, es decir, al menos un caso exitoso y uno fallido por cada
regla semántica.

Cada programa declara su propia especificación en comentarios, de tal forma que el
archivo es su propia documentación, y agregar un caso es agregar un archivo sin
tener que tocar código de pruebas. Cabe mencionar que ese archivo nuevo también
aparece automáticamente en el selector del IDE. Por ejemplo:

```cps
// NOMBRE: Código inalcanzable
// ESPERADO: linea 6, "Código inalcanzable"
```

```cps
// NOMBRE: Tipos: aritmética
// SALIDA: 8
// SALIDA: 120
```

- `// NOMBRE:`, el nombre que aparece en el selector del IDE.
- `// ESPERADO:`, el error que el programa debe producir y en qué línea.
- `// SALIDA:`, cada línea que el programa debe imprimir, en orden.

---

## Compilar y empaquetar

```bash
./gradlew build
```

El plugin de Compose Desktop genera instaladores nativos sin dependencias
adicionales:

```bash
./gradlew packageDmg    # macOS
./gradlew packageMsi    # Windows
./gradlew packageDeb    # Linux
```

Los instaladores quedan en `app/build/compose/binaries/main/<formato>/`.

---

## Overrides por desarrollador

Para que cada integrante configure su JDK sin afectar el repositorio:

1. Copia `gradle.properties.local.template` a `gradle.properties.local`.
2. Edita los valores locales, por ejemplo `org.gradle.java.home`.
3. `gradle.properties.local` está en `.gitignore`, así que no se commitea.

---

## Estructura del proyecto

```
Compilador_Kotlin/
├── app/src/
│   ├── main/
│   │   ├── antlr/Compiscript.g4              gramática, genera lexer y parser
│   │   ├── kotlin/org/compiler/
│   │   │   ├── frontend/
│   │   │   │   ├── syntax/                   SyntaxAnalyzer, listener de errores
│   │   │   │   ├── ast/                      AstBuilder y los nodos del AST
│   │   │   │   └── semantic/                 las dos pasadas, flujo y vivacidad
│   │   │   │       └── symbols/              Scope, Symbol, Type
│   │   │   ├── interpreter/                  Interpreter, Environment, RuntimeValue
│   │   │   ├── runtime/                      CompilerPipeline, CompilationResult
│   │   │   ├── samples/                      cargador de los programas de ejemplo
│   │   │   ├── diagnostics/                  Diagnostics, CompilerError
│   │   │   ├── gui/                          pantallas y componentes Compose
│   │   │   └── GuiApp.kt                     entry point
│   │   └── resources/programas/              los .cps, el selector y la batería
│   │       ├── validos/                      16 programas que compilan
│   │       └── invalidos/                    22 programas que deben fallar
│   └── test/kotlin/org/compiler/             pruebas de cada fase
├── docs/
│   ├── roadmap/                              el plan por fases y tickets
│   ├── reglas-de-tipos.md                    las reglas del sistema de tipos
│   └── arquitectura.excalidraw               diagrama de la implementación
├── gradle/libs.versions.toml                 version catalog
└── app/build.gradle.kts                      config del módulo
```

Cabe mencionar que los `.cps` viven en `src/main/resources` y no en
`src/test/resources` a propósito, porque los recursos de pruebas no existen en
tiempo de ejecución, y se busca que el IDE los pueda leer. De esta forma queda una
sola copia que el selector muestra y las pruebas verifican, así que las dos nunca
se pueden desincronizar.

---

## Integrantes

- Bryan Alberto Martínez Orellana, 23542
- Adriana Sophia Contreras Palacios, 23044
- Brandon Werner Rivera Cabrera, 23088

---

## Referencias

- [Dragon Book](https://www.amazon.com/Compilers-Principles-Techniques-Tools-2nd/dp/0321486811), algoritmos canónicos
- [ANTLR 4](https://www.antlr.org/), generador del lexer y el parser
- [Kotlin docs](https://kotlinlang.org/docs/home.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/), framework de la GUI
- [Ing. Pablo Koch](https://gt.linkedin.com/in/pablo-koch-075839119)
