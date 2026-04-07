package com.scl.plugin.highlighting

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Factory registrada no plugin.xml como:
 *   <lang.syntaxHighlighterFactory language="SCL" implementationClass="..."/>
 *
 * A plataforma chama getSyntaxHighlighter() para cada arquivo SCL aberto.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/syntax-highlighter-and-color-settings-page.html
 */
class SclSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?
    ): SyntaxHighlighter = SclSyntaxHighlighter()
}
