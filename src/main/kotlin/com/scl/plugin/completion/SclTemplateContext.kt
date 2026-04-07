package com.scl.plugin.completion

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.scl.plugin.language.SclLanguage

/**
 * Contexto de live templates para SCL.
 *
 * Registrado em plugin.xml como <liveTemplateContext contextId="SCL"/>.
 * O SCL.xml referencia esse contexto com <option name="SCL" value="true"/>.
 *
 * Ativa os templates apenas em arquivos .scl (por language ID),
 * evitando que aparecam em outros contextos.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/live-templates.html
 */
class SclTemplateContext : TemplateContextType("SCL") {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        templateActionContext.file.language.isKindOf(SclLanguage)
}
