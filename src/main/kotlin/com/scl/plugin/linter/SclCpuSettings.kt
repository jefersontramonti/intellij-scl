package com.scl.plugin.linter

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

enum class CpuFamily(val displayName: String) {
    S7_1200("S7-1200"),
    S7_1500("S7-1500")
}

enum class FirmwareVersion(val displayName: String) {
    S7_1200_BEFORE_V4("S7-1200 < V4.0"),
    S7_1200_V4_0("S7-1200 V4.0+"),
    S7_1200_V4_1_PLUS("S7-1200 V4.1+"),
    S7_1500_ANY("S7-1500")
}

@Service(Service.Level.PROJECT)
@State(
    name = "SclCpuSettings",
    storages = [Storage("sclCpuSettings.xml")]
)
class SclCpuSettings : PersistentStateComponent<SclCpuSettings.State> {

    class State {
        @JvmField var cpuFamily: String = CpuFamily.S7_1200.name
        @JvmField var firmwareVersion: String = FirmwareVersion.S7_1200_V4_0.name
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    val cpuFamily: CpuFamily
        get() = runCatching { CpuFamily.valueOf(myState.cpuFamily) }.getOrDefault(CpuFamily.S7_1200)

    val firmwareVersion: FirmwareVersion
        get() = runCatching { FirmwareVersion.valueOf(myState.firmwareVersion) }.getOrDefault(FirmwareVersion.S7_1200_V4_0)

    fun setCpuFamily(v: CpuFamily) { myState.cpuFamily = v.name }
    fun setFirmwareVersion(v: FirmwareVersion) { myState.firmwareVersion = v.name }

    val isS7_1200 get() = cpuFamily == CpuFamily.S7_1200
    val isS7_1500 get() = cpuFamily == CpuFamily.S7_1500
    val hasFW4_1  get() = firmwareVersion == FirmwareVersion.S7_1200_V4_1_PLUS ||
                          firmwareVersion == FirmwareVersion.S7_1500_ANY
    val tempInitialized get() = firmwareVersion != FirmwareVersion.S7_1200_BEFORE_V4

    companion object {
        fun getInstance(project: Project): SclCpuSettings = project.service()
    }
}
