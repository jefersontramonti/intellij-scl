package com.scl.plugin.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.tree.TokenSet
import com.scl.plugin.psi.SclTypes

/**
 * Adapta o SclLexer (JFlex) para a interface Lexer do IntelliJ.
 *
 * Usa MergingLexerAdapter para fundir os tokens BLOCK_COMMENT adjacentes
 * (gerados caractere a caractere pelo estado exclusivo %xstate) em um
 * único token — necessário para folding, documentação e realce corretos.
 */
class SclLexerAdapter : MergingLexerAdapter(
    FlexAdapter(SclLexer(null)),
    TokenSet.create(SclTypes.BLOCK_COMMENT)
)
