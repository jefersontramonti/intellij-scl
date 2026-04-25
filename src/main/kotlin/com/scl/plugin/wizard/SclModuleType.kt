package com.scl.plugin.wizard

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.scl.plugin.SclIcons
import javax.swing.Icon

class SclModuleType : ModuleType<SclModuleBuilder>(ID) {

    override fun createModuleBuilder(): SclModuleBuilder = SclModuleBuilder()

    override fun getName(): String = "SCL"

    override fun getDescription(): String =
        "Siemens TIA Portal SCL (Structured Control Language) project for S7-1200/S7-1500 PLCs"

    override fun getNodeIcon(isOpened: Boolean): Icon = SclIcons.FILE

    companion object {
        const val ID = "SCL_MODULE_TYPE"

        val INSTANCE: SclModuleType
            get() = ModuleTypeManager.getInstance().findByID(ID) as SclModuleType
    }
}
