package com.scl.plugin.linter.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.scl.plugin.psi.SclTypes

/**
 * Detecta uso de endereçamento absoluto (%M, %I, %Q) e sugere uso de tags simbólicas.
 */
class SclAbsoluteAddressInspection : LocalInspectionTool() {

    override fun getDisplayName()      = "Absolute memory address used"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel()     = HighlightDisplayLevel.WARNING

    // LocalInspectionTool JA itera todos os elementos do arquivo e chama
    // visitElement uma vez por elemento. Usar PsiRecursiveElementVisitor aqui
    // causa visita DUPLA: a plataforma percorre + o visitor recursivo tambem
    // percorre filhos, fazendo cada folha ser visitada N vezes (N = profundidade).
    // Isso gerava ate 9 warnings para o mesmo %I0.3.
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.node?.elementType == SclTypes.MEMORY_ACCESS) {
                    holder.registerProblem(
                        element,
                        "Absolute address '${element.text}' found. " +
                        "Use symbolic PLC tags for maintainability. " +
                        "Absolute addressing breaks with hardware changes.",
                        ProblemHighlightType.WEAK_WARNING,
                        SclConvertToSymbolicFix(element)
                    )
                }
            }
        }
}

private class SclConvertToSymbolicFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText()       = "How to convert to symbolic tag"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        HintManager.getInstance().showInformationHint(
            editor,
            "Create a symbolic tag in the PLC tag table and replace " +
            "'${startElement.text}' with the tag name."
        )
    }
}
