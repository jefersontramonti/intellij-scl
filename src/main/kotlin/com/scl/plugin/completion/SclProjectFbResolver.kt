package com.scl.plugin.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarSection

/**
 * Busca a definição de um FUNCTION_BLOCK no projeto e extrai seus parâmetros
 * de interface (VAR_INPUT, VAR_IN_OUT, VAR_OUTPUT).
 *
 * Uso: ao completar `"FB_StackLight_DB"(`, encontra FB_StackLight e gera
 * scaffold completo com todos os parâmetros.
 *
 * Heurística de nome:
 *   "FB_StackLight_DB" → tenta "FB_StackLight" (remove sufixo _DB)
 *   "MyBlock_DB"       → tenta "MyBlock"
 *   "MyBlock"          → usa "MyBlock" diretamente
 */
object SclProjectFbResolver {

    /**
     * Resolve os parâmetros de interface do FB cujo nome de instância/DB é [rawName].
     * Retorna null se nenhum FB compatível for encontrado.
     */
    fun findParameters(project: Project, rawName: String): List<SclParameter>? {
        val fbName = guessFbName(rawName)
        val scope  = GlobalSearchScope.projectScope(project)
        val files  = FileTypeIndex.getFiles(SclFileType, scope)
        for (vf in files) {
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: continue
            val fb = PsiTreeUtil
                .findChildrenOfType(psiFile, SclFunctionBlockDecl::class.java)
                .firstOrNull { blockNameOf(it).equals(fbName, ignoreCase = true) }
                ?: continue
            return extractParams(fb)
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internos
    // ─────────────────────────────────────────────────────────────────────────

    /** Remove sufixo _DB / DB para inferir o nome do FUNCTION_BLOCK. */
    private fun guessFbName(name: String): String = when {
        name.endsWith("_DB", ignoreCase = true) -> name.dropLast(3)
        name.endsWith("DB",  ignoreCase = true)  -> name.dropLast(2).trimEnd('_')
        else                                      -> name
    }

    /**
     * Extrai o nome do FB — primeiro IDENTIFIER ou QUOTED_IDENTIFIER (sem aspas)
     * após o token FUNCTION_BLOCK.
     */
    private fun blockNameOf(fb: SclFunctionBlockDecl): String? {
        var node = fb.node.firstChildNode
        var seenKeyword = false
        while (node != null) {
            when {
                node.elementType == SclTypes.FUNCTION_BLOCK -> seenKeyword = true
                seenKeyword && node.elementType == SclTypes.IDENTIFIER ->
                    return node.text
                seenKeyword && node.elementType == SclTypes.QUOTED_IDENTIFIER ->
                    return node.text.trim('"')
                // skip whitespace / comments between keyword and name
            }
            node = node.treeNext
        }
        return null
    }

    /** Extrai VAR_INPUT, VAR_IN_OUT e VAR_OUTPUT — ignora VAR/VAR_STATIC/VAR_TEMP. */
    private fun extractParams(fb: SclFunctionBlockDecl): List<SclParameter> {
        val result = mutableListOf<SclParameter>()
        for (section in fb.varSectionList) {
            val dir = sectionDirection(section) ?: continue
            for (decl in section.varDeclList) {
                val name = decl.node.firstChildNode
                    ?.takeIf { it.elementType == SclTypes.IDENTIFIER }?.text ?: continue
                val type = decl.typeRef?.node?.firstChildNode?.text ?: "ANY"
                result += SclParameter(name, type, dir)
            }
        }
        return result
    }

    /** VAR_INPUT → INPUT, VAR_IN_OUT → INOUT, VAR_OUTPUT → OUTPUT, resto → null. */
    private fun sectionDirection(section: SclVarSection): Direction? =
        when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT  -> Direction.INPUT
            SclTypes.VAR_IN_OUT -> Direction.INOUT
            SclTypes.VAR_OUTPUT -> Direction.OUTPUT
            else                -> null
        }
}
