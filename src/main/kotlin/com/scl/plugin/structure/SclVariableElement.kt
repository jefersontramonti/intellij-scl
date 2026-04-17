package com.scl.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.scl.plugin.psi.SclConstDecl
import com.scl.plugin.psi.SclStructField
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import javax.swing.Icon

/**
 * Nó folha do Structure View — representa uma variável ou constante declarada.
 *
 * Suporta três tipos PSI:
 *   SclVarDecl   — declaração numa seção VAR_xxx (ex: i_bStart : BOOL)
 *   SclConstDecl — declaração em CONST (ex: MAX_VAL := 100)
 *   SclStructField — campo de STRUCT (ex: rSpeed : REAL)
 *
 * @param declaration  Elemento PSI da declaração
 * @param sectionLabel Rótulo da seção pai ("VAR_INPUT", "VAR_TEMP", "STRUCT", etc.)
 */
class SclVariableElement(
    private val declaration: PsiElement,
    private val sectionLabel: String,
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = declaration

    override fun navigate(requestFocus: Boolean) {
        (declaration as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        (declaration as? NavigatablePsiElement)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean =
        (declaration as? NavigatablePsiElement)?.canNavigateToSource() == true

    // ── Chave para ordenação alfabética ──────────────────────────────────────
    override fun getAlphaSortKey(): String = varName()

    // ── Apresentação visual ───────────────────────────────────────────────────
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {

        // Texto principal: nome da variável (ex: "i_bStart")
        override fun getPresentableText(): String = varName()

        // Texto secundário à direita: ": TIPO" em cinza
        override fun getLocationString(): String = varType()?.let { ": $it" } ?: ""

        // Ícone consistente com a seção pai e com o completion/hover doc
        override fun getIcon(unused: Boolean): Icon = sectionIcon()
    }

    // Variáveis são sempre folhas — sem filhos
    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extrai o nome da variável percorrendo o AST.
     * Para SclVarDecl, SclConstDecl e SclStructField, o primeiro token é sempre IDENTIFIER.
     */
    private fun varName(): String {
        // Para SclVarDecl, SclConstDecl e SclStructField o IDENTIFIER é sempre o primeiro token.
        // Percorre os filhos diretos até encontrá-lo (para antes do COLON).
        var node = declaration.node.firstChildNode
        while (node != null) {
            if (node.elementType == SclTypes.IDENTIFIER) return node.text
            if (node.elementType == SclTypes.COLON) break
            node = node.treeNext
        }
        return "?"
    }

    /**
     * Extrai o texto do tipo (typeRef) da declaração.
     * Para SclConstDecl sem typeRef explícito, retorna null.
     */
    private fun varType(): String? = when (declaration) {
        is SclVarDecl    -> declaration.typeRef?.text?.trim()
        is SclConstDecl  -> declaration.typeRef?.text?.trim()
        is SclStructField -> declaration.typeRef.text.trim()
        else             -> null
    }

    private fun sectionIcon(): Icon = when (sectionLabel) {
        "VAR_INPUT"    -> AllIcons.Nodes.Parameter    // verde
        "VAR_OUTPUT"   -> AllIcons.Nodes.Property     // vermelho
        "VAR_IN_OUT"   -> AllIcons.Nodes.PropertyRead // laranja
        "VAR (STATIC)" -> AllIcons.Nodes.Field        // roxo
        "VAR_TEMP"     -> AllIcons.Nodes.Variable     // cinza
        "CONST",
        "VAR_CONSTANT" -> AllIcons.Nodes.Constant     // ciano
        "STRUCT"       -> AllIcons.Nodes.Field        // roxo
        else           -> AllIcons.Nodes.Variable
    }
}
