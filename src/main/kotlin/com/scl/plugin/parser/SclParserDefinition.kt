package com.scl.plugin.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.lexer.SclLexerAdapter
import com.scl.plugin.psi.SclFile
import com.scl.plugin.psi.SclTypes

/**
 * ParserDefinition — registro central da linguagem SCL no IntelliJ Platform.
 *
 * Fase 2: usa o SclParser gerado pelo Grammar-Kit (a partir de Scl.bnf)
 * e o SclTypes gerado como holder de todos os IElementType.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/lexer-and-parser-definition.html
 */
class SclParserDefinition : ParserDefinition {

    companion object {
        /** No raiz da PSI tree — representa o arquivo .scl inteiro */
        @JvmField
        val FILE = IFileElementType(SclLanguage)

        /** Tokens de comentario — usados pelo platform para smart enter, selection, etc. */
        @JvmField
        val COMMENTS: TokenSet = TokenSet.create(
            SclTypes.LINE_COMMENT,
            SclTypes.BLOCK_COMMENT
        )

        @JvmField
        val WHITESPACE: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

        @JvmField
        val STRINGS: TokenSet = TokenSet.create(SclTypes.STRING_LITERAL)
    }

    override fun createLexer(project: Project?): Lexer = SclLexerAdapter()

    /** Fase 2: parser gerado pelo Grammar-Kit a partir de Scl.bnf */
    override fun createParser(project: Project?): PsiParser = SclParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    /**
     * Cria o elemento PSI correto para cada no da AST.
     * SclTypes.Factory e gerado automaticamente pelo Grammar-Kit.
     */
    override fun createElement(node: ASTNode): PsiElement =
        SclTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile =
        SclFile(viewProvider)
}
