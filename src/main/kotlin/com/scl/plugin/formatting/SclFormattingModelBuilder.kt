package com.scl.plugin.formatting

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.scl.plugin.language.SclLanguage

/**
 * Entry point do formatter SCL — registrado via `lang.formatter` no plugin.xml.
 *
 * Ativado por Ctrl+Alt+L (Reformat Code) em qualquer arquivo .scl.
 * Delega a lógica de indentação para [SclBlock] e de espaçamento para [SclSpacingBuilder].
 */
class SclFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings

        // Garante que o continuation indent seja igual ao indent normal (4 espaços).
        // Sem isto, o formatter aplica CONTINUATION_INDENT_SIZE (padrão 8) nos filhos
        // de ARG_LIST, resultando em 8 espaços em chamadas de FB multi-linha.
        val indentOptions = settings.getCommonSettings(SclLanguage)
            .initIndentOptions()
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.USE_TAB_CHARACTER = false

        // 1. Cria o SpacingBuilder com todas as regras de espaço SCL
        val spacingBuilder = SclSpacingBuilder.create(settings)

        // 2. Cria o bloco raiz que cobre o arquivo inteiro (sem indent próprio)
        val rootBlock = SclBlock(
            node           = formattingContext.node,
            wrap           = Wrap.createWrap(WrapType.NONE, false),
            alignment      = null,
            settings       = settings,
            spacingBuilder = spacingBuilder,
            indent         = Indent.getNoneIndent(),
        )

        // 3. Retorna modelo PSI-based (padrão recomendado pelo IntelliJ Platform)
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            rootBlock,
            settings,
        )
    }
}
