package com.scl.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.scl.plugin.SclIcons
import com.scl.plugin.psi.SclConstSection
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclTopLevelDecl
import com.scl.plugin.psi.SclTypeDef
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarSection
import javax.swing.Icon

/**
 * Nó genérico do Structure View.
 *
 * Cobre: PsiFile (raiz), SclFunctionBlockDecl (FB), SclFunctionDecl (FC),
 * SclOrgBlockDecl (OB), SclTypeDef (cada UDT dentro de TYPE...END_TYPE).
 *
 * Mapeamento PSI real (diferente da spec que usa nomes fictícios):
 *   SclFunctionBlock  → SclFunctionBlockDecl
 *   SclFunction       → SclFunctionDecl
 *   SclOrganizationBlock → SclOrgBlockDecl
 *   SclTypeBlock      → SclTypeDef  (cada definição de UDT)
 *   SclVarInputSection etc. → SclVarSection (genérico, kind = primeiro token)
 *   SclVariableDeclaration  → SclVarDecl
 *   SclStructType     → SclStructDecl
 */
class SclStructureViewElement(
    private val element: PsiElement,
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    // ── Navegação: clicar no nó move o cursor para a declaração ──────────────
    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() == true

    // ── Chave para ordenação alfabética ──────────────────────────────────────
    override fun getAlphaSortKey(): String = blockName()

    // ── Apresentação visual ───────────────────────────────────────────────────
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {

        override fun getPresentableText(): String = when (element) {
            is PsiFile -> element.name
            else       -> blockName()
        }

        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon? = when (element) {
            is PsiFile              -> AllIcons.FileTypes.Custom
            is SclFunctionBlockDecl -> SclIcons.FUNCTION_BLOCK
            is SclFunctionDecl      -> SclIcons.FUNCTION
            is SclOrgBlockDecl      -> SclIcons.ORG_BLOCK
            is SclTypeDef           -> SclIcons.UDT
            else                    -> null
        }
    }

    // ── Filhos na árvore ──────────────────────────────────────────────────────
    override fun getChildren(): Array<TreeElement> = when (element) {
        is PsiFile              -> buildChildrenForFile(element)
        is SclFunctionBlockDecl -> buildChildrenForFB(element)
        is SclFunctionDecl      -> buildChildrenForFC(element)
        is SclOrgBlockDecl      -> buildChildrenForOB(element)
        is SclTypeDef           -> buildChildrenForTypeDef(element)
        else                    -> TreeElement.EMPTY_ARRAY
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUILDERS DE FILHOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Arquivo: itera SclTopLevelDecl na ordem do arquivo e extrai cada bloco.
     * Cada TYPE...END_TYPE pode conter múltiplos SclTypeDef — cada um vira um nó.
     */
    private fun buildChildrenForFile(file: PsiFile): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        for (child in file.children) {
            if (child !is SclTopLevelDecl) continue
            child.functionBlockDecl?.let { children.add(SclStructureViewElement(it)) }
            child.functionDecl?.let      { children.add(SclStructureViewElement(it)) }
            child.orgBlockDecl?.let      { children.add(SclStructureViewElement(it)) }
            child.typeDecl?.let { typeDecl ->
                // Cada typeDef com STRUCT vira um nó UDT separado
                for (typeDef in typeDecl.typeDefList) {
                    if (typeDef.structDecl != null || typeDef.typeRef != null) {
                        children.add(SclStructureViewElement(typeDef))
                    }
                }
            }
            // dataBlockDecl é omitido por ora (não aparece no spec)
        }
        return children.toTypedArray()
    }

    /**
     * FUNCTION_BLOCK: seções VAR não-vazias + CONST + BEGIN (corpo executável).
     */
    private fun buildChildrenForFB(fb: SclFunctionBlockDecl): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        fb.constSection?.takeIf { it.constDeclList.isNotEmpty() }
            ?.let { children.add(SclVarSectionElement(it)) }
        for (section in fb.varSectionList) {
            if (section.varDeclList.isNotEmpty()) {
                children.add(SclVarSectionElement(section))
            }
        }
        fb.statementList?.takeIf { it.hasInterestingStatements() }
            ?.let { children.add(SclBodyElement(it)) }
        return children.toTypedArray()
    }

    /**
     * FUNCTION: seções VAR não-vazias + nó de tipo de retorno + BEGIN.
     */
    private fun buildChildrenForFC(fc: SclFunctionDecl): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        for (section in fc.varSectionList) {
            if (section.varDeclList.isNotEmpty()) {
                children.add(SclVarSectionElement(section))
            }
        }
        children.add(SclReturnTypeElement(fc))
        fc.statementList?.takeIf { it.hasInterestingStatements() }
            ?.let { children.add(SclBodyElement(it)) }
        return children.toTypedArray()
    }

    /**
     * ORGANIZATION_BLOCK: seções VAR não-vazias + BEGIN.
     */
    private fun buildChildrenForOB(ob: SclOrgBlockDecl): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        for (section in ob.varSectionList) {
            if (section.varDeclList.isNotEmpty()) {
                children.add(SclVarSectionElement(section))
            }
        }
        ob.statementList?.takeIf { it.hasInterestingStatements() }
            ?.let { children.add(SclBodyElement(it)) }
        return children.toTypedArray()
    }

    /**
     * TYPE/UDT: campos do STRUCT diretamente (sem nó intermediário).
     * Para typeDef com typeRef simples (alias), não há campos a mostrar.
     */
    private fun buildChildrenForTypeDef(typeDef: SclTypeDef): Array<TreeElement> {
        val structDecl = typeDef.structDecl ?: return TreeElement.EMPTY_ARRAY
        return structDecl.structFieldList
            .map { SclVariableElement(it, "STRUCT") }
            .toTypedArray()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extrai o nome do bloco / UDT percorrendo o AST.
     * - FB/FC/OB: IDENTIFIER ou QUOTED_IDENTIFIER após a keyword do bloco
     * - SclTypeDef: primeiro token (blockName é private = inline no nó)
     */
    private fun blockName(): String = when (element) {
        is SclFunctionBlockDecl -> extractAfterKeyword(element, SclTypes.FUNCTION_BLOCK)
        is SclFunctionDecl      -> extractAfterKeyword(element, SclTypes.FUNCTION)
        is SclOrgBlockDecl      -> extractAfterKeyword(element, SclTypes.ORGANIZATION_BLOCK)
        is SclTypeDef           -> extractFirstIdentifier(element)
        is PsiFile              -> element.name
        else                    -> element.text.take(20)
    }

    private fun extractAfterKeyword(block: PsiElement, keyword: com.intellij.psi.tree.IElementType): String {
        var node = block.node.firstChildNode
        var seenKeyword = false
        while (node != null) {
            when {
                node.elementType == keyword                    -> seenKeyword = true
                seenKeyword && node.elementType == SclTypes.IDENTIFIER ->
                    return node.text
                seenKeyword && node.elementType == SclTypes.QUOTED_IDENTIFIER ->
                    return node.text.trim('"')
            }
            node = node.treeNext
        }
        return "?"
    }

    private fun extractFirstIdentifier(psiElement: PsiElement): String {
        var node = psiElement.node.firstChildNode
        while (node != null) {
            when (node.elementType) {
                SclTypes.IDENTIFIER         -> return node.text
                SclTypes.QUOTED_IDENTIFIER  -> return node.text.trim('"')
                SclTypes.COLON              -> return "?"  // não passar do ':'
            }
            node = node.treeNext
        }
        return "?"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nó especial para tipo de retorno de FC
// ─────────────────────────────────────────────────────────────────────────────

class SclReturnTypeElement(
    private val function: SclFunctionDecl,
) : StructureViewTreeElement {

    override fun getValue(): Any = function

    override fun navigate(requestFocus: Boolean) {
        (function as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String {
            // typeRef?.text é o tipo de retorno; null quando VOID
            val typeName = function.typeRef?.text?.trim() ?: "VOID"
            return "\u21A9 $typeName"  // ↩ REAL
        }

        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.Type
    }

    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY
}
