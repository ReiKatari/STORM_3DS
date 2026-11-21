package com.storm3ds.emulator.domain.model.emulator

import com.storm3ds.emulator.domain.model.VideoRenderer

sealed class EmulatorEvent {
    data class RumbleStart(val duration: Int) : EmulatorEvent()
    data object RumbleStop : EmulatorEvent()
    data class RendererInitFailed(val renderer: VideoRenderer) : EmulatorEvent()
    data class VulkanCompileProgress(
        val stageId: Int,
        val current: Int,
        val total: Int,
    ) : EmulatorEvent()
    data class Stop(val reason: Reason) : EmulatorEvent() {
        enum class Reason {
            GBAModeNotSupported,
            BadExceptionRegion,
            PowerOff,
        }
    }
}
