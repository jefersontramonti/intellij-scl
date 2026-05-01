package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclTypes

class SclSafetyValueStatusInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "F-I/O accessed without value status check"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.node.elementType != SclTypes.QUOTED_IDENTIFIER) return
                if (!SclSafetyUtils.isInSafetyBlock(element)) return

                // Only flag F-I/O channels: DI_ or DO_ prefix (Siemens naming convention)
                val rawName = element.text.trim('"')
                val isIO = rawName.startsWith("DI_", ignoreCase = true) ||
                           rawName.startsWith("DO_", ignoreCase = true)
                if (!isIO) return

                // Must be followed by DOT (member access)
                var next: PsiElement? = element.nextSibling
                while (next != null && next.text.isBlank()) next = next.nextSibling
                if (next?.node?.elementType != SclTypes.DOT) return

                // Get the field name after DOT
                var field: PsiElement? = next.nextSibling
                while (field != null && field.text.isBlank()) field = field.nextSibling
                if (field?.node?.elementType != SclTypes.IDENTIFIER) return

                // If field already ends with _VS the value status is being checked — OK
                if (field.text.endsWith("_VS", ignoreCase = true)) return

                holder.registerProblem(
                    element,
                    "F-I/O channel '${element.text}.${field.text}' accessed without value status check. " +
                    "Use '${field.text}_VS' to verify signal validity (Siemens Guideline §3.4). " +
                    "Example: ${element.text}.${field.text} AND ${element.text}.${field.text}_VS",
                    ProblemHighlightType.WEAK_WARNING
                )
            }
        }
}
