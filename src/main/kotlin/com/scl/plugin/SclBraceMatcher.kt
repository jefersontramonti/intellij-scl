package com.scl.plugin

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.scl.plugin.psi.SclTypes

/**
 * Destaque de pares de palavras-chave SCL — Fase 4A (fix Bug 1).
 *
 * TODOS os pares usam structural=true.
 *
 * No IntelliJ Platform, structural=false é destinado a brackets simples
 * como (){}[] e NÃO produz highlighting visual para pares de palavras-chave.
 * Para IF↔END_IF, CASE↔END_CASE etc. funcionarem no editor, todos os pares
 * precisam de structural=true, que ativa o BraceHighlightingHandler baseado
 * em PSI (mais robusto que o scan de tokens usado para pares não-estruturais).
 *
 * Impacto de structural=true em todos:
 *   – Ctrl+Shift+M funciona para QUALQUER par (bloco ou controle)
 *   – Destaque visual ao clicar em IF destaca END_IF, e vice-versa
 *   – Depth counting correto mesmo com pares aninhados (IF dentro de IF)
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/additional-minor-features.html
 */
class SclBraceMatcher : PairedBraceMatcher {

    private val PAIRS = arrayOf(

        // ── Blocos de programa ────────────────────────────────────────────────
        BracePair(SclTypes.FUNCTION_BLOCK,         SclTypes.END_FUNCTION_BLOCK,         true),
        BracePair(SclTypes.FUNCTION,               SclTypes.END_FUNCTION,               true),
        BracePair(SclTypes.ORGANIZATION_BLOCK,     SclTypes.END_ORGANIZATION_BLOCK,     true),
        BracePair(SclTypes.DATA_BLOCK,             SclTypes.END_DATA_BLOCK,             true),
        BracePair(SclTypes.TYPE,                   SclTypes.END_TYPE,                   true),

        // ── Controle de fluxo — structural=true para highlighting funcionar ───
        BracePair(SclTypes.IF,     SclTypes.END_IF,     true),
        BracePair(SclTypes.CASE,   SclTypes.END_CASE,   true),
        BracePair(SclTypes.FOR,    SclTypes.END_FOR,    true),
        BracePair(SclTypes.WHILE,  SclTypes.END_WHILE,  true),
        BracePair(SclTypes.REPEAT, SclTypes.END_REPEAT, true),
        BracePair(SclTypes.REGION, SclTypes.END_REGION, true),

        // ── Seções de variáveis (todas fecham com END_VAR) ────────────────────
        // Múltiplos tokens de abertura → mesmo token de fechamento.
        // O matcher conta depth corretamente: cada VAR_* incrementa +1,
        // cada END_VAR decrementa -1.
        BracePair(SclTypes.VAR,          SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_INPUT,    SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_OUTPUT,   SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_IN_OUT,   SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_STATIC,   SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_TEMP,     SclTypes.END_VAR, true),
        BracePair(SclTypes.VAR_CONSTANT, SclTypes.END_VAR, true),
    )

    override fun getPairs(): Array<BracePair> = PAIRS

    /**
     * Retorna true para todos os contextos — SCL não tem restrições de posição
     * para palavras-chave estruturais além da sintaxe da linguagem.
     */
    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?,
    ): Boolean = true

    /**
     * Offset onde a construção começa — para SCL é o próprio token de abertura.
     */
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int =
        openingBraceOffset
}
