package com.scl.plugin

import com.intellij.icons.AllIcons

/**
 * Ícones usados no plugin SCL (Structure View, completion, hover doc).
 *
 * Usa AllIcons como fallback enquanto ícones SVG customizados não existem.
 * Para ícones próprios: adicionar SVGs em src/main/resources/icons/ e
 * substituir por IconLoader.getIcon("/icons/scl_fb.svg", SclIcons::class.java).
 */
object SclIcons {
    // Ícones de bloco SCL
    val FUNCTION_BLOCK = AllIcons.Nodes.Class          // FB — azul
    val FUNCTION       = AllIcons.Nodes.Function       // FC — laranja
    val ORG_BLOCK      = AllIcons.Nodes.Plugin         // OB — verde
    val UDT            = AllIcons.Nodes.Record         // TYPE/UDT — roxo
    val DATA_BLOCK     = AllIcons.Nodes.DataTables     // DB
}
