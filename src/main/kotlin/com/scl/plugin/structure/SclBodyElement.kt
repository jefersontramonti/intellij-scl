package com.scl.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.scl.plugin.psi.SclCaseStatement
import com.scl.plugin.psi.SclForStatement
import com.scl.plugin.psi.SclIfStatement
import com.scl.plugin.psi.SclRegionStmt
import com.scl.plugin.psi.SclRepeatStatement
import com.scl.plugin.psi.SclStatementList
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclWhileStatement
import javax.swing.Icon

/** True when the body has at least one REGION, CASE, IF, FOR, WHILE, or REPEAT. */
internal fun SclStatementList.hasInterestingStatements(): Boolean =
    children.any {
        it is SclRegionStmt || it is SclCaseStatement ||
        it is SclIfStatement || it is SclForStatement ||
        it is SclWhileStatement || it is SclRepeatStatement
    }

/**
 * Nó "▶ BEGIN" no Structure View — mostra o corpo executável de FB/FC/OB.
 *
 * Filhos: REGIONs, CASEs, IFs, FORs, WHILEs, REPEATs em ordem de aparição.
 * Statements de linha (assign, call) são omitidos para não poluir a árvore.
 */
class SclBodyElement(
    private val statementList: SclStatementList,
) : StructureViewTreeElement {

    override fun getValue(): Any = statementList

    override fun navigate(requestFocus: Boolean) {
        (statementList as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "BEGIN"
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = AllIcons.Actions.Execute
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        for (child in statementList.children) {
            when (child) {
                is SclRegionStmt      -> children.add(SclStmtElement(child))
                is SclCaseStatement   -> children.add(SclStmtElement(child))
                is SclIfStatement     -> children.add(SclStmtElement(child))
                is SclForStatement    -> children.add(SclStmtElement(child))
                is SclWhileStatement  -> children.add(SclStmtElement(child))
                is SclRepeatStatement -> children.add(SclStmtElement(child))
            }
        }
        return children.toTypedArray()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nó folha para cada statement relevante no corpo do bloco
// ─────────────────────────────────────────────────────────────────────────────

class SclStmtElement(
    private val stmt: PsiElement,
) : StructureViewTreeElement {

    override fun getValue(): Any = stmt

    override fun navigate(requestFocus: Boolean) {
        (stmt as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        (stmt as? NavigatablePsiElement)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean =
        (stmt as? NavigatablePsiElement)?.canNavigateToSource() == true

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = stmtLabel()
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = stmtIcon()
    }

    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY

    private fun stmtLabel(): String = when (stmt) {
        is SclRegionStmt -> {
            val name = extractRegionName(stmt)
            if (name != null) "REGION $name" else "REGION"
        }
        is SclCaseStatement -> {
            val expr = stmt.expression?.text?.trim()
            if (expr != null) "CASE $expr" else "CASE"
        }
        is SclIfStatement -> {
            val cond = stmt.expression?.text?.trim()?.take(40)
            if (cond != null) "IF $cond" else "IF"
        }
        is SclForStatement -> {
            val varName = extractForVar(stmt)
            if (varName != null) "FOR $varName" else "FOR"
        }
        is SclWhileStatement -> {
            val cond = stmt.expression?.text?.trim()?.take(40)
            if (cond != null) "WHILE $cond" else "WHILE"
        }
        is SclRepeatStatement -> "REPEAT"
        else -> stmt.javaClass.simpleName
    }

    private fun stmtIcon(): Icon = when (stmt) {
        is SclRegionStmt      -> AllIcons.Actions.GroupByModule
        is SclCaseStatement   -> AllIcons.Nodes.DataTables
        is SclIfStatement     -> AllIcons.General.ChevronDown
        is SclForStatement,
        is SclWhileStatement,
        is SclRepeatStatement -> AllIcons.Actions.Refresh
        else                  -> AllIcons.Nodes.Unknown
    }

    private fun extractRegionName(region: SclRegionStmt): String? {
        var node = region.node.firstChildNode
        var pastRegion = false
        while (node != null) {
            when {
                node.elementType == SclTypes.REGION      -> pastRegion = true
                pastRegion && node.elementType == SclTypes.REGION_NAME -> return node.text.trim()
                pastRegion && node.elementType !in SKIP_IN_NAME -> return null
            }
            node = node.treeNext
        }
        return null
    }

    companion object {
        private val SKIP_IN_NAME = setOf(
            com.intellij.psi.TokenType.WHITE_SPACE,
            SclTypes.LINE_COMMENT,
            SclTypes.BLOCK_COMMENT
        )
    }

    private fun extractForVar(forStmt: SclForStatement): String? {
        var node = forStmt.node.firstChildNode
        var pastFor = false
        while (node != null) {
            when {
                node.elementType == SclTypes.FOR                   -> pastFor = true
                pastFor && node.elementType == SclTypes.IDENTIFIER -> return node.text
            }
            node = node.treeNext
        }
        return null
    }
}
