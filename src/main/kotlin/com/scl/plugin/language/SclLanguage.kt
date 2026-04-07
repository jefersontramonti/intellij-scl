package com.scl.plugin.language

import com.intellij.lang.Language

/**
 * Define a linguagem SCL (Structured Control Language) no IntelliJ Platform.
 *
 * Padrao Singleton confirmado na documentacao oficial:
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 *
 * O ID "SCL" deve ser unico na plataforma — usado em todos os registros
 * do plugin.xml (syntaxHighlighter, parserDefinition, completion, etc.)
 */
object SclLanguage : Language("SCL") {
    // readResolve garante que a desserializacao retorna o singleton
    private fun readResolve(): Any = SclLanguage
}
