package com.storm3ds.emulator.ui.romdetails

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.MicSource
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.domain.model.rom.config.RomGbaSlotConfig
import com.storm3ds.emulator.domain.repositories.LayoutsRepository
import com.storm3ds.emulator.ui.romdetails.model.RomConfigUiModel
import com.storm3ds.emulator.ui.romdetails.model.RomGbaSlotConfigUiModel

class RomDetailsUiMapper(
    private val context: Context,
    private val layoutsRepository: LayoutsRepository,
) {

    suspend fun mapRomConfigToUi(
        romConfig: RomConfig,
        globalRuntimeConsoleType: ConsoleType,
        globalRuntimeMicSource: MicSource,
        globalVideoRenderer: VideoRenderer,
        globalThreadedRendering: Boolean,
        globalInternalResolutionScaling: Int,
        globalVideoFiltering: VideoFiltering,
        globalRetroArchShaderPresetPath: String?,
        globalRetroArchShaderParameters: String?,
        hasValidRetroArchShaderRoot: Boolean,
        globalRetroAchievementsEnabled: Boolean,
        globalArm9Overclock: Int = 1,
        globalFastForwardSpeedMultiplier: Float = 2f,
        globalVulkanDriverDisplayName: String = "System GPU",
        availableVulkanDrivers: List<Pair<String?, String>> = emptyList(),
        isCustomVulkanDriverSupported: Boolean = true,
        globalIsNew3ds: Boolean = true,
        globalCpuClockPercentage: Int = 100,
        globalRender3dMode: Int = 0,
        globalRender3dFactor: Int = 50,
        globalUseLimitFramesAheadOfGpu: Boolean = false,
        globalSkipDuplicateFrames: Boolean = false,
        globalDisableRightEyeRender: Boolean = false,
        globalTextureFilter: Int = 0,
        globalSimulate3dsGpuTimings: Boolean = false,
        globalSimulateHeadphonesPlugged: Boolean = false,
        globalEnableRealtimeAudio: Boolean = false,
    ): RomConfigUiModel {
        return RomConfigUiModel(
            runtimeConsoleType = romConfig.runtimeConsoleType,
            globalRuntimeConsoleType = globalRuntimeConsoleType,
            runtimeMicSource = romConfig.runtimeMicSource,
            globalRuntimeMicSource = globalRuntimeMicSource,
            layoutId = romConfig.layoutId,
            layoutName = romConfig.layoutId?.let { layoutsRepository.getLayout(it)?.name },
            globalLayoutName = layoutsRepository.getGlobalLayoutPlaceholder().name,
            gbaSlotConfig = mapGbaSlotConfigToUi(romConfig.gbaSlotConfig),
            customName = romConfig.customName,
            useHgEngineFix = romConfig.useHgEngineFix,
            inputMode = romConfig.inputMode,
            videoRenderer = romConfig.videoRenderer,
            globalVideoRenderer = globalVideoRenderer,
            threadedRendering = romConfig.threadedRendering,
            globalThreadedRendering = globalThreadedRendering,
            internalResolutionScaling = romConfig.internalResolutionScaling,
            globalInternalResolutionScaling = globalInternalResolutionScaling,
            videoFiltering = romConfig.videoFiltering,
            globalVideoFiltering = globalVideoFiltering,
            retroArchShaderPresetPath = romConfig.retroArchShaderPresetPath,
            globalRetroArchShaderPresetPath = globalRetroArchShaderPresetPath,
            retroArchShaderParameters = romConfig.retroArchShaderParameters,
            globalRetroArchShaderParameters = globalRetroArchShaderParameters,
            hasValidRetroArchShaderRoot = hasValidRetroArchShaderRoot,
            retroAchievementsEnabled = romConfig.retroAchievementsEnabled,
            globalRetroAchievementsEnabled = globalRetroAchievementsEnabled,
            dualScreenPreset = romConfig.dualScreenPreset,
            dualScreenKeepAspectRatio = romConfig.dualScreenKeepAspectRatio,
            dualScreenIntegerScale = romConfig.dualScreenIntegerScale,
            translatorSourceLang = romConfig.translatorSourceLang,
            arm9Overclock = romConfig.arm9Overclock,
            globalArm9Overclock = globalArm9Overclock,
            fastForwardSpeedMultiplier = romConfig.fastForwardSpeedMultiplier,
            globalFastForwardSpeedMultiplier = globalFastForwardSpeedMultiplier,
            customVulkanDriverName = romConfig.customVulkanDriverName,
            globalVulkanDriverDisplayName = globalVulkanDriverDisplayName,
            availableVulkanDrivers = availableVulkanDrivers,
            isCustomVulkanDriverSupported = isCustomVulkanDriverSupported,
            isNew3ds = romConfig.isNew3ds,
            globalIsNew3ds = globalIsNew3ds,
            cpuClockPercentage = romConfig.cpuClockPercentage,
            globalCpuClockPercentage = globalCpuClockPercentage,
            render3dMode = romConfig.render3dMode,
            globalRender3dMode = globalRender3dMode,
            render3dFactor = romConfig.render3dFactor,
            globalRender3dFactor = globalRender3dFactor,
            useLimitFramesAheadOfGpu = romConfig.useLimitFramesAheadOfGpu,
            globalUseLimitFramesAheadOfGpu = globalUseLimitFramesAheadOfGpu,
            skipDuplicateFrames = romConfig.skipDuplicateFrames,
            globalSkipDuplicateFrames = globalSkipDuplicateFrames,
            disableRightEyeRender = romConfig.disableRightEyeRender,
            globalDisableRightEyeRender = globalDisableRightEyeRender,
            textureFilter = romConfig.textureFilter,
            globalTextureFilter = globalTextureFilter,
            simulate3dsGpuTimings = romConfig.simulate3dsGpuTimings,
            globalSimulate3dsGpuTimings = globalSimulate3dsGpuTimings,
            simulateHeadphonesPlugged = romConfig.simulateHeadphonesPlugged,
            globalSimulateHeadphonesPlugged = globalSimulateHeadphonesPlugged,
            enableRealtimeAudio = romConfig.enableRealtimeAudio,
            globalEnableRealtimeAudio = globalEnableRealtimeAudio,
        )
    }

    private suspend fun mapGbaSlotConfigToUi(gbaSlotConfig: RomGbaSlotConfig): RomGbaSlotConfigUiModel {
        return when (gbaSlotConfig) {
            is RomGbaSlotConfig.None -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.None)
            is RomGbaSlotConfig.GbaRom -> withContext(Dispatchers.IO) {
                RomGbaSlotConfigUiModel(
                    type = RomGbaSlotConfigUiModel.Type.GbaRom,
                    gbaRomPath = gbaSlotConfig.romPath?.let { DocumentFile.fromSingleUri(context, it)?.name },
                    gbaSavePath = gbaSlotConfig.savePath?.let { DocumentFile.fromSingleUri(context, it)?.name },
                )
            }
            RomGbaSlotConfig.RumblePak -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.RumblePak)
            RomGbaSlotConfig.MemoryExpansion -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.MemoryExpansion)
            RomGbaSlotConfig.AnalogInput -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.AnalogInput)
        }
    }
}
