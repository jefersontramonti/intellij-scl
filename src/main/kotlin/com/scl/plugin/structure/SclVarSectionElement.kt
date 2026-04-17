package com.scl.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.scl.plugin.psi.SclConstDecl
import com.scl.plugin.psi.SclConstSection
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarSection
import javax.swing.Icon

/**
 * Nó agrupador de seção VAR no Structure View.
 *
 * Aceita SclVarSection (todas as seções VAR_INPUT, VAR_OUTPUT, VAR, VAR_IN_OUT,
 * VAR_STATIC, VAR_TEMP, VAR_CONSTANT) e SclConstSection (CONST...END_CONST).
 *
 * Como nossa gramática usa um único PSI genérico SclVarSection para todas as
 * seções VAR, o tipo real (INPUT/OUTPUT/etc.) é determinado pelo primeiro token.
 */
class SclVarSectionElement(
    private val section: PsiElement,   // SclVarSection ou SclConstSection
) : StructureViewTreeElement {

    override fun getValue(): Any = section

    override fun navigate(requestFocus: Boolean) {
        (section as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    // ── Apresentação visual ───────────────────────────────────────────────────
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = sectionLabel()
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = sectionIcon()
    }

    // ── Filhos: variáveis declaradas nesta seção ──────────────────────────────
    override fun getChildren(): Array<TreeElement> {
        val label = sectionLabel()
        return when (section) {
            is SclVarSection -> section.varDeclList
                .map { SclVariableElement(it, label) }
                .toTypedArray()
            is SclConstSection -> section.constDeclList
                .map { SclVariableElement(it, label) }
                .toTypedArray()
            else -> TreeElement.EMPTY_ARRAY
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun sectionLabel(): String {
        if (section is SclConstSection) return "CONST"
        if (section is SclVarSection) {
            return when (section.node.firstChildNode?.elementType) {
                SclTypes.VAR_INPUT    -> "VAR_INPUT"
                SclTypes.VAR_OUTPUT   -> "VAR_OUTPUT"
                SclTypes.VAR_IN_OUT   -> "VAR_IN_OUT"
                SclTypes.VAR_STATIC   -> "VAR (STATIC)"
                SclTypes.VAR          -> "VAR (STATIC)"
                SclTypes.VAR_TEMP     -> "VAR_TEMP"
                SclTypes.VAR_CONSTANT -> "VAR_CONSTANT"
                else                  -> "VAR"
            }
        }
        return "VAR"
    }

    private fun sectionIcon(): Icon {
        if (section is SclConstSection) return AllIcons.Nodes.Constant
        if (section is SclVarSection) {
            return when (section.node.firstChildNode?.elementType) {
                SclTypes.VAR_INPUT    -> AllIcons.Nodes.Parameter    // verde  — input
                SclTypes.VAR_OUTPUT   -> AllIcons.Nodes.Property     // vermelho — output
                SclTypes.VAR_IN_OUT   -> AllIcons.Nodes.PropertyRead // laranja — in_out
                SclTypes.VAR_STATIC   -> AllIcons.Nodes.Field        // roxo — static
                SclTypes.VAR          -> AllIcons.Nodes.Field        // roxo — static
                SclTypes.VAR_TEMP     -> AllIcons.Nodes.Variable     // cinza — temp
                SclTypes.VAR_CONSTANT -> AllIcons.Nodes.Constant     // ciano — constant
                else                  -> AllIcons.Nodes.Field
            }
        }
        return AllIcons.Nodes.Field
    }
}
