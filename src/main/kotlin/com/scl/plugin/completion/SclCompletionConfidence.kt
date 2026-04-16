package com.scl.plugin.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclTypes

/**
 * Controla o auto-popup de completion enquanto o usuário digita em arquivos SCL.
 *
 * Sem este componente, a IntelliJ Platform suprime o popup automático em
 * linguagens customizadas (comportamento de fallback conservador).
 * Ctrl+Space continua funcionando, mas digitar `s_`, `i_Start`, `#var`, etc.
 * não abre o popup — este é o bug relatado pelo usuário.
 *
 * Lógica em 3 camadas:
 *   1. Dentro de comentários (// ou (* *)) → YES (suprimir sempre)
 *   2. Char antes do cursor é letra/dígito/sublinhado/'#' → NO (forçar popup)
 *   3. Qualquer outro caractere (';', '(', ':') → UNSURE (deferir ao padrão)
 */
@Suppress("OVERRIDE_DEPRECATION")
class SclCompletionConfidence : CompletionConfidence() {

    override fun shouldSkipAutopopup(
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int,
    ): ThreeState {
        if (psiFile.language != SclLanguage) return ThreeState.UNSURE

        // 1. Suprimir dentro de comentários
        val tokenType = contextElement.node?.elementType
        if (tokenType == SclTypes.LINE_COMMENT ||
            tokenType == SclTypes.BLOCK_COMMENT ||
            PsiTreeUtil.getParentOfType(contextElement, PsiComment::class.java) != null
        ) {
            return ThreeState.YES
        }

        // 2. Ativar popup ao digitar identificadores
        if (offset == 0) return ThreeState.UNSURE
        val charBefore = psiFile.text[offset - 1]
        return if (charBefore.isLetterOrDigit() || charBefore == '_' || charBefore == '#') {
            ThreeState.NO   // forçar auto-popup
        } else {
            ThreeState.UNSURE
        }
    }
}
