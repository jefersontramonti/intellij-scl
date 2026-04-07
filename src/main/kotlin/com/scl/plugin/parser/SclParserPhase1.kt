package com.scl.plugin.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LightPsiParser
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Parser minimo para a Fase 1.
 *
 * Na Fase 1, o highlighting nao precisa de uma PSI tree completa.
 * Este parser consome todos os tokens sem construir nos — suficiente
 * para o highlighting e o code folding basico funcionarem.
 *
 * Na Fase 2, este arquivo sera removido e substituido pelo parser
 * gerado automaticamente pelo Grammar-Kit a partir do arquivo Scl.bnf.
 */
class SclParserPhase1 : PsiParser, LightPsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        parseLight(root, builder)
        return builder.treeBuilt
    }

    override fun parseLight(root: IElementType, builder: PsiBuilder) {
        val marker = builder.mark()
        // Consome todos os tokens sem criar estrutura hierarquica
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
    }
}
