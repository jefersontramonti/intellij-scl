package com.scl.plugin.linter.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.scl.plugin.linter.SclCpuSettings
import com.scl.plugin.psi.SclCallStmt
import com.scl.plugin.psi.SclTypeRef
import com.scl.plugin.psi.SclTypes

/**
 * Detecta tipos de dados e instruções não suportados pela CPU alvo.
 *
 * S7-1500 only: LINT, ULINT, LTIME, LTOD, LDT, WCHAR, WSTRING e instruções 64-bit.
 * FW >= 4.1:   VARIANT, SERIALIZE, DESERIALIZE, MOVE_BLK_VARIANT.
 */
class SclUnsupportedTypeInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Unsupported data type or instruction for target CPU"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel()     = HighlightDisplayLevel.ERROR

    companion object {
        private val s7_1500OnlyIdTypes = setOf(
            "LTIME", "LTOD", "LDT", "WCHAR", "WSTRING"
        )
        private val requiresFW41Types = setOf(
            "VARIANT"
        )
        private val s7_1500OnlyInstructions = setOf(
            "MAX_LEN", "JOIN", "SPLIT",
            "TON_LTIME", "TOF_LTIME", "TP_LTIME", "TONR_LTIME",
            "CTU_LINT", "CTD_LINT", "CTUD_LINT",
            "GATHER_BLK", "SCATTER_BLK",
            "RUNTIME", "GET_ERROR"
        )
        private val requiresFW41Instructions = setOf(
            "SERIALIZE", "DESERIALIZE", "MOVE_BLK_VARIANT"
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = SclCpuSettings.getInstance(holder.project)
        if (settings.isS7_1500) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is SclTypeRef  -> checkTypeRef(element, holder, settings)
                    is SclCallStmt -> checkCallStmt(element, holder, settings)
                }
            }
        }
    }

    private fun checkTypeRef(typeRef: SclTypeRef, holder: ProblemsHolder, settings: SclCpuSettings) {
        val firstChild = typeRef.node.firstChildNode ?: return
        val typeText   = firstChild.text.uppercase()

        when (firstChild.elementType) {
            SclTypes.TYPE_LINT  ->
                holder.registerProblem(typeRef,
                    "Type 'LINT' (64-bit integer) is not supported on S7-1200. Use DINT or target S7-1500.",
                    ProblemHighlightType.GENERIC_ERROR)

            SclTypes.TYPE_ULINT ->
                holder.registerProblem(typeRef,
                    "Type 'ULINT' (64-bit unsigned integer) is not supported on S7-1200. Use UDINT or target S7-1500.",
                    ProblemHighlightType.GENERIC_ERROR)

            SclTypes.IDENTIFIER -> when {
                typeText in s7_1500OnlyIdTypes ->
                    holder.registerProblem(typeRef,
                        "Type '$typeText' is not supported on S7-1200. Use S7-1500 or change the data type.",
                        ProblemHighlightType.GENERIC_ERROR)

                typeText in requiresFW41Types && !settings.hasFW4_1 ->
                    holder.registerProblem(typeRef,
                        "'$typeText' requires S7-1200 firmware \u2265 V4.1. Current target: ${settings.firmwareVersion.displayName}.",
                        ProblemHighlightType.GENERIC_ERROR)
            }
        }
    }

    private fun checkCallStmt(callStmt: SclCallStmt, holder: ProblemsHolder, settings: SclCpuSettings) {
        val firstChild = callStmt.node.firstChildNode ?: return
        if (firstChild.elementType != SclTypes.IDENTIFIER) return
        val callText = firstChild.text.uppercase()

        val target = callStmt.firstChild ?: return

        when {
            callText in s7_1500OnlyInstructions ->
                holder.registerProblem(target,
                    "Instruction '$callText' is only available on S7-1500.",
                    ProblemHighlightType.GENERIC_ERROR)

            callText in requiresFW41Instructions && !settings.hasFW4_1 ->
                holder.registerProblem(target,
                    "'$callText' requires S7-1200 firmware \u2265 V4.1. Current target: ${settings.firmwareVersion.displayName}.",
                    ProblemHighlightType.GENERIC_ERROR)
        }
    }
}
