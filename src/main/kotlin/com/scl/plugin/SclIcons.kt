package com.scl.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

object SclIcons {
    val FILE           = IconLoader.getIcon("/icons/scl.svg", SclIcons::class.java)
    val FUNCTION_BLOCK = AllIcons.Nodes.Class
    val FUNCTION       = AllIcons.Nodes.Function
    val ORG_BLOCK      = AllIcons.Nodes.Plugin
    val UDT            = AllIcons.Nodes.Record
    val DATA_BLOCK     = AllIcons.Nodes.DataTables
    val SAFETY_BLOCK   = AllIcons.Nodes.ErrorIntroduction
}
