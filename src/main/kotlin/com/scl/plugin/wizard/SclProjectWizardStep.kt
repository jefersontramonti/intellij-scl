package com.scl.plugin.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class SclProjectWizardStep(
    private val builder: SclModuleBuilder
) : ModuleWizardStep() {

    private val panel = JPanel(GridBagLayout())

    private val rbS7_1200 = JRadioButton("S7-1200")
    private val rbS7_1500 = JRadioButton("S7-1500")

    private val rbEmpty = JRadioButton("Empty Project")
    private val rbBasic = JRadioButton("Basic FB + OB (recommended)")
    private val rbFsm   = JRadioButton("FB with State Machine + OB")

    private val descLabel = JLabel()

    init {
        ButtonGroup().apply { add(rbS7_1200); add(rbS7_1500) }
        rbS7_1200.isSelected = true

        ButtonGroup().apply { add(rbEmpty); add(rbBasic); add(rbFsm) }
        rbBasic.isSelected = true

        buildPanel()
        updateDesc()

        listOf(rbEmpty, rbBasic, rbFsm).forEach { it.addActionListener { updateDesc() } }
    }

    private fun updateDesc() {
        descLabel.text = when {
            rbEmpty.isSelected -> "Creates empty folder structure only (FBs / FCs / OBs / UDTs)"
            rbBasic.isSelected -> "Creates FB_Main.scl + OB_Main.scl + FC_Utils.scl + UDT_Config.scl"
            rbFsm.isSelected   -> "Creates FB with 4-state FSM (IDLE / RUNNING / FAULT / EMERGENCY) + OB_Main.scl"
            else -> ""
        }
    }

    private fun buildPanel() {
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill   = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 8, 4, 8)
        }

        var row = 0

        // ── CPU Target ────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2
        panel.add(JLabel("CPU Target").also { it.font = it.font.deriveFont(Font.BOLD) }, gbc)

        gbc.gridy = row; gbc.gridwidth = 1
        gbc.gridx = 0; panel.add(rbS7_1200, gbc)
        gbc.gridx = 1; panel.add(rbS7_1500, gbc)
        row++

        // ── Separator ────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.insets = Insets(8, 8, 8, 8)
        panel.add(JSeparator(), gbc)
        gbc.insets = Insets(4, 8, 4, 8)

        // ── Template ─────────────────────────────────────────────────
        gbc.gridy = row++
        panel.add(JLabel("Template").also { it.font = it.font.deriveFont(Font.BOLD) }, gbc)

        gbc.gridy = row++; panel.add(rbEmpty, gbc)
        gbc.gridy = row++; panel.add(rbBasic, gbc)
        gbc.gridy = row++; panel.add(rbFsm,   gbc)

        // ── Description ──────────────────────────────────────────────
        gbc.gridy = row
        gbc.insets = Insets(0, 24, 8, 8)
        descLabel.foreground = UIManager.getColor("Label.disabledForeground")
        panel.add(descLabel, gbc)
    }

    override fun getComponent(): JComponent = panel

    override fun updateDataModel() {
        builder.cpuTarget = if (rbS7_1500.isSelected) SclModuleBuilder.CpuTarget.S7_1500
                            else SclModuleBuilder.CpuTarget.S7_1200
        builder.template = when {
            rbEmpty.isSelected -> SclModuleBuilder.ProjectTemplate.EMPTY
            rbFsm.isSelected   -> SclModuleBuilder.ProjectTemplate.FSM
            else               -> SclModuleBuilder.ProjectTemplate.BASIC_FB_OB
        }
    }
}
