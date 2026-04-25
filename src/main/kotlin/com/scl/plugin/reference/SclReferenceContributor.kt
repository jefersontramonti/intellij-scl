package com.scl.plugin.reference

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclTypes

private val LOG = Logger.getInstance("SclReference")

/**
 * Registra providers de referência para IDENTIFIER e QUOTED_IDENTIFIER em
 * arquivos .scl — habilita Ctrl+Click e Find Usages.
 *
 * Usa LeafPsiElement + withElementType em vez de psiElement(IElementType)
 * para garantir que tokens dentro de regras private (lvalue, qualifiedName,
 * callableRef) sejam alcançados em IntelliJ 2026.1+.
 */
class SclReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        LOG.warn("[SCL-REF] registerReferenceProviders called")

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withElementType(SclTypes.IDENTIFIER),
            SclIdentifierReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withElementType(SclTypes.QUOTED_IDENTIFIER),
            SclQuotedIdentifierReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }
}

class SclIdentifierReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val parentType = element.parent?.javaClass?.simpleName
        val isDecl = isDeclarationNameNode(element)
        LOG.warn("[SCL-REF] ID='${element.text}' parent=$parentType isDecl=$isDecl")

        if (isDecl) {
            LOG.warn("[SCL-REF]   SKIPPED: is declaration name")
            return PsiReference.EMPTY_ARRAY
        }

        LOG.warn("[SCL-REF]   CREATING ref for '${element.text}'")
        return arrayOf(SclReference(element, TextRange(0, element.textLength)))
    }

    private fun isDeclarationNameNode(element: PsiElement): Boolean {
        val parent = element.parent as? SclNamedElement ?: return false
        return parent.nameIdentifier === element
    }
}

class SclQuotedIdentifierReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val parentType = element.parent?.javaClass?.simpleName
        LOG.warn("[SCL-REF] QUOTED='${element.text}' parent=$parentType")

        val parent = element.parent
        if (parent is SclNamedElement && parent.nameIdentifier === element) {
            LOG.warn("[SCL-REF]   SKIPPED: is declaration name")
            return PsiReference.EMPTY_ARRAY
        }

        val text = element.text
        if (text.length < 2) return PsiReference.EMPTY_ARRAY
        val range = TextRange(1, text.length - 1)
        return arrayOf(SclReference(element, range))
    }
}
