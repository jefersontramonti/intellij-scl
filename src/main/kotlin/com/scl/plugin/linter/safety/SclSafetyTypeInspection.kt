package com.scl.plugin.linter.safety

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.scl.plugin.psi.SclTypeRef
import com.scl.plugin.psi.SclTypes

class SclSafetyTypeInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Unsupported type in Safety program"
    override fun getGroupDisplayName() = "SCL Safety"
    override fun getDefaultLevel()     = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclTypeRef) return
                if (!SclSafetyUtils.isInSafetyBlock(element)) return

                val child = element.firstChild ?: return
                val (typeName, reason, highlight) = resolveType(child) ?: return

                holder.registerProblem(element, "$typeName: $reason", highlight)
            }
        }

    // Triple: (typeName, message, highlightType)
    private fun resolveType(child: PsiElement): Triple<String, String, ProblemHighlightType>? {
        val et = child.node.elementType
        val id = if (et == SclTypes.IDENTIFIER) child.text.uppercase() else null
        return when {
            // ── Errors: 64-bit types fully prohibited (IEC 61508 SIL3) ──────────
            et == SclTypes.TYPE_LINT  -> Triple("LINT",  "64-bit integer not supported in Safety programs. Use INT or DINT.", ProblemHighlightType.ERROR)
            et == SclTypes.TYPE_ULINT -> Triple("ULINT", "64-bit integer not supported in Safety programs. Use UINT or UDINT.", ProblemHighlightType.ERROR)
            et == SclTypes.TYPE_LWORD -> Triple("LWORD", "64-bit word not supported in Safety programs. Use WORD or DWORD.", ProblemHighlightType.ERROR)
            // LTIME / LTOD / LDT / WSTRING are not grammar keywords — matched as IDENTIFIER
            id == "LTIME"   -> Triple("LTIME",   "64-bit time type not supported in Safety programs. Use TIME.", ProblemHighlightType.ERROR)
            id == "LTOD"    -> Triple("LTOD",    "64-bit time-of-day not supported in Safety programs. Use TOD.", ProblemHighlightType.ERROR)
            id == "LDT"     -> Triple("LDT",     "64-bit date-time not supported in Safety programs. Use DTL.", ProblemHighlightType.ERROR)
            id == "WSTRING" -> Triple("WSTRING", "WSTRING not supported in Safety programs (SIL3). Use STRING.", ProblemHighlightType.ERROR)
            id == "WCHAR"   -> Triple("WCHAR",   "WCHAR not supported in Safety programs (SIL3). Use CHAR.", ProblemHighlightType.ERROR)
            // ── Warning: LREAL is a grammar keyword (TYPE_LREAL) ─────────────────
            // 64-bit on some CPUs; discouraged in Safety — verify firmware support
            et == SclTypes.TYPE_LREAL -> Triple("LREAL", "LReal (64-bit) has limited support in Safety programs. Verify compatibility with your F-CPU firmware. Use Real (32-bit) for maximum compatibility.", ProblemHighlightType.WEAK_WARNING)
            else -> null
        }
    }
}
