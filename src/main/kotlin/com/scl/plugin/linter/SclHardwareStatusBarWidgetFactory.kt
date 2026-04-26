package com.scl.plugin.linter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.awt.event.MouseEvent
import com.intellij.util.Consumer

// ─────────────────────────────────────────────────────────────────────────────
// Factory — registrada no plugin.xml como <statusBarWidgetFactory>
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cria o widget de hardware target na status bar do IntelliJ.
 *
 * Exibe "SCL: S7-1200" ou "SCL: S7-1500".
 * Clique abre popup para trocar o target; a troca reinicia o daemon
 * de anotações para que as regras do [SclAnnotator] sejam reaplicadas.
 */
class SclHardwareStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = SclHardwareStatusBarWidget.ID

    override fun getDisplayName(): String = "SCL Hardware Target"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        SclHardwareStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget as Disposable)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget
// ─────────────────────────────────────────────────────────────────────────────

class SclHardwareStatusBarWidget(
    private val project: Project
) : StatusBarWidget, StatusBarWidget.TextPresentation, Disposable {

    companion object {
        const val ID = "SclHardwareTarget"
    }

    private var statusBar: StatusBar? = null

    // ── StatusBarWidget ───────────────────────────────────────────────────────

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    // ── TextPresentation ──────────────────────────────────────────────────────

    override fun getText(): String {
        val svc = SclHardwareTargetService.getInstance(project)
        return "SCL: ${svc.target.displayName}"
    }

    override fun getTooltipText(): String =
        "SCL Hardware Target — clique para trocar (S7-1200 / S7-1500)"

    override fun getAlignment(): Float = 0f

    /**
     * Clique → popup com os dois targets disponíveis.
     * Selecionar um target persiste a escolha e reinicia as anotações.
     */
    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        val svc = SclHardwareTargetService.getInstance(project)

        val actions = SclHardwareTarget.values().map { target ->
            object : AnAction(target.displayName) {
                override fun actionPerformed(e: AnActionEvent) {
                    svc.target = target
                    // Sincroniza SclCpuSettings para que SclUnsupportedTypeInspection veja o mesmo target
                    val cpuSettings = SclCpuSettings.getInstance(project)
                    when (target) {
                        SclHardwareTarget.S7_1500 -> {
                            cpuSettings.setCpuFamily(CpuFamily.S7_1500)
                            cpuSettings.setFirmwareVersion(FirmwareVersion.S7_1500_ANY)
                        }
                        SclHardwareTarget.S7_1200 -> {
                            cpuSettings.setCpuFamily(CpuFamily.S7_1200)
                            if (cpuSettings.firmwareVersion == FirmwareVersion.S7_1500_ANY)
                                cpuSettings.setFirmwareVersion(FirmwareVersion.S7_1200_V4_0)
                        }
                    }
                    statusBar?.updateWidget(ID)
                    // Reinicia as anotações em todos os arquivos abertos do projeto
                    @Suppress("DEPRECATION")
                    DaemonCodeAnalyzer.getInstance(project).restart()
                }
            }
        }

        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            "Hardware Target",
            DefaultActionGroup(actions),
            DataManager.getInstance().getDataContext(event.component),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )

        // Exibe o popup acima do widget (offset negativo em Y)
        val prefHeight = popup.content.preferredSize.height
        popup.show(RelativePoint(event.component, Point(0, -prefHeight - 4)))
    }
}
