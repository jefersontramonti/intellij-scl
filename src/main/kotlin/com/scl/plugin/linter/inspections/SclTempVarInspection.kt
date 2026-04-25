package com.scl.plugin.linter.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.linter.SclCpuSettings
import com.scl.plugin.psi.*

/**
 * Detecta variáveis VAR_TEMP lidas antes de serem inicializadas.
 *
 * Severidade:
 *   FW < V4.0 → ERROR (L-stack contém lixo, comportamento indefinido)
 *   FW ≥ V4.0 → WARNING (inicializado em blocos otimizados, mas boa prática)
 */
class SclTempVarInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "VAR_TEMP variable read before assignment"
    override fun getGroupDisplayName() = "SCL"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is SclVarSection && isTempSection(element)) {
                    checkTempSection(element, holder)
                }
            }
        }

    private fun isTempSection(section: SclVarSection): Boolean =
        section.node.firstChildNode?.elementType == SclTypes.VAR_TEMP

    private fun checkTempSection(section: SclVarSection, holder: ProblemsHolder) {
        val settings = SclCpuSettings.getInstance(holder.project)
        val body     = findBlockBody(section) ?: return

        for (decl in section.varDeclList) {
            val varName = decl.node.firstChildNode
                ?.takeIf { it.elementType == SclTypes.IDENTIFIER }?.text ?: continue

            val (firstRead, firstWrite) = findFirstReadWrite(body, varName)
            if (firstRead == null) continue

            val readBefore = firstWrite == null || firstRead.textOffset < firstWrite.textOffset
            if (!readBefore) continue

            val level   = if (settings.tempInitialized) ProblemHighlightType.WEAK_WARNING
                          else ProblemHighlightType.GENERIC_ERROR
            val message = if (settings.tempInitialized)
                "VAR_TEMP '$varName' may not be initialized before first use. Initialize for clarity."
            else
                "VAR_TEMP '$varName' is read before assignment. " +
                "On S7-1200 FW < V4.0, TEMP variables contain undefined values (L-stack garbage)."

            holder.registerProblem(decl, message, level)
        }
    }

    private fun findBlockBody(section: SclVarSection): SclStatementList? {
        var el: PsiElement? = section.parent
        while (el != null) {
            val body: SclStatementList? = when (el) {
                is SclFunctionBlockDecl -> el.statementList
                is SclFunctionDecl      -> el.statementList
                is SclOrgBlockDecl      -> el.statementList
                else                    -> null
            }
            if (body != null) return body
            el = el.parent
        }
        return null
    }

    private fun findFirstReadWrite(body: SclStatementList, varName: String): Pair<PsiElement?, PsiElement?> {
        var firstRead:  PsiElement? = null
        var firstWrite: PsiElement? = null

        // Collect first write: lvalue of any SclAssignStmt matching varName
        for (assign in PsiTreeUtil.findChildrenOfType(body, SclAssignStmt::class.java)) {
            val lval = assignLvalue(assign)
            if (lval.equals(varName, ignoreCase = true) ||
                lval.equals("#$varName", ignoreCase = true)) {
                if (firstWrite == null || assign.textOffset < firstWrite.textOffset) {
                    firstWrite = assign
                }
            }
        }

        // Collect first read: IDENTIFIER / LOCAL_VAR_ID tokens NOT in lvalue position
        PsiTreeUtil.processElements(body) { el ->
            val elType = el.node?.elementType
            if (elType == SclTypes.IDENTIFIER || elType == SclTypes.LOCAL_VAR_ID) {
                val elName = el.text.removePrefix("#")
                if (elName.equals(varName, ignoreCase = true) && !isAssignLvalue(el)) {
                    if (firstRead == null || el.textOffset < firstRead!!.textOffset) {
                        firstRead = el
                    }
                }
            }
            true
        }

        return Pair(firstRead, firstWrite)
    }

    private fun assignLvalue(assign: SclAssignStmt): String {
        val assignOp = assign.node.findChildByType(SclTypes.ASSIGN) ?: return ""
        return assign.text.substring(0, assignOp.startOffset - assign.textOffset).trim()
    }

    // A token is an lvalue if it's a direct child of SclAssignStmt before the ASSIGN operator.
    // lvalue is a private grammar rule — its tokens are direct children of assignStmt.
    private fun isAssignLvalue(element: PsiElement): Boolean {
        val parent = element.parent as? SclAssignStmt ?: return false
        val assignOp = parent.node.findChildByType(SclTypes.ASSIGN) ?: return false
        return element.textOffset < assignOp.startOffset
    }
}
