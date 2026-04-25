package com.scl.plugin.findUsages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.lexer.SclLexerAdapter
import com.scl.plugin.psi.SclDataBlockDecl
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclStructField
import com.scl.plugin.psi.SclTypeDef
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import com.scl.plugin.psi.SclVarSection

/**
 * Habilita Alt+F7 (Find Usages) em elementos SCL nomeados.
 *
 * Indexa IDENTIFIER e QUOTED_IDENTIFIER como "palavras pesquisáveis" (para
 * que o IntelliJ saiba em quais arquivos buscar antes de resolver referências)
 * e classifica cada tipo de declaração no popup de resultados.
 */
class SclFindUsagesProvider : FindUsagesProvider {

    /**
     * WordsScanner — varre o arquivo com o lexer e reporta palavras por
     * categoria (identifier / comment / literal). Essencial: sem IDENTIFIER
     * no tokenSet, o índice de stubs não encontra usos.
     */
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        SclLexerAdapter(),
        TokenSet.create(SclTypes.IDENTIFIER, SclTypes.QUOTED_IDENTIFIER),
        TokenSet.create(SclTypes.LINE_COMMENT, SclTypes.BLOCK_COMMENT),
        TokenSet.create(SclTypes.STRING_LITERAL),
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is SclNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    /**
     * Rótulo mostrado em "Find Usages of <name> (<type>)".
     * Para variáveis, inspeciona a seção VAR pai para distinguir
     * input / output / in_out / static / temp / constant.
     */
    override fun getType(element: PsiElement): String = when (element) {
        is SclVarDecl           -> varKindLabel(element)
        is SclStructField       -> "struct field"
        is SclTypeDef           -> "user data type"
        is SclFunctionBlockDecl -> "function block"
        is SclFunctionDecl      -> "function"
        is SclOrgBlockDecl      -> "organization block"
        is SclDataBlockDecl     -> "data block"
        else                    -> "element"
    }

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? SclNamedElement)?.name ?: element.text.take(40)

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        getDescriptiveName(element)

    /**
     * Determina se um [SclVarDecl] está em VAR_INPUT / VAR_OUTPUT / etc.
     * pela inspeção do primeiro token da [SclVarSection] pai.
     */
    private fun varKindLabel(decl: SclVarDecl): String {
        val section = PsiTreeUtil.getParentOfType(decl, SclVarSection::class.java)
            ?: return "variable"
        val kw = section.node.firstChildNode?.elementType
        return when (kw) {
            SclTypes.VAR_INPUT    -> "input variable"
            SclTypes.VAR_OUTPUT   -> "output variable"
            SclTypes.VAR_IN_OUT   -> "in/out variable"
            SclTypes.VAR_STATIC   -> "static variable"
            SclTypes.VAR_TEMP     -> "temp variable"
            SclTypes.VAR_CONSTANT -> "constant"
            SclTypes.VAR          -> "variable"
            else                  -> "variable"
        }
    }
}
