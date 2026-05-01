package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclTypes

class SclSafetyGlobalDataInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Global DB access in F-FB"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.node.elementType != SclTypes.QUOTED_IDENTIFIER) return
                if (!SclSafetyUtils.isInSafetyFunctionBlock(element)) return

                // Strip surrounding quotes and check for DB_ prefix
                val rawName = element.text.trim('"')
                if (!rawName.startsWith("DB_", ignoreCase = true)) return

                // Pattern: "DB_xxx".someVar — next non-whitespace sibling must be DOT
                var next: PsiElement? = element.nextSibling
                while (next != null && next.text.isBlank()) next = next.nextSibling
                if (next?.node?.elementType != SclTypes.DOT) return

                holder.registerProblem(
                    element,
                    "Global DB access not allowed in F-FB — pass data as input parameters instead. " +
                    "Only Main Safety OB may access global DBs (Siemens Guideline §3.7).",
                    ProblemHighlightType.ERROR
                )
            }
        }
}
