package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclContinueStmt
import com.scl.plugin.psi.SclExitStmt

class SclSafetyJumpInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Jump statement in Safety program"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (!SclSafetyUtils.isInSafetyBlock(element)) return
                val keyword = when (element) {
                    is SclExitStmt     -> "EXIT"
                    is SclContinueStmt -> "CONTINUE"
                    else -> return
                }
                holder.registerProblem(
                    element,
                    "$keyword: jumps should be minimized in Safety programs — " +
                    "prefer structured loops without early exits (Siemens Guideline §4.1.1).",
                    ProblemHighlightType.WEAK_WARNING
                )
            }
        }
}
