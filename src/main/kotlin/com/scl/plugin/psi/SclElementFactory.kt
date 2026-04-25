package com.scl.plugin.psi

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil

internal object SclElementFactory {

    /**
     * Cria um ASTNode leaf para substituição de identificador via replaceChild.
     *
     * Usa ASTFactory.leaf() em vez de PsiFileFactory para evitar que o novo nó
     * mantenha containingFile apontando para um arquivo dummy, o que corromperia
     * PsiTreeUtil.getParentOfType ao percorrer a árvore do arquivo real após a
     * substituição.
     *
     * @param project não utilizado; mantido na assinatura para compatibilidade.
     * @param rawName texto exato do token — sem aspas para IDENTIFIER,
     *                com aspas para QUOTED_IDENTIFIER (ex.: `"FB_Name"`).
     */
    @Suppress("UNUSED_PARAMETER")
    fun createNameNode(project: Project, rawName: String): ASTNode {
        val type = if (rawName.startsWith('"')) SclTypes.QUOTED_IDENTIFIER else SclTypes.IDENTIFIER
        val node = ASTFactory.leaf(type, rawName)
        // Marca como "gerado" para que PostprocessReformattingAspect não tente
        // calcular indentação antiga de um token recém-criado (evita assertion SEVERE).
        CodeEditUtil.setNodeGenerated(node, true)
        return node
    }
}
