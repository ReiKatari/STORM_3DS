package com.storm3ds.emulator.domain.model.rom.config

import com.storm3ds.emulator.domain.model.ControllerConfiguration
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import java.util.*

data class RomConfig(
    val runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    val runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    val layoutId: UUID? = null,
    val gbaSlotConfig: RomGbaSlotConfig = RomGbaSlotConfig.None,
    val customName: String? = null,
    val useHgEngineFix: Boolean = false,
    val inputMode: RomInputMode = RomInputMode.GLOBAL,
    val customControllerConfiguration: ControllerConfiguration? = null,
    val videoRenderer: VideoRenderer? = null,
    val threadedRendering: Boolean? = null,
    val internalResolutionScaling: Int? = null,
    val videoFiltering: VideoFiltering? = null,
    val retroArchShaderPresetPath: String? = null,
    val retroArchShaderParameters: String? = null,
    val retroAchievementsEnabled: Boolean? = null,
    val dualScreenPreset: com.storm3ds.emulator.domain.model.DualScreenPreset? = null,
    val dualScreenKeepAspectRatio: Boolean? = null,
    val dualScreenIntegerScale: Boolean? = null,
    val translatorSourceLang: String? = null,
    val arm9Overclock: Int? = null,
    val fastForwardSpeedMultiplier: Float? = null,
    val customVulkanDriverName: String? = null,
    val isNew3ds: Boolean? = null,
    val cpuClockPercentage: Int? = null,
    val render3dMode: Int? = null,
    val render3dFactor: Int? = null,
    val useLimitFramesAheadOfGpu: Boolean? = null,
    val skipDuplicateFrames: Boolean? = null,
    val disableRightEyeRender: Boolean? = null,
    val textureFilter: Int? = null,
    val simulate3dsGpuTimings: Boolean? = null,
    val simulateHeadphonesPlugged: Boolean? = null,
    val enableRealtimeAudio: Boolean? = null,
) {

    fun getEffectiveControllerConfiguration(globalConfiguration: ControllerConfiguration): ControllerConfiguration {
        return if (inputMode == RomInputMode.CUSTOM) {
            customControllerConfiguration ?: globalConfiguration
        } else {
            globalConfiguration
        }
    }

    companion object {
        fun default() = RomConfig()

        fun forDsiWareTitle(): RomConfig {
            return RomConfig(
                runtimeConsoleType = RuntimeConsoleType.DSi,
                runtimeMicSource = RuntimeMicSource.DEFAULT,
                layoutId = null,
                gbaSlotConfig = RomGbaSlotConfig.None,
                customName = null,
                useHgEngineFix = false,
                inputMode = RomInputMode.GLOBAL,
                customControllerConfiguration = null,
                videoRenderer = null,
                threadedRendering = null,
                internalResolutionScaling = null,
                videoFiltering = null,
                retroArchShaderPresetPath = null,
                retroArchShaderParameters = null,
                retroAchievementsEnabled = null,
                dualScreenPreset = null,
                dualScreenKeepAspectRatio = null,
                dualScreenIntegerScale = null,
                translatorSourceLang = null,
                arm9Overclock = null,
                fastForwardSpeedMultiplier = null,
                customVulkanDriverName = null,
            )
        }
    }
}
