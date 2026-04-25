package com.scl.plugin.reference

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclTypes

/**
 * Shift+F6 — Rename refactoring para elementos SCL.
 *
 * Usa abordagem offset-based (mesma do SclGotoDeclarationHandler) porque o
 * PsiReferenceContributor não chama providers para LeafPsiElement de linguagens
 * Grammar-Kit em IntelliJ 2026.1 — então element.getReferences() retorna vazio
 * em usos, e o framework nativo de rename não encontra a declaração.
 *
 * Fluxo após encontrar a declaração:
 *   PsiElementRenameHandler.invoke() → diálogo → declaration.setName() +
 *   SclUsagesSearcher → SclReference.handleElementRename() para cada uso.
 */
class SclRenameHandler : RenameHandler {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        if (file.fileType != SclFileType) return false
        return resolveAtCaret(editor, file) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
        dataContext: DataContext,
    ) {
        editor ?: return
        file ?: return
        val target = resolveAtCaret(editor, file) ?: return
        PsiElementRenameHandler.invoke(target, project, target, editor)
    }

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext,
    ) {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
        invoke(project, editor, file, dataContext)
    }

    /**
     * Retorna o SclNamedElement que deve ser renomeado — declaração ou alvo
     * resolvido de um uso — ou null se o cursor não estiver sobre um identificador SCL.
     */
    private fun resolveAtCaret(editor: Editor, file: PsiFile): SclNamedElement? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null

        val nodeType = element.node?.elementType ?: return null
        if (nodeType != SclTypes.IDENTIFIER && nodeType != SclTypes.QUOTED_IDENTIFIER) return null

        // Cursor na declaração: pai direto já é o elemento nomeado
        val parent = element.parent
        if (parent is SclNamedElement && parent.nameIdentifier === element) {
            return parent
        }

        // Cursor num uso: resolve a referência para encontrar a declaração
        val rangeInElement = if (nodeType == SclTypes.QUOTED_IDENTIFIER) {
            val text = element.text
            if (text.length < 2) return null
            TextRange(1, text.length - 1)
        } else {
            TextRange(0, element.textLength)
        }

        return SclReference(element, rangeInElement).resolve() as? SclNamedElement
    }
}
