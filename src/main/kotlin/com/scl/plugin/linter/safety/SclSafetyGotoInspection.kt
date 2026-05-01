package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclGotoStmt

class SclSafetyGotoInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "GOTO in Safety program"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclGotoStmt) return
                if (!SclSafetyUtils.isInSafetyBlock(element)) return
                holder.registerProblem(
                    element,
                    "GOTO is prohibited in Safety programs (SIL3 / PLe — Siemens Guideline §4.1.1). " +
                    "Replace with a state machine using CASE.",
                    ProblemHighlightType.ERROR
                )
            }
        }
}
