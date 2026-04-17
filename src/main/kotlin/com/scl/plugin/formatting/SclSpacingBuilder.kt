package com.scl.plugin.formatting

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclTypes

/**
 * Constrói as regras de espaçamento entre tokens SCL para o formatter.
 *
 * Nota de mapeamento (spec → token real):
 *   MUL      → MULTIPLY  (asterisco *)
 *   DIV      → DIVIDE    (barra / ), DIV é a keyword de divisão inteira
 *   LE/GE    → LEQ / GEQ
 *   AND_KW   → AND
 *   OR_KW    → OR
 *   NOT_KW   → NOT
 *   XOR_KW   → XOR
 *   MOD_KW   → MOD
 *   RANGE    → DOTDOT    (..)
 */
object SclSpacingBuilder {

    fun create(settings: CodeStyleSettings): SpacingBuilder =
        SpacingBuilder(settings, SclLanguage)

            // ── OPERADORES DE ATRIBUIÇÃO ──────────────────────────────────────
            // := → 1 espaço antes e depois
            .around(SclTypes.ASSIGN).spaces(1)
            // => (output assign) → 1 espaço antes e depois: Q => q_bDone
            .around(SclTypes.OUTPUT_ASSIGN).spaces(1)

            // ── OPERADORES DE COMPARAÇÃO ──────────────────────────────────────
            .around(SclTypes.EQ).spaces(1)
            .around(SclTypes.NEQ).spaces(1)
            .around(SclTypes.LT).spaces(1)
            .around(SclTypes.GT).spaces(1)
            .around(SclTypes.LEQ).spaces(1)
            .around(SclTypes.GEQ).spaces(1)

            // ── OPERADORES ARITMÉTICOS ────────────────────────────────────────
            .around(SclTypes.PLUS).spaces(1)
            .around(SclTypes.MINUS).spaces(1)
            .around(SclTypes.MULTIPLY).spaces(1)   // * (não confundir com DIV keyword)
            .around(SclTypes.DIVIDE).spaces(1)     // / (barra, não a keyword DIV)
            .around(SclTypes.DIV).spaces(1)        // DIV keyword (divisão inteira)
            .around(SclTypes.MOD).spaces(1)
            .around(SclTypes.POWER).spaces(1)      // ** (potência)

            // ── DECLARAÇÕES DE VARIÁVEIS ──────────────────────────────────────
            // "nomeVar : TIPO" → 1 espaço antes e depois do ":"
            .around(SclTypes.COLON).spaces(1)

            // ── PONTO-E-VÍRGULA ───────────────────────────────────────────────
            // "stmt;" → sem espaço antes do ";"
            .before(SclTypes.SEMICOLON).none()

            // ── VÍRGULAS ──────────────────────────────────────────────────────
            // "TON(IN:=x, PT:=T#5S)" → sem espaço antes, 1 depois
            .before(SclTypes.COMMA).none()
            .after(SclTypes.COMMA).spaces(1)

            // ── PARÊNTESES ────────────────────────────────────────────────────
            // "ABS(x)" → sem espaço após "(" e antes de ")"
            .after(SclTypes.LPAREN).none()
            .before(SclTypes.RPAREN).none()

            // ── COLCHETES ─────────────────────────────────────────────────────
            .after(SclTypes.LBRACKET).none()
            .before(SclTypes.RBRACKET).none()

            // ── OPERADORES LÓGICOS ────────────────────────────────────────────
            // AND, OR, XOR → 1 espaço; NOT → 1 espaço depois (unário)
            .around(SclTypes.AND).spaces(1)
            .around(SclTypes.AMPERSAND).spaces(1)  // & alternativo ao AND
            .around(SclTypes.OR).spaces(1)
            .around(SclTypes.XOR).spaces(1)
            .before(SclTypes.NOT).spaces(1)
            .after(SclTypes.NOT).spaces(1)

            // ── PONTO (acesso de membro) ──────────────────────────────────────
            // "timer.Q" → sem espaços em volta do "."
            .around(SclTypes.DOT).none()

            // ── RANGE (..) ────────────────────────────────────────────────────
            // "ARRAY[1..10]" → sem espaços em volta de ".."
            .around(SclTypes.DOTDOT).none()
}
