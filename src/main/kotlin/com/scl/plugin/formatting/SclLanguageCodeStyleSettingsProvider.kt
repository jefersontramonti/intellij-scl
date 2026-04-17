package com.scl.plugin.formatting

import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.scl.plugin.language.SclLanguage

/**
 * Registra [SclCodeStyleSettings] no framework de Code Style do IntelliJ.
 *
 * Sem este provider, `CodeStyleSettings.getCustomSettings(SclCodeStyleSettings::class.java)`
 * lança RuntimeException e o Ctrl+Alt+L aborta silenciosamente.
 */
class SclLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = SclLanguage

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
        SclCodeStyleSettings(settings)

    /** Exemplo de código exibido em Settings → Editor → Code Style → SCL. */
    override fun getCodeSample(settingsType: SettingsType): String = """
        FUNCTION_BLOCK FB_Example
        VAR_INPUT
            i_bEnable   : BOOL;
            i_rSetpoint : REAL := 0.0;
        END_VAR
        VAR
            s_iCounter  : INT;
        END_VAR
        BEGIN
            IF i_bEnable THEN
                s_iCounter := s_iCounter + 1;
            END_IF;
        END_FUNCTION_BLOCK
    """.trimIndent()
}
