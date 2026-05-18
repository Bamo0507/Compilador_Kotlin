# Compilador Kotlin

Generador de Analizador Lexico y Sintactico implementado en Kotlin sobre la JVM.
Lee una especificacion `.yal` (definicion lexica) y `.yalp` (gramatica sintactica),
construye los automatas y tablas correspondientes, y permite parsear cadenas
con LL(1), SLR(1) o LALR(1) -- todo orquestado desde una GUI de escritorio
escrita en Compose Multiplatform.

---

## Requisitos

### JDK 21

Compose Multiplatform 1.8.x corre sobre JVM 17+, pero el proyecto fija el toolchain
en **Java 21**. Cualquier distribucion 21 sirve: Amazon Corretto, Eclipse Temurin, Oracle, etc.

**Verificar:**
```bash
java -version
# debe imprimir "21.x.x" o superior
```

**Si tienes varias versiones instaladas**, exporta `JAVA_HOME` apuntando al JDK 21:

```bash
# macOS Corretto
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home

# macOS Temurin (Adoptium)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

# Linux (ajusta el path segun tu distro)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
```

Si prefieres no exportar `JAVA_HOME` cada vez, copia el template
`gradle.properties.local.template` a `gradle.properties.local` y ajusta el path
local (ver "Overrides per developer" abajo).

### Gradle

No es necesario instalarlo: el proyecto incluye el wrapper (`./gradlew`).
La version se resuelve automaticamente desde `gradle/wrapper/gradle-wrapper.properties`.

### Graphviz (opcional, solo para renderizar imagenes del automata)

```bash
# macOS
brew install graphviz

# Linux Debian/Ubuntu
sudo apt install graphviz

# Windows
choco install graphviz
```

Sin Graphviz, las funciones de exportacion siguen produciendo texto DOT correctamente,
pero `DotExporter.renderToImage(...)` retornara `false` (no lanza excepcion).

---

## Compilar

```bash
./gradlew build
```

---

## Ejecutar

El proyecto tiene **tres entry points**:

### 1. GUI (interfaz principal)

```bash
./gradlew runGui
```

Abre la ventana Compose Desktop. Es la interfaz primaria del proyecto;
desde ahi se carga el `.yal`, el `.yalp`, la cadena de entrada, se elige
el metodo de parsing (LL(1)/SLR(1)/LALR(1)) y se visualizan tokens, arbol
de derivacion, automata y tablas.

### 2. Preprocesador (CLI, legacy de Proyecto 1)

```bash
./gradlew runPreprocessor
```

Lee `app/src/main/resources/java_lang.yal`, construye los DFAs minimizados,
y escribe los YAMLs intermedios.

### 3. Lexer (CLI, legacy de Proyecto 1)

```bash
./gradlew runLexer
```

Carga los YAMLs generados por el preprocesador y tokeniza
`app/src/main/resources/input.java`.

---

## Empaquetar instalador nativo

El plugin de Compose Desktop genera instaladores nativos sin dependencias adicionales:

```bash
./gradlew packageDmg          # macOS .dmg
./gradlew packageMsi          # Windows .msi
./gradlew packageDeb          # Linux .deb
./gradlew packageDistributionForCurrentOS  # el formato correspondiente al SO actual
```

Los instaladores quedan en `app/build/compose/binaries/main/<formato>/`.

---

## Tests

```bash
./gradlew test
```

Resultados en `app/build/reports/tests/test/index.html`.

---

## Overrides per developer

Para que cada miembro del equipo configure su JDK sin afectar el repo:

1. Copia `gradle.properties.local.template` a `gradle.properties.local`.
2. Edita los valores locales (p.ej. `org.gradle.java.home`).
3. `gradle.properties.local` esta en `.gitignore` -- no se commitea.

Alternativamente exporta `JAVA_HOME` en tu shell.

---

## Estructura del proyecto

```
Compilador_Kotlin/
├── app/
│   └── src/
│       ├── main/
│       │   ├── kotlin/org/compiler/
│       │   │   ├── frontend/
│       │   │   │   ├── lexicalAnalyzer/    Proyecto 1: regex -> DFA -> scanner
│       │   │   │   └── syntaxAnalyzer/     Proyecto 2: gramatica -> tablas -> parser
│       │   │   │       ├── grammar/        YalpReader, GrammarValidator, rewriters
│       │   │   │       ├── sets/           FIRST/FOLLOW
│       │   │   │       ├── ll1/            tabla y parser LL(1)
│       │   │   │       ├── slr1/           automata, tabla y parser SLR(1)
│       │   │   │       ├── lalr1/          merger, tabla y parser LALR(1)
│       │   │   │       ├── runtime/        Pipeline, TokenStream, ParseTree
│       │   │   │       └── visualization/  DotExporter, ParseTreeExporter, TableFormatter
│       │   │   ├── gui/                    componentes Compose Desktop
│       │   │   ├── models/                 LexemeLocation
│       │   │   ├── diagnostics/            ErrorEntry y similares
│       │   │   ├── symbolTable/            SymbolTable
│       │   │   ├── LexerApp.kt             entry point CLI lexer
│       │   │   ├── PreprocessorApp.kt      entry point CLI preprocesador
│       │   │   └── GuiApp.kt               entry point GUI
│       │   └── resources/                  inputs y outputs de los CLI
│       └── test/kotlin/org/compiler/       tests de cada fase
├── docs/
│   ├── ROADMAP.md                          plan de 40 tickets en 12 fases
│   └── architecture.md                     diseno canonico
├── extras/                                 ejemplos y artefactos auxiliares
├── gradle/libs.versions.toml               version catalog (Kotlin, Compose, etc.)
├── gradle.properties                       config Gradle del repo
├── gradle.properties.local.template        template de overrides locales
├── build.gradle.kts                        config root
└── app/build.gradle.kts                    config del modulo app
```

---

## Documentos de referencia

- [Dragon Book](https://www.amazon.com/Compilers-Principles-Techniques-Tools-2nd/dp/0321486811) -- algoritmos canonicos
- [Kotlin docs](https://kotlinlang.org/docs/home.html)
- [Gradle docs](https://docs.gradle.org/current/userguide/userguide.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) -- framework de la GUI
- [Java Language Specification](https://docs.oracle.com/javase/specs/)
- [Ing. Pablo Koch](https://gt.linkedin.com/in/pablo-koch-075839119)
