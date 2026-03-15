# Kotlin Java Compiler
Analizador Lexico y Sintactico de Java usando Kotlin

# Getting Started
## Requisitos
- JDK 21 [{MacOs} {Linux}](https://adoptium.net/es/temurin/releases/?version=21) [{Windows}](https://adoptium.net/es/temurin/releases/?version=21&architecture=x64&image_type=jdk)

MacOS/Linux:
```bash
# Check Java version and PATH
java -version
echo $PATH
```

Windows:
```bash
# Check Java version and PATH
java -version
echo $JAVA_HOME
```

- Gradle 8.0.0 o superior
Linux:
```bash
sdk install gradle
```

MacOS:
```bash
brew install gradle
# or
sudo port install gradle
```

Windows e instalacion manual:
```bash
# Download and install Gradle from
https://docs.gradle.org/current/userguide/installation.html#linux_installation
```

## Compilar Codigo

```bash
./gradlew build
```

## Correr

### Ejecutar el Preprocesador
```bash
./gradlew runPreprocessor 
```

### Ejecutar el Analizador Lexico
```bash
./gradlew runLexer
```

## File Structure
```
COMPILADOR_KOTLIN
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin.org.compiler
│   │   │   │   ├── lexicalAnalyzer
│   │   │   │   |    ├── lexer/
│   │   │   │   |    ├── manageGrammar/
│   │   │   │   |    |   ├── models/
│   │   │   │   |    |   ├── utils/
│   │   │   │   |    |   ├── DFABuilder.kt
│   │   │   │   |    |   ├── DFAMinimizer.kt
│   │   │   │   |    |   ├── ShuntingYard.kt
│   │   │   │   |    |   └── TreeBuilder.kt
│   │   │   │   |    └── scanner/
│   │   │   │   |       └── Scanner.kt
│   │   │   │   ├── LexerApp.kt
│   │   │   │   └── PreprocessorApp.kt
│   │   └── resources
│   │       ├── output
│   │       │   ├── errors.txt
│   │       │   ├── symbolTable.txt
│   │       │   └── tokens.txt
│   │       ├── input.java
│   │       ├── java_lang.yal
│   │       └── DFA.yaml
├── build.gradle.kts
├── gradle
├── readme.md
└── settings.gradle.kts
```

# Input Files
``` 
app/src/main/resources/input.java
app/src/main/resources/java_lang.yal
```

# Output Files
```
app/src/main/resources/output/errors.txt
app/src/main/resources/output/symbolTable.txt
app/src/main/resources/output/tokens.txt
app/src/main/resources/DFA.yaml
```

# References
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Gradle Documentation](https://docs.gradle.org/current/userguide/userguide.html)
- [Java Language Specification](https://docs.oracle.com/javase/specs/)
- [The Dragon Book](https://www.amazon.com/Compilers-Principles-Techniques-Tools-2nd/dp/0321486811)
- [Ing. Pablo Koch](https://gt.linkedin.com/in/pablo-koch-075839119)
- [Flex](https://github.com/Kosho969/Flex_Setup)