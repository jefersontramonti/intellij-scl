package com.scl.plugin.formatting

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/**
 * Configurações de estilo de código SCL personalizáveis pelo usuário.
 * Acessíveis em Settings → Editor → Code Style → SCL (quando o provider for registrado).
 */
class SclCodeStyleSettings(container: CodeStyleSettings)
    : CustomCodeStyleSettings("SclCodeStyleSettings", container) {

    /** Alinhar ":" de todas as declarações na mesma seção VAR.
     *
     *  Exemplo (ALIGN_VARIABLE_COLONS = true):
     *    i_bStart    : BOOL
     *    i_rSetpoint : REAL := 0.0
     *
     *  Exemplo (false):
     *    i_bStart : BOOL
     *    i_rSetpoint : REAL := 0.0
     */
    @JvmField
    var ALIGN_VARIABLE_COLONS: Boolean = true

    /** Preservar até N linhas em branco consecutivas no código. */
    @JvmField
    var KEEP_BLANK_LINES_IN_CODE: Int = 1

    /** Espaço dentro dos parênteses de chamada de FB.
     *  true  → TON( IN := x, PT := T#5S )
     *  false → TON(IN := x, PT := T#5S)
     */
    @JvmField
    var SPACE_WITHIN_FB_CALL_PARENS: Boolean = false
}
