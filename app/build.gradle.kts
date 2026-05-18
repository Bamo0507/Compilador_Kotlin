import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation("guru.nidi:graphviz-java:0.18.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.register<JavaExec>("runPreprocessor") {
    group = "application"
    description = "Program 1 -- Preprocessor: reads .yal, builds DFAs, writes YAMLs"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.compiler.PreprocessorAppKt")
    workingDir = file(".")
}

tasks.register<JavaExec>("runLexer") {
    group = "application"
    description = "Program 2 -- Scanner: loads YAMLs, runs scanner, prints tokens"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.compiler.LexerAppKt")
    workingDir = file(".")
}

tasks.register<JavaExec>("runGui") {
    group = "application"
    description = "Program 3 -- GUI: launches the Compose Desktop interface"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.compiler.GuiAppKt")
    workingDir = file(".")
}

compose.desktop {
    application {
        mainClass = "org.compiler.GuiAppKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CompilerKotlin"
            packageVersion = "1.0.0"
            description = "Lexical and Syntactic Analyzer Generator"
            vendor = "UVG -- Disenio de Lenguajes"
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
