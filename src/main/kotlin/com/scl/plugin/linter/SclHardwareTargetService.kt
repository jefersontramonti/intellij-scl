package com.scl.plugin.linter

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Serviço de projeto que persiste o hardware target (S7-1200 / S7-1500)
 * em `.idea/sclHardwareTarget.xml`.
 *
 * Registro no plugin.xml:
 *   <projectService serviceImplementation="com.scl.plugin.linter.SclHardwareTargetService"/>
 *
 * Acesso:
 *   val hw = SclHardwareTargetService.getInstance(project).target
 */
@Service(Service.Level.PROJECT)
@State(
    name = "SclHardwareTarget",
    storages = [Storage("sclHardwareTarget.xml")]
)
class SclHardwareTargetService : PersistentStateComponent<SclHardwareTargetService.State> {

    // ── Estado serializável (campo @JvmField obrigatório para XmlSerializer) ──
    class State {
        @JvmField
        var target: String = SclHardwareTarget.S7_1200.name
    }

    private var myState = State()

    /** Hardware target atual. Padrão: S7-1200. */
    var target: SclHardwareTarget
        get() = runCatching { SclHardwareTarget.valueOf(myState.target) }
            .getOrDefault(SclHardwareTarget.S7_1200)
        set(value) { myState.target = value.name }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): SclHardwareTargetService = project.service()
    }
}
