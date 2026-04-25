package com.scl.plugin.linter.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.scl.plugin.psi.*

/**
 * Detecta variáveis REAL/LREAL usadas como índice de FOR.
 * SCL exige tipo inteiro; índice float causa erro de compilação no TIA Portal.
 */
class SclForIndexTypeInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "FOR loop index must be integer type"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel()     = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is SclForStatement) checkForStatement(element, holder)
            }
        }

    private fun checkForStatement(forStmt: SclForStatement, holder: ProblemsHolder) {
        // Grammar: FOR IDENTIFIER ASSIGN expression TO expression ...
        val indexNode = forStmt.node.findChildByType(SclTypes.IDENTIFIER) ?: return
        val varName   = indexNode.text
        val typeName  = resolveVarTypeName(forStmt, varName)?.uppercase() ?: return

        if (typeName in setOf("REAL", "LREAL")) {
            holder.registerProblem(
                indexNode.psi,
                "FOR loop index '$varName' is type '$typeName'. " +
                "SCL requires integer type (INT, DINT, etc.). " +
                "Floating-point indices cause compile errors in TIA Portal.",
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }

    private fun resolveVarTypeName(context: PsiElement, varName: String): String? {
        var el: PsiElement? = context.parent
        while (el != null && el !is PsiFile) {
            val sections: List<SclVarSection>? = when (el) {
                is SclFunctionBlockDecl -> el.varSectionList
                is SclFunctionDecl      -> el.varSectionList
                is SclOrgBlockDecl      -> el.varSectionList
                else                    -> null
            }
            if (sections != null) {
                for (section in sections) {
                    for (decl in section.varDeclList) {
                        val name = decl.node.firstChildNode
                            ?.takeIf { it.elementType == SclTypes.IDENTIFIER }?.text ?: continue
                        if (name.equals(varName, ignoreCase = true)) {
                            return decl.typeRef?.node?.firstChildNode?.text
                        }
                    }
                }
            }
            el = el.parent
        }
        return null
    }
}
