package com.scl.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.scl.plugin.psi.SclCaseStatement
import com.scl.plugin.psi.SclConstSection
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclRegionStmt
import com.scl.plugin.psi.SclTypeDef
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import com.scl.plugin.psi.SclVarSection

/**
 * Modelo do Structure View SCL.
 *
 * Define:
 *  - Quais tipos PSI disparam auto-seleção na árvore ao mover o cursor
 *  - Filtros da toolbar (VAR_TEMP, CONST)
 *  - Sorters (alfabético)
 */
class SclStructureViewModel(
    editor: Editor?,
    psiFile: PsiFile,
) : StructureViewModelBase(psiFile, editor, SclStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    // ── Tipos PSI que ativam auto-seleção na árvore ───────────────────────────
    override fun getSuitableClasses(): Array<Class<out PsiElement>> = arrayOf(
        SclFunctionBlockDecl::class.java,
        SclFunctionDecl::class.java,
        SclOrgBlockDecl::class.java,
        SclTypeDef::class.java,
        SclVarDecl::class.java,
        SclRegionStmt::class.java,
        SclCaseStatement::class.java,
    )

    // ── Variáveis são sempre folhas ───────────────────────────────────────────
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element is SclVariableElement

    // ── FBs sempre têm filhos expandíveis ────────────────────────────────────
    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
        element is SclStructureViewElement &&
        element.value is SclFunctionBlockDecl

    // ── Filtros disponíveis na toolbar ────────────────────────────────────────
    override fun getFilters(): Array<Filter> = arrayOf(
        SclShowTempVarsFilter,
        SclShowConstFilter,
    )

    // ── Sorter opcional — ordenação alfabética ────────────────────────────────
    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)
}

// ─────────────────────────────────────────────────────────────────────────────
// Filtro: mostrar/ocultar VAR_TEMP
// ─────────────────────────────────────────────────────────────────────────────

object SclShowTempVarsFilter : Filter {
    private const val ID = "SCL_SHOW_TEMP"

    override fun getName(): String = ID
    override fun toString(): String = ID

    override fun getPresentation(): ActionPresentation =
        ActionPresentationData(
            "Show VAR_TEMP",
            "Show or hide temporary variable sections",
            AllIcons.Nodes.Variable,
        )

    /** Por padrão OCULTAR VAR_TEMP (isReverted = true → filtro ativo por padrão). */
    override fun isReverted(): Boolean = true

    override fun isVisible(treeNode: TreeElement): Boolean {
        if (treeNode !is SclVarSectionElement) return true
        val section = treeNode.getValue()
        if (section is SclVarSection) {
            return section.node.firstChildNode?.elementType != SclTypes.VAR_TEMP
        }
        return true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filtro: mostrar/ocultar CONST
// ─────────────────────────────────────────────────────────────────────────────

object SclShowConstFilter : Filter {
    private const val ID = "SCL_SHOW_CONST"

    override fun getName(): String = ID
    override fun toString(): String = ID

    override fun getPresentation(): ActionPresentation =
        ActionPresentationData(
            "Show CONST",
            "Show or hide constant declaration sections",
            AllIcons.Nodes.Constant,
        )

    /** Por padrão MOSTRAR CONST (isReverted = false). */
    override fun isReverted(): Boolean = false

    override fun isVisible(treeNode: TreeElement): Boolean {
        if (treeNode !is SclVarSectionElement) return true
        return treeNode.getValue() !is SclConstSection
    }
}
