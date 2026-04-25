package com.scl.plugin.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.reference.SclReference
import com.intellij.psi.search.PsiSearchHelper

/**
 * Find Usages (Alt+F7) sem depender de PsiReferenceContributor.
 *
 * O PsiReferenceContributor não chama providers para LeafPsiElement de linguagens
 * customizadas em IntelliJ 2026.1. Este searcher usa PsiSearchHelper (word index
 * gerado pelo DefaultWordsScanner do SclFindUsagesProvider) para encontrar todos
 * os IDENTIFIER tokens com o nome buscado, depois confirma via resolve().
 */
class SclUsagesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = parameters.elementToSearch as? SclNamedElement ?: return
        val name   = target.name?.takeIf { it.isNotEmpty() } ?: return
        val scope  = parameters.effectiveSearchScope

        val helper = PsiSearchHelper.getInstance(target.project)

        helper.processElementsWithWord(
            { element, _ ->
                if (element.containingFile?.fileType != SclFileType) return@processElementsWithWord true

                val nodeType = element.node?.elementType
                if (nodeType != SclTypes.IDENTIFIER && nodeType != SclTypes.QUOTED_IDENTIFIER) {
                    return@processElementsWithWord true
                }

                // Não incluir o nome da própria declaração
                val parent = element.parent as? SclNamedElement
                if (parent != null && parent.nameIdentifier === element) return@processElementsWithWord true

                val range = if (nodeType == SclTypes.QUOTED_IDENTIFIER) {
                    val t = element.text
                    if (t.length >= 2) TextRange(1, t.length - 1) else return@processElementsWithWord true
                } else {
                    TextRange(0, element.textLength)
                }

                val ref = SclReference(element, range)
                if (ref.isReferenceTo(target)) {
                    consumer.process(ref)
                }
                true
            },
            scope,
            name,
            UsageSearchContext.IN_CODE,
            true, // case insensitive
        )
    }
}
