package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclCaseStatement

class SclSafetyCaseElseInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "CASE without ELSE in Safety program"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclCaseStatement) return
                if (!SclSafetyUtils.isInSafetyBlock(element)) return
                if (element.caseElseClause != null) return

                holder.registerProblem(
                    element.firstChild ?: element,
                    "CASE in Safety program must have an ELSE branch that returns to a safe state " +
                    "(Siemens Guideline §4.1.1). Example: ELSE: s_State := 0; // safe state",
                    ProblemHighlightType.WEAK_WARNING
                )
            }
        }
}
