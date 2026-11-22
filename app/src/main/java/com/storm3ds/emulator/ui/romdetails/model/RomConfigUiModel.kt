package com.storm3ds.emulator.ui.romdetails.model

import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.MicSource
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.config.RomInputMode
import com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource
import java.util.UUID

data class RomConfigUiModel(
    val runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    val globalRuntimeConsoleType: ConsoleType = ConsoleType.DS,
    val runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    val globalRuntimeMicSource: MicSource = MicSource.BLOW,
    val layoutId: UUID? = null,
    val layoutName: String? = null,
    val globalLayoutName: String? = null,
    val gbaSlotConfig: RomGbaSlotConfigUiModel = RomGbaSlotConfigUiModel(),
    val customName: String? = null,
    val useHgEngineFix: Boolean = false,
    val inputMode: RomInputMode = RomInputMode.GLOBAL,
    val videoRenderer: VideoRenderer? = null,
    val globalVideoRenderer: VideoRenderer = VideoRenderer.SOFTWARE,
    val threadedRendering: Boolean? = null,
    val globalThreadedRendering: Boolean = true,
    val internalResolutionScaling: Int? = null,
    val globalInternalResolutionScaling: Int = 1,
    val videoFiltering: VideoFiltering? = null,
    val globalVideoFiltering: VideoFiltering = VideoFiltering.NONE,
    val retroArchShaderPresetPath: String? = null,
    val globalRetroArchShaderPresetPath: String? = null,
    val retroArchShaderParameters: String? = null,
    val globalRetroArchShaderParameters: String? = null,
    val hasValidRetroArchShaderRoot: Boolean = false,
    val retroAchievementsEnabled: Boolean? = null,
    val globalRetroAchievementsEnabled: Boolean = true,
    val dualScreenPreset: com.storm3ds.emulator.domain.model.DualScreenPreset? = null,
    val dualScreenKeepAspectRatio: Boolean? = null,
    val dualScreenIntegerScale: Boolean? = null,
    val translatorSourceLang: String? = null,
    val arm9Overclock: Int? = null,
    val globalArm9Overclock: Int = 1,
    val fastForwardSpeedMultiplier: Float? = null,
    val globalFastForwardSpeedMultiplier: Float = 2f,
    val customVulkanDriverName: String? = null,
    val globalVulkanDriverDisplayName: String = "System GPU",
    val availableVulkanDrivers: List<Pair<String?, String>> = emptyList(),
    val isCustomVulkanDriverSupported: Boolean = true,
    val isNew3ds: Boolean? = null,
    val globalIsNew3ds: Boolean = true,
    val cpuClockPercentage: Int? = null,
    val globalCpuClockPercentage: Int = 100,
    val render3dMode: Int? = null,
    val globalRender3dMode: Int = 0,
    val render3dFactor: Int? = null,
    val globalRender3dFactor: Int = 50,
)
