package com.storm3ds.emulator.ui.romdetails.model

import android.net.Uri
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.config.RomInputMode
import com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource
import java.util.UUID

sealed class RomConfigUpdateEvent {
    data class RuntimeConsoleUpdate(val newRuntimeConsole: RuntimeConsoleType) : RomConfigUpdateEvent()
    data class RuntimeMicSourceUpdate(val newRuntimeMicSource: RuntimeMicSource) : RomConfigUpdateEvent()
    data class UseHgEngineFixUpdate(val enabled: Boolean) : RomConfigUpdateEvent()
    data class InputModeUpdate(val inputMode: RomInputMode) : RomConfigUpdateEvent()
    data class LayoutUpdate(val newLayoutId: UUID?) : RomConfigUpdateEvent()
    data class GbaSlotTypeUpdated(val type: RomGbaSlotConfigUiModel.Type) : RomConfigUpdateEvent()
    data class GbaRomPathUpdate(val gbaRomPath: Uri?) : RomConfigUpdateEvent()
    data class GbaSavePathUpdate(val gbaSavePath: Uri?) : RomConfigUpdateEvent()
    data class CustomNameUpdate(val customName: String?) : RomConfigUpdateEvent()
    data class VideoRendererUpdate(val videoRenderer: VideoRenderer?) : RomConfigUpdateEvent()
    data class ThreadedRenderingUpdate(val threadedRendering: Boolean?) : RomConfigUpdateEvent()
    data class InternalResolutionScalingUpdate(val internalResolutionScaling: Int?) : RomConfigUpdateEvent()
    data class VideoFilteringUpdate(val videoFiltering: VideoFiltering?) : RomConfigUpdateEvent()
    data class RetroArchShaderPresetPathUpdate(val presetPath: String?) : RomConfigUpdateEvent()
    data class RetroArchShaderParametersUpdate(val parameters: String?) : RomConfigUpdateEvent()
    data class RetroAchievementsEnabledUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class DualScreenPresetUpdate(val preset: com.storm3ds.emulator.domain.model.DualScreenPreset?) : RomConfigUpdateEvent()
    data class TranslatorSourceLangUpdate(val sourceLang: String?) : RomConfigUpdateEvent()
    data class Arm9OverclockUpdate(val arm9Overclock: Int?) : RomConfigUpdateEvent()
    data class FastForwardSpeedMultiplierUpdate(val speedMultiplier: Float?) : RomConfigUpdateEvent()
    data class CustomVulkanDriverUpdate(val customVulkanDriverName: String?) : RomConfigUpdateEvent()
    data class IsNew3dsUpdate(val isNew3ds: Boolean?) : RomConfigUpdateEvent()
    data class CpuClockPercentageUpdate(val cpuClockPercentage: Int?) : RomConfigUpdateEvent()
    data class Render3dModeUpdate(val render3dMode: Int?) : RomConfigUpdateEvent()
    data class Render3dFactorUpdate(val render3dFactor: Int?) : RomConfigUpdateEvent()
    data class UseLimitFramesAheadOfGpuUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class SkipDuplicateFramesUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class DisableRightEyeRenderUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class TextureFilterUpdate(val filter: Int?) : RomConfigUpdateEvent()
    data class Simulate3dsGpuTimingsUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class SimulateHeadphonesPluggedUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class EnableRealtimeAudioUpdate(val enabled: Boolean?) : RomConfigUpdateEvent()
    data class ResetToDefaults(val preserveCustomName: Boolean = true) : RomConfigUpdateEvent()
}
