package com.scl.plugin.wizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile

class SclProjectGenerator(
    private val root: VirtualFile,
    private val project: Project,
    private val cpuTarget: SclModuleBuilder.CpuTarget,
    private val template: SclModuleBuilder.ProjectTemplate
) {

    fun generate() {
        ApplicationManager.getApplication().runWriteAction {
            // getOrCreate: evita IOException se a pasta já existe no VFS
            val fbsDir  = root.getOrCreateDir("FBs")
            val fcsDir  = root.getOrCreateDir("FCs")
            val obsDir  = root.getOrCreateDir("OBs")
            val udtsDir = root.getOrCreateDir("UDTs")

            when (template) {
                SclModuleBuilder.ProjectTemplate.EMPTY -> Unit

                SclModuleBuilder.ProjectTemplate.BASIC_FB_OB -> {
                    createFile(fbsDir,  "FB_Main.scl",    SclTemplates.functionBlock("FB_Main"))
                    createFile(fcsDir,  "FC_Utils.scl",   SclTemplates.function("FC_Utils"))
                    createFile(obsDir,  "OB_Main.scl",    SclTemplates.organizationBlock("OB_Main"))
                    createFile(udtsDir, "UDT_Config.scl", SclTemplates.udt("UDT_Config"))
                }

                SclModuleBuilder.ProjectTemplate.FSM -> {
                    createFile(fbsDir, "FB_Main.scl", SclTemplates.functionBlockFsm("FB_Main"))
                    createFile(obsDir, "OB_Main.scl", SclTemplates.organizationBlock("OB_Main"))
                }
            }
        }

        if (template != SclModuleBuilder.ProjectTemplate.EMPTY) {
            openMainFile()
        }
    }

    private fun openMainFile() {
        StartupManager.getInstance(project).runAfterOpened {
            val fbMain = root.findFileByRelativePath("FBs/FB_Main.scl") ?: return@runAfterOpened
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(project).openFile(fbMain, true)
            }
        }
    }

    private fun VirtualFile.getOrCreateDir(name: String): VirtualFile =
        findChild(name) ?: createChildDirectory(this@SclProjectGenerator, name)

    private fun createFile(dir: VirtualFile, name: String, content: String) {
        val file = dir.findChild(name) ?: dir.createChildData(this, name)
        file.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    }
}
