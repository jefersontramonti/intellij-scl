package com.scl.plugin.linter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class SclCpuConfigurable(private val project: Project) : Configurable {

    private lateinit var cpuFamilyCombo: JComboBox<String>
    private lateinit var firmwareCombo: JComboBox<String>

    override fun getDisplayName() = "SCL CPU Target"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply { insets = Insets(4, 8, 4, 8); anchor = GridBagConstraints.WEST }

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("CPU Family:"), gbc)

        cpuFamilyCombo = JComboBox(CpuFamily.values().map { it.displayName }.toTypedArray())
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(cpuFamilyCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Firmware:"), gbc)

        firmwareCombo = JComboBox(FirmwareVersion.values().map { it.displayName }.toTypedArray())
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(firmwareCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("<html><i>Affects linter rules for data types and instructions</i></html>"), gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.NORTH)
        return wrapper
    }

    override fun isModified(): Boolean {
        val s = SclCpuSettings.getInstance(project)
        return cpuFamilyCombo.selectedIndex != CpuFamily.values().indexOf(s.cpuFamily) ||
               firmwareCombo.selectedIndex != FirmwareVersion.values().indexOf(s.firmwareVersion)
    }

    override fun apply() {
        val s = SclCpuSettings.getInstance(project)
        val newFamily = CpuFamily.values()[cpuFamilyCombo.selectedIndex]
        s.setCpuFamily(newFamily)
        s.setFirmwareVersion(FirmwareVersion.values()[firmwareCombo.selectedIndex])
        // Sincroniza SclHardwareTargetService para que o widget e SclHardwareLinter vejam o mesmo target
        val hwSvc = SclHardwareTargetService.getInstance(project)
        hwSvc.target = if (newFamily == CpuFamily.S7_1500) SclHardwareTarget.S7_1500 else SclHardwareTarget.S7_1200
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun reset() {
        val s = SclCpuSettings.getInstance(project)
        cpuFamilyCombo.selectedIndex = CpuFamily.values().indexOf(s.cpuFamily)
        firmwareCombo.selectedIndex = FirmwareVersion.values().indexOf(s.firmwareVersion)
    }
}
