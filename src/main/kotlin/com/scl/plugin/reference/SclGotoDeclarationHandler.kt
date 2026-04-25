package com.scl.plugin.reference

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclTypes

private val GTLOG = Logger.getInstance("SclGotoDecl")

/**
 * Ctrl+Click / Ctrl+B → Go to Declaration.
 *
 * Usa GotoDeclarationHandler (offset-based) em vez de PsiReferenceContributor
 * porque em IntelliJ 2026.1 o PsiReferenceContributor não chama providers para
 * LeafPsiElement de linguagens customizadas Grammar-Kit (mesmo bug que já afetou
 * o documentation provider — solução idêntica).
 */
class SclGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {
        sourceElement ?: return null
        if (sourceElement.containingFile?.fileType != SclFileType) return null

        val nodeType = sourceElement.node?.elementType ?: return null
        if (nodeType != SclTypes.IDENTIFIER && nodeType != SclTypes.QUOTED_IDENTIFIER) return null

        // Não navegar a partir do próprio nome da declaração (já estamos nela)
        if (isDeclarationNameNode(sourceElement)) return null

        val rangeInElement = if (nodeType == SclTypes.QUOTED_IDENTIFIER) {
            val text = sourceElement.text
            if (text.length < 2) return null
            TextRange(1, text.length - 1)
        } else {
            TextRange(0, sourceElement.textLength)
        }

        GTLOG.warn("[SCL-GOTO] '${sourceElement.text}' in ${sourceElement.containingFile?.name}")

        val target = SclReference(sourceElement, rangeInElement).resolve() ?: run {
            GTLOG.warn("[SCL-GOTO]   -> null (not resolved)")
            return null
        }

        GTLOG.warn("[SCL-GOTO]   -> ${target.javaClass.simpleName} '${(target as? SclNamedElement)?.name}'")
        return arrayOf(target)
    }

    private fun isDeclarationNameNode(element: PsiElement): Boolean {
        val parent = element.parent as? SclNamedElement ?: return false
        return parent.nameIdentifier === element
    }
}