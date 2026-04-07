package com.scl.plugin.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.scl.plugin.language.SclFileType
import com.scl.plugin.language.SclLanguage

/**
 * Representa um arquivo .scl como elemento PSI.
 *
 * PsiFileBase e a classe base recomendada para arquivos de linguagens customizadas.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
class SclFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, SclLanguage) {

    override fun getFileType() = SclFileType

    override fun toString() = "SCL File"
}
