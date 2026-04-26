package com.scl.plugin.linter

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.*

/**
 * Linter SCL com consciência de hardware — Fase 4.
 *
 * Implemented as [LocalInspectionTool] (not as Annotator) because it is the
 * recommended API for linters in custom languages on IntelliJ Platform 2024+.
 * Visible in Settings → Editor → Inspections → SCL.
 *
 * Five rules:
 *   1. TIME_TO_DINT  → ERROR   on S7-1200
 *   2. LREAL         → WARNING on S7-1200  (quick fix: → REAL)
 *   3. %Mx conflict  → ERROR   (both HW)
 *   4. Blink bug     → WARNING (both HW)
 *   5. CASE deadlock → WARNING (both HW)
 */
class SclHardwareLinter : LocalInspectionTool() {

    override fun getDisplayName()      = "SCL Hardware Compatibility"
    override fun getGroupDisplayName() = "SCL"
    override fun getShortName()        = "SclHardwareCompatibility"
    override fun isEnabledByDefault()  = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val hw = try {
            SclHardwareTargetService.getInstance(holder.project).target
        } catch (_: Exception) {
            SclHardwareTarget.S7_1200
        }

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {

                // ── Rule 1: TIME_TO_DINT → ERROR on S7-1200 ──────────────────
                if (element.node?.elementType == SclTypes.IDENTIFIER) {
                    checkTimeToDint(element, holder, hw)
                }

                // ── Rule 2: LREAL → WARNING on S7-1200 ───────────────────────
                if (element.node?.elementType == SclTypes.VAR_DECL) {
                    checkLreal(element as SclVarDecl, holder, hw)
                }

                // ── Rule 4: Blink bug ─────────────────────────────────────────
                if (element.node?.elementType == SclTypes.CALL_STMT) {
                    checkBlinkBug(element as SclCallStmt, holder)
                }

                // ── Rule 3: Memory conflict — whole-file analysis ─────────────
                if (element is SclFile) {
                    checkMemoryConflicts(element, holder)
                }

                // ── Rule 5: CASE with no state transition ────────────────────
                if (element.node?.elementType == SclTypes.CASE_STATEMENT) {
                    checkCaseDeadlock(element as SclCaseStatement, holder)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 1 — TIME_TO_DINT not supported on S7-1200
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkTimeToDint(identifier: PsiElement, holder: ProblemsHolder, hw: SclHardwareTarget) {
        if (hw != SclHardwareTarget.S7_1200) return
        val name = identifier.text.uppercase()
        if (name !in TIME_CONVERSIONS) return

        // Is it a function call? Next non-whitespace token must be '('
        var next = identifier.node.treeNext
        while (next != null && next.elementType == com.intellij.psi.TokenType.WHITE_SPACE) {
            next = next.treeNext
        }
        if (next?.elementType != SclTypes.LPAREN) return

        holder.registerProblem(
            identifier,
            "'$name' is not supported on S7-1200. Rewrite using intermediate operations.",
            ProblemHighlightType.GENERIC_ERROR
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regra 2 — LREAL no S7-1200
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkLreal(varDecl: SclVarDecl, holder: ProblemsHolder, hw: SclHardwareTarget) {
        if (hw != SclHardwareTarget.S7_1200) return
        val typeRef = varDecl.typeRef ?: return

        val lrealNode = typeRef.node.findChildByType(SclTypes.TYPE_LREAL)
            ?: typeRef.arrayTypeRef?.node?.findChildByType(SclTypes.TYPE_LREAL)
            ?: return

        holder.registerProblem(
            lrealNode.psi,
            "LREAL (64-bit float) has limited support on S7-1200. Use REAL (32-bit) instead.",
            ProblemHighlightType.WARNING,
            ReplaceLrealWithRealFix()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 3 — Memory access conflict
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkMemoryConflicts(file: SclFile, holder: ProblemsHolder) {
        data class MemToken(val psi: PsiElement, val size: Char, val addr: Int)

        val tokens = mutableListOf<MemToken>()
        PsiTreeUtil.processElements(file) { el ->
            if (el.node?.elementType == SclTypes.MEMORY_ACCESS) {
                MEMORY_PATTERN.find(el.text)?.let { m ->
                    val size = m.groupValues[1].uppercase()[0]
                    val addr = m.groupValues[2].toIntOrNull()
                    if (addr != null) tokens.add(MemToken(el, size, addr))
                }
            }
            true
        }

        val byAddr = tokens.groupBy { it.addr }
        for ((_, group) in byAddr) {
            val sizes = group.map { it.size }.toSet()
            if (sizes.size < 2) continue
            val sizesStr = sizes.sorted().joinToString(", ") { "%M${it}x" }
            for (tok in group) {
                holder.registerProblem(
                    tok.psi,
                    "Memory conflict: address ${tok.addr} accessed with different sizes ($sizesStr).",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regra 4 — Blink bug: IN := NOT timer.Q
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkBlinkBug(call: SclCallStmt, holder: ProblemsHolder) {
        val argList = call.argList ?: return
        for (arg in argList.argumentList) {
            val children = arg.node.getChildren(null)
            val hasIn = children.any { it.elementType == SclTypes.IDENTIFIER && it.text.equals("IN", ignoreCase = true) }
            if (!hasIn) continue
            val hasAssign = children.any { it.elementType == SclTypes.ASSIGN }
            if (!hasAssign) continue

            val exprText = arg.expression?.text?.trim() ?: continue
            if (BLINK_PATTERN.containsMatchIn(exprText)) {
                holder.registerProblem(
                    arg,
                    "Blink bug: 'IN := NOT *.Q' creates a circular dependency with 1 scan-cycle delay. Use an intermediate variable or R_TRIG/F_TRIG.",
                    ProblemHighlightType.WARNING
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 5 — CASE with no state transition
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkCaseDeadlock(caseStmt: SclCaseStatement, holder: ProblemsHolder) {
        val stateVar = caseStmt.expression?.text?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z_#][A-Za-z0-9_]*")) } ?: return

        // Only check deadlock for VAR / VAR_STATIC — VAR_INPUT is never assigned inside a FB.
        if (!isStateMachineVar(stateVar, caseStmt)) return

        // Warn only when NO branch assigns stateVar.
        // If at least one branch transitions, the FSM is not stuck.
        val anyBranchTransitions = caseStmt.caseAltList.any { alt ->
            PsiTreeUtil
                .findChildrenOfType(alt.statementList, SclAssignStmt::class.java)
                .any { assign ->
                    assign.node.findChildByType(SclTypes.IDENTIFIER)?.text
                        ?.equals(stateVar, ignoreCase = true) == true
                }
        }
        if (!anyBranchTransitions) {
            val target = caseStmt.expression ?: return
            holder.registerProblem(
                target,
                "Potential deadlock: no branch assigns '$stateVar'. FSM may get stuck.",
                ProblemHighlightType.WARNING
            )
        }
    }

    private fun isStateMachineVar(varName: String, context: PsiElement): Boolean {
        val cleanName = varName.removePrefix("#")
        var el: PsiElement? = context.parent
        while (el != null) {
            val sections: List<SclVarSection>? = when (el) {
                is SclFunctionBlockDecl -> el.varSectionList
                is SclFunctionDecl      -> el.varSectionList
                is SclOrgBlockDecl      -> el.varSectionList
                else                    -> null
            }
            if (sections != null) {
                for (section in sections) {
                    for (decl in section.varDeclList) {
                        val declName = decl.node.firstChildNode?.text ?: continue
                        if (declName.equals(cleanName, ignoreCase = true)) {
                            val keyword = section.node.firstChildNode?.elementType
                            return keyword == SclTypes.VAR || keyword == SclTypes.VAR_STATIC
                        }
                    }
                }
                return false  // declared in block but not in VAR/VAR_STATIC
            }
            el = el.parent
        }
        return false  // not found — conservative: do not report
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private val TIME_CONVERSIONS = setOf(
            "TIME_TO_DINT", "DINT_TO_TIME",
            "TIME_TO_LINT", "LINT_TO_TIME",
            "TIME_TO_INT",  "INT_TO_TIME",
        )
        private val MEMORY_PATTERN = Regex("%M([BWD])(\\d+)", RegexOption.IGNORE_CASE)
        private val BLINK_PATTERN  = Regex("NOT\\s+[A-Za-z_]\\w*\\.[A-Za-z_]\\w*", RegexOption.IGNORE_CASE)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Fix — LREAL → REAL
// ─────────────────────────────────────────────────────────────────────────────

private class ReplaceLrealWithRealFix : LocalQuickFix {
    override fun getName()       = "Substituir 'LREAL' por 'REAL'"
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val doc = PsiDocumentManager.getInstance(project)
                .getDocument(element.containingFile) ?: return@runWriteCommandAction
            doc.replaceString(element.textRange.startOffset, element.textRange.endOffset, "REAL")
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
    }
}
