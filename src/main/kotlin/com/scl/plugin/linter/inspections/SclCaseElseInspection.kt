package com.scl.plugin.linter.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiDocumentManager
import com.scl.plugin.psi.SclCaseStatement
import com.scl.plugin.psi.SclTypes

/**
 * Detecta instruções CASE sem ramo ELSE.
 * Per Siemens Programming Guidelines, sempre adicione ELSE para tratar estados inesperados.
 */
class SclCaseElseInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "CASE statement missing ELSE branch"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is SclCaseStatement && element.caseElseClause == null) {
                    val caseKeyword = element.firstChild ?: element
                    holder.registerProblem(
                        caseKeyword,
                        "CASE statement has no ELSE branch. " +
                        "Per Siemens Programming Guideline, always add ELSE to handle unexpected states " +
                        "(e.g., set error flag or status code).",
                        ProblemHighlightType.WEAK_WARNING,
                        SclAddCaseElseFix(element)
                    )
                }
            }
        }
}

private class SclAddCaseElseFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText()       = "Add ELSE branch to CASE"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val caseStmt = startElement as? SclCaseStatement ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runWriteCommandAction
            val endCaseNode = caseStmt.node.findChildByType(SclTypes.END_CASE) ?: return@runWriteCommandAction
            doc.insertString(endCaseNode.startOffset, "\nELSE\n    ; // TODO: handle unexpected state\n")
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
    }
}
