package com.scl.plugin.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import com.scl.plugin.psi.SclVarSection

/**
 * Fornece [SclVariableDocumentationTarget] para qualquer variável SCL sob o cursor.
 *
 * Usa a API offset-based [DocumentationTargetProvider] (não PsiDocumentationTargetProvider)
 * para evitar problemas de PSI element matching: Grammar-Kit leaf tokens às vezes
 * chegam com linguagem inesperada, ou o IntelliJ passa o nó composto pai em vez
 * do token IDENTIFIER folha.
 *
 * Estratégia:
 *   1. findElementAt(offset) → leaf sob o cursor
 *   2. Sobe na PSI tree procurando SclVarDecl (hover sobre a declaração)
 *   3. Se não encontrar, resolve pelo texto do leaf nas seções VAR do bloco pai
 */
class SclDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file.language != SclLanguage) return emptyList()

        val leaf = file.findElementAt(offset) ?: return emptyList()

        val target = resolveTarget(leaf) ?: return emptyList()
        return listOf(target)
    }

    private fun resolveTarget(leaf: com.intellij.psi.PsiElement): SclVariableDocumentationTarget? {
        // Passo 1 — subir na árvore: talvez o próprio leaf ou pai imediato seja SclVarDecl
        var el: com.intellij.psi.PsiElement? = leaf
        while (el != null && el !is PsiFile) {
            if (el is SclVarDecl) return SclVariableDocumentationTarget(el)
            el = el.parent
        }

        // Passo 2 — é um uso (referência): resolver pelo nome no bloco pai
        val tokenType = leaf.node?.elementType
        val name = when (tokenType) {
            SclTypes.IDENTIFIER    -> leaf.text.trim()
            SclTypes.LOCAL_VAR_ID  -> leaf.text.removePrefix("#").trim()
            else                   -> return null
        }.ifBlank { return null }

        return findDeclInScope(name, leaf)
    }

    /**
     * Procura [SclVarDecl] com [name] no bloco pai (FB/FC/OB) que contém [context].
     * Fallback: arquivo inteiro.
     * Case-insensitive (SCL é case-insensitive por spec IEC 61131-3).
     */
    private fun findDeclInScope(
        name: String,
        context: com.intellij.psi.PsiElement,
    ): SclVariableDocumentationTarget? {
        val block = PsiTreeUtil.getParentOfType(
            context,
            SclFunctionBlockDecl::class.java,
            SclFunctionDecl::class.java,
            SclOrgBlockDecl::class.java,
        )
        val scanRoot = block ?: context.containingFile ?: return null

        val decl = PsiTreeUtil.findChildrenOfType(scanRoot, SclVarSection::class.java)
            .flatMap { it.varDeclList }
            .firstOrNull { d ->
                d.node.firstChildNode
                    ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
                    ?.text
                    ?.equals(name, ignoreCase = true) == true
            } ?: return null

        return SclVariableDocumentationTarget(decl)
    }
}
