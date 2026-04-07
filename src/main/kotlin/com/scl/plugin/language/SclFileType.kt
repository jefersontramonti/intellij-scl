package com.scl.plugin.language

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Registra o tipo de arquivo .scl no IntelliJ.
 *
 * IMPORTANTE: o campo deve se chamar INSTANCE (maiusculo) porque o plugin.xml
 * referencia via fieldName="INSTANCE" — exigido pela plataforma.
 *
 * Documentacao oficial:
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 *
 * Trecho confirmado do tutorial:
 *   <fileType name="Simple File"
 *             implementationClass="..."
 *             fieldName="INSTANCE"    <-- obrigatorio
 *             language="Simple"
 *             extensions="simple"/>
 */
object SclFileType : LanguageFileType(SclLanguage) {

    // Kotlin object ja gera INSTANCE automaticamente — plugin.xml usa fieldName="INSTANCE"
    // Nao declarar @JvmField val INSTANCE aqui ou havera clash de assinatura JVM

    override fun getName()        = "SCL File"
    override fun getDescription() = "Siemens SCL / Structured Control Language (TIA Portal)"
    override fun getDefaultExtension() = "scl"

    override fun getIcon(): Icon =
        // Usando icone padrao enquanto nao temos um icone SCL customizado
        // Para criar icone proprio: adicionar scl.svg em src/main/resources/icons/
        // e usar: IconLoader.getIcon("/icons/scl.svg", SclFileType::class.java)
        IconLoader.getIcon("/icons/scl.svg", SclFileType::class.java)
}
