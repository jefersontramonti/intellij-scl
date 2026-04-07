package com.scl.plugin.psi

import com.intellij.psi.tree.IElementType
import com.scl.plugin.language.SclLanguage

/**
 * Tipo de elemento para tokens (terminais) gerados pelo Grammar-Kit.
 * Referenciado no cabecalho do Scl.bnf via tokenTypeClass.
 * O Grammar-Kit usa esta classe para criar IElementType de cada token.
 */
class SclTokenType(debugName: String) : IElementType(debugName, SclLanguage) {
    override fun toString(): String = "SCL:" + super.toString()
}
