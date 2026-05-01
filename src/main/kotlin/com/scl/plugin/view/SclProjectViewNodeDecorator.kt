package com.scl.plugin.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiManager
import com.scl.plugin.SclIcons
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclTopLevelDecl
import javax.swing.Icon

/**
 * Decora arquivos .scl na Project View padrão com o ícone do tipo de bloco.
 *
 * FB_TankControl.scl → ícone FUNCTION_BLOCK (azul)
 * FC_CalcFlow.scl    → ícone FUNCTION       (laranja)
 * OB_Main.scl        → ícone ORG_BLOCK      (verde)
 * UDT_Config.scl     → ícone UDT            (roxo)
 */
class SclProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val vf = node.virtualFile ?: return
        if (vf.fileType != SclFileType) return
        val project = node.project ?: return
        if (project.isDisposed) return

        // Safety files (F_*.scl) get their own icon before PSI lookup
        if (vf.nameWithoutExtension.startsWith("F_")) {
            data.setIcon(SclIcons.SAFETY_BLOCK)
            return
        }

        val icon: Icon? = ReadAction.compute<Icon?, Throwable> {
            if (project.isDisposed) return@compute null
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@compute null
            val first = psiFile.children.filterIsInstance<SclTopLevelDecl>().firstOrNull()
                ?: return@compute null
            when {
                first.functionBlockDecl != null -> SclIcons.FUNCTION_BLOCK
                first.functionDecl != null      -> SclIcons.FUNCTION
                first.orgBlockDecl != null      -> SclIcons.ORG_BLOCK
                first.typeDecl != null          -> SclIcons.UDT
                else                           -> null
            }
        }

        if (icon != null) data.setIcon(icon)
    }
}
