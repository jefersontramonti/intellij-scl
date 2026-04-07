package com.scl.plugin.psi

import com.intellij.psi.tree.IElementType
import com.scl.plugin.language.SclLanguage

/**
 * Tipo de elemento para nos compostos gerados pelo Grammar-Kit (regras nao-terminais).
 * Referenciado no cabecalho do Scl.bnf via elementTypeClass.
 */
class SclElementType(debugName: String) : IElementType(debugName, SclLanguage)
