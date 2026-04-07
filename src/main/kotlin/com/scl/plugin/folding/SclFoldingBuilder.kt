package com.scl.plugin.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.SclTypes

/**
 * Folding de codigo SCL — recolhe blocos estruturais na gutter.
 *
 * Estende FoldingBuilderEx e implementa DumbAware (exigido pela doc oficial
 * para que o folding funcione mesmo com indices nao prontos).
 *
 * Regioes recolhidas:
 *   FUNCTION_BLOCK...END_FUNCTION_BLOCK
 *   FUNCTION...END_FUNCTION
 *   ORGANIZATION_BLOCK...END_ORGANIZATION_BLOCK
 *   DATA_BLOCK...END_DATA_BLOCK
 *   VAR / VAR_INPUT / VAR_OUTPUT / ...END_VAR
 *   IF...END_IF
 *   CASE...END_CASE
 *   FOR...END_FOR
 *   WHILE...END_WHILE
 *   REPEAT...END_REPEAT
 *   Comentarios de bloco (* ... *) com mais de 2 linhas
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/folding-builder.html
 */
class SclFoldingBuilder : FoldingBuilderEx(), DumbAware {

    // ── Tipos de nos PSI que geram regioes de folding ────────────────────────
    companion object {
        private val BLOCK_TYPES = setOf(
            SclTypes.FUNCTION_BLOCK_DECL,
            SclTypes.FUNCTION_DECL,
            SclTypes.ORG_BLOCK_DECL,
            SclTypes.DATA_BLOCK_DECL,
            SclTypes.TYPE_DECL,
            SclTypes.STRUCT_DECL,
            SclTypes.VAR_SECTION,
            SclTypes.CONST_SECTION,    // CONST ... END_CONST
            SclTypes.IF_STATEMENT,
            SclTypes.CASE_STATEMENT,
            SclTypes.FOR_STATEMENT,
            SclTypes.WHILE_STATEMENT,
            SclTypes.REPEAT_STATEMENT,
            SclTypes.REGION_STMT       // REGION ... END_REGION (TIA Portal)
        )
    }

    /**
     * Coleta todas as regioes de folding no arquivo.
     * Visitamos a arvore PSI em profundidade e criamos FoldingDescriptor
     * para cada no multilinhas de interesse.
     */
    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        PsiTreeUtil.processElements(root) { element ->
            val node = element.node ?: return@processElements true
            val type = node.elementType

            when {
                // Nos estruturais gerados pelo Grammar-Kit
                type in BLOCK_TYPES -> {
                    val range = element.textRange
                    if (range.length > 0 &&
                        document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset)
                    ) {
                        descriptors.add(FoldingDescriptor(node, range))
                    }
                }

                // Comentarios de bloco (* ... *) com mais de 2 linhas
                type == SclTypes.BLOCK_COMMENT -> {
                    val range = element.textRange
                    val startLine = document.getLineNumber(range.startOffset)
                    val endLine   = document.getLineNumber(range.endOffset)
                    if (endLine - startLine >= 2) {
                        descriptors.add(FoldingDescriptor(node, range))
                    }
                }
            }
            true
        }

        return descriptors.toTypedArray()
    }

    /**
     * Texto exibido quando a regiao esta recolhida.
     * Exibe a primeira linha + " ..." para blocos e "(* ... *)" para comentarios.
     */
    override fun getPlaceholderText(node: ASTNode): String {
        val type = node.elementType
        if (type == SclTypes.BLOCK_COMMENT) return "(* ... *)"

        // Primeira linha do bloco (ex: "FUNCTION_BLOCK MyFB") + " ..."
        val firstLine = node.text.lineSequence().first().trim()
        return if (firstLine.length > 80) "${firstLine.take(80)} ..."
        else "$firstLine ..."
    }

    /**
     * Blocos nao sao recolhidos por padrao — o usuario decide.
     * Exceto comentarios de bloco muito longos (> 10 linhas).
     */
    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return node.elementType == SclTypes.BLOCK_COMMENT &&
               node.text.count { it == '\n' } > 10
    }
}
