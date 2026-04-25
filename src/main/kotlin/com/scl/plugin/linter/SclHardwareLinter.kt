package com.scl.plugin.linter

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.*

/**
 * Linter SCL com consciência de hardware — Fase 4.
 *
 * Implementado como [LocalInspectionTool] (não como Annotator) porque é a
 * API recomendada para linters em linguagens customizadas no IntelliJ Platform 2024+.
 * Aparece em Settings → Editor → Inspections → SCL.
 *
 * Cinco regras:
 *   1. TIME_TO_DINT  → ERROR   no S7-1200
 *   2. LREAL         → WARNING no S7-1200  (quick fix: → REAL)
 *   3. Conflito %Mx  → ERROR   (ambos HW)
 *   4. Blink bug     → WARNING (ambos HW)
 *   5. CASE deadlock → WARNING (ambos HW)
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

                // ── Regra 1: TIME_TO_DINT → ERROR no S7-1200 ─────────────────
                if (element.node?.elementType == SclTypes.IDENTIFIER) {
                    checkTimeToDint(element, holder, hw)
                }

                // ── Regra 2: LREAL → WARNING no S7-1200 ──────────────────────
                if (element.node?.elementType == SclTypes.VAR_DECL) {
                    checkLreal(element as SclVarDecl, holder, hw)
                }

                // ── Regra 4: Blink bug ────────────────────────────────────────
                if (element.node?.elementType == SclTypes.CALL_STMT) {
                    checkBlinkBug(element as SclCallStmt, holder)
                }

                // ── Regra 3: Conflito de memória — análise de arquivo inteiro ─
                if (element is SclFile) {
                    checkMemoryConflicts(element, holder)
                }

                // ── Regra 5: CASE sem transição de estado ─────────────────────
                if (element.node?.elementType == SclTypes.CASE_STATEMENT) {
                    checkCaseDeadlock(element as SclCaseStatement, holder)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regra 1 — TIME_TO_DINT não suportado no S7-1200
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkTimeToDint(identifier: PsiElement, holder: ProblemsHolder, hw: SclHardwareTarget) {
        if (hw != SclHardwareTarget.S7_1200) return
        val name = identifier.text.uppercase()
        if (name !in TIME_CONVERSIONS) return

        // É uma chamada de função? Próximo token não-whitespace deve ser '('
        var next = identifier.node.treeNext
        while (next != null && next.elementType == com.intellij.psi.TokenType.WHITE_SPACE) {
            next = next.treeNext
        }
        if (next?.elementType != SclTypes.LPAREN) return

        holder.registerProblem(
            identifier,
            "'$name' não é suportado no S7-1200. Reescreva usando operações intermediárias.",
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
            "LREAL (64-bit float) tem suporte limitado no S7-1200. Use REAL (32-bit).",
            ProblemHighlightType.WARNING,
            ReplaceLrealWithRealFix()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regra 3 — Conflito de acesso de memória
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
                    "Conflito de memória: endereço ${tok.addr} acessado com tamanhos diferentes ($sizesStr).",
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
                    "Blink bug: 'IN := NOT *.Q' causa dependência circular com atraso de 1 ciclo de scan. Use variável intermediária ou R_TRIG/F_TRIG.",
                    ProblemHighlightType.WARNING
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regra 5 — CASE sem transição de estado
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkCaseDeadlock(caseStmt: SclCaseStatement, holder: ProblemsHolder) {
        val stateVar = caseStmt.expression?.text?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z_#][A-Za-z0-9_]*")) } ?: return

        // Só verificar deadlock quando a variável pertence a VAR ou VAR_STATIC.
        // VAR_INPUT nunca deve ser atribuído dentro do FB — não é deadlock.
        if (!isStateMachineVar(stateVar, caseStmt)) return

        for (alt in caseStmt.caseAltList) {
            val hasTransition = PsiTreeUtil
                .findChildrenOfType(alt.statementList, SclAssignStmt::class.java)
                .any { assign ->
                    assign.node.findChildByType(SclTypes.IDENTIFIER)?.text
                        ?.equals(stateVar, ignoreCase = true) == true
                }
            if (!hasTransition) {
                val label = alt.caseLabelList.firstOrNull() ?: continue
                holder.registerProblem(
                    label,
                    "Deadlock potencial: estado '${label.text}' não atribui '$stateVar'. FSM pode ficar presa aqui.",
                    ProblemHighlightType.WARNING
                )
            }
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
                return false  // declarado no bloco mas não em VAR/VAR_STATIC
            }
            el = el.parent
        }
        return false  // não encontrado — conservador: não reportar
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
