package com.scl.plugin.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Entry point do Structure View SCL — registrado via `lang.psiStructureViewFactory`.
 *
 * Ativado por Alt+7 (View → Tool Windows → Structure).
 * Mostra a hierarquia do arquivo SCL:
 *   Arquivo → FB/FC/OB/TYPE → VAR_INPUT/VAR_OUTPUT/… → variáveis
 */
class SclStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return SclStructureViewModel(editor, psiFile)
            }
        }
    }
}
