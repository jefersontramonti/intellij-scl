package com.scl.plugin.linter.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiDocumentManager
import com.scl.plugin.psi.*

/**
 * Detecta o antipadrão IF bool THEN x := TRUE; ELSE x := FALSE; END_IF
 * e oferece quick fix para simplificar para x := condição;
 */
class SclBooleanIfInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Unnecessary IF for boolean assignment"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is SclIfStatement) checkIfStatement(element, holder)
            }
        }

    private fun checkIfStatement(stmt: SclIfStatement, holder: ProblemsHolder) {
        if (stmt.elsifClauseList.isNotEmpty()) return
        val elseClause = stmt.elseClause ?: return
        val condition  = stmt.expression  ?: return

        val thenAssign = singleAssign(stmt.statementList)      ?: return
        val elseAssign = singleAssign(elseClause.statementList) ?: return

        val thenVar = lvalueText(thenAssign)
        val elseVar = lvalueText(elseAssign)
        if (thenVar.isEmpty() || !thenVar.equals(elseVar, ignoreCase = true)) return

        val thenVal = rhsText(thenAssign)
        val elseVal = rhsText(elseAssign)
        val isBoolPattern = (thenVal == "TRUE" && elseVal == "FALSE") ||
                            (thenVal == "FALSE" && elseVal == "TRUE")
        if (!isBoolPattern) return

        val inverted  = thenVal == "FALSE"
        val condText  = condition.text
        val rhs       = if (inverted) "NOT ($condText)" else condText
        val ifKeyword = stmt.firstChild ?: stmt

        holder.registerProblem(
            ifKeyword,
            "Unnecessary IF: simplify to '$thenVar := $rhs;'",
            ProblemHighlightType.WEAK_WARNING,
            SclSimplifyBooleanIfFix(stmt, thenVar, condText, inverted)
        )
    }

    private fun singleAssign(stmtList: SclStatementList?): SclAssignStmt? {
        stmtList ?: return null
        val assigns = stmtList.assignStmtList
        if (assigns.size != 1) return null
        val total = assigns.size +
            stmtList.callStmtList.size + stmtList.ifStatementList.size +
            stmtList.forStatementList.size + stmtList.whileStatementList.size +
            stmtList.repeatStatementList.size + stmtList.caseStatementList.size +
            stmtList.returnStmtList.size + stmtList.exitStmtList.size
        return if (total == 1) assigns.single() else null
    }

    private fun lvalueText(assign: SclAssignStmt): String {
        val assignOp = assign.node.findChildByType(SclTypes.ASSIGN) ?: return ""
        return assign.text.substring(0, assignOp.startOffset - assign.textOffset).trim()
    }

    private fun rhsText(assign: SclAssignStmt): String =
        assign.expressionList.lastOrNull()?.text?.uppercase()?.trim() ?: ""
}

private class SclSimplifyBooleanIfFix(
    element: PsiElement,
    private val varName: String,
    private val condText: String,
    private val inverted: Boolean
) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText(): String {
        val rhs = if (inverted) "NOT ($condText)" else condText
        return "Simplify to: $varName := $rhs;"
    }

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val rhs     = if (inverted) "NOT ($condText)" else condText
        val newText = "$varName := $rhs;"
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runWriteCommandAction
            doc.replaceString(startElement.textRange.startOffset, startElement.textRange.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
    }
}
