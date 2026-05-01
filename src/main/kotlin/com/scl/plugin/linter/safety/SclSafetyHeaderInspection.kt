package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl

class SclSafetyHeaderInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Missing Safety block header"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val name = when (element) {
                    is SclFunctionBlockDecl -> element.name
                    is SclFunctionDecl      -> element.name
                    else -> return
                }
                if (!SclSafetyUtils.isSafetyBlock(name)) return

                val hasRequiredHeader = PsiTreeUtil
                    .findChildrenOfType(element, PsiComment::class.java)
                    .any { comment ->
                        val text = comment.text
                        // Required keywords from Siemens Guideline §3.1.6
                        text.contains("SIL") ||
                        text.contains("Safety Level", ignoreCase = true) ||
                        text.contains("F-Signature", ignoreCase = true) ||
                        text.contains("Safety block", ignoreCase = true)
                    }

                if (!hasRequiredHeader) {
                    holder.registerProblem(
                        element.firstChild ?: element,
                        "Safety block '$name' is missing the required header " +
                        "(SIL level, F-Signature, author — Siemens Guideline §3.1.6). " +
                        "Use the 'fsafety' live template to generate it.",
                        ProblemHighlightType.WEAK_WARNING
                    )
                }
            }
        }
}
