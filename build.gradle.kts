// ─────────────────────────────────────────────────────────────────────────────
// build.gradle.kts — SCL Language Support Plugin
// Versoes verificadas em 06/04/2026:
//   Kotlin JVM Plugin:               2.3.20  (kotlinlang.org/docs/releases)
//   IntelliJ Platform Gradle Plugin: 2.13.1  (plugins.gradle.org)
//   GrammarKit standalone:           2023.3.0.3
//   IntelliJ IDEA target:            2026.1  (jetbrains.com)
//   JVM target compilacao:           21      (docs oficiais JetBrains)
//   Apache PDFBox:                   3.0.7   (pdfbox.apache.org)
//   Gradle wrapper:                  9.4.1   (gradle.org/releases)
// ─────────────────────────────────────────────────────────────────────────────

import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("java")

    // Kotlin 2.3.20 — versao estavel atual (abril 2026)
    id("org.jetbrains.kotlin.jvm") version "2.3.20"

    // IntelliJ Platform plugin — versao declarada no settings.gradle.kts
    id("org.jetbrains.intellij.platform")

    // GrammarKit standalone — gera lexer (JFlex) e parser (Grammar-Kit)
    id("org.jetbrains.grammarkit") version "2023.3.0.3"
}

group   = "com.scl.plugin"
version = providers.gradleProperty("pluginVersion").get()

// ── Compilar com JVM 21 (padrao JetBrains para plugins 2026.x) ───────────────
// JDK 25 roda o Gradle; bytecode do plugin gerado para JVM 21
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

// ── Dependencias ──────────────────────────────────────────────────────────────
dependencies {
    intellijPlatform {
        // IntelliJ IDEA 2026.1 — unificado Community+Ultimate desde 2025.3
        intellijIdea("2026.1")

        // MCP Server — embutido desde IntelliJ 2024.3, usado na Fase 7
        bundledPlugin("com.intellij.mcpServer")

        // Framework de testes da plataforma
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // Apache PDFBox 3.0.7 — usado na Fase 5 para indexar manual SCL em PDF
    implementation("org.apache.pdfbox:pdfbox:3.0.7")

    testImplementation("junit:junit:4.13.2")
}

// ── Grammar-Kit: geracao de Lexer e Parser ────────────────────────────────────

// generateSclParser DEVE rodar antes do Lexer:
// o Scl.flex importa SclTypes (gerado pelo parser), entao
// SclTypes.java precisa existir antes do JFlex processar o .flex.
val generateSclParser = tasks.register<GenerateParserTask>("generateSclParser") {
    sourceFile.set(file("src/main/resources/grammar/Scl.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("com/scl/plugin/parser/SclParser.java")
    pathToPsiRoot.set("com/scl/plugin/psi")
    purgeOldFiles.set(true)
}

val generateSclLexer = tasks.register<GenerateLexerTask>("generateSclLexer") {
    sourceFile.set(file("src/main/resources/grammar/Scl.flex"))
    targetOutputDir.set(file("src/main/gen/com/scl/plugin/lexer"))
    purgeOldFiles.set(true)
    // Lexer depende do parser porque Scl.flex importa SclTypes (gerado)
    dependsOn(generateSclParser)
}

// Inclui codigo gerado pelo Grammar-Kit no classpath de compilacao
sourceSets {
    main {
        java {
            srcDir("src/main/gen")
        }
    }
}

// Compilacao Kotlin depende de ambas as geracoes
tasks.compileKotlin {
    dependsOn(generateSclLexer)   // transitivamente depende de generateSclParser
}

// ── runIde: abre a pasta examples/ automaticamente como projeto de teste ─────
tasks {
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        // IntelliJ aceita um caminho de projeto como argumento posicional.
        // Isso elimina o passo manual de File > Open a cada teste.
        args(project.file("examples").absolutePath)
    }
}

// ── Configuracao do Plugin ────────────────────────────────────────────────────
intellijPlatform {
    pluginConfiguration {
        name.set(providers.gradleProperty("pluginName"))
        version.set(providers.gradleProperty("pluginVersion"))

        ideaVersion {
            // 261 = IntelliJ IDEA 2026.1
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
            untilBuild.set(provider { null })
        }
    }
}