package com.scl.plugin.lexer

import com.intellij.lexer.FlexAdapter

/**
 * Adapta o SclLexer gerado pelo JFlex para a interface Lexer do IntelliJ.
 *
 * FlexAdapter e o adaptador padrao recomendado pela plataforma para
 * lexers JFlex. Ele implementa a interface Lexer do IntelliJ delegando
 * para o lexer gerado.
 *
 * IMPORTANTE: SclLexer.java e gerado automaticamente pelo JFlex a partir
 * de Scl.flex. Antes de compilar o projeto, execute:
 *   ./gradlew generateSclLexer
 * ou no IntelliJ: clique com botao direito em Scl.flex → Run JFlex Generator
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html
 */
class SclLexerAdapter : FlexAdapter(SclLexer(null))
