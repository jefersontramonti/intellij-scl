package com.scl.plugin

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.scl.plugin.psi.SclTypes

/**
 * Destaque de pares de palavras-chave SCL — Fase 4A.
 *
 * Ao clicar em FUNCTION_BLOCK, o IDE destaca o END_FUNCTION_BLOCK correspondente
 * (e vice-versa). Pares estruturais (structural=true) recebem maior prioridade
 * de matching e são usados também para navegação de bloco (Ctrl+Shift+M).
 *
 * Pares suportados:
 *   Estruturais : FUNCTION_BLOCK, FUNCTION, ORGANIZATION_BLOCK, DATA_BLOCK, TYPE
 *   Controle    : IF, CASE, FOR, WHILE, REPEAT, REGION
 *   Var sections: VAR / VAR_INPUT / VAR_OUTPUT / VAR_IN_OUT /
 *                 VAR_STATIC / VAR_TEMP / VAR_CONSTANT → END_VAR
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/additional-minor-features.html
 */
class SclBraceMatcher : PairedBraceMatcher {

    private val PAIRS = arrayOf(

        // ── Pares estruturais ─────────────────────────────────────────────────
        // structural=true: maior prioridade + navegação por bloco (Ctrl+Shift+M)
        BracePair(SclTypes.FUNCTION_BLOCK,         SclTypes.END_FUNCTION_BLOCK,         true),
        BracePair(SclTypes.FUNCTION,               SclTypes.END_FUNCTION,               true),
        BracePair(SclTypes.ORGANIZATION_BLOCK,     SclTypes.END_ORGANIZATION_BLOCK,     true),
        BracePair(SclTypes.DATA_BLOCK,             SclTypes.END_DATA_BLOCK,             true),
        BracePair(SclTypes.TYPE,                   SclTypes.END_TYPE,                   true),

        // ── Controle de fluxo ─────────────────────────────────────────────────
        // structural=false: destaque visual mas não ativa navegação de bloco
        BracePair(SclTypes.IF,     SclTypes.END_IF,     false),
        BracePair(SclTypes.CASE,   SclTypes.END_CASE,   false),
        BracePair(SclTypes.FOR,    SclTypes.END_FOR,    false),
        BracePair(SclTypes.WHILE,  SclTypes.END_WHILE,  false),
        BracePair(SclTypes.REPEAT, SclTypes.END_REPEAT, false),
        BracePair(SclTypes.REGION, SclTypes.END_REGION, false),

        // ── Seções de variáveis (todas fecham com END_VAR) ─────────────────────
        // Múltiplos tokens de abertura → mesmo token de fechamento é válido;
        // o matcher conta depth corretamente pois cada abertura incrementa +1.
        BracePair(SclTypes.VAR,          SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_INPUT,    SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_OUTPUT,   SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_IN_OUT,   SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_STATIC,   SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_TEMP,     SclTypes.END_VAR, false),
        BracePair(SclTypes.VAR_CONSTANT, SclTypes.END_VAR, false),
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
