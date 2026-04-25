package com.scl.plugin.mcp

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclVarSection
import com.scl.plugin.psi.SclTypes

open class SclMcpBase {

    protected fun findSclFiles(project: Project): List<VirtualFile> {
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        val result = mutableListOf<VirtualFile>()
        VfsUtil.iterateChildrenRecursively(baseDir, null) { file ->
            if (!file.isDirectory && file.extension?.lowercase() == "scl") result.add(file)
            true
        }
        return result
    }

    protected fun findBlock(project: Project, name: String): SclFunctionBlockDecl? =
        findSclFiles(project)
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, SclFunctionBlockDecl::class.java) }
            .firstOrNull { it.name?.equals(name.trim('"'), ignoreCase = true) == true }

    protected fun extractInterface(fb: SclFunctionBlockDecl): String = buildString {
        appendLine("FUNCTION_BLOCK \"${fb.name}\"")
        PsiTreeUtil.findChildrenOfType(fb, SclVarSection::class.java).forEach { section ->
            val sectionName = when (section.firstChild?.node?.elementType) {
                SclTypes.VAR_INPUT  -> "VAR_INPUT"
                SclTypes.VAR_OUTPUT -> "VAR_OUTPUT"
                SclTypes.VAR_IN_OUT -> "VAR_IN_OUT"
                SclTypes.VAR_TEMP   -> "VAR_TEMP"
                else                -> "VAR"
            }
            appendLine("  $sectionName")
            section.varDeclList.forEach { decl ->
                val varName = decl.name ?: return@forEach
                val varType = decl.typeRef?.text ?: "?"
                appendLine("    $varName : $varType;")
            }
            appendLine("  END_VAR")
        }
    }
}
