package com.scl.plugin.linter

/**
 * Alvo de hardware para o linter SCL.
 *
 * O target é persistido por projeto em [SclHardwareTargetService]
 * e exibido / alterável na status bar via [SclHardwareStatusBarWidgetFactory].
 *
 * Regras de validação que dependem do target: ver [SclAnnotator].
 */
enum class SclHardwareTarget(val displayName: String) {
    S7_1200("S7-1200"),
    S7_1500("S7-1500")
}
