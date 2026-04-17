package com.scl.plugin.formatting

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.scl.plugin.language.SclLanguage

/**
 * Registra [SclCodeStyleSettings] no [com.intellij.psi.codeStyle.CustomCodeStyleSettingsManager].
 *
 * Este EP (`codeStyleSettingsProvider`) é o que o `CustomCodeStyleSettingsManager` escaneia
 * para descobrir e instanciar `CustomCodeStyleSettings`. Sem ele, `getCustomSettings()` lança
 * RuntimeException mesmo que o `langCodeStyleSettingsProvider` esteja presente.
 */
class SclCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
        SclCodeStyleSettings(settings)

    override fun getLanguage(): Language = SclLanguage

    override fun getConfigurableDisplayName(): String = "SCL"

    override fun createConfigurable(
        settings: CodeStyleSettings,
        modelSettings: CodeStyleSettings,
    ): CodeStyleConfigurable =
        object : CodeStyleAbstractConfigurable(settings, modelSettings, "SCL") {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                object : TabbedLanguageCodeStylePanel(SclLanguage, currentSettings, settings) {}
        }
}
