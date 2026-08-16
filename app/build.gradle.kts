import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.plugins.antlr.AntlrTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)

    // Viene con Gradle, no lleva version. Lee app/src/main/antlr/Compiscript.g4 y
    // genera el lexer, el parser, el listener y el visitor.
    antlr
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // `antlr(...)` no es `implementation(...)`: es una configuracion propia que el
    // plugin registra, y es la que le dice a Gradle con que jar correr el generador.
    antlr(libs.antlr)
    implementation(libs.antlr.runtime)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.generateGrammarSource {
    // Es `arguments + listOf(...)` y NO `arguments = listOf(...)`: el plugin ya puso
    // argumentos ahi (los directorios de salida), y reemplazarlos rompe la generacion.
    arguments = arguments + listOf(
        // OBLIGATORIOS los dos: por defecto ANTLR genera SOLO el listener, y sin
        // -visitor no existe CompiscriptBaseVisitor, del que hereda el AstBuilder.
        "-visitor",
        "-listener",

        // Sin esto las clases salen sin `package` y Kotlin no puede importarlas.
        "-package", "org.compiler.parser"
    )

    // El -package de arriba solo escribe la linea `package` DENTRO de los .java; el
    // plugin igual los deja planos en generated-src/antlr/main. Compilan asi, pero
    // deja un arbol donde carpeta y paquete no coinciden, que confunde al navegarlo.
    outputDirectory =
        layout.buildDirectory.dir("generated-src/antlr/main/org/compiler/parser").get().asFile
}

// El plugin `antlr` es de la era pre-Kotlin: declara la dependencia para compileJava,
// no para compileKotlin. Sin esto, Gradle compila Kotlin antes de que exista
// CompiscriptParser.java y falla con `unresolved reference`.
//
// Son TODAS las AntlrTask y no solo `generateGrammarSource`: el plugin registra una
// por source set, asi que tambien existe `generateTestGrammarSource`. Apuntando solo
// a la de main, compileTestKotlin falla con "uses this output without declaring an
// explicit dependency".
tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

tasks.register<JavaExec>("runGui") {
    group = "application"
    description = "Launches the Compose Desktop IDE"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.compiler.GuiAppKt")
    workingDir = file(".")
}

compose.desktop {
    application {
        mainClass = "org.compiler.GuiAppKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Compiscript"
            packageVersion = "1.0.0"
            description = "Analizador semantico de Compiscript"
            vendor = "UVG -- Disenio de Lenguajes"
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
