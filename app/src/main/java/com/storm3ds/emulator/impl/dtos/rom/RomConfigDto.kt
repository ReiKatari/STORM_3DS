package com.storm3ds.emulator.impl.dtos.rom

import com.google.gson.annotations.SerializedName
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.domain.model.rom.config.RomInputMode
import com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource
import com.storm3ds.emulator.impl.dtos.input.ControllerConfigurationDto
import java.util.UUID

data class RomConfigDto(
    @SerializedName("runtimeConsoleType")
    val runtimeConsoleType: RuntimeConsoleType? = null,
    @SerializedName("runtimeMicSource")
    val runtimeMicSource: RuntimeMicSource? = null,
    @SerializedName("layoutId")
    val layoutId: String? = null,
    @SerializedName("gbaSlotConfig")
    val gbaSlotConfig: RomGbaSlotConfigDto? = null,
    @SerializedName("customName")
    val customName: String? = null,
    @SerializedName(value = "useHgEngineFix", alternate = ["useHgInputWorkaround"])
    val useHgEngineFix: Boolean? = null,
    @SerializedName("inputMode")
    val inputMode: RomInputMode? = null,
    @SerializedName("customControllerConfiguration")
    val customControllerConfiguration: ControllerConfigurationDto? = null,
    @SerializedName("videoRenderer")
    val videoRenderer: VideoRenderer? = null,
    @SerializedName("threadedRendering")
    val threadedRendering: Boolean? = null,
    @SerializedName("internalResolutionScaling")
    val internalResolutionScaling: Int? = null,
    @SerializedName("videoFiltering")
    val videoFiltering: VideoFiltering? = null,
    @SerializedName("retroArchShaderPresetPath")
    val retroArchShaderPresetPath: String? = null,
    @SerializedName("retroArchShaderParameters")
    val retroArchShaderParameters: String? = null,
    @SerializedName("retroAchievementsEnabled")
    val retroAchievementsEnabled: Boolean? = null,
    @SerializedName("dualScreenPreset")
    val dualScreenPreset: com.storm3ds.emulator.domain.model.DualScreenPreset? = null,
    @SerializedName("dualScreenKeepAspectRatio")
    val dualScreenKeepAspectRatio: Boolean? = null,
    @SerializedName("dualScreenIntegerScale")
    val dualScreenIntegerScale: Boolean? = null,
    @SerializedName("translatorSourceLang")
    val translatorSourceLang: String? = null,
    @SerializedName("arm9Overclock")
    val arm9Overclock: Int? = null,
    @SerializedName("fastForwardSpeedMultiplier")
    val fastForwardSpeedMultiplier: Float? = null,
    @SerializedName("customVulkanDriverName")
    val customVulkanDriverName: String? = null,
    @SerializedName("isNew3ds")
    val isNew3ds: Boolean? = null,
    @SerializedName("cpuClockPercentage")
    val cpuClockPercentage: Int? = null,
    @SerializedName("render3dMode")
    val render3dMode: Int? = null,
    @SerializedName("render3dFactor")
    val render3dFactor: Int? = null,
    @SerializedName("useLimitFramesAheadOfGpu")
    val useLimitFramesAheadOfGpu: Boolean? = null,
    @SerializedName("skipDuplicateFrames")
    val skipDuplicateFrames: Boolean? = null,
    @SerializedName("disableRightEyeRender")
    val disableRightEyeRender: Boolean? = null,
    @SerializedName("textureFilter")
    val textureFilter: Int? = null,
    @SerializedName("simulate3dsGpuTimings")
    val simulate3dsGpuTimings: Boolean? = null,
    @SerializedName("simulateHeadphonesPlugged")
    val simulateHeadphonesPlugged: Boolean? = null,
    @SerializedName("enableRealtimeAudio")
    val enableRealtimeAudio: Boolean? = null,
) {

    companion object {
        fun fromModel(romConfig: RomConfig): RomConfigDto {
            return RomConfigDto(
                romConfig.runtimeConsoleType,
                romConfig.runtimeMicSource,
                romConfig.layoutId?.toString(),
                RomGbaSlotConfigDto.fromModel(romConfig.gbaSlotConfig),
                romConfig.customName,
                romConfig.useHgEngineFix,
                romConfig.inputMode,
                romConfig.customControllerConfiguration?.let { ControllerConfigurationDto.fromControllerConfiguration(it) },
                romConfig.videoRenderer,
                romConfig.threadedRendering,
                romConfig.internalResolutionScaling,
                romConfig.videoFiltering,
                romConfig.retroArchShaderPresetPath,
                romConfig.retroArchShaderParameters,
                romConfig.retroAchievementsEnabled,
                romConfig.dualScreenPreset,
                romConfig.dualScreenKeepAspectRatio,
                romConfig.dualScreenIntegerScale,
                romConfig.translatorSourceLang,
                romConfig.arm9Overclock,
                romConfig.fastForwardSpeedMultiplier,
                romConfig.customVulkanDriverName,
                romConfig.isNew3ds,
                romConfig.cpuClockPercentage,
                romConfig.render3dMode,
                romConfig.render3dFactor,
                romConfig.useLimitFramesAheadOfGpu,
                romConfig.skipDuplicateFrames,
                romConfig.disableRightEyeRender,
                romConfig.textureFilter,
                romConfig.simulate3dsGpuTimings,
                romConfig.simulateHeadphonesPlugged,
                romConfig.enableRealtimeAudio,
            )
        }
    }

    fun toModel(): RomConfig {
        return RomConfig(
            runtimeConsoleType = runtimeConsoleType ?: RuntimeConsoleType.DEFAULT,
            runtimeMicSource = runtimeMicSource ?: RuntimeMicSource.DEFAULT,
            layoutId = layoutId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            gbaSlotConfig = gbaSlotConfig?.toModel() ?: com.storm3ds.emulator.domain.model.rom.config.RomGbaSlotConfig.None,
            customName = customName,
            useHgEngineFix = useHgEngineFix ?: false,
            inputMode = inputMode ?: RomInputMode.GLOBAL,
            customControllerConfiguration = customControllerConfiguration?.toControllerConfiguration(),
            videoRenderer = videoRenderer,
            threadedRendering = threadedRendering,
            internalResolutionScaling = internalResolutionScaling,
            videoFiltering = videoFiltering,
            retroArchShaderPresetPath = retroArchShaderPresetPath,
            retroArchShaderParameters = retroArchShaderParameters,
            retroAchievementsEnabled = retroAchievementsEnabled,
            dualScreenPreset = dualScreenPreset,
            dualScreenKeepAspectRatio = dualScreenKeepAspectRatio,
            dualScreenIntegerScale = dualScreenIntegerScale,
            translatorSourceLang = translatorSourceLang,
            arm9Overclock = arm9Overclock,
            fastForwardSpeedMultiplier = fastForwardSpeedMultiplier,
            customVulkanDriverName = customVulkanDriverName,
            isNew3ds = isNew3ds,
            cpuClockPercentage = cpuClockPercentage,
            render3dMode = render3dMode,
            render3dFactor = render3dFactor,
            useLimitFramesAheadOfGpu = useLimitFramesAheadOfGpu,
            skipDuplicateFrames = skipDuplicateFrames,
            disableRightEyeRender = disableRightEyeRender,
            textureFilter = textureFilter,
            simulate3dsGpuTimings = simulate3dsGpuTimings,
            simulateHeadphonesPlugged = simulateHeadphonesPlugged,
            enableRealtimeAudio = enableRealtimeAudio,
        )
    }
}
