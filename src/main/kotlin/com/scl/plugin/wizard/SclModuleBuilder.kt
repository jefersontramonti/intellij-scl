package com.scl.plugin.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class SclModuleBuilder : ModuleBuilder() {

    var cpuTarget: CpuTarget    = CpuTarget.S7_1200
    var template:  ProjectTemplate = ProjectTemplate.BASIC_FB_OB

    enum class CpuTarget { S7_1200, S7_1500 }

    enum class ProjectTemplate {
        EMPTY,
        BASIC_FB_OB,
        FSM
    }

    override fun getModuleType(): ModuleType<*> = SclModuleType.INSTANCE

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = createAndGetContentEntry()
        modifiableRootModel.addContentEntry(root)

        SclProjectGenerator(
            root      = root,
            project   = modifiableRootModel.module.project,
            cpuTarget = cpuTarget,
            template  = template
        ).generate()
    }

    override fun getCustomOptionsStep(
        context: WizardContext,
        parentDisposable: Disposable
    ): ModuleWizardStep = SclProjectWizardStep(this)

    private fun createAndGetContentEntry() =
        contentEntryPath
            ?.also { VfsUtil.createDirectories(it) }
            ?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
            ?: error("Cannot create/find content entry: $contentEntryPath")
}
