// ─────────────────────────────────────────────────────────────────────────────
// settings.gradle.kts
// Versoes verificadas em 06/04/2026:
//   IntelliJ Platform Gradle Plugin: 2.13.1
//   Gradle minimo exigido: 9.0.0
// ─────────────────────────────────────────────────────────────────────────────

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    // Settings plugin — OBRIGATORIO ficar aqui, nunca no build.gradle.kts
    id("org.jetbrains.intellij.platform.settings") version "2.13.1"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "scl-plugin"
